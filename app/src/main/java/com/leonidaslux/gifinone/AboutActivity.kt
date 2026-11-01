package com.leonidaslux.gifinone

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import com.leonidaslux.gifinone.databinding.ActivityAboutBinding
import com.leonidaslux.gifinone.toolbox.Toolbox.logRed
import com.leonidaslux.gifinone.toolbox.Toolbox.onClick
import com.leonidaslux.gifinone.toolbox.Toolbox.openLink
import com.leonidaslux.gifinone.toolbox.Toolbox.setupTextViewWithClickablePart
import com.leonidaslux.gifinone.toolbox.Toolbox.toast
import com.leonidaslux.gifinone.update.AppUpdateDialogs
import com.leonidaslux.gifinone.update.AppUpdateException
import com.leonidaslux.gifinone.update.AppUpdateInfo
import com.leonidaslux.gifinone.update.AppUpdateInstaller
import com.leonidaslux.gifinone.update.AppUpdateManager
import com.leonidaslux.gifinone.update.AppUpdateStore
import com.leonidaslux.gifinone.update.InstallPermissionRequiredException
import kotlin.concurrent.thread

class AboutActivity : BaseActivity() {
  private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }
  private val mainHandler = Handler(Looper.getMainLooper())
  private var availableUpdate: AppUpdateInfo? = null
  private var checkingForUpdates = false
  private val updateStatePoller = object : Runnable {
    override fun run() {
      refreshUpdateState()
      if (AppUpdateManager.downloadState(this@AboutActivity).active) {
        mainHandler.postDelayed(this, 800L)
      }
    }
  }

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setContentView(binding.root)
    setFinishOnTouchOutside(true)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      binding.mbChooseFileWay13.visibility = View.GONE
    }
    binding.mbtgChooseFileWay.apply {
      check(
        when (MySettings.fileOpenWay) {
          MySettings.INT_FILE_OPEN_WAY_DOCUMENT -> binding.mbChooseFileWayDocument.id
          MySettings.INT_FILE_OPEN_WAY_GALLERY -> binding.mbChooseFileWayGallery.id
          MySettings.INT_FILE_OPEN_WAY_13 -> binding.mbChooseFileWay13.id
          else -> throw IllegalArgumentException()
        }
      )
      addOnButtonCheckedListener { _, checkedId, isChecked ->
        performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
        if (isChecked) {
          MySettings.fileOpenWay = when (checkedId) {
            binding.mbChooseFileWayDocument.id -> MySettings.INT_FILE_OPEN_WAY_DOCUMENT
            binding.mbChooseFileWayGallery.id -> MySettings.INT_FILE_OPEN_WAY_GALLERY
            binding.mbChooseFileWay13.id -> MySettings.INT_FILE_OPEN_WAY_13
            else -> throw IllegalArgumentException()
          }
        }
      }
    }
    binding.mbDone.onClick {
      finish()
    }
    binding.mbCheckUpdates.onClick {
      checkForUpdates()
    }
    binding.mbInstallUpdate.onClick {
      handleUpdateAction()
    }
    binding.mbCancelUpdateDownload.onClick {
      AppUpdateManager.cancelDownload(this@AboutActivity)
      availableUpdate = null
      refreshUpdateState()
      toast(R.string.update_download_cancelled)
    }
    binding.mtvLegalInfo.setupTextViewWithClickablePart(
      "${getString(R.string.whats_new)} | ${getString(R.string.eula_and_privacy_policy)} | ${getString(R.string.open_source_license)}", listOf(
        getString(R.string.whats_new) to { WhatsNewActivity.start(this) },
        getString(R.string.eula_and_privacy_policy) to { EulaActivity.start(this) },
        getString(R.string.open_source_license) to { OpenSourceLicenseActivity.start(this) }
      ), true
    )
    binding.mtvVersionInfo.text = getString(R.string.version_X, BuildConfig.VERSION_NAME)
    binding.mtvBasedOn.setupTextViewWithClickablePart(
      getString(R.string.based_on_cutegif_), listOf(
        "CuteGIF" to { openLink(this, getString(R.string._cutegif_github_url)) }
      ), true
    )
    refreshUpdateState()
  }

  private fun checkForUpdates() {
    if (checkingForUpdates) return
    checkingForUpdates = true
    binding.mbCheckUpdates.isEnabled = false
    binding.mtvUpdateStatus.text = getString(R.string.checking_for_updates)
    thread {
      try {
        val latest = AppUpdateManager.fetchLatest()
        runOnUiThread {
          if (isFinishing || isDestroyed) return@runOnUiThread
          checkingForUpdates = false
          binding.mbCheckUpdates.isEnabled = true
          if (AppUpdateManager.isNewer(latest)) {
            availableUpdate = latest
            refreshUpdateState()
            showUpdateDialog()
          } else {
            availableUpdate = null
            refreshUpdateState()
            binding.mtvUpdateStatus.text = getString(R.string.up_to_date)
          }
        }
      } catch (e: Exception) {
        logRed("checkForUpdates", e.stackTraceToString())
        runOnUiThread {
          if (isFinishing || isDestroyed) return@runOnUiThread
          checkingForUpdates = false
          binding.mbCheckUpdates.isEnabled = true
          refreshUpdateState()
          binding.mtvUpdateStatus.text = getString(R.string.update_check_failed_s, e.message ?: "")
        }
      }
    }
  }

  private fun refreshUpdateState() {
    val state = AppUpdateManager.downloadState(this)
    binding.mbCheckUpdates.isEnabled = !checkingForUpdates && !state.active
    binding.linearUpdateProgress.visibility = GONE
    binding.llUpdateActions.visibility = GONE
    binding.mbCancelUpdateDownload.visibility = GONE
    when {
      state.active -> {
        binding.linearUpdateProgress.visibility = VISIBLE
        binding.llUpdateActions.visibility = VISIBLE
        binding.mbCancelUpdateDownload.visibility = VISIBLE
        binding.mbInstallUpdate.visibility = GONE
        binding.linearUpdateProgress.isIndeterminate = state.phase == AppUpdateStore.PHASE_VERIFYING ||
          state.phase == AppUpdateStore.PHASE_RETRYING
        binding.linearUpdateProgress.setProgressCompat(state.percent, true)
        binding.mtvUpdateStatus.text = when (state.phase) {
          AppUpdateStore.PHASE_VERIFYING -> getString(R.string.verifying_update)
          AppUpdateStore.PHASE_RETRYING -> getString(R.string.retrying_update_download)
          else -> if (state.totalBytes > 0) {
            getString(R.string.downloading_update_d_percent, state.percent)
          } else {
            getString(R.string.downloading_update)
          }
        }
        if (AppUpdateManager.downloadState(this).active) scheduleUpdatePoll()
      }

      state.downloaded -> {
        binding.llUpdateActions.visibility = VISIBLE
        binding.mbInstallUpdate.visibility = VISIBLE
        binding.mbInstallUpdate.setText(R.string.install_update)
        binding.mtvUpdateStatus.text = getString(R.string.update_downloaded_version, state.versionName)
      }

      availableUpdate != null -> {
        binding.llUpdateActions.visibility = VISIBLE
        binding.mbInstallUpdate.visibility = VISIBLE
        binding.mbInstallUpdate.setText(R.string.download_update)
        binding.mtvUpdateStatus.text = getString(R.string.update_available_title, availableUpdate!!.versionName)
      }

      state.phase == AppUpdateStore.PHASE_FAILED && state.error.isNotBlank() -> {
        binding.mtvUpdateStatus.text = getString(R.string.update_check_failed_s, state.error)
      }

      !checkingForUpdates -> binding.mtvUpdateStatus.text = getString(R.string.check_for_updates)
    }
  }

  private fun handleUpdateAction() {
    val state = AppUpdateManager.downloadState(this)
    if (state.downloaded) {
      installDownloadedUpdate()
      return
    }
    val update = availableUpdate ?: return
    AppUpdateManager.startDownload(this, update)
    refreshUpdateState()
  }

  private fun showUpdateDialog() {
    val update = availableUpdate ?: return
    AppUpdateDialogs.showAvailable(
      activity = this,
      update = update,
      state = AppUpdateManager.downloadState(this),
      onDownload = {
        AppUpdateManager.startDownload(this, update)
        refreshUpdateState()
      },
      onInstall = ::installDownloadedUpdate,
    )
  }

  private fun installDownloadedUpdate() {
    try {
      AppUpdateInstaller.install(this)
    } catch (_: InstallPermissionRequiredException) {
      AppUpdateInstaller.openInstallPermissionSettings(this)
      toast(R.string.update_install_permission_required)
    } catch (e: AppUpdateException) {
      toast(getString(R.string.update_install_failed_s, e.message ?: ""))
    }
  }

  private fun scheduleUpdatePoll() {
    mainHandler.removeCallbacks(updateStatePoller)
    mainHandler.postDelayed(updateStatePoller, 800L)
  }

  override fun onResume() {
    super.onResume()
    refreshUpdateState()
  }

  override fun onPause() {
    mainHandler.removeCallbacks(updateStatePoller)
    super.onPause()
  }

  override fun onDestroy() {
    mainHandler.removeCallbacks(updateStatePoller)
    super.onDestroy()
  }

  companion object {
    fun start(context: Context) {
      context.startActivity(Intent(context, AboutActivity::class.java))
    }
  }
}
