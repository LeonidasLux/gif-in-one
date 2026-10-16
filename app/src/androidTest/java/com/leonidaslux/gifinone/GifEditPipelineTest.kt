package com.leonidaslux.gifinone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.leonidaslux.gifinone.MyConstants.OUTPUT_GIF_TEMP_PATH
import com.leonidaslux.gifinone.MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH
import com.leonidaslux.gifinone.toolbox.FileTools.resetDirectory
import com.leonidaslux.gifinone.toolbox.MediaTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** End-to-end guard for the "edit an existing GIF" export pipeline, running real FFmpeg on the device. */
@RunWith(AndroidJUnit4::class)
class GifEditPipelineTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val sourceGifPath = "${context.cacheDir.canonicalPath}/gif_edit_test_source.gif"

  private fun assertCommandSuccess(command: String) {
    val session = FFmpegKit.execute(command)
    assertTrue("command failed: $command\n${session.allLogsAsString}", session.returnCode.isValueSuccess)
  }

  /** Creates a 64x48, 5 fps, 8 frames (1.6 s) test GIF. */
  private fun createTestGif() {
    assertCommandSuccess("-hide_banner -f lavfi -i testsrc=duration=1.6:size=64x48:rate=5 -y \"$sourceGifPath\"")
  }

  private fun runExportPipeline(taskBuilder: TaskBuilderVideoToGif) {
    resetDirectory(VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH)
    assertCommandSuccess(taskBuilder.getCommandExtractFrame())
    assertCommandSuccess(taskBuilder.getCommandCreatePalette())
    assertCommandSuccess(taskBuilder.getCommandVideoToGif())
    assertTrue(File(OUTPUT_GIF_TEMP_PATH).length() > 0)
  }

  @Test
  fun probesShouldReadGifInformation() {
    createTestGif()
    val duration = MediaTools.getVideoDurationMsByFFmpeg(sourceGifPath)!!
    assertTrue("unexpected duration: $duration", duration in 1500..1700)
    assertEquals(Pair(64, 48), MediaTools.getVideoWidthHeightByFFmpeg(sourceGifPath))
    assertEquals(8, MediaTools.getFrameCountByFFprobe(sourceGifPath))
    assertEquals(5.0, MediaTools.getGifFps(sourceGifPath)!!, 0.2)
  }

  @Test
  fun exportPipelineShouldExportTrimmedGifFromGifInput() {
    createTestGif()
    runExportPipeline(
      TaskBuilderVideoToGif(
        trimTime = 0 to 800,
        inputVideoPath = sourceGifPath,
        cropParams = CropParams(64, 48, 0, 0),
        shortLength = 48,
        outputSpeed = 1f,
        outputFps = 5,
        colorQuality = 64,
        reverse = false,
        textRender = null,
        lossy = null,
        videoWH = 64 to 48,
        duration = 1600,
        finalDelay = -1,
        colorKey = null,
      )
    )
    assertEquals(4, MediaTools.getFrameCountByFFprobe(OUTPUT_GIF_TEMP_PATH))
    val outputDuration = MediaTools.getVideoDurationMsByFFmpeg(OUTPUT_GIF_TEMP_PATH)!!
    assertTrue("unexpected output duration: $outputDuration", outputDuration in 700..900)
  }

  @Test
  fun exportPipelineShouldExportFullGifWithoutTrim() {
    createTestGif()
    runExportPipeline(
      TaskBuilderVideoToGif(
        trimTime = null,
        inputVideoPath = sourceGifPath,
        cropParams = CropParams(64, 48, 0, 0),
        shortLength = 48,
        outputSpeed = 1f,
        outputFps = 5,
        colorQuality = 64,
        reverse = false,
        textRender = null,
        lossy = null,
        videoWH = 64 to 48,
        duration = 1600,
        finalDelay = -1,
        colorKey = null,
      )
    )
    assertEquals(8, MediaTools.getFrameCountByFFprobe(OUTPUT_GIF_TEMP_PATH))
  }

  @Test
  fun gifsicleShouldCompressGif() {
    createTestGif()
    val inputPath = "${context.cacheDir.canonicalPath}/gifsicle_test_in.gif"
    val outputPath = "${context.cacheDir.canonicalPath}/gifsicle_test_out.gif"
    File(sourceGifPath).copyTo(File(inputPath), overwrite = true)
    assertTrue(MediaTools.gifsicleLossy(70, inputPath, outputPath, true))
    assertTrue(File(outputPath).length() > 0)
  }
}