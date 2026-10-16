package com.leonidaslux.gifinone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.leonidaslux.gifinone.MyApplication.Companion.appContext
import com.leonidaslux.gifinone.MyConstants.EXTRA_GIF_PATH
import com.leonidaslux.gifinone.databinding.ActivityGifToVideoBinding
import com.leonidaslux.gifinone.toolbox.FileTools
import com.leonidaslux.gifinone.toolbox.MediaTools
import com.leonidaslux.gifinone.toolbox.FileTools.createNewFile
import com.leonidaslux.gifinone.toolbox.MediaTools.getVideoDurationMsByFFmpeg
import com.leonidaslux.gifinone.toolbox.Toolbox
import com.leonidaslux.gifinone.toolbox.Toolbox.constraintBy
import com.leonidaslux.gifinone.toolbox.Toolbox.getExtra
import com.leonidaslux.gifinone.toolbox.Toolbox.keepScreenOn
import com.leonidaslux.gifinone.toolbox.Toolbox.logRed
import com.leonidaslux.gifinone.toolbox.Toolbox.onClick
import com.leonidaslux.gifinone.toolbox.Toolbox.toast
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class GifToVideoActivity : BaseActivity() {
  private val binding by lazy { ActivityGifToVideoBinding.inflate(layoutInflater) }
  private val inputGifPath by lazy { intent.getExtra<String>(EXTRA_GIF_PATH) }
  private var taskThread: Thread? = null
  private var taskQuitOrFailed = false

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setContentView(binding.root)
    setFinishOnTouchOutside(false)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        quitOrFailed(getString(R.string.cancelled))
      }
    })
    binding.mbClose.onClick {
      quitOrFailed(getString(R.string.cancelled))
    }
    taskThread = thread { performTranscode() }
  }

  private fun performTranscode() {
    keepScreenOn(true)
    val duration = getVideoDurationMsByFFmpeg(inputGifPath)
    if (duration == null) {
      quitOrFailed(getString(R.string.unable_to_load_gif))
      return
    }

    val videoUri = createNewFile(FileTools.FileName(inputGifPath).nameWithoutExtension, "mp4")
    // reference: https://stackoverflow.com/a/53024964
    val command = MediaTools.gifToVideoCommand(
      inputGifPath, FFmpegKitConfig.getSafParameterForWrite(appContext, videoUri)!!
    )
    logRed("command", command)
    FFmpegKit.executeAsync(command, {
      when {
        it.returnCode.isValueSuccess && !taskQuitOrFailed -> {
          FileSavedActivity.start(this, videoUri)
          finish()
        }

        it.returnCode.isValueError -> {
          runOnUiThread { toast(R.string.gif_to_video_conversion_failed) }
          finish()
        }
      }
    }, {
      logRed("logcallback", it.message.toString())
    }, {
      val progress = (it.time * 100 / duration).roundToInt().constraintBy(0..99)
      runOnUiThread {
        binding.mtvTitle.text = getString(R.string.converting_to_video_d_percent, progress)
        binding.linearProgressIndicator.setProgress(progress, true)
      }
    })
  }

  private fun quitOrFailed(toastText: String?) {
    runOnUiThread {
      taskQuitOrFailed = true
      toastText?.let { toast(it) }
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
    fun start(context: Context, gifPath: String) =
      context.startActivity(Intent(context, GifToVideoActivity::class.java).apply {
        putExtra(EXTRA_GIF_PATH, gifPath)
      })
  }
}