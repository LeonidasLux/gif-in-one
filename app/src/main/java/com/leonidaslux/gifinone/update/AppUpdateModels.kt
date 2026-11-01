package com.leonidaslux.gifinone.update

data class AppUpdateInfo(
  val versionName: String,
  val versionCode: Long,
  val releaseTitle: String,
  val releaseNotes: String,
  val publishedAt: String,
  val releaseUrl: String,
  val assetName: String,
  val downloadUrl: String,
  val sha256: String,
  val size: Long,
)

data class AppUpdateDownloadState(
  val versionName: String,
  val phase: String,
  val workId: String,
  val bytesDownloaded: Long,
  val totalBytes: Long,
  val error: String,
  val updatedAt: Long,
) {
  val active get() = phase in setOf(
    AppUpdateStore.PHASE_QUEUED,
    AppUpdateStore.PHASE_DOWNLOADING,
    AppUpdateStore.PHASE_VERIFYING,
    AppUpdateStore.PHASE_RETRYING,
  )

  val downloaded get() = phase == AppUpdateStore.PHASE_DOWNLOADED

  val percent get() = if (totalBytes > 0) {
    (bytesDownloaded * 100.0 / totalBytes).toInt().coerceIn(0, 100)
  } else {
    0
  }
}

class AppUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

class InstallPermissionRequiredException : Exception()
