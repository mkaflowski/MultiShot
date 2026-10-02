package pl.multishot

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object Displays {

    /** Publiczne ekrany urządzenia (bez prywatnych ekranów wirtualnych innych aplikacji), wg id. */
    fun list(context: Context): List<Display> =
        context.getSystemService(DisplayManager::class.java).displays
            .filter { it.flags and Display.FLAG_PRIVATE == 0 }
            .sortedBy { it.displayId }

    /** Ekrany, z których robimy zrzut w trybie „wszystkie” – pomijamy wyłączone. */
    fun capturable(context: Context): List<Display> {
        val on = list(context).filter { it.state != Display.STATE_OFF }
        if (on.isNotEmpty()) return on
        val default = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        return listOfNotNull(default)
    }

    fun exists(context: Context, displayId: Int): Boolean =
        context.getSystemService(DisplayManager::class.java).getDisplay(displayId) != null

    fun label(context: Context, display: Display): String {
        val mode = display.mode
        val res = if (display.state == Display.STATE_OFF) R.string.display_label_off else R.string.display_label
        return context.getString(res, display.name, display.displayId, mode.physicalWidth, mode.physicalHeight)
    }
}
