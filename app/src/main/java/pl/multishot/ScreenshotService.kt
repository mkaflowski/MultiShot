package pl.multishot

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Usługa dostępności – jedyny sposób, by bez roota zrobić zrzut dowolnego ekranu
 * (AccessibilityService.takeScreenshot(displayId)) i pokazać nakładkę na każdym ekranie.
 */
class ScreenshotService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var capturer: ScreenshotCapturer
    private var button: FloatingButton? = null
    private var shutter: Feedback.Shutter? = null
    private var busy = false
    private var connected = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.KEY_BUTTON_ENABLED, Prefs.KEY_BUTTON_DISPLAY -> updateButton()
            Prefs.KEY_TRANSPARENCY, Prefs.KEY_BUTTON_SIZE -> button?.applyStyle()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (displayId == prefs.buttonDisplayId) updateButton()
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == button?.currentDisplayId) {
                button?.hide()
                updateButton()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == button?.currentDisplayId) button?.clampToScreen()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        capturer = ScreenshotCapturer(this, worker)
        button = FloatingButton(this, prefs, ::takeScreenshotNow, ::openSettings)
        shutter = Feedback.Shutter()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
        connected = true
        instance = this
        updateButton()
        FloatingButtonTileService.requestUpdate(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.post { button?.clampToScreen() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        worker.shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (!connected) return
        connected = false
        instance = null
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        button?.hide()
        button = null
        shutter?.release()
        shutter = null
        FloatingButtonTileService.requestUpdate(this)
    }

    private fun updateButton() {
        val b = button ?: return
        if (prefs.buttonEnabled) b.show(prefs.buttonDisplayId) else b.hide()
    }

    /** Wywoływane po dotknięciu pływającego przycisku. */
    fun takeScreenshotNow() {
        if (busy || !connected) return
        busy = true
        button?.setHiddenForCapture(true)
        // Daj kompozytorowi kilka klatek na zniknięcie przycisku.
        handler.postDelayed(::startCapture, HIDE_DELAY_MS)
    }

    private fun startCapture() {
        if (!connected) {
            busy = false
            return
        }
        val mode = prefs.captureMode
        val targets = when (mode) {
            CaptureMode.SINGLE -> {
                val id = prefs.captureDisplayId
                listOf(if (Displays.exists(this, id)) id else android.view.Display.DEFAULT_DISPLAY)
            }
            CaptureMode.ALL_STACKED, CaptureMode.ALL_SEPARATE ->
                Displays.capturable(this).map { it.displayId }
        }
        capturer.capture(targets) { outcome ->
            button?.setHiddenForCapture(false)
            if (outcome.shots.isNotEmpty()) {
                // Potwierdzenie w chwili zrobienia zrzutu, zanim zapis PNG się skończy.
                if (prefs.vibrate) Feedback.vibrate(this)
                if (prefs.sound) shutter?.play()
            }
            worker.execute {
                val result = try {
                    save(mode, outcome)
                } catch (t: Throwable) {
                    Log.e(TAG, "Błąd zapisu", t)
                    SaveResult(getString(R.string.msg_save_failed), isError = true)
                }
                handler.post {
                    busy = false
                    button?.pulse()
                    if (result.isError || prefs.showToast) {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /** [isError] – coś się nie udało (także częściowo); taki komunikat pokazujemy zawsze. */
    private class SaveResult(val message: String, val isError: Boolean)

    /** Zapis na wątku roboczym. */
    private fun save(mode: CaptureMode, outcome: ScreenshotCapturer.Outcome): SaveResult {
        val error = { msg: String -> SaveResult(msg, isError = true) }
        val shots = outcome.shots
        val failuresText = outcome.failures.entries.joinToString(", ") { (id, code) ->
            getString(R.string.msg_failure_item, id, code)
        }
        if (shots.isEmpty()) return error(getString(R.string.msg_failed, failuresText))

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var message: String
        var partial = false
        try {
            message = when {
                shots.size == 1 -> {
                    ImageSaver.savePng(this, shots[0].bitmap, "Screenshot_$stamp.png")
                        ?: return error(getString(R.string.msg_save_failed))
                    getString(R.string.msg_saved_single)
                }

                mode == CaptureMode.ALL_STACKED -> {
                    // Kolejność wg id ekranu: ekran główny (#0) na górze, kolejne pod nim.
                    val combined = ImageSaver.stackVertically(shots.map { it.bitmap })
                    val uri = try {
                        ImageSaver.savePng(this, combined, "Screenshot_${stamp}_all.png")
                    } finally {
                        combined.recycle()
                    }
                    uri ?: return error(getString(R.string.msg_save_failed))
                    getString(R.string.msg_saved_stacked, screens(shots.size))
                }

                else -> {
                    val saved = shots.count {
                        ImageSaver.savePng(this, it.bitmap, "Screenshot_${stamp}_display${it.displayId}.png") != null
                    }
                    if (saved == 0) return error(getString(R.string.msg_save_failed))
                    partial = saved < shots.size
                    getString(R.string.msg_saved_separate, screens(saved))
                }
            }
        } finally {
            shots.forEach { it.bitmap.recycle() }
        }
        if (outcome.failures.isNotEmpty()) {
            message += "\n" + getString(R.string.msg_skipped, failuresText)
        }
        // Pominięty ekran to też błąd – użytkownik powinien o nim wiedzieć mimo wyłączonego komunikatu.
        return SaveResult(message, isError = partial || outcome.failures.isNotEmpty())
    }

    private fun screens(count: Int): String =
        resources.getQuantityString(R.plurals.screens, count, count)

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val displayId = button?.currentDisplayId ?: android.view.Display.DEFAULT_DISPLAY
        try {
            // Otwórz ustawienia na tym ekranie, na którym jest przycisk.
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
            startActivity(intent, options)
        } catch (e: Exception) {
            Log.w(TAG, "Start na ekranie #$displayId nieudany", e)
            runCatching { startActivity(intent) }
        }
    }

    companion object {
        private const val TAG = "MultiShot"
        private const val HIDE_DELAY_MS = 150L

        @Volatile
        private var instance: ScreenshotService? = null

        val isRunning: Boolean get() = instance != null
    }
}
