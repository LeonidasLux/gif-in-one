package com.leonidaslux.gifinone.update

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

internal object AppUpdateNetwork {
  private const val USER_AGENT = "GIFinOne-Android-Updater"
  private const val MAX_REDIRECTS = 6

  fun open(
    rawUrl: String,
    accept: String,
    rangeFrom: Long = 0L,
  ): HttpURLConnection {
    var currentUrl = URL(rawUrl)
    repeat(MAX_REDIRECTS + 1) {
      requireAllowedHttpsUrl(currentUrl)
      val connection = currentUrl.openConnection() as HttpURLConnection
      connection.instanceFollowRedirects = false
      connection.connectTimeout = 20_000
      connection.readTimeout = 35_000
      connection.useCaches = false
      connection.setRequestProperty("Accept", accept)
      connection.setRequestProperty("User-Agent", USER_AGENT)
      if (rangeFrom > 0L) connection.setRequestProperty("Range", "bytes=$rangeFrom-")

      when (val status = connection.responseCode) {
        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL -> return connection
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308, -> {
          val location = connection.getHeaderField("Location")
          connection.disconnect()
          if (location.isNullOrBlank()) throw AppUpdateException("GitHub 下载跳转地址缺失。")
          currentUrl = URL(currentUrl, location)
        }

        else -> {
          connection.disconnect()
          val message = when (status) {
            404 -> "GitHub 尚未发布可安装版本。"
            403, 429 -> "GitHub 请求过于频繁，请稍后重试。"
            else -> "GitHub 请求失败（HTTP $status）。"
          }
          throw AppUpdateException(message)
        }
      }
    }
    throw AppUpdateException("GitHub 跳转次数过多。")
  }

  fun isAllowedDownloadUrl(value: String): Boolean = try {
    val uri = Uri.parse(value)
    uri.scheme.equals("https", ignoreCase = true) &&
      (uri.host.equals("github.com", ignoreCase = true) ||
        uri.host.equals("api.github.com", ignoreCase = true))
  } catch (_: Exception) {
    false
  }

  private fun requireAllowedHttpsUrl(url: URL) {
    val host = url.host.lowercase()
    val allowed = url.protocol.equals("https", ignoreCase = true) &&
      (host == "github.com" ||
        host == "api.github.com" ||
        host == "raw.githubusercontent.com" ||
        host == "objects.githubusercontent.com" ||
        host == "release-assets.githubusercontent.com" ||
        host.endsWith(".githubusercontent.com"))
    if (!allowed) throw AppUpdateException("GitHub 返回了不安全的下载地址。")
  }
}
