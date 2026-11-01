package com.leonidaslux.gifinone.update

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.Locale

class UpdateDownloadWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
  override fun doWork(): Result {
    val context = applicationContext
    val workId = id.toString()
    if (!AppUpdateStore.isCurrentWork(context, workId)) return Result.success()

    val url = inputData.getString(INPUT_URL).orEmpty()
    val versionName = inputData.getString(INPUT_VERSION).orEmpty().trim()
    val expectedSha256 = inputData.getString(INPUT_SHA256).orEmpty().trim().lowercase(Locale.ROOT)
    val expectedSize = inputData.getLong(INPUT_SIZE, 0L).coerceAtLeast(0L)
    if (versionName.isBlank() ||
      !AppUpdateNetwork.isAllowedDownloadUrl(url) ||
      !expectedSha256.matches(Regex("[a-f0-9]{64}"))
    ) {
      return fail(workId, "更新下载参数无效。")
    }

    AppUpdateStore.markRunning(context, workId)
    val existing = AppUpdateStore.downloadedFile(context, versionName)
    if (existing.isFile) {
      try {
        verifyChecksum(existing, expectedSha256)
        validateApk(existing)
        AppUpdateStore.markDownloaded(context, workId, existing)
        return Result.success()
      } catch (_: UpdateFailure) {
        existing.delete()
      }
    }

    return try {
      val partial = AppUpdateStore.partialFile(context)
      var existingBytes = if (partial.isFile) partial.length() else 0L
      if (expectedSize > 0L && existingBytes == expectedSize) {
        finishDownload(context, workId, versionName, partial, expectedSha256)
        return Result.success()
      }
      if (expectedSize > 0L && existingBytes > expectedSize) {
        partial.delete()
        existingBytes = 0L
      }
      if (existingBytes > MAX_APK_BYTES || expectedSize > MAX_APK_BYTES) {
        partial.delete()
        throw UpdateFailure("安装包超过 350 MB，已取消下载。", retryable = false)
      }

      val connection = AppUpdateNetwork.open(url, "application/octet-stream", existingBytes)
      try {
        if (existingBytes > 0L && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
          existingBytes = 0L
          partial.delete()
        }
        val contentLength = connection.contentLengthLong.coerceAtLeast(0L)
        val totalBytes = if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
          totalBytesFromContentRange(connection.getHeaderField("Content-Range"), expectedSize)
        } else {
          maxOf(contentLength, expectedSize)
        }
        if (totalBytes > MAX_APK_BYTES) {
          throw UpdateFailure("安装包超过 350 MB，已取消下载。", retryable = false)
        }

        var downloadedBytes = existingBytes
        var lastPersistedAt = 0L
        BufferedInputStream(connection.inputStream).use { input ->
          BufferedOutputStream(FileOutputStream(partial, existingBytes > 0L)).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              if (isStopped) throw StoppedException()
              val read = input.read(buffer)
              if (read < 0) break
              downloadedBytes += read
              if (downloadedBytes > MAX_APK_BYTES) {
                throw UpdateFailure("安装包超过 350 MB，已取消下载。", retryable = false)
              }
              output.write(buffer, 0, read)
              val now = System.currentTimeMillis()
              if (now - lastPersistedAt >= 800L) {
                persistProgress(context, workId, downloadedBytes, totalBytes)
                lastPersistedAt = now
              }
            }
          }
        }
        persistProgress(context, workId, downloadedBytes, totalBytes)
      } finally {
        connection.disconnect()
      }

      AppUpdateStore.markVerifying(context, workId, partial.length())
      finishDownload(context, workId, versionName, partial, expectedSha256)
      Result.success()
    } catch (_: StoppedException) {
      Result.success()
    } catch (failure: UpdateFailure) {
      handleFailure(workId, failure)
    } catch (exception: Exception) {
      if (isStopped) {
        Result.success()
      } else {
        handleFailure(workId, UpdateFailure("更新下载中断，请检查网络后重试。", retryable = true, cause = exception))
      }
    }
  }

  private fun finishDownload(
    context: Context,
    workId: String,
    versionName: String,
    partial: File,
    expectedSha256: String,
  ) {
    verifyChecksum(partial, expectedSha256)
    validateApk(partial)
    val downloaded = AppUpdateStore.downloadedFile(context, versionName)
    if (downloaded.exists()) downloaded.delete()
    if (!partial.renameTo(downloaded)) {
      throw UpdateFailure("无法保存已下载的安装包。", retryable = false)
    }
    cleanupOldPackages(context, downloaded)
    if (!AppUpdateStore.isCurrentWork(context, workId)) {
      downloaded.delete()
      return
    }
    AppUpdateStore.markDownloaded(context, workId, downloaded)
  }

  private fun persistProgress(context: Context, workId: String, downloaded: Long, total: Long) {
    AppUpdateStore.markProgress(context, workId, downloaded, total)
    setProgressAsync(
      Data.Builder()
        .putLong("bytesDownloaded", downloaded)
        .putLong("totalBytes", total)
        .build()
    )
  }

  private fun verifyChecksum(file: File, expectedSha256: String) {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
      val buffer = ByteArray(32 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xFF) }
    if (actual != expectedSha256) {
      file.delete()
      throw UpdateFailure("安装包 SHA-256 校验失败，请重新下载。", retryable = false)
    }
  }

  private fun validateApk(apk: File) {
    val packageManager = applicationContext.packageManager
    val archiveInfo = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
      ?: throw UpdateFailure("下载的文件不是有效 APK。", retryable = false)
    if (archiveInfo.packageName != applicationContext.packageName) {
      apk.delete()
      throw UpdateFailure("下载的安装包不属于 GIF in One。", retryable = false)
    }
    val incoming = archiveInfo.longVersionCode
    val installed = applicationContext.packageManager
      .getPackageInfo(applicationContext.packageName, 0)
      .longVersionCode
    if (incoming <= installed) {
      apk.delete()
      throw UpdateFailure("下载的安装包没有高于当前版本。", retryable = false)
    }
  }

  private fun totalBytesFromContentRange(value: String?, fallback: Long): Long {
    val total = value?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
    return total ?: fallback
  }

  private fun cleanupOldPackages(context: Context, keep: File) {
    AppUpdateStore.updateDir(context).listFiles()?.forEach { file ->
      if (file != keep && file.isFile && (file.extension == "apk" || file.extension == "part")) file.delete()
    }
  }

  private fun handleFailure(workId: String, failure: UpdateFailure): Result {
    val context = applicationContext
    if (!AppUpdateStore.isCurrentWork(context, workId)) return Result.success()
    if (failure.retryable && runAttemptCount < MAX_RETRY_ATTEMPTS) {
      AppUpdateStore.markRetrying(context, workId, failure.message)
      return Result.retry()
    }
    return fail(workId, failure.message ?: "更新下载失败。")
  }

  private fun fail(workId: String, message: String): Result {
    if (!AppUpdateStore.isCurrentWork(applicationContext, workId)) return Result.success()
    AppUpdateStore.markFailed(applicationContext, workId, message)
    return Result.failure(Data.Builder().putString("error", message).build())
  }

  private class StoppedException : Exception()

  private class UpdateFailure(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
  ) : Exception(message, cause)

  companion object {
    const val UNIQUE_WORK_NAME = "gif-in-one-apk-update"
    const val INPUT_URL = "url"
    const val INPUT_VERSION = "version"
    const val INPUT_SHA256 = "sha256"
    const val INPUT_SIZE = "size"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val MAX_APK_BYTES = 350L * 1024L * 1024L
  }
}
