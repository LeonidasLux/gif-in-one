package com.leonidaslux.gifinone

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.activity.result.contract.ActivityResultContracts
import com.leonidaslux.gifinone.MySettings.INT_FILE_OPEN_WAY_13
import com.leonidaslux.gifinone.MySettings.INT_FILE_OPEN_WAY_DOCUMENT
import com.leonidaslux.gifinone.MySettings.INT_FILE_OPEN_WAY_GALLERY
import com.leonidaslux.gifinone.databinding.ActivityMainBinding
import com.leonidaslux.gifinone.toolbox.FileTools.copyToInputFileDir
import com.leonidaslux.gifinone.toolbox.Toolbox.enableDropFile
import com.leonidaslux.gifinone.toolbox.Toolbox.logRed
import com.leonidaslux.gifinone.toolbox.Toolbox.onClick
import com.leonidaslux.gifinone.toolbox.Toolbox.toast
import com.leonidaslux.gifinone.update.AppUpdateDialogs
import com.leonidaslux.gifinone.update.AppUpdateException
import com.leonidaslux.gifinone.update.AppUpdateInfo
import com.leonidaslux.gifinone.update.AppUpdateInstaller
import com.leonidaslux.gifinone.update.AppUpdateManager
import com.leonidaslux.gifinone.update.InstallPermissionRequiredException
import kotlin.concurrent.thread

class MainActivity : BaseActivity() {
  private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
  private val mainHandler = Handler(Looper.getMainLooper())
  private var availableUpdate: AppUpdateInfo? = null
  private var startedUpdateCheck = false
  private val updateStatePoller = object : Runnable {
    override fun run() {
      refreshUpdateBanner()
      if (AppUpdateManager.downloadState(this@MainActivity).active) {
        mainHandler.postDelayed(this, 800L)
      }
    }
  }

  private fun importFileTryCatch(function: () -> Unit) {
    try {
      function.invoke()
    } catch (e: Exception) {
      logRed("importFileFailed", e)
      e.printStackTrace()
      runOnUiThread { toast(R.string.import_file_failed_please_try) }
    }
  }

