package com.leonidaslux.gifinone

import com.leonidaslux.gifinone.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL
import com.leonidaslux.gifinone.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN

/** Pure FFmpeg command builders used by video/GIF -> Motion Photo conversion. */
object MotionPhotoCommands {
  private const val EVEN_DIMENSIONS_FILTER =
    "scale=trunc(iw/2)*2:trunc(ih/2)*2:flags=lanczos,setsar=1"
  private const val VIDEO_ENCODING_OPTIONS =
    "-r 30 -c:v libx264 -preset veryfast -crf 20 -profile:v high -pix_fmt yuv420p -movflags +faststart"

  fun videoToMp4Command(inputPath: String, outputPath: String) =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL -i \"$inputPath\" -map 0:v:0 -map 0:a:0? " +
      "-vf \"$EVEN_DIMENSIONS_FILTER\" $VIDEO_ENCODING_OPTIONS " +
      "-c:a aac -b:a 128k -ar 48000 -ac 2 -map_metadata -1 -y \"$outputPath\""

  fun gifToMp4Command(inputPath: String, outputPath: String) =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"$inputPath\" " +
      "-vf \"$EVEN_DIMENSIONS_FILTER\" $VIDEO_ENCODING_OPTIONS " +
      "-map_metadata -1 -y \"$outputPath\""

  fun extractCoverCommand(inputPath: String, outputPath: String) =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -ss 0ms -i \"$inputPath\" -map 0:v:0 " +
      "-frames:v 1 -q:v 2 -y \"$outputPath\""
}
