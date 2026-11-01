package com.leonidaslux.gifinone

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPhotoCommandsTest {

  @Test
  fun videoCommandPreservesAudioAndUsesCompatibleH264Output() {
    assertEquals(
      "-hwaccel auto -hide_banner -benchmark -i \"/in.mp4\" -map 0:v:0 -map 0:a:0? " +
        "-vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2:flags=lanczos,setsar=1\" " +
        "-r 30 -c:v libx264 -preset veryfast -crf 20 -profile:v high -pix_fmt yuv420p -movflags +faststart " +
        "-c:a aac -b:a 128k -ar 48000 -ac 2 -map_metadata -1 -y \"/out.mp4\"",
      MotionPhotoCommands.videoToMp4Command("/in.mp4", "/out.mp4"),
    )
  }

  @Test
  fun gifCommandDisablesAudioAndUsesCompatibleH264Output() {
    assertEquals(
      "-hwaccel auto -hide_banner -benchmark -an -i \"/in.gif\" " +
        "-vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2:flags=lanczos,setsar=1\" " +
        "-r 30 -c:v libx264 -preset veryfast -crf 20 -profile:v high -pix_fmt yuv420p -movflags +faststart " +
        "-map_metadata -1 -y \"/out.mp4\"",
      MotionPhotoCommands.gifToMp4Command("/in.gif", "/out.mp4"),
    )
  }

  @Test
  fun coverCommandExtractsFirstFrame() {
    assertEquals(
      "-hwaccel auto -hide_banner -benchmark -an -ss 0ms -i \"/in.mp4\" -map 0:v:0 " +
        "-frames:v 1 -q:v 2 -y \"/cover.jpg\"",
      MotionPhotoCommands.extractCoverCommand("/in.mp4", "/cover.jpg"),
    )
  }
}
