package pl.multishot

import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/** Potwierdzenie zrobienia zrzutu: lekka wibracja i dźwięk migawki. */
object Feedback {

    fun vibrate(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            // Krótki, słaby impuls; bez kontroli amplitudy – predefiniowany „tick”.
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createOneShot(VIBRATION_MS, VIBRATION_AMPLITUDE)
            } else {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.w("MultiShot", "Wibracja nieudana", e)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** Systemowy dźwięk migawki aparatu. Ładowany raz, zwalniany przez [release]. */
    class Shutter {
        private var sound: MediaActionSound? = MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
        }

        fun play() {
            runCatching { sound?.play(MediaActionSound.SHUTTER_CLICK) }
        }

        fun release() {
            sound?.release()
            sound = null
        }
    }

    private const val VIBRATION_MS = 25L
    private const val VIBRATION_AMPLITUDE = 80
}
