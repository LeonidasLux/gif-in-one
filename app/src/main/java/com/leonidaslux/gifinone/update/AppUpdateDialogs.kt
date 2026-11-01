package com.leonidaslux.gifinone.update

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leonidaslux.gifinone.MySettings
import com.leonidaslux.gifinone.R

object AppUpdateDialogs {
  fun showAvailable(
    activity: Activity,
    update: AppUpdateInfo,
    state: AppUpdateDownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
  ) {
    val alreadyDownloaded = state.downloaded && state.versionName == update.versionName
    val notes = update.releaseNotes.ifBlank { activity.getString(R.string.no_release_notes) }
    MaterialAlertDialogBuilder(activity)
      .setTitle(activity.getString(R.string.update_available_title, update.versionName))
      .setMessage(
        "${activity.getString(R.string.update_available_hint)}\n\n" +
          "${activity.getString(R.string.release_notes)}\n\n$notes"
      )
      .setNegativeButton(R.string.later) { _, _ ->
        MySettings.dismissedUpdateVersion = update.versionName
      }
      .setPositiveButton(if (alreadyDownloaded) R.string.install_update else R.string.download_update) { _, _ ->
        MySettings.dismissedUpdateVersion = null
        if (alreadyDownloaded) onInstall() else onDownload()
      }
      .show()
  }
}