  private val arlImportVideoToGifDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { VideoToGifActivity.start(this, it.copyToInputFileDir()) } }
  }
  private val arlImportVideoToGifElse = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { VideoToGifActivity.start(this, uri.copyToInputFileDir()) } }
  }
  private val arlImportVideoToMotionPhotoDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { MotionPhotoActivity.startForVideo(this, it.copyToInputFileDir()) } }
  }
  private val arlImportVideoToMotionPhotoElse = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { MotionPhotoActivity.startForVideo(this, uri.copyToInputFileDir()) } }
  }

  private val arlImportGifToMotionPhotoDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { MotionPhotoActivity.startForGif(this, it.copyToInputFileDir()) } }
  }
  private val arlImportGifToMotionPhoto13 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { MotionPhotoActivity.startForGif(this, uri.copyToInputFileDir()) } }
  }

  private val arlImportGifSplitDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { GifSplitActivity.start(this, it.copyToInputFileDir()) } }
  }
  private val arlImportGifSplit13 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { GifSplitActivity.start(this, uri.copyToInputFileDir()) } }
  }
  private val arlImportGifEditDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { GifEditPrepareActivity.start(this, it.copyToInputFileDir()) } }
  }
  private val arlImportGifEdit13 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { GifEditPrepareActivity.start(this, uri.copyToInputFileDir()) } }
  }
  private val arlImportGifToVideoDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { GifToVideoActivity.start(this, it.copyToInputFileDir()) } }
  }
  private val arlImportGifToVideo13 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { GifToVideoActivity.start(this, uri.copyToInputFileDir()) } }
  }
  private val arlImportMvimgToGifDocument = registerForActivityResult(ActivityResultContracts.GetContent()) {
    it?.let { _ -> importFileTryCatch { ImportMvimgActivity.start(this, it.copyToInputFileDir()) } }
  }
  private val arlImportMvimgToGif13 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    it?.data?.data?.let { uri -> importFileTryCatch { ImportMvimgActivity.start(this, uri.copyToInputFileDir()) } }
  }

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setContentView(binding.root)
    setSupportActionBar(binding.materialToolbar)
    binding.materialToolbar.subtitle = getString(R.string.version_X, BuildConfig.VERSION_NAME)
    binding.mbUpdateAction.onClick {
      handleUpdateAction()
    }
    binding.mbUpdateDismiss.onClick {
      availableUpdate?.let { MySettings.dismissedUpdateVersion = it.versionName }
      binding.mcvUpdateBanner.visibility = GONE
    }
    binding.mcvVideoToGif.apply {
      onClick { importVideoToGif() }
      enableDropFile(this@MainActivity, "video/*") {
        VideoToGifActivity.start(
          this@MainActivity, it.copyToInputFileDir()
        )
      }
    }
    binding.mcvVideoToMotionPhoto.apply {
      onClick { importVideoToMotionPhoto() }
      enableDropFile(this@MainActivity, "video/*") {
        MotionPhotoActivity.startForVideo(this@MainActivity, it.copyToInputFileDir())
      }
    }
    binding.mcvGifToMotionPhoto.apply {
      onClick { importGifToMotionPhoto() }
      enableDropFile(this@MainActivity, "image/gif") {
        MotionPhotoActivity.startForGif(this@MainActivity, it.copyToInputFileDir())
      }
    }
    binding.mcvGifEdit.apply {
      onClick { importForGifEdit() }
      enableDropFile(this@MainActivity, "image/gif") {
        GifEditPrepareActivity.start(
          this@MainActivity, it.copyToInputFileDir()
        )
      }
    }
    binding.mcvGifSplit.apply {
      onClick { importForGifSplit() }
      enableDropFile(this@MainActivity, "image/gif") {
        GifSplitActivity.start(
          this@MainActivity, it.copyToInputFileDir()
        )
      }
    }
    binding.mcvGifToVideo.apply {
      onClick { importForGifToVideo() }
      enableDropFile(this@MainActivity, "image/gif") {
        GifToVideoActivity.start(
          this@MainActivity, it.copyToInputFileDir()
        )
      }
    }
    binding.mcvMvimgToVideo.apply {
      onClick { importForMvimgToGif() }
      enableDropFile(this@MainActivity, "image/jpeg") {
        ImportMvimgActivity.start(
          this@MainActivity, it.copyToInputFileDir()
        )
      }
    }

    @Suppress("DEPRECATION")
    val uriFromActionViewOrSend = intent?.extras?.getParcelable(Intent.EXTRA_STREAM) ?: intent.data
    if (uriFromActionViewOrSend != null) {
      VideoToGifActivity.start(this, uriFromActionViewOrSend.copyToInputFileDir())
    }
    if (!MySettings.whatsNewRead) WhatsNewActivity.start(this)
    refreshUpdateBanner()
    checkForUpdatesAutomatically()
  }

  private fun checkForUpdatesAutomatically() {
    if (startedUpdateCheck) return
    startedUpdateCheck = true
    thread {
      try {
        val latest = AppUpdateManager.fetchLatest()
        runOnUiThread {
          if (isFinishing || isDestroyed) return@runOnUiThread
          if (!AppUpdateManager.isNewer(latest)) return@runOnUiThread
          availableUpdate = latest
          refreshUpdateBanner()
          val state = AppUpdateManager.downloadState(this)
          val alreadyHandled = state.downloaded && state.versionName == latest.versionName ||
            state.active && state.versionName == latest.versionName
          if (!alreadyHandled && MySettings.dismissedUpdateVersion != latest.versionName) {
            showUpdateDialog()
          }
        }
      } catch (e: Exception) {
        logRed("automaticUpdateCheck", e.stackTraceToString())
      }
    }
  }

  private fun refreshUpdateBanner() {
    val state = AppUpdateManager.downloadState(this)
    binding.linearUpdateProgress.visibility = GONE
    binding.mbUpdateDismiss.visibility = VISIBLE
    when {
      state.active -> {
        binding.mcvUpdateBanner.visibility = VISIBLE
        binding.mtvUpdateTitle.setText(R.string.update_banner_downloading)
        binding.mtvUpdateMessage.text = if (state.totalBytes > 0) {
          getString(R.string.downloading_update_d_percent, state.percent)
        } else {
          getString(R.string.downloading_update)
        }
        binding.mbUpdateDismiss.visibility = GONE
        binding.linearUpdateProgress.visibility = VISIBLE
        binding.linearUpdateProgress.isIndeterminate = false
        binding.linearUpdateProgress.setProgressCompat(state.percent, true)
        binding.mbUpdateAction.setText(R.string.view_update)
        mainHandler.removeCallbacks(updateStatePoller)
        mainHandler.postDelayed(updateStatePoller, 800L)
      }

      state.downloaded -> {
        binding.mcvUpdateBanner.visibility = VISIBLE
        binding.mtvUpdateTitle.setText(R.string.update_banner_downloaded)
        binding.mtvUpdateMessage.text = getString(R.string.update_downloaded_version, state.versionName)
        binding.mbUpdateDismiss.visibility = GONE
        binding.mbUpdateAction.setText(R.string.install_update)
      }

      availableUpdate != null -> {
        binding.mcvUpdateBanner.visibility = VISIBLE
        binding.mtvUpdateTitle.setText(R.string.update_banner_available)
        binding.mtvUpdateMessage.text = getString(R.string.update_available_title, availableUpdate!!.versionName)
        binding.mbUpdateAction.setText(R.string.view_update)
      }

      else -> binding.mcvUpdateBanner.visibility = GONE
    }
  }

  private fun handleUpdateAction() {
    val state = AppUpdateManager.downloadState(this)
    when {
      state.downloaded -> installDownloadedUpdate()
      state.active -> AboutActivity.start(this)
      availableUpdate != null -> showUpdateDialog()
    }
  }

  private fun showUpdateDialog() {
    val update = availableUpdate ?: return
    AppUpdateDialogs.showAvailable(
      activity = this,
      update = update,
      state = AppUpdateManager.downloadState(this),
      onDownload = {
        AppUpdateManager.startDownload(this, update)
        refreshUpdateBanner()
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

  private fun importVideoToGif() {
    when (MySettings.fileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportVideoToGifDocument.launch("video/*")
      INT_FILE_OPEN_WAY_GALLERY -> arlImportVideoToGifElse.launch(Intent(
        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      ).apply {
        type = "video/*"
      })

      INT_FILE_OPEN_WAY_13 -> arlImportVideoToGifElse.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "video/*"
      })
    }
  }

  private fun importVideoToMotionPhoto() {
    when (MySettings.fileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportVideoToMotionPhotoDocument.launch("video/*")
      INT_FILE_OPEN_WAY_GALLERY -> arlImportVideoToMotionPhotoElse.launch(Intent(
        Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      ).apply {
        type = "video/*"
      })

      INT_FILE_OPEN_WAY_13 -> arlImportVideoToMotionPhotoElse.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "video/*"
      })
    }
  }

  private fun importGifToMotionPhoto(intFileOpenWay: Int = MySettings.fileOpenWay) {
    when (intFileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportGifToMotionPhotoDocument.launch("image/gif")
      INT_FILE_OPEN_WAY_13 -> arlImportGifToMotionPhoto13.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "image/gif"
      })

      else -> importGifToMotionPhoto(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) INT_FILE_OPEN_WAY_13 else INT_FILE_OPEN_WAY_DOCUMENT
      )
    }
  }

  private fun importForGifSplit(intFileOpenWay: Int = MySettings.fileOpenWay) {
    when (intFileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportGifSplitDocument.launch("image/gif")
      INT_FILE_OPEN_WAY_13 -> arlImportGifSplit13.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "image/gif"
      })

      else -> importForGifSplit(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) INT_FILE_OPEN_WAY_13 else INT_FILE_OPEN_WAY_DOCUMENT
      )
    }
  }

  private fun importForGifEdit(intFileOpenWay: Int = MySettings.fileOpenWay) {
    when (intFileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportGifEditDocument.launch("image/gif")
      INT_FILE_OPEN_WAY_13 -> arlImportGifEdit13.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "image/gif"
      })

      else -> importForGifEdit(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) INT_FILE_OPEN_WAY_13 else INT_FILE_OPEN_WAY_DOCUMENT
      )
    }
  }

  private fun importForGifToVideo(intFileOpenWay: Int = MySettings.fileOpenWay) {
    when (intFileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportGifToVideoDocument.launch("image/gif")
      INT_FILE_OPEN_WAY_13 -> arlImportGifToVideo13.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "image/gif"
      })

      else -> importForGifToVideo(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) INT_FILE_OPEN_WAY_13 else INT_FILE_OPEN_WAY_DOCUMENT
      )
    }
  }

  private fun importForMvimgToGif(intFileOpenWay: Int = MySettings.fileOpenWay) {
    when (intFileOpenWay) {
      INT_FILE_OPEN_WAY_DOCUMENT -> arlImportMvimgToGifDocument.launch("image/jpeg")
      INT_FILE_OPEN_WAY_13 -> arlImportMvimgToGif13.launch(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "image/jpeg"
      })

      else -> importForMvimgToGif(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) INT_FILE_OPEN_WAY_13 else INT_FILE_OPEN_WAY_DOCUMENT
      )
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.toolbar_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_item_about -> AboutActivity.start(this)
    }
    return true
  }

  override fun onResume() {
    super.onResume()
    refreshUpdateBanner()
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
      context.startActivity(Intent(context, MainActivity::class.java))
    }
  }
}
