package com.leonidaslux.gifinone

import org.junit.Assert.assertEquals
import org.junit.Test

class CropParamsTest {

  @Test
  fun toFFmpegCropCommandShouldMatchExpectedCommand() {
    assertEquals("crop=100:50:10:20", CropParams(100, 50, 10, 20).toFFmpegCropCommand())
  }

  @Test
  fun shortLengthShouldBeTheShorterSide() {
    assertEquals(50, CropParams(100, 50, 0, 0).shortLength())
    assertEquals(50, CropParams(50, 100, 0, 0).shortLength())
  }

  @Test
  fun calcScaledResolutionShouldKeepTargetShortSideAndEvenLongSide() {
    assertEquals(Pair(96, 48), CropParams(100, 50, 0, 0).calcScaledResolution(48))
    assertEquals(Pair(48, 96), CropParams(50, 100, 0, 0).calcScaledResolution(48))
    assertEquals(Pair(100, 50), CropParams(101, 51, 0, 0).calcScaledResolution(50))
  }

  @Test
  fun calcScaledResolutionStringShouldRespectUseMinus2() {
    assertEquals("-2:48", CropParams(100, 50, 0, 0).calcScaledResolutionString(48, ":", true))
    assertEquals("48:-2", CropParams(50, 100, 0, 0).calcScaledResolutionString(48, ":", true))
    assertEquals("96:48", CropParams(100, 50, 0, 0).calcScaledResolutionString(48, ":", false))
  }
}