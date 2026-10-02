package pl.multishot

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.radiobutton.MaterialRadioButton
import pl.multishot.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private data class CaptureChoice(val mode: CaptureMode, val displayId: Int)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = rebuildDisplayOptions()
        override fun onDisplayRemoved(displayId: Int) = rebuildDisplayOptions()
        override fun onDisplayChanged(displayId: Int) = rebuildDisplayOptions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }

        b.switchButton.setOnCheckedChangeListener { _, checked -> prefs.buttonEnabled = checked }

        b.switchVibrate.isChecked = prefs.vibrate
        b.switchVibrate.setOnCheckedChangeListener { _, checked ->
            prefs.vibrate = checked
            if (checked) Feedback.vibrate(this) // podgląd siły wibracji
        }
        b.switchSound.isChecked = prefs.sound
        b.switchSound.setOnCheckedChangeListener { _, checked -> prefs.sound = checked }
        b.switchToast.isChecked = prefs.showToast
        b.switchToast.setOnCheckedChangeListener { _, checked -> prefs.showToast = checked }

        b.sliderTransparency.value = snap(prefs.transparency, 0, Prefs.MAX_TRANSPARENCY, 5)
        updateTransparencyLabel(prefs.transparency)
        b.sliderTransparency.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            updateTransparencyLabel(v)
            if (fromUser) prefs.transparency = v
        }

        b.sliderSize.value = snap(prefs.buttonSizeDp, Prefs.MIN_SIZE_DP, Prefs.MAX_SIZE_DP, 4)
        updateSizeLabel(prefs.buttonSizeDp)
        b.sliderSize.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            updateSizeLabel(v)
            if (fromUser) prefs.buttonSizeDp = v
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            b.btnAddTile.setOnClickListener { requestAddTile() }
            requestNotificationPermissionIfNeeded()
        } else {
            b.btnAddTile.visibility = View.GONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermissionIfNeeded() {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
        b.switchButton.isChecked = prefs.buttonEnabled
        refreshStatus()
        rebuildDisplayOptions()
        // Usługa łączy się chwilę po powrocie z ustawień dostępności.
        handler.postDelayed(::refreshStatus, 600)
    }

    override fun onPause() {
        super.onPause()
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        handler.removeCallbacksAndMessages(null)
    }

    private fun refreshStatus() {
        val running = ScreenshotService.isRunning
        b.textStatus.setText(if (running) R.string.status_running else R.string.status_stopped)
        b.textStatus.setTextColor(
            ContextCompat.getColor(this, if (running) R.color.status_ok else R.color.status_bad),
        )
        b.textRestricted.visibility = if (running) View.GONE else View.VISIBLE
        b.rowServiceButtons.visibility = if (running) View.GONE else View.VISIBLE
    }

    private fun rebuildDisplayOptions() {
        val displays = Displays.list(this)
        b.labelDisplays.text = getString(R.string.label_displays_found, displays.size)

        // Tryb zrzutu
        val choices = mutableListOf(
            getString(R.string.mode_all_stacked) to CaptureChoice(CaptureMode.ALL_STACKED, -1),
            getString(R.string.mode_all_separate) to CaptureChoice(CaptureMode.ALL_SEPARATE, -1),
        )
        displays.forEach { d ->
            choices += getString(R.string.mode_single, Displays.label(this, d)) to
                CaptureChoice(CaptureMode.SINGLE, d.displayId)
        }
        val current = when (prefs.captureMode) {
            CaptureMode.SINGLE -> CaptureChoice(CaptureMode.SINGLE, prefs.captureDisplayId)
            else -> CaptureChoice(prefs.captureMode, -1)
        }
        if (choices.none { it.second == current }) {
            choices += getString(R.string.mode_single_missing, current.displayId) to current
        }
        fillRadioGroup(b.groupCapture, choices, current) { prefs.setCapture(it.mode, it.displayId) }

        // Ekran przycisku – tylko gdy jest z czego wybierać
        val multi = displays.size > 1
        b.labelButtonDisplay.visibility = if (multi) View.VISIBLE else View.GONE
        b.groupButtonDisplay.visibility = if (multi) View.VISIBLE else View.GONE
        if (multi) {
            fillRadioGroup(
                b.groupButtonDisplay,
                displays.map { Displays.label(this, it) to it.displayId },
                prefs.buttonDisplayId,
            ) { prefs.buttonDisplayId = it }
        }
    }

    private fun <T> fillRadioGroup(
        group: RadioGroup,
        items: List<Pair<String, T>>,
        selected: T,
        onSelect: (T) -> Unit,
    ) {
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        val values = HashMap<Int, T>()
        var checkedId = View.NO_ID
        for ((label, value) in items) {
            val rb = MaterialRadioButton(this).apply {
                id = View.generateViewId()
                text = label
            }
            group.addView(rb, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            values[rb.id] = value
            if (value == selected) checkedId = rb.id
        }
        if (checkedId != View.NO_ID) group.check(checkedId)
        group.setOnCheckedChangeListener { _, id -> values[id]?.let(onSelect) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        val sbm = getSystemService(StatusBarManager::class.java)
        sbm.requestAddTileService(
            ComponentName(this, FloatingButtonTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            val msg = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                else -> R.string.tile_not_added
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTransparencyLabel(value: Int) {
        b.labelTransparency.text = getString(R.string.label_transparency, value)
    }

    private fun updateSizeLabel(value: Int) {
        b.labelSize.text = getString(R.string.label_size, value)
    }

    /** Slider wymaga wartości będącej wielokrotnością kroku. */
    private fun snap(value: Int, from: Int, to: Int, step: Int): Float {
        val clamped = value.coerceIn(from, to)
        return (from + ((clamped - from) / step) * step).toFloat()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
    }
}
