package com.leonidaslux.gifinone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.leonidaslux.gifinone.MyConstants.EXTRA_MOTION_PHOTO_SOURCE_IS_GIF
import com.leonidaslux.gifinone.MyConstants.MOTION_PHOTO_COVER_PATH
import com.leonidaslux.gifinone.MyConstants.MOTION_PHOTO_TEMP_MP4_PATH
import com.leonidaslux.gifinone.databinding.ActivityMotionPhotoBinding
import com.leonidaslux.gifinone.toolbox.FileTools
import com.leonidaslux.gifinone.toolbox.FileTools.createNewMotionPhotoFile
import com.leonidaslux.gifinone.toolbox.FileTools.finishPendingFile
import com.leonidaslux.gifinone.toolbox.MediaTools.getVideoDurationMsByFFmpeg
import com.leonidaslux.gifinone.toolbox.Toolbox.constraintBy
import com.leonidaslux.gifinone.toolbox.Toolbox.getExtra
import com.leonidaslux.gifinone.toolbox.Toolbox.keepScreenOn
import com.leonidaslux.gifinone.toolbox.Toolbox.logRed
import com.leonidaslux.gifinone.toolbox.Toolbox.onClick
import com.leonidaslux.gifinone.toolbox.Toolbox.toast
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

/** Converts a selected video or GIF into a Xiaomi-compatible Android Motion Photo. */
class MotionPhotoActivity : BaseActivity() {
  private val binding by lazy { ActivityMotionPhotoBinding.inflate(layoutInflater) }
  private val inputPath by lazy { intent.getExtra<String>(MyConstants.EXTRA_VIDEO_PATH) }
  private val sourceIsGif by lazy { intent.getBooleanExtra(EXTRA_MOTION_PHOTO_SOURCE_IS_GIF, false) }
  private var taskThread: Thread? = null
  private var taskQuitOrFailed = false
  private var pendingOutputUri: Uri? = null

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setContentView(binding.root)
    setFinishOnTouchOutside(false)
    binding.mtvTitle.text = getString(R.string.creating_motion_photo_d_percent, 0)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        quitOrFailed(null)
      }
    })
    binding.mbClose.onClick { quitOrFailed(null) }
    taskThread = thread { transcodeSource() }
  }

  private fun transcodeSource() {
    keepScreenOn(true)
    val duration = getVideoDurationMsByFFmpeg(inputPath)
    if (duration == null || duration <= 0) {
      quitOrFailed(getString(if (sourceIsGif) R.string.unable_to_load_gif else R.string.unable_to_read_video))
      return
    }
    val command = if (sourceIsGif) {
      MotionPhotoCommands.gifToMp4Command(inputPath, MOTION_PHOTO_TEMP_MP4_PATH)
    } else {
      MotionPhotoCommands.videoToMp4Command(inputPath, MOTION_PHOTO_TEMP_MP4_PATH)
    }
    logRed("motionPhotoTranscode", command)
    FFmpegKit.executeAsync(command, { session ->
      when {
        taskQuitOrFailed -> Unit
        session.returnCode.isValueSuccess -> extractCover()
        session.returnCode.isValueError -> quitOrFailed(getString(R.string.motion_photo_conversion_failed))
      }
    }, { log ->
      logRed("motionPhotoLog", log.message.toString())
    }, { statistics ->
      val progress = (statistics.time * 75 / duration).toInt().constraintBy(0..75)
      putProgress(progress, getString(R.string.creating_motion_photo_d_percent, progress))
    })
  }

  private fun extractCover() {
    if (taskQuitOrFailed) return
    putProgress(80, getString(R.string.creating_motion_photo_d_percent, 80))
    val command = MotionPhotoCommands.extractCoverCommand(MOTION_PHOTO_TEMP_MP4_PATH, MOTION_PHOTO_COVER_PATH)
    logRed("motionPhotoCover", command)
    FFmpegKit.executeAsync(command, { session ->
      when {
        taskQuitOrFailed -> Unit
        session.returnCode.isValueSuccess -> writeMotionPhoto()
        session.returnCode.isValueError -> quitOrFailed(getString(R.string.motion_photo_conversion_failed))
      }
    }, { log ->
      logRed("motionPhotoCoverLog", log.message.toString())
    }, {})
  }

  private fun writeMotionPhoto() {
    if (taskQuitOrFailed) return
    try {
      val mp4File = File(MOTION_PHOTO_TEMP_MP4_PATH)
      val coverFile = File(MOTION_PHOTO_COVER_PATH)
      check(mp4File.length() > 0) { "Converted video is empty" }
      check(coverFile.length() > 0) { "Cover image is empty" }

      val outputUri = createNewMotionPhotoFile(FileTools.FileName(inputPath).nameWithoutExtension)
      pendingOutputUri = outputUri
      contentResolver.openOutputStream(outputUri, "w").use { output ->
        checkNotNull(output) { "Unable to open Motion Photo output" }
        FileInputStream(coverFile).use { coverInput ->
          FileInputStream(mp4File).use { videoInput ->
            MotionPhotoFormat.write(
              jpegInput = coverInput,
              mp4Input = videoInput,
              output = output,
              videoLength = mp4File.length(),
            )
          }
        }
      }
      finishPendingFile(outputUri)
      if (taskQuitOrFailed) {
        contentResolver.delete(outputUri, null, null)
        return
      }
      runOnUiThread {
        finish()
        FileSavedActivity.startMotionPhoto(this, outputUri, MOTION_PHOTO_TEMP_MP4_PATH)
      }
    } catch (e: Exception) {
      logRed("writeMotionPhotoFailed", e.stackTraceToString())
      pendingOutputUri?.let { contentResolver.delete(it, null, null) }
      quitOrFailed(getString(R.string.motion_photo_conversion_failed))
    }
  }

  private fun putProgress(progress: Int, text: String) {
    runOnUiThread {
      binding.linearProgressIndicator.setProgress(progress.constraintBy(0..100), true)
      binding.mtvTitle.text = text
    }
  }

  private fun quitOrFailed(toastText: String?) {
    runOnUiThread {
      taskQuitOrFailed = true
      toastText?.let { toast(it) }
      pendingOutputUri?.let {
        contentResolver.delete(it, null, null)
        pendingOutputUri = null
      }
      FFmpegKit.cancel()
      FFmpegKitConfig.clearSessions()
      taskThread?.interrupt()
      finish()
    }
  }

  override fun onDestroy() {
    keepScreenOn(false)
    super.onDestroy()
  }

  companion object {
    fun startForVideo(context: Context, videoPath: String) =
      start(context, videoPath, sourceIsGif = false)

    fun startForGif(context: Context, gifPath: String) =
      start(context, gifPath, sourceIsGif = true)

    private fun start(context: Context, inputPath: String, sourceIsGif: Boolean) {
      context.startActivity(
        Intent(context, MotionPhotoActivity::class.java)
          .putExtra(MyConstants.EXTRA_VIDEO_PATH, inputPath)
          .putExtra(EXTRA_MOTION_PHOTO_SOURCE_IS_GIF, sourceIsGif)
      )
    }
  }
}
