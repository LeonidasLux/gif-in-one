package com.leonidaslux.gifinone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.leonidaslux.gifinone.toolbox.FileTools
import kotlin.system.exitProcess

abstract class BaseActivity : AppCompatActivity() {

  abstract fun onCreateIfEulaAccepted(savedInstanceState: Bundle?)

  final override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ActivityCollector.addActivity(this)
    if (!MySettings.eulaAccepted) {
      /** Ask user to accept EULA */
      EulaActivity.start(this)
      finish()
    } else {
      if (BuildConfig.DEBUG) {
        /** Show a dialog with logs when app crashed */
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
          AppCrashedActivity.start(this, e.stackTraceToString())
          exitProcess(1)
        }
      }
      onCreateIfEulaAccepted(savedInstanceState)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    ActivityCollector.removeActivity(this)
    if (ActivityCollector.isEmpty()) {
      FFmpegKit.cancel()
      FileTools.resetDirectory(MyConstants.CACHE_DIR_PATH)
    }
  }

}