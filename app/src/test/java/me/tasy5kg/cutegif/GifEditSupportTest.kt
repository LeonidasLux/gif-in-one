package me.tasy5kg.cutegif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GifEditSupportTest {

  @Test
  fun estimateFpsShouldDivideFrameCountByDuration() {
    assertEquals(5.0, GifEditSupport.estimateFps(8, 1600)!!, 0.0001)
    assertEquals(12.5, GifEditSupport.estimateFps(25, 2000)!!, 0.0001)
  }

  @Test
  fun estimateFpsShouldBeNullWhenInputIsInvalid() {
    assertNull(GifEditSupport.estimateFps(0, 1600))
    assertNull(GifEditSupport.estimateFps(8, 0))
    assertNull(GifEditSupport.estimateFps(-1, 1600))
  }

  @Test
  fun nearestFramerateChoiceShouldReturnClosestChoice() {
    assertEquals(5, GifEditSupport.nearestFramerateChoice(5.0))
    assertEquals(10, GifEditSupport.nearestFramerateChoice(12.0))
    assertEquals(16, GifEditSupport.nearestFramerateChoice(15.0))
    assertEquals(25, GifEditSupport.nearestFramerateChoice(25.0))
    assertEquals(50, GifEditSupport.nearestFramerateChoice(60.0))
    assertEquals(5, GifEditSupport.nearestFramerateChoice(1.0))
  }

  @Test
  fun nearestFramerateChoiceShouldBeNullWhenFpsIsNull() {
    assertNull(GifEditSupport.nearestFramerateChoice(null))
  }
}