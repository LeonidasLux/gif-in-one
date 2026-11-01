package com.leonidaslux.gifinone

import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.leonidaslux.gifinone.toolbox.MediaTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** End-to-end Motion Photo generation using the real FFmpeg build on the device. */
@RunWith(AndroidJUnit4::class)
class MotionPhotoPipelineTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val testDir = File(context.cacheDir, "motion_photo_pipeline_test").apply {
    deleteRecursively()
    mkdirs()
  }

  private fun assertCommandSuccess(command: String) {
    val session = FFmpegKit.execute(command)
    assertTrue("command failed: $command\n${session.allLogsAsString}", session.returnCode.isValueSuccess)
  }

  private fun createPipeline(transcodeCommand: String): File {
    val mp4 = File(testDir, "motion.mp4")
    val cover = File(testDir, "cover.jpg")
    val output = File(testDir, "motion_photo.jpg")
    assertCommandSuccess(transcodeCommand)
    assertCommandSuccess(MotionPhotoCommands.extractCoverCommand(mp4.path, cover.path))

    FileOutputStream(output).use { outputStream ->
      FileInputStream(cover).use { coverInput ->
        FileInputStream(mp4).use { videoInput ->
          MotionPhotoFormat.write(coverInput, videoInput, outputStream, mp4.length())
        }
      }
    }
    return output
  }

  @Test
  fun videoShouldBecomeExtractableMotionPhoto() {
    val source = File(testDir, "source_video.mp4")
    assertCommandSuccess(
      "-hide_banner -f lavfi -i testsrc2=duration=1:size=96x64:rate=15 -f lavfi -i sine=frequency=440:duration=1 " +
        "-shortest -c:v libx264 -pix_fmt yuv420p -c:a aac -y \"${source.path}\""
    )
    val result = createPipeline(MotionPhotoCommands.videoToMp4Command(source.path, File(testDir, "motion.mp4").path))
    val videoStart = MotionPhotoFormat.findEmbeddedMp4Start(result.readBytes())
    val mp4 = File(testDir, "motion.mp4")

    assertEquals((result.length() - mp4.length()).toInt(), videoStart)
    val xmp = ExifInterface(result.path).getAttribute(ExifInterface.TAG_XMP)
    assertTrue(xmp?.contains("<Item:Semantic>MotionPhoto</Item:Semantic>") == true)
    val extracted = File(testDir, "extracted_video.mp4")
    assertTrue(MediaTools.extractVideoFromMvimg(result.path, extracted.path))
    assertTrue(MediaTools.getVideoDurationMsByFFmpeg(extracted.path)!! in 900..1100)
  }

  @Test
  fun gifShouldBecomeExtractableMotionPhoto() {
    val source = File(testDir, "source.gif")
    assertCommandSuccess("-hide_banner -f lavfi -i testsrc=duration=1:size=64x48:rate=5 -y \"${source.path}\"")
    val result = createPipeline(MotionPhotoCommands.gifToMp4Command(source.path, File(testDir, "motion.mp4").path))
    val videoStart = MotionPhotoFormat.findEmbeddedMp4Start(result.readBytes())
    val mp4 = File(testDir, "motion.mp4")

    assertEquals((result.length() - mp4.length()).toInt(), videoStart)
    val extracted = File(testDir, "extracted_gif_video.mp4")
    assertTrue(MediaTools.extractVideoFromMvimg(result.path, extracted.path))
    assertTrue(MediaTools.getVideoDurationMsByFFmpeg(extracted.path)!! in 900..1100)
  }
}
