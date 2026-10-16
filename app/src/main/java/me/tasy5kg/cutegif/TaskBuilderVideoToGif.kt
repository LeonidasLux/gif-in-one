package me.tasy5kg.cutegif

import me.tasy5kg.cutegif.MyConstants.ADD_TEXT_RENDER_PNG_PATH
import me.tasy5kg.cutegif.MyConstants.OUTPUT_GIF_TEMP_PATH
import me.tasy5kg.cutegif.MyConstants.PALETTE_PATH
import me.tasy5kg.cutegif.MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH
import me.tasy5kg.cutegif.toolbox.MediaTools.saveToPng
import java.io.Serializable

data class TaskBuilderVideoToGif(
  val inputVideoPath: String,
  /** 1 == 1ms , do not use IntRange because it is not serializable */
  val trimTime: Pair<Int, Int>?,
  val cropParams: CropParams,
  /** the resolution of the short side when outputting */
  val shortLength: Int,
  val outputSpeed: Float,
  val outputFps: Int,
  val colorQuality: Int,
  val reverse: Boolean,
  val textRender: TextRender?,
  val lossy: Int?,
  val videoWH: Pair<Int, Int>,
  val duration: Int,
  /** The interval between every loops, in centi seconds. (1 == 0.01 sec) */
  val finalDelay: Int,
  /** Color(RRGGBB), Similarity * 100 */
  val colorKey: Pair<String, Int>?
) : Serializable {

  init {
    TextRender.render(textRender, videoWH.first, videoWH.second).saveToPng(ADD_TEXT_RENDER_PNG_PATH)
  }

  fun getForPreviewOnly() = TaskBuilderVideoToGifForPreview(shortLength, colorQuality, lossy, videoWH, colorKey)

  fun getOutputFramesEstimated() =
    VideoToGifCommands.estimateOutputFrames(trimTime, duration, outputFps, outputSpeed)

  fun getCommandExtractFrame() = VideoToGifCommands.extractFramesCommand(
    inputPath = inputVideoPath,
    trimTime = trimTime,
    cropParams = cropParams,
    shortLength = shortLength,
    outputSpeed = outputSpeed,
    outputFps = outputFps,
    reverse = reverse,
    colorKey = colorKey,
    textRenderPngPath = ADD_TEXT_RENDER_PNG_PATH,
    outputFramesPath = VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
  )

  fun getCommandCreatePalette() =
    VideoToGifCommands.createPaletteCommand(colorQuality, VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH, PALETTE_PATH)

  fun getCommandVideoToGif() =
    VideoToGifCommands.framesToGifCommand(outputFps, VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH, PALETTE_PATH, finalDelay, OUTPUT_GIF_TEMP_PATH)
}