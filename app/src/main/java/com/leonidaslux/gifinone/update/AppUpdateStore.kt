package com.leonidaslux.gifinone.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.io.File

@SuppressLint("ApplySharedPref")
object AppUpdateStore {
  const val PHASE_IDLE = "idle"
  const val PHASE_QUEUED = "queued"
  const val PHASE_DOWNLOADING = "downloading"
  const val PHASE_VERIFYING = "verifying"
  const val PHASE_RETRYING = "retrying"
  const val PHASE_DOWNLOADED = "downloaded"
  const val PHASE_FAILED = "failed"
  const val PHASE_CANCELLED = "cancelled"

  private const val PREFS_NAME = "gif_in_one_app_update_v1"
  private const val KEY_VERSION = "version"
  private const val KEY_PHASE = "phase"
  private const val KEY_WORK_ID = "workId"
  private const val KEY_BYTES = "bytesDownloaded"
  private const val KEY_TOTAL = "totalBytes"
  private const val KEY_ERROR = "error"
  private const val KEY_UPDATED_AT = "updatedAt"
  private val lock = Any()

  private fun preferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun updateDir(context: Context): File =
    File(context.applicationContext.filesDir, "updates").apply { mkdirs() }

  fun partialFile(context: Context): File = File(updateDir(context), "gif-in-one-update.part")

  fun downloadedFile(context: Context, versionName: String = preferences(context).getString(KEY_VERSION, "").orEmpty()): File =
    File(updateDir(context), "gif-in-one-${safeVersion(versionName)}.apk")

  fun markQueued(context: Context, versionName: String, workId: String, totalBytes: Long) = synchronized(lock) {
    preferences(context).edit()
      .clear()
      .putString(KEY_VERSION, versionName)
      .putString(KEY_PHASE, PHASE_QUEUED)
      .putString(KEY_WORK_ID, workId)
      .putLong(KEY_BYTES, 0L)
      .putLong(KEY_TOTAL, totalBytes.coerceAtLeast(0L))
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun isCurrentWork(context: Context, workId: String): Boolean =
    workId == preferences(context).getString(KEY_WORK_ID, "")

  fun markRunning(context: Context, workId: String) = synchronized(lock) {
    if (!isCurrentWork(context, workId)) return@synchronized
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_DOWNLOADING)
      .remove(KEY_ERROR)
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun markProgress(context: Context, workId: String, bytesDownloaded: Long, totalBytes: Long) = synchronized(lock) {
    if (!isCurrentWork(context, workId)) return@synchronized
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_DOWNLOADING)
      .putLong(KEY_BYTES, bytesDownloaded.coerceAtLeast(0L))
      .putLong(KEY_TOTAL, totalBytes.coerceAtLeast(0L))
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .apply()
  }

  fun markVerifying(context: Context, workId: String, bytes: Long) = synchronized(lock) {
    if (!isCurrentWork(context, workId)) return@synchronized
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_VERIFYING)
      .putLong(KEY_BYTES, bytes.coerceAtLeast(0L))
      .remove(KEY_ERROR)
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun markRetrying(context: Context, workId: String, error: String?) = synchronized(lock) {
    if (!isCurrentWork(context, workId)) return@synchronized
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_RETRYING)
      .putString(KEY_ERROR, error.orEmpty())
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun markDownloaded(context: Context, workId: String, apk: File) = synchronized(lock) {
    if (!isCurrentWork(context, workId)) return@synchronized
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_DOWNLOADED)
      .putLong(KEY_BYTES, apk.length())
      .putLong(KEY_TOTAL, apk.length())
      .remove(KEY_ERROR)
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun markFailed(context: Context, workId: String, error: String?) = synchronized(lock) {
    if (!isCurrentWork(context, workId)) return@synchronized
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_FAILED)
      .putString(KEY_ERROR, error.orEmpty())
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun markCancelled(context: Context) = synchronized(lock) {
    preferences(context).edit()
      .putString(KEY_PHASE, PHASE_CANCELLED)
      .putString(KEY_WORK_ID, "")
      .remove(KEY_ERROR)
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .commit()
  }

  fun snapshot(context: Context): AppUpdateDownloadState {
    val preferences = preferences(context)
    var phase = preferences.getString(KEY_PHASE, PHASE_IDLE) ?: PHASE_IDLE
    var error = preferences.getString(KEY_ERROR, "").orEmpty()
    val versionName = preferences.getString(KEY_VERSION, "").orEmpty()
    var downloadedBytes = preferences.getLong(KEY_BYTES, 0L)
    var totalBytes = preferences.getLong(KEY_TOTAL, 0L)
    if (phase == PHASE_DOWNLOADED) {
      val apk = downloadedFile(context, versionName)
      if (apk.isFile) {
        downloadedBytes = apk.length()
        totalBytes = apk.length()
      } else {
        phase = PHASE_FAILED
        error = "安装包已不存在，请重新下载。"
      }
    }
    return AppUpdateDownloadState(
      versionName = versionName,
      phase = phase,
      workId = preferences.getString(KEY_WORK_ID, "").orEmpty(),
      bytesDownloaded = downloadedBytes,
      totalBytes = totalBytes,
      error = error,
      updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L),
    )
  }

  fun deleteDownloadFiles(context: Context) {
    updateDir(context).listFiles()?.forEach { file ->
      if (file.isFile && (file.extension == "apk" || file.extension == "part")) file.delete()
    }
  }

  fun safeVersion(value: String): String =
    value.trim().replace(Regex("[^a-zA-Z0-9._+-]"), "-").ifBlank { "unknown" }
}
