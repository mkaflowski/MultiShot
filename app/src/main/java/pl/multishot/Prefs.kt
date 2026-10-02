package pl.multishot

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point
import android.view.Display

enum class CaptureMode {
    /** Wszystkie ekrany w jednym obrazie, jeden pod drugim (domyślnie). */
    ALL_STACKED,

    /** Wszystkie ekrany, każdy w osobnym pliku. */
    ALL_SEPARATE,

    /** Tylko jeden wybrany ekran. */
    SINGLE,
}

/**
 * Ustawienia aplikacji. Wszystkie instancje korzystają z tego samego pliku SharedPreferences,
 * więc usługa dostępności dostaje zmiany z aktywności i kafelka przez listener.
 */
class Prefs(context: Context) {
    private val appContext = context.applicationContext
    val sp: SharedPreferences = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var buttonEnabled: Boolean
        get() = sp.getBoolean(KEY_BUTTON_ENABLED, true)
        set(value) {
            if (value == buttonEnabled) return
            sp.edit().putBoolean(KEY_BUTTON_ENABLED, value).apply()
            FloatingButtonTileService.requestUpdate(appContext)
        }

    /** Przezroczystość przycisku w procentach (0 = nieprzezroczysty). */
    var transparency: Int
        get() = sp.getInt(KEY_TRANSPARENCY, DEFAULT_TRANSPARENCY).coerceIn(0, MAX_TRANSPARENCY)
        set(value) = sp.edit().putInt(KEY_TRANSPARENCY, value.coerceIn(0, MAX_TRANSPARENCY)).apply()

    val buttonAlpha: Float get() = 1f - transparency / 100f

    var buttonSizeDp: Int
        get() = sp.getInt(KEY_BUTTON_SIZE, DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
        set(value) = sp.edit().putInt(KEY_BUTTON_SIZE, value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)).apply()

    var buttonDisplayId: Int
        get() = sp.getInt(KEY_BUTTON_DISPLAY, Display.DEFAULT_DISPLAY)
        set(value) = sp.edit().putInt(KEY_BUTTON_DISPLAY, value).apply()

    var captureMode: CaptureMode
        get() = runCatching { CaptureMode.valueOf(sp.getString(KEY_CAPTURE_MODE, null)!!) }
            .getOrDefault(CaptureMode.ALL_STACKED)
        set(value) = sp.edit().putString(KEY_CAPTURE_MODE, value.name).apply()

    var captureDisplayId: Int
        get() = sp.getInt(KEY_CAPTURE_DISPLAY, Display.DEFAULT_DISPLAY)
        set(value) = sp.edit().putInt(KEY_CAPTURE_DISPLAY, value).apply()

    /** Lekka wibracja w chwili zrobienia zrzutu. */
    var vibrate: Boolean
        get() = sp.getBoolean(KEY_VIBRATE, false)
        set(value) = sp.edit().putBoolean(KEY_VIBRATE, value).apply()

    /** Dźwięk migawki w chwili zrobienia zrzutu. */
    var sound: Boolean
        get() = sp.getBoolean(KEY_SOUND, false)
        set(value) = sp.edit().putBoolean(KEY_SOUND, value).apply()

    /** Komunikat „Zapisano zrzut” (błędy są pokazywane zawsze). */
    var showToast: Boolean
        get() = sp.getBoolean(KEY_TOAST, true)
        set(value) = sp.edit().putBoolean(KEY_TOAST, value).apply()

    fun setCapture(mode: CaptureMode, displayId: Int) {
        sp.edit().putString(KEY_CAPTURE_MODE, mode.name).putInt(KEY_CAPTURE_DISPLAY, displayId).apply()
    }

    fun buttonPosition(displayId: Int): Point? {
        val x = sp.getInt("$KEY_POS_X$displayId", Int.MIN_VALUE)
        val y = sp.getInt("$KEY_POS_Y$displayId", Int.MIN_VALUE)
        return if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) null else Point(x, y)
    }

    fun saveButtonPosition(displayId: Int, x: Int, y: Int) {
        sp.edit().putInt("$KEY_POS_X$displayId", x).putInt("$KEY_POS_Y$displayId", y).apply()
    }

    companion object {
        private const val FILE = "multishot"

        const val KEY_BUTTON_ENABLED = "button_enabled"
        const val KEY_TRANSPARENCY = "button_transparency"
        const val KEY_BUTTON_SIZE = "button_size_dp"
        const val KEY_BUTTON_DISPLAY = "button_display"
        const val KEY_CAPTURE_MODE = "capture_mode"
        const val KEY_CAPTURE_DISPLAY = "capture_display"
        const val KEY_VIBRATE = "feedback_vibrate"
        const val KEY_SOUND = "feedback_sound"
        const val KEY_TOAST = "feedback_toast"
        private const val KEY_POS_X = "pos_x_"
        private const val KEY_POS_Y = "pos_y_"

        const val DEFAULT_TRANSPARENCY = 30
        const val MAX_TRANSPARENCY = 90
        const val DEFAULT_SIZE_DP = 56
        const val MIN_SIZE_DP = 36
        const val MAX_SIZE_DP = 96
    }
}
