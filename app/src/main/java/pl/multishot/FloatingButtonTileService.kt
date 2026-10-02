package pl.multishot

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/** Kafelek w szybkich ustawieniach: włącza / wyłącza pływający przycisk. */
class FloatingButtonTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (!ScreenshotService.isRunning) {
            // Bez usługi dostępności przycisk i tak by się nie pokazał – otwórz aplikację z instrukcją.
            openApp()
            return
        }
        val prefs = Prefs(this)
        prefs.buttonEnabled = !prefs.buttonEnabled
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val running = ScreenshotService.isRunning
        val enabled = Prefs(this).buttonEnabled
        tile.state = if (running && enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(
            when {
                !running -> R.string.tile_subtitle_no_service
                enabled -> R.string.tile_subtitle_on
                else -> R.string.tile_subtitle_off
            },
        )
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, FloatingButtonTileService::class.java),
                )
            } catch (e: Exception) {
                Log.w("MultiShot", "requestListeningState nieudane", e)
            }
        }
    }
}
