package com.leonidaslux.gifinone

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MotionPhotoFormatTest {

  @Test
  fun xmpContainsXiaomiAndStandardMotionPhotoFields() {
    val xmp = MotionPhotoFormat.buildXmp(videoLength = 123456, presentationTimestampUs = 250000)

    assertTrue(xmp.contains("GCamera:MicroVideoOffset=\"123456\""))
    assertTrue(xmp.contains("GCamera:MotionPhoto=\"1\""))
    assertTrue(xmp.contains("<Item:Semantic>MotionPhoto</Item:Semantic>"))
    assertTrue(xmp.contains("<OpCamera:MotionPhotoOwner>xhs</OpCamera:MotionPhotoOwner>"))
    assertTrue(xmp.contains("<OpCamera:VideoLength>123456</OpCamera:VideoLength>"))
    assertTrue(xmp.contains("PresentationTimestampUs=\"250000\""))
  }

  @Test
  fun xmpApp1SegmentHasValidLengthAndHeader() {
    val segment = MotionPhotoFormat.createXmpApp1Segment(videoLength = 42)
    val declaredLength = ((segment[2].toInt() and 0xFF) shl 8) or (segment[3].toInt() and 0xFF)

    assertEquals(0xFF, segment[0].toInt() and 0xFF)
    assertEquals(0xE1, segment[1].toInt() and 0xFF)
    assertEquals(segment.size - 2, declaredLength)
    assertEquals(
      "http://ns.adobe.com/xap/1.0/\u0000",
      segment.copyOfRange(4, 4 + 29).toString(Charsets.UTF_8),
    )
  }

  @Test
  fun writerInsertsXmpAfterAppSegmentsAndAppendsMp4() {
    val jpeg = byteArrayOf(
      0xFF.toByte(), 0xD8.toByte(),
      0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
      0xFF.toByte(), 0xD9.toByte(),
    )
    val mp4 = byteArrayOf(
      0x00, 0x00, 0x00, 0x10,
      'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
      'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
    )
    val output = ByteArrayOutputStream()

    MotionPhotoFormat.write(
      jpegInput = ByteArrayInputStream(jpeg),
      mp4Input = ByteArrayInputStream(mp4),
      output = output,
      videoLength = mp4.size.toLong(),
    )
    val result = output.toByteArray()
    val videoStart = MotionPhotoFormat.findEmbeddedMp4Start(result)

    assertEquals(result.size - mp4.size, videoStart)
    assertArrayEquals(mp4, result.copyOfRange(videoStart, result.size))
    assertEquals(0xFF, result[8].toInt() and 0xFF)
    assertEquals(0xE1, result[9].toInt() and 0xFF)
    assertTrue(result.toString(Charsets.UTF_8).contains("GCamera:MicroVideoOffset=\"16\""))
  }
}
