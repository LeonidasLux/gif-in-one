package me.tasy5kg.cutegif

import kotlin.math.abs

/** Pure helpers for the "edit an existing GIF" feature (unit-testable). */
object GifEditSupport {

  /** Framerate choices offered by the export options dialog. */
  val FRAMERATE_CHOICES = listOf(5, 10, 16, 25, 50)

  /** Estimates the fps of a GIF: frame count divided by duration. */
  fun estimateFps(frameCount: Int, durationMs: Int): Double? =
    if (frameCount > 0 && durationMs > 0) frameCount * 1000.0 / durationMs else null

  /** The framerate choice (one of [FRAMERATE_CHOICES]) closest to the given fps, or null when fps is null. */
  fun nearestFramerateChoice(fps: Double?): Int? = fps?.let { value -> FRAMERATE_CHOICES.minByOrNull { abs(it - value) } }
}