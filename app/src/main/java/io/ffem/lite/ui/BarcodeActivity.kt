@file:Suppress("DEPRECATION")

package io.ffem.lite.ui

import android.app.Activity
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.MediaActionSound
import android.os.Bundle
import android.os.Environment.DIRECTORY_PICTURES
import android.os.Handler
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.ffem.lite.BuildConfig
import io.ffem.lite.R
import io.ffem.lite.app.App
import io.ffem.lite.app.App.Companion.TEST_VALUE
import io.ffem.lite.app.App.Companion.getTestInfo
import io.ffem.lite.app.AppDatabase
import io.ffem.lite.databinding.ActivityBarcodeBinding
import io.ffem.lite.model.TestResult
import io.ffem.lite.util.ColorUtil
import io.ffem.lite.util.PreferencesUtil
import kotlinx.android.synthetic.main.activity_barcode.*
import java.io.File
import java.util.*
import kotlin.math.round

/** Combination of all flags required to put activity into immersive mode */
const val FLAGS_FULLSCREEN =
    View.SYSTEM_UI_FLAG_LOW_PROFILE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

const val DEBUG_MODE = "debugMode"
const val TEST_ID = "testId"

/**
 * Activity to display info about the app.
 */
class BarcodeActivity : BaseActivity() {

    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var b: ActivityBarcodeBinding

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!BuildConfig.TEST_RUNNING.get()) {
                val sound = MediaActionSound()
                sound.play(MediaActionSound.SHUTTER_CLICK)
            }

            saveImageData(intent)

            setResult(Activity.RESULT_OK, intent)

            Handler().postDelayed({
                finish()
            }, 2000)
        }
    }

    private val resultBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            setResult(Activity.RESULT_OK, intent)
            Handler().postDelayed({
                finish()
            }, 2000)
        }
    }

    private fun saveImageData(data: Intent) {
        val id = data.getStringExtra(App.TEST_ID_KEY)

        val testName = data.getStringExtra(App.TEST_NAME_KEY)

        if (testName.isNullOrEmpty()) {
            return
        }

        if (id != null) {

            val testImageNumber = PreferencesUtil
                .getString(this, R.string.testImageNumberKey, "")

            val db = AppDatabase.getDatabase(baseContext)
            db.resultDao().insert(
                TestResult(
                    id, 0, testName, Date().time,
                    Date().time, "", testImageNumber, getString(R.string.outbox)
                )
            )
            analyzeImage()
        }
    }

    private fun analyzeImage() {
        val db = AppDatabase.getDatabase(baseContext)
        db.resultDao().getPendingResults().forEach {
            val path = getExternalFilesDir(DIRECTORY_PICTURES).toString() +
                    File.separator + "captures" + File.separator

            val fileName = it.name.replace(" ", "")
            val filePath = "$path${it.id}" + "_" + "$fileName.jpg"

            val file = File(filePath)

            val bitmap = BitmapFactory.decodeFile(file.path)

            if (bitmap != null) {
                ColorUtil.extractImage(this, it.id, bitmap)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = DataBindingUtil.setContentView(this, R.layout.activity_barcode)

        broadcastManager = LocalBroadcastManager.getInstance(this)

        broadcastManager.registerReceiver(
            broadcastReceiver,
            IntentFilter(App.CAPTURED_EVENT)
        )

        broadcastManager.registerReceiver(
            resultBroadcastReceiver,
            IntentFilter(App.LOCAL_RESULT_EVENT)
        )

        if (intent.getBooleanExtra(DEBUG_MODE, false)) {
            sendDummyResultForDebugging(intent.getStringExtra(TEST_ID))
        }
    }

    /**
     * Create dummy results to send when in debug mode
     */
    private fun sendDummyResultForDebugging(uuid: String?) {
        if (uuid != null) {
            val testInfo = getTestInfo(uuid)
            if (testInfo != null) {
                val resultIntent = Intent()
                val random = Random()
                val maxValue = testInfo.values[testInfo.values.size / 2].value

                resultIntent.putExtra(App.TEST_ID_KEY, testInfo.uuid)
                resultIntent.putExtra(App.TEST_NAME_KEY, testInfo.name)

                val result = (round(random.nextDouble() * maxValue * 100) / 100.0).toString()
                resultIntent.putExtra(TEST_VALUE, result)

                val pd = ProgressDialog(this)
                pd.setMessage("Sending dummy result...")
                pd.setCancelable(false)
                pd.show()

                setResult(Activity.RESULT_OK, resultIntent)
                Handler().postDelayed({
                    pd.dismiss()
                    finish()
                }, 3000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        layout_container.postDelayed({
            layout_container.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(broadcastReceiver)
        broadcastManager.unregisterReceiver(resultBroadcastReceiver)
    }

    companion object {
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L
    }
}
