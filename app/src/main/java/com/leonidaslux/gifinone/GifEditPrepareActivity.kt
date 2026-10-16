package com.leonidaslux.gifinone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.leonidaslux.gifinone.MyConstants.EXTRA_GIF_PATH
import com.leonidaslux.gifinone.MyConstants.GIF_EDIT_PREVIEW_MP4_PATH
import com.leonidaslux.gifinone.databinding.ActivityGifToVideoBinding
import com.leonidaslux.gifinone.toolbox.MediaTools
import com.leonidaslux.gifinone.toolbox.MediaTools.getVideoDurationMsByFFmpeg
import com.leonidaslux.gifinone.toolbox.Toolbox.constraintBy
import com.leonidaslux.gifinone.toolbox.Toolbox.getExtra
import com.leonidaslux.gifinone.toolbox.Toolbox.logRed
import com.leonidaslux.gifinone.toolbox.Toolbox.onClick
import com.leonidaslux.gifinone.toolbox.Toolbox.toast
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Converts the GIF selected for editing into a temporary mp4.
 * The mp4 is only used for previewing inside [VideoToGifActivity];
 * the exported GIF is rendered from the original GIF file.
 */
class GifEditPrepareActivity : BaseActivity() {
  private val binding by lazy { ActivityGifToVideoBinding.inflate(layoutInflater) }
  private val inputGifPath by lazy { intent.getExtra<String>(EXTRA_GIF_PATH) }
  private var taskThread: Thread? = null
  private var taskQuitOrFailed = false

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setContentView(binding.root)
    setFinishOnTouchOutside(false)
    binding.mtvTitle.text = getString(R.string.preparing_gif_edit_preview_d_percent, 0)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        quitOrFailed(null)
      }
    })
    binding.mbClose.onClick { quitOrFailed(null) }
    taskThread = thread { preparePreview() }
  }

  private fun preparePreview() {
    val duration = getVideoDurationMsByFFmpeg(inputGifPath)
    if (duration == null) {
      quitOrFailed(getString(R.string.unable_to_load_gif))
      return
    }
    val command = MediaTools.gifToVideoCommand(inputGifPath, "-y \"$GIF_EDIT_PREVIEW_MP4_PATH\"", "veryfast")
    logRed("command", command)
    FFmpegKit.executeAsync(command, {
      when {
        it.returnCode.isValueSuccess && !taskQuitOrFailed -> runOnUiThread {
          VideoToGifActivity.startForGifEdit(this, inputGifPath, GIF_EDIT_PREVIEW_MP4_PATH)
          finish()
        }

        it.returnCode.isValueError && !taskQuitOrFailed -> quitOrFailed(getString(R.string.unable_to_load_gif))
      }
    }, { log ->
      logRed("logcallback", log.message.toString())
    }, {
      val progress = (it.time * 100 / duration).roundToInt().constraintBy(0..99)
      runOnUiThread {
        binding.mtvTitle.text = getString(R.string.preparing_gif_edit_preview_d_percent, progress)
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

  companion object {
    fun start(context: Context, gifPath: String) = context.startActivity(
      Intent(context, GifEditPrepareActivity::class.java).putExtra(EXTRA_GIF_PATH, gifPath)
    )
  }
}