package com.leonidaslux.gifinone

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leonidaslux.gifinone.update.AppUpdateManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateLogicTest {
  private val sha256 = "a".repeat(64)

  private fun releaseJson() = JSONObject(
    """
      {
        "tag_name": "v26.1101.1834",
        "name": "GIF in One v26.1101.1834",
        "body": "### 本次更新\n\n- 测试更新",
        "published_at": "2026-11-01T10:34:00Z",
        "html_url": "https://github.com/LeonidasLux/gif-in-one/releases/tag/v26.1101.1834",
        "assets": [
          {
            "name": "gif-in-one-arm64-v8a.apk",
            "state": "uploaded",
            "browser_download_url": "https://github.com/LeonidasLux/gif-in-one/releases/download/v26.1101.1834/gif-in-one-arm64-v8a.apk"
          },
          {
            "name": "update.json",
            "state": "uploaded",
            "browser_download_url": "https://github.com/LeonidasLux/gif-in-one/releases/download/v26.1101.1834/update.json"
          }
        ]
      }
    """.trimIndent()
  )

  private fun metadataJson() = JSONObject(
    """
      {
        "versionName": "26.1101.1834",
        "versionCode": 261102834,
        "assets": {
          "arm64-v8a": {
            "name": "gif-in-one-arm64-v8a.apk",
            "size": 123456,
            "sha256": "$sha256"
          }
        }
      }
    """.trimIndent()
  )

  @Test
  fun parsesArm64ReleaseMetadata() {
    val update = AppUpdateManager.parseRelease(releaseJson(), metadataJson(), "arm64-v8a")

    assertEquals("26.1101.1834", update.versionName)
    assertEquals(261102834L, update.versionCode)
    assertEquals("gif-in-one-arm64-v8a.apk", update.assetName)
    assertEquals(sha256, update.sha256)
    assertEquals(123456L, update.size)
  }

  @Test(expected = Exception::class)
  fun rejectsMissingDeviceAbi() {
    AppUpdateManager.parseRelease(releaseJson(), metadataJson(), "x86_64")
  }

  @Test(expected = Exception::class)
  fun rejectsMetadataVersionThatDoesNotMatchReleaseTag() {
    val release = releaseJson().put("tag_name", "v26.1101.1900")
    AppUpdateManager.parseRelease(release, metadataJson(), "arm64-v8a")
  }
}
