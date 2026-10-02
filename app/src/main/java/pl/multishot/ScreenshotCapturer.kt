package pl.multishot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executor

/**
 * Robi zrzuty kolejnych ekranów przez [AccessibilityService.takeScreenshot].
 *
 * System ogranicza częstotliwość wywołań (ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT – limit jest
 * wspólny dla wszystkich ekranów na części wersji Androida), więc przy tym błędzie ponawiamy
 * próbę po krótkiej przerwie zamiast od razu pomijać ekran.
 */
class ScreenshotCapturer(
    private val service: AccessibilityService,
    private val worker: Executor,
) {
    class Shot(val displayId: Int, val bitmap: Bitmap)
    class Outcome(val shots: List<Shot>, val failures: Map<Int, Int>)

    private val handler = Handler(Looper.getMainLooper())

    /** [callback] wywoływany na głównym wątku; kolejność ujęć = kolejność [displayIds]. */
    fun capture(displayIds: List<Int>, callback: (Outcome) -> Unit) {
        val queue = ArrayDeque(displayIds)
        val shots = mutableListOf<Shot>()
        val failures = linkedMapOf<Int, Int>()

        fun next(attempt: Int) {
            val id = queue.firstOrNull()
            if (id == null) {
                callback(Outcome(shots, failures))
                return
            }
            val fail: (Int) -> Unit = { code ->
                if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT &&
                    attempt < MAX_RETRIES
                ) {
                    handler.postDelayed({ next(attempt + 1) }, RETRY_DELAY_MS)
                } else {
                    Log.w(TAG, "Zrzut ekranu #$id nieudany, kod $code")
                    queue.removeFirst()
                    failures[id] = code
                    next(0)
                }
            }
            try {
                service.takeScreenshot(id, worker, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bitmap = toSoftwareBitmap(result) // na wątku roboczym
                        handler.post {
                            if (bitmap != null) {
                                queue.removeFirst()
                                shots += Shot(id, bitmap)
                                next(0)
                            } else {
                                fail(ERROR_CONVERSION)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        handler.post { fail(errorCode) }
                    }
                })
            } catch (e: RuntimeException) {
                Log.e(TAG, "takeScreenshot rzucił wyjątek dla ekranu #$id", e)
                fail(ERROR_EXCEPTION)
            }
        }

        next(0)
    }

    private fun toSoftwareBitmap(result: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        return try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            // Bitmapy sprzętowej nie da się narysować na programowym Canvas – kopiujemy.
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            software
        } catch (t: Throwable) {
            Log.e(TAG, "Konwersja zrzutu nieudana", t)
            null
        } finally {
            buffer.close()
        }
    }

    companion object {
        private const val TAG = "MultiShot"
        private const val MAX_RETRIES = 8
        private const val RETRY_DELAY_MS = 350L
        const val ERROR_CONVERSION = -1
        const val ERROR_EXCEPTION = -2
    }
}
