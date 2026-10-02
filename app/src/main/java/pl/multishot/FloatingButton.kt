package pl.multishot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pływający przycisk jako nakładka usługi dostępności (TYPE_ACCESSIBILITY_OVERLAY).
 * Może być wyświetlany na dowolnym ekranie – na ekranach dodatkowych przez kontekst okna
 * utworzony dla danego ekranu.
 */
class FloatingButton(
    private val service: AccessibilityService,
    private val prefs: Prefs,
    private val onClick: () -> Unit,
    private val onLongClick: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var view: ImageView? = null
    private var params: WindowManager.LayoutParams? = null

    var currentDisplayId: Int = Display.INVALID_DISPLAY
        private set

    val isShown: Boolean get() = view != null

    /** Pokazuje przycisk na ekranie [displayId] (a gdy się nie da – na ekranie głównym). */
    fun show(displayId: Int) {
        if (view != null && currentDisplayId == displayId) {
            applyStyle()
            return
        }
        hide()
        if (!attach(displayId) && displayId != Display.DEFAULT_DISPLAY) {
            attach(Display.DEFAULT_DISPLAY)
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        val v = view ?: return
        runCatching { windowManager?.removeViewImmediate(v) }
        view = null
        params = null
        windowManager = null
        currentDisplayId = Display.INVALID_DISPLAY
    }

    /** Ukrywa przycisk na czas robienia zrzutu (żeby nie było go na obrazie). */
    fun setHiddenForCapture(hidden: Boolean) {
        view?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** Krótka animacja potwierdzająca zrzut. */
    fun pulse() {
        val v = view ?: return
        v.animate().cancel()
        v.scaleX = 0.7f
        v.scaleY = 0.7f
        v.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    fun applyStyle() {
        val v = view ?: return
        val lp = params ?: return
        val size = sizePx(v.context)
        v.alpha = prefs.buttonAlpha
        val pad = (size * 0.24f).roundToInt()
        v.setPadding(pad, pad, pad, pad)
        if (lp.width != size) {
            lp.width = size
            lp.height = size
        }
        clamp(lp)
        update()
    }

    fun clampToScreen() {
        val lp = params ?: return
        clamp(lp)
        update()
    }

    // Dotyk obsługuje TouchHandler (przeciąganie); tap wywołuje performClick(), więc
    // akcje dostępności (TalkBack) działają przez zwykłe OnClick/OnLongClick.
    @SuppressLint("ClickableViewAccessibility")
    private fun attach(displayId: Int): Boolean {
        val dm = service.getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(displayId) ?: return false
        return try {
            val ctx: Context = if (displayId == Display.DEFAULT_DISPLAY) {
                service
            } else {
                service.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            }
            val wm = ctx.getSystemService(WindowManager::class.java)
            val size = sizePx(ctx)

            val v = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_camera)
                setBackgroundResource(R.drawable.bg_floating_button)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = ctx.getString(R.string.button_description)
                alpha = prefs.buttonAlpha
                val pad = (size * 0.24f).roundToInt()
                setPadding(pad, pad, pad, pad)
            }

            val lp = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                // Celowo LEFT: x/y to bezwzględne współrzędne ekranu, niezależne od kierunku tekstu.
                @SuppressLint("RtlHardcoded")
                gravity = Gravity.TOP or Gravity.LEFT
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = "MultiShot"
            }

            val bounds = wm.currentWindowMetrics.bounds
            val saved = prefs.buttonPosition(displayId)
            lp.x = saved?.x ?: (bounds.width() - size - size / 4)
            lp.y = saved?.y ?: (bounds.height() / 3)

            windowManager = wm
            params = lp
            clamp(lp)

            v.setOnClickListener { onClick() }
            v.setOnLongClickListener { onLongClick(); true }
            v.setOnTouchListener(TouchHandler(ViewConfiguration.get(ctx).scaledTouchSlop))
            wm.addView(v, lp)
            view = v
            currentDisplayId = displayId
            true
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się dodać przycisku na ekranie #$displayId", e)
            windowManager = null
            params = null
            false
        }
    }

    private fun sizePx(ctx: Context): Int =
        (prefs.buttonSizeDp * ctx.resources.displayMetrics.density).roundToInt()

    private fun screenBounds(): Rect? = windowManager?.currentWindowMetrics?.bounds

    private fun clamp(lp: WindowManager.LayoutParams) {
        val b = screenBounds() ?: return
        lp.x = lp.x.coerceIn(0, (b.width() - lp.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (b.height() - lp.height).coerceAtLeast(0))
    }

    private fun update() {
        val v = view ?: return
        runCatching { windowManager?.updateViewLayout(v, params) }
    }

    private inner class TouchHandler(private val touchSlop: Int) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false

        private val longPress = Runnable {
            longPressed = true
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongClick()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = params ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longPressed = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && !longPressed && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        lp.x = startX + dx.roundToInt()
                        lp.y = startY + dy.roundToInt()
                        clamp(lp)
                        update()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    if (dragging) {
                        prefs.saveButtonPosition(currentDisplayId, lp.x, lp.y)
                    } else if (!longPressed) {
                        v.performClick()
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }
            }
            return true
        }
    }

    companion object {
        private const val TAG = "MultiShot"
    }
}
