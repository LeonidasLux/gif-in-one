package com.leonidaslux.gifinone.update

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.leonidaslux.gifinone.BuildConfig
import org.json.JSONObject
import java.io.BufferedInputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {
  const val REPOSITORY = "LeonidasLux/gif-in-one"
  private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
  private const val GITHUB_ACCEPT = "application/vnd.github+json"
  private const val MAX_METADATA_BYTES = 2 * 1024 * 1024

  fun fetchLatest(): AppUpdateInfo {
    val release = JSONObject(readText(LATEST_RELEASE_URL, GITHUB_ACCEPT))
    if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
      throw AppUpdateException("最新发布不是正式版本。")
    }
    val assets = release.optJSONArray("assets")
      ?: throw AppUpdateException("Release 中没有安装资源。")
    val metadataAsset = findAsset(assets, "update.json")
      ?: throw AppUpdateException("Release 缺少 update.json 更新清单。")
    val metadataUrl = metadataAsset.optString("browser_download_url")
    if (!AppUpdateNetwork.isAllowedDownloadUrl(metadataUrl)) {
      throw AppUpdateException("Release 更新清单地址无效。")
    }
    val metadata = JSONObject(readText(metadataUrl, "application/octet-stream"))
    return parseRelease(release, metadata, currentAbiKey())
  }

  internal fun parseRelease(
    release: JSONObject,
    metadata: JSONObject,
    abiKey: String,
  ): AppUpdateInfo {
    val versionCode = metadata.optLong("versionCode", 0L)
    val versionName = metadata.optString("versionName").trim()
    if (versionCode <= 0L || versionName.isBlank()) {
      throw AppUpdateException("Release 更新清单中的版本信息无效。")
    }
    val releaseTagVersion = release.optString("tag_name").trim().removePrefix("v")
    if (releaseTagVersion.isNotBlank() && releaseTagVersion != versionName) {
      throw AppUpdateException("Release 标签与 update.json 版本不一致。")
    }

    val assetMetadata = metadata.optJSONObject("assets")?.optJSONObject(abiKey)
      ?: throw AppUpdateException("当前设备架构暂时没有可用的安装包。")
    val assetName = assetMetadata.optString("name").trim()
    val expectedSha256 = assetMetadata.optString("sha256").trim().lowercase()
    val expectedSize = assetMetadata.optLong("size", 0L)
    if (!expectedSha256.matches(Regex("[a-f0-9]{64}"))) {
      throw AppUpdateException("Release 安装包校验值无效。")
    }
    val assets = release.optJSONArray("assets")
      ?: throw AppUpdateException("Release 中没有安装资源。")
    val apkAsset = findAsset(assets, assetName)
      ?: throw AppUpdateException("Release 中缺少 $assetName。")
    val downloadUrl = apkAsset.optString("browser_download_url")
    if (!AppUpdateNetwork.isAllowedDownloadUrl(downloadUrl)) {
      throw AppUpdateException("Release 安装包地址无效。")
    }

    return AppUpdateInfo(
      versionName = versionName,
      versionCode = versionCode,
      releaseTitle = release.optString("name", versionName).ifBlank { versionName },
      releaseNotes = release.optString("body").trim(),
      publishedAt = release.optString("published_at"),
      releaseUrl = release.optString("html_url"),
      assetName = assetName,
      downloadUrl = downloadUrl,
      sha256 = expectedSha256,
      size = expectedSize.coerceAtLeast(0L),
    )
  }

  fun isNewer(update: AppUpdateInfo): Boolean = update.versionCode > BuildConfig.VERSION_CODE.toLong()

  fun downloadState(context: Context): AppUpdateDownloadState {
    val state = AppUpdateStore.snapshot(context)
    if (state.downloaded && state.versionName == BuildConfig.VERSION_NAME) {
      AppUpdateStore.downloadedFile(context, state.versionName).delete()
      AppUpdateStore.partialFile(context).delete()
      AppUpdateStore.markCancelled(context)
      return AppUpdateStore.snapshot(context)
    }
    return state
  }

  fun startDownload(context: Context, update: AppUpdateInfo): AppUpdateDownloadState {
    require(AppUpdateNetwork.isAllowedDownloadUrl(update.downloadUrl))
    require(update.sha256.matches(Regex("[a-f0-9]{64}")))
    AppUpdateStore.deleteDownloadFiles(context)
    val input = Data.Builder()
      .putString(UpdateDownloadWorker.INPUT_URL, update.downloadUrl)
      .putString(UpdateDownloadWorker.INPUT_VERSION, update.versionName)
      .putString(UpdateDownloadWorker.INPUT_SHA256, update.sha256)
      .putLong(UpdateDownloadWorker.INPUT_SIZE, update.size)
      .build()
    val request = OneTimeWorkRequest.Builder(UpdateDownloadWorker::class.java)
      .setInputData(input)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()
    AppUpdateStore.markQueued(context, update.versionName, request.id.toString(), update.size)
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      UpdateDownloadWorker.UNIQUE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
    return AppUpdateStore.snapshot(context)
  }

  fun cancelDownload(context: Context) {
    WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UpdateDownloadWorker.UNIQUE_WORK_NAME)
    AppUpdateStore.partialFile(context).delete()
    AppUpdateStore.downloadedFile(context).delete()
    AppUpdateStore.markCancelled(context)
  }

  private fun currentAbiKey(): String {
    val abis = Build.SUPPORTED_ABIS.map { it.lowercase() }
    return when {
      abis.any { it == "arm64-v8a" } -> "arm64-v8a"
      abis.any { it == "x86_64" } -> "x86_64"
      else -> throw AppUpdateException("当前设备架构不受支持。")
    }
  }

  private fun findAsset(assets: org.json.JSONArray, name: String): JSONObject? {
    for (index in 0 until assets.length()) {
      val asset = assets.optJSONObject(index) ?: continue
      if (asset.optString("name") == name && asset.optString("state") == "uploaded") return asset
    }
    return null
  }

  private fun readText(url: String, accept: String): String {
    val connection = AppUpdateNetwork.open(url, accept)
    return try {
      BufferedInputStream(connection.inputStream).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          total += read
          if (total > MAX_METADATA_BYTES) throw AppUpdateException("GitHub 更新信息过大。")
          output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
      }
    } finally {
      connection.disconnect()
    }
  }
}
