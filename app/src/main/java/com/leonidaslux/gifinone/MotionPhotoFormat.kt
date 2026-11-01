package com.leonidaslux.gifinone

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Builds Android Motion Photo files using the JPEG + appended MP4 layout.
 *
 * Xiaomi Gallery recognises both the legacy MicroVideo fields and the newer
 * Motion Photo container fields, so the generated XMP includes both.
 */
object MotionPhotoFormat {
  private val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(StandardCharsets.UTF_8)
  private val ftypBytes = "ftyp".toByteArray(StandardCharsets.US_ASCII)
  private const val copyBufferSize = 128 * 1024

  fun buildXmp(videoLength: Long, presentationTimestampUs: Long = 0L) = """
    <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="GIF in One">
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description
            rdf:about=""
            xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
            GCamera:MicroVideo="1"
            GCamera:MicroVideoVersion="1"
            GCamera:MicroVideoOffset="$videoLength"
            GCamera:MicroVideoPresentationTimestampUs="$presentationTimestampUs"
            GCamera:MotionPhoto="1"
            GCamera:MotionPhotoVersion="1"
            GCamera:MotionPhotoPresentationTimestampUs="$presentationTimestampUs"/>
        <rdf:Description
            rdf:about=""
            xmlns:GContainer="http://ns.google.com/photos/1.0/container/"
            xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
          <GContainer:Directory>
            <rdf:Seq>
              <rdf:li rdf:parseType="Resource">
                <GContainer:Item rdf:parseType="Resource">
                  <Item:Mime>image/jpeg</Item:Mime>
                  <Item:Semantic>Primary</Item:Semantic>
                </GContainer:Item>
              </rdf:li>
              <rdf:li rdf:parseType="Resource">
                <GContainer:Item rdf:parseType="Resource">
                  <Item:Mime>video/mp4</Item:Mime>
                  <Item:Semantic>MotionPhoto</Item:Semantic>
                  <Item:Length>$videoLength</Item:Length>
                </GContainer:Item>
              </rdf:li>
            </rdf:Seq>
          </GContainer:Directory>
        </rdf:Description>
        <rdf:Description
            rdf:about=""
            xmlns:OpCamera="http://ns.oplus.com/photos/1.0/camera/">
          <OpCamera:MotionPhotoOwner>xhs</OpCamera:MotionPhotoOwner>
          <OpCamera:MotionPhotoPrimaryPresentationTimestampUs>$presentationTimestampUs</OpCamera:MotionPhotoPrimaryPresentationTimestampUs>
          <OpCamera:OLivePhotoVersion>2</OpCamera:OLivePhotoVersion>
          <OpCamera:VideoLength>$videoLength</OpCamera:VideoLength>
        </rdf:Description>
      </rdf:RDF>
    </x:xmpmeta>
  """.trimIndent()

  fun createXmpApp1Segment(videoLength: Long, presentationTimestampUs: Long = 0L): ByteArray {
    require(videoLength > 0) { "videoLength must be positive" }
    val xmpBytes = buildXmp(videoLength, presentationTimestampUs).toByteArray(StandardCharsets.UTF_8)
    val segmentLength = xmpHeader.size + xmpBytes.size + 2
    require(segmentLength <= 0xFFFF) { "XMP segment is too large" }
    return ByteArray(4 + xmpHeader.size + xmpBytes.size).also { segment ->
      segment[0] = 0xFF.toByte()
      segment[1] = 0xE1.toByte()
      segment[2] = (segmentLength shr 8).toByte()
      segment[3] = segmentLength.toByte()
      xmpHeader.copyInto(segment, destinationOffset = 4)
      xmpBytes.copyInto(segment, destinationOffset = 4 + xmpHeader.size)
    }
  }

  /**
   * Inserts XMP metadata into the JPEG APP segment area and appends the MP4.
   * Neither media stream is loaded into memory as a whole.
   */
  fun write(
    jpegInput: InputStream,
    mp4Input: InputStream,
    output: OutputStream,
    videoLength: Long,
    presentationTimestampUs: Long = 0L,
  ) {
    require(videoLength > 0) { "videoLength must be positive" }
    val jpeg = jpegInput.readBytes()
    val insertionOffset = xmpInsertionOffset(jpeg)
    val xmpSegment = createXmpApp1Segment(videoLength, presentationTimestampUs)

    output.write(jpeg, 0, insertionOffset)
    output.write(xmpSegment)
    output.write(jpeg, insertionOffset, jpeg.size - insertionOffset)

    val buffer = ByteArray(copyBufferSize)
    var bytesRead = mp4Input.read(buffer)
    var copied = 0L
    while (bytesRead >= 0) {
      output.write(buffer, 0, bytesRead)
      copied += bytesRead
      bytesRead = mp4Input.read(buffer)
    }
    check(copied == videoLength) {
      "MP4 length changed while copying: expected $videoLength, copied $copied"
    }
  }

  /** Finds the beginning of the appended MP4 by locating a valid `ftyp` box. */
  fun findEmbeddedMp4Start(bytes: ByteArray): Int {
    var ftypOffset = bytes.indexOf(ftypBytes, 0)
    while (ftypOffset >= 0) {
      val boxOffset = ftypOffset - 4
      if (boxOffset >= 0 && boxOffset + 8 <= bytes.size) {
        val boxSize = bytes.readUnsignedInt(boxOffset)
        if (boxSize >= 12L && boxOffset + boxSize <= bytes.size) return boxOffset
      }
      ftypOffset = bytes.indexOf(ftypBytes, ftypOffset + 1)
    }
    return -1
  }

  private fun xmpInsertionOffset(jpeg: ByteArray): Int {
    require(jpeg.size >= 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) {
      "Cover is not a JPEG"
    }
    var offset = 2
    while (offset + 4 <= jpeg.size && jpeg[offset] == 0xFF.toByte()) {
      val marker = jpeg[offset + 1].toInt() and 0xFF
      if (marker !in 0xE0..0xEF) break
      val segmentLength = ((jpeg[offset + 2].toInt() and 0xFF) shl 8) or
        (jpeg[offset + 3].toInt() and 0xFF)
      if (segmentLength < 2 || offset + 2 + segmentLength > jpeg.size) break
      offset += 2 + segmentLength
    }
    require(offset < jpeg.size) { "JPEG has no image data" }
    return offset
  }

  private fun ByteArray.indexOf(needle: ByteArray, startIndex: Int): Int {
    if (needle.isEmpty() || startIndex < 0 || startIndex > size - needle.size) return -1
    for (index in startIndex..size - needle.size) {
      var matched = true
      for (needleIndex in needle.indices) {
        if (this[index + needleIndex] != needle[needleIndex]) {
          matched = false
          break
        }
      }
      if (matched) return index
    }
    return -1
  }

  private fun ByteArray.readUnsignedInt(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
      ((this[offset + 1].toLong() and 0xFF) shl 16) or
      ((this[offset + 2].toLong() and 0xFF) shl 8) or
      (this[offset + 3].toLong() and 0xFF)
}
