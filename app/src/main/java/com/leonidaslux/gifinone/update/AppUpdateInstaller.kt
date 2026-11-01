package com.leonidaslux.gifinone.update

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider

object AppUpdateInstaller {
  private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

  fun canInstallPackages(activity: Activity): Boolean =
    activity.packageManager.canRequestPackageInstalls()

  fun openInstallPermissionSettings(activity: Activity) {
    activity.startActivity(
      Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${activity.packageName}"),
      )
    )
  }

  @Throws(InstallPermissionRequiredException::class, AppUpdateException::class)
  fun install(activity: Activity) {
    if (!canInstallPackages(activity)) throw InstallPermissionRequiredException()
    val state = AppUpdateStore.snapshot(activity)
    if (!state.downloaded) throw AppUpdateException("安装包尚未下载完成。")
    val apk = AppUpdateStore.downloadedFile(activity, state.versionName)
    if (!apk.isFile) throw AppUpdateException("安装包已不存在，请重新下载。")
    validateApk(activity, apk)
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, APK_MIME_TYPE)
      clipData = ClipData.newRawUri("GIF in One APK", uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(intent)
  }

  private fun validateApk(activity: Activity, apk: java.io.File) {
    val packageManager = activity.packageManager
    val incoming = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
      ?: throw AppUpdateException("下载的文件不是有效 APK。")
    if (incoming.packageName != activity.packageName) {
      apk.delete()
      AppUpdateStore.markCancelled(activity)
      throw AppUpdateException("下载的安装包不属于 GIF in One。")
    }
    val installed = try {
      packageManager.getPackageInfo(activity.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
      throw AppUpdateException("无法读取当前应用版本。", e)
    }
    val incomingCode = incoming.longVersionCodeCompat()
    val installedCode = installed.longVersionCodeCompat()
    if (incomingCode <= 0L) throw AppUpdateException("安装包版本信息无效。")
    if (incomingCode < installedCode) {
      throw AppUpdateException("Android 不允许直接覆盖安装低版本；请先备份数据并卸载当前版本。")
    }
  }

  private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long = longVersionCode
}
