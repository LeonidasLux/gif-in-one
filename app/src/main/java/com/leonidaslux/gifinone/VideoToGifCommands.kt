package com.leonidaslux.gifinone

import com.leonidaslux.gifinone.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import com.leonidaslux.gifinone.toolbox.Toolbox.toEmptyStringIf
import kotlin.math.ceil
import kotlin.math.min

/**
 * Pure FFmpeg command-string builders for the "video/gif -> gif" export pipeline.
 * All functions are free of Android dependencies so they can be covered by unit tests.
 */
object VideoToGifCommands {

  fun extractFramesCommand(
    inputPath: String,
    trimTime: Pair<Int, Int>?,
    cropParams: CropParams,
    shortLength: Int,
    outputSpeed: Float,
    outputFps: Int,
    reverse: Boolean,
    colorKey: Pair<String, Int>?,
    textRenderPngPath: String,
    outputFramesPath: String,
  ) =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN ${trimTime?.let { "-ss ${it.first}ms -to ${it.second}ms " } ?: ""} -i \"$inputPath\" -i \"$textRenderPngPath\" " +
      "-filter_complex \"[0:v] setpts=PTS/$outputSpeed,fps=fps=$outputFps [0vPreprocessed]; " +
      "[0vPreprocessed][1:v] overlay=0:0," + cropParams.toFFmpegCropCommand() + resolutionParams(
      cropParams, shortLength
    ) + (colorKey?.let { ",colorkey=#${it.first}:${it.second / 100f}:0" } ?: "") + (",reverse").toEmptyStringIf { !reverse } +
      "\" \"${outputFramesPath}%06d.bmp\""

  fun createPaletteCommand(colorQuality: Int, framesPath: String, palettePath: String) =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${framesPath}%06d.bmp\" " +
      "-vf palettegen=max_colors=$colorQuality:stats_mode=diff -y \"$palettePath\""

  fun framesToGifCommand(outputFps: Int, framesPath: String, palettePath: String, finalDelay: Int, outputGifPath: String) =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -framerate $outputFps -i \"${framesPath}%06d.bmp\" -i \"$palettePath\" " +
      "-filter_complex paletteuse=dither=bayer -final_delay $finalDelay -y \"$outputGifPath\""

  fun estimateOutputFrames(trimTime: Pair<Int, Int>?, duration: Int, outputFps: Int, outputSpeed: Float) =
    ceil((trimTime?.let { it.second - it.first } ?: duration) * outputFps / outputSpeed / 1000.0).toInt()

  private fun resolutionParams(cropParams: CropParams, shortLength: Int): String {
    val short = cropParams.shortLength()
    val pixel = min(shortLength, short)
    return if (shortLength == 0 || shortLength >= short) {
      ""
    } else {
      ",scale=" + if ((cropParams.outW > cropParams.outH)) {
        "-2:$pixel"
      } else {
        "$pixel:-2"
      } + ":flags=lanczos"
    }
  }
}