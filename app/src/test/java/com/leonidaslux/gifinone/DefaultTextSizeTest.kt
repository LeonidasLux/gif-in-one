package com.leonidaslux.gifinone

import com.leonidaslux.gifinone.toolbox.Toolbox.defaultTextSizePx
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTextSizeTest {

  @Test
  fun sizeShouldKeepOriginalDefaultFor1080ShortSide() {
    assertEquals(36f, defaultTextSizePx(1920, 1080), 0.001f)
    assertEquals(36f, defaultTextSizePx(1080, 1920), 0.001f)
    assertEquals(36f, defaultTextSizePx(1080, 1080), 0.001f)
  }

  @Test
  fun sizeShouldScaleDownForLowResolution() {
    assertEquals(24f, defaultTextSizePx(1280, 720), 0.001f)
    assertEquals(8f, defaultTextSizePx(320, 240), 0.001f)
  }

  @Test
  fun sizeShouldNotExceedTheOriginalDefault() {
    assertEquals(36f, defaultTextSizePx(3840, 2160), 0.001f)
  }

  @Test
  fun sizeShouldNotGoBelowTheMinimum() {
    assertEquals(4f, defaultTextSizePx(64, 48), 0.001f)
  }
}