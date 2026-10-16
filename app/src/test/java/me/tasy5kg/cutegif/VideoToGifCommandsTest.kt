package me.tasy5kg.cutegif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoToGifCommandsTest {

  private val cropParams = CropParams(100, 50, 10, 20)

  @Test
  fun extractFramesCommandShouldMatchExpectedCommand() {
    val command = VideoToGifCommands.extractFramesCommand(
      inputPath = "/in/video.mp4",
      trimTime = null,
      cropParams = cropParams,
      shortLength = 0,
      outputSpeed = 1f,
      outputFps = 10,
      reverse = false,
      colorKey = null,
      textRenderPngPath = "/cache/add_text.png",
      outputFramesPath = "/cache/frames/",
    )
    val expected =
      "-hwaccel auto -hide_banner -benchmark -an  -i \"/in/video.mp4\" -i \"/cache/add_text.png\" " +
        "-filter_complex \"[0:v] setpts=PTS/1.0,fps=fps=10 [0vPreprocessed]; " +
        "[0vPreprocessed][1:v] overlay=0:0,crop=100:50:10:20\" \"/cache/frames/%06d.bmp\""
    assertEquals(expected, command)
  }

  @Test
  fun extractFramesCommandShouldIncludeTrimScaleColorKeyAndReverse() {
    val command = VideoToGifCommands.extractFramesCommand(
      inputPath = "/in/video.mp4",
      trimTime = 200 to 900,
      cropParams = CropParams(50, 100, 2, 3),
      shortLength = 40,
      outputSpeed = 2f,
      outputFps = 16,
      reverse = true,
      colorKey = "FF00FF" to 30,
      textRenderPngPath = "/cache/add_text.png",
      outputFramesPath = "/cache/frames/",
    )
    val expected =
      "-hwaccel auto -hide_banner -benchmark -an -ss 200ms -to 900ms  -i \"/in/video.mp4\" -i \"/cache/add_text.png\" " +
        "-filter_complex \"[0:v] setpts=PTS/2.0,fps=fps=16 [0vPreprocessed]; " +
        "[0vPreprocessed][1:v] overlay=0:0,crop=50:100:2:3,scale=40:-2:flags=lanczos,colorkey=#FF00FF:0.3:0,reverse\" \"/cache/frames/%06d.bmp\""
    assertEquals(expected, command)
  }

  @Test
  fun extractFramesCommandScalesLandscapeWithMinus2LongSide() {
    val command = VideoToGifCommands.extractFramesCommand(
      inputPath = "/in/video.mp4",
      trimTime = null,
      cropParams = CropParams(100, 50, 0, 0),
      shortLength = 40,
      outputSpeed = 1f,
      outputFps = 10,
      reverse = false,
      colorKey = null,
      textRenderPngPath = "/cache/add_text.png",
      outputFramesPath = "/cache/frames/",
    )
    assertTrue(command.contains("crop=100:50:0:0,scale=-2:40:flags=lanczos"))
  }

  @Test
  fun extractFramesCommandShouldNotScaleWhenShortLengthIsNotSmaller() {
    val command = VideoToGifCommands.extractFramesCommand(
      inputPath = "/in/video.mp4",
      trimTime = null,
      cropParams = CropParams(100, 50, 0, 0),
      shortLength = 50,
      outputSpeed = 1f,
      outputFps = 10,
      reverse = false,
      colorKey = null,
      textRenderPngPath = "/cache/add_text.png",
      outputFramesPath = "/cache/frames/",
    )
    assertTrue(command.contains("overlay=0:0,crop=100:50:0:0\""))
    assertTrue(!command.contains("scale="))
  }

  @Test
  fun createPaletteCommandShouldMatchExpectedCommand() {
    val command = VideoToGifCommands.createPaletteCommand(64, "/cache/frames/", "/cache/palette.png")
    assertEquals(
      "-hwaccel auto -hide_banner -benchmark -an -i \"/cache/frames/%06d.bmp\" " +
        "-vf palettegen=max_colors=64:stats_mode=diff -y \"/cache/palette.png\"",
      command
    )
  }

  @Test
  fun framesToGifCommandShouldMatchExpectedCommand() {
    val command = VideoToGifCommands.framesToGifCommand(10, "/cache/frames/", "/cache/palette.png", -1, "/cache/output_temp.gif")
    assertEquals(
      "-hwaccel auto -hide_banner -benchmark -an -framerate 10 -i \"/cache/frames/%06d.bmp\" -i \"/cache/palette.png\" " +
        "-filter_complex paletteuse=dither=bayer -final_delay -1 -y \"/cache/output_temp.gif\"",
      command
    )
  }

  @Test
  fun estimateOutputFramesShouldUseTrimRangeWhenPresent() {
    assertEquals(8, VideoToGifCommands.estimateOutputFrames(0 to 800, 1600, 10, 1f))
    assertEquals(2, VideoToGifCommands.estimateOutputFrames(0 to 400, 1600, 10, 2f))
  }

  @Test
  fun estimateOutputFramesShouldUseWholeDurationWhenNotTrimmed() {
    assertEquals(8, VideoToGifCommands.estimateOutputFrames(null, 1600, 5, 1f))
    assertEquals(3, VideoToGifCommands.estimateOutputFrames(null, 1000, 3, 1f))
    assertEquals(5, VideoToGifCommands.estimateOutputFrames(null, 1500, 3, 1f))
  }
}