package me.tasy5kg.cutegif

import me.tasy5kg.cutegif.toolbox.MediaTools
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaToolsCommandsTest {

  @Test
  fun gifsicleCommandStringShouldOverwriteInputWhenOutputIsNull() {
    assertEquals(
      "/lib/libgifsicle.so -b -O3 --lossy=70 /in.gif",
      MediaTools.gifsicleCommandString("/lib/libgifsicle.so", 70, true, "/in.gif", null)
    )
  }

  @Test
  fun gifsicleCommandStringShouldWriteToOutputWhenGiven() {
    assertEquals(
      "/lib/libgifsicle.so  --lossy=30 --output /out.gif /in.gif",
      MediaTools.gifsicleCommandString("/lib/libgifsicle.so", 30, false, "/in.gif", "/out.gif")
    )
  }

  @Test
  fun gifToVideoCommandShouldMatchExpectedCommand() {
    assertEquals(
      "-hwaccel auto -hide_banner -benchmark -an -i \"/in.gif\" " +
        "-c:v libx264 -crf 23 -preset veryfast -pix_fmt yuv420p " +
        "-vf pad=\"width=ceil(iw/2)*2:height=ceil(ih/2)*2\" -movflags +faststart -y \"/out.mp4\"",
      MediaTools.gifToVideoCommand("/in.gif", "-y \"/out.mp4\"", "veryfast")
    )
  }
}