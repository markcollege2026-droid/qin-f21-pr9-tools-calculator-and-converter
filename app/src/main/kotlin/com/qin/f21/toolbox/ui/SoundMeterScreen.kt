package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.hardware.SoundLevel
import com.qin.f21.toolbox.core.hardware.SoundMeter

/**
 * Microphone level meter with a simple spectrum display.
 *
 * Phone microphones are not calibrated, so the absolute decibel value is an estimate
 * derived from the full scale reading plus a calibration offset that can be changed in
 * Settings. The dBFS value is always exact.
 */
class SoundMeterScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Sound meter"

    private val meter = SoundMeter(Toolbox.settings.micCalibrationDb)
    private lateinit var decibelLabel: TextView
    private lateinit var detailLabel: TextView
    private lateinit var levelBar: View
    private lateinit var bandViews: List<View>
    private lateinit var startButton: android.widget.Button

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val holder = Widgets.column(context)
        holder.setBackgroundColor(Widgets.color(context, R.color.bg))

        if (!Toolbox.capabilities.hasMicrophone) {
            holder.addView(unavailableCard())
            return holder
        }
        if (!Toolbox.capabilities.hasMicrophonePermission) {
            holder.addView(permissionCard())
            return holder
        }

        // The fixed stack (reading, level bar, spectrum, actions, note) is ~430 dp on a
        // 427 dp screen, so the content scrolls instead of pushing the button off the edge.
        val scroll = android.widget.ScrollView(context).forKeypad()
        scroll.isVerticalScrollBarEnabled = false
        val content = Widgets.column(context)
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        holder.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val readingCard = Widgets.displayCard(context, 14)
        readingCard.gravity = Gravity.CENTER
        readingCard.addView(Widgets.eyebrow(context, context.getString(R.string.sound_level)))
        decibelLabel = Widgets.text(context, "—", R.style.Text_Display)
        decibelLabel.gravity = Gravity.CENTER
        decibelLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 36f)
        readingCard.addView(decibelLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 6) })
        detailLabel = Widgets.caption(context, context.getString(R.string.sound_idle))
        readingCard.addView(detailLabel)
        content.addView(readingCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 12), Widgets.dp(context, 12), 0) })

        // level bar
        val barTrack = Widgets.row(context)
        barTrack.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_high), 0, 6
        )
        levelBar = View(context)
        levelBar.background = Widgets.roundedBackground(context, Widgets.color(context, R.color.primary), 0, 6)
        barTrack.addView(levelBar, LinearLayout.LayoutParams(0, Widgets.dp(context, 10), 0.05f))
        barTrack.addView(View(context), LinearLayout.LayoutParams(0, Widgets.dp(context, 10), 0.95f))
        content.addView(barTrack, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 10)
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0) })

        // spectrum
        content.addView(Widgets.sectionHeader(context, context.getString(R.string.sound_spectrum)))
        val spectrum = Widgets.row(context)
        spectrum.gravity = Gravity.BOTTOM
        val bands = ArrayList<View>()
        for (band in SoundMeter.BANDS) {
            val column = Widgets.column(context)
            column.gravity = Gravity.BOTTOM
            val bar = View(context)
            bar.background = Widgets.roundedBackground(context, Widgets.color(context, R.color.info), 0, 4)
            column.addView(bar, LinearLayout.LayoutParams(Widgets.dp(context, 22), Widgets.dp(context, 4)))
            val label = Widgets.caption(context, bandLabel(band))
            label.gravity = Gravity.CENTER
            column.addView(label)
            spectrum.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            bands.add(bar)
        }
        bandViews = bands
        content.addView(spectrum, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 110)
        ).apply { setMargins(Widgets.dp(context, 12), 0, Widgets.dp(context, 12), 0) })

        startButton = Widgets.button(context, context.getString(R.string.action_start))
        startButton.setOnClickListener { toggle() }
        content.addView(startButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 10), Widgets.dp(context, 12), 0) })

        val note = Widgets.caption(context, context.getString(R.string.sound_calibration_note))
        content.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), Widgets.dp(context, 16)) })

        return holder
    }

    private fun bandLabel(hz: Int): String = when {
        hz >= 1000 -> "${hz / 1000}k"
        else -> hz.toString()
    }

    /**
     * The microphone exists but the app may not open it yet. The request is a deliberate
     * button instead of an automatic prompt, so a rebuild of the screen never loops.
     */
    private fun permissionCard(): View {
        val context = host.activity
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 14),
            Widgets.dp(context, 12), Widgets.dp(context, 14)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_alt), Widgets.color(context, R.color.outline_strong)
        )
        card.addView(Widgets.text(context, context.getString(R.string.sound_unavailable_title), R.style.Text_Subtitle))
        card.addView(Widgets.caption(context, context.getString(R.string.mic_permission_needed)))
        val button = Widgets.button(context, context.getString(R.string.action_grant_permission))
        button.setOnClickListener {
            host.askPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = Widgets.dp(context, 10)
        card.addView(button, params)
        val holder = Widgets.column(context)
        holder.setPadding(Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 8), 0)
        holder.addView(card)
        return holder
    }

    private fun unavailableCard(): View {
        val context = host.activity
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 16),
            Widgets.dp(context, 12), Widgets.dp(context, 16)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_alt), Widgets.color(context, R.color.outline_strong)
        )
        card.addView(Widgets.text(context, context.getString(R.string.sound_unavailable_title), R.style.Text_Subtitle))
        card.addView(Widgets.caption(context, context.getString(R.string.sound_unavailable_message)))
        return card
    }

    private fun toggle() {
        if (meter.isRunning) {
            meter.stop()
            startButton.text = host.activity.getString(R.string.action_start)
            detailLabel.text = host.activity.getString(R.string.sound_idle)
            return
        }
        val started = meter.start { level -> onLevel(level) }
        if (started) {
            startButton.text = host.activity.getString(R.string.action_stop)
        } else {
            host.showMessage(host.activity.getString(R.string.sound_unavailable_message))
        }
    }

    /** The meter calls back on its capture thread, the views are updated on the UI thread. */
    private fun onLevel(level: SoundLevel) {
        host.activity.runOnUiThread { render(level) }
    }

    private fun render(level: SoundLevel) {
        val context = host.activity
        decibelLabel.text = String.format(java.util.Locale.US, "%.1f dB", level.decibels)
        detailLabel.text = context.getString(
            R.string.sound_detail,
            level.dbfs,
            level.rms * 100.0,
            level.peak * 100.0
        )
        val normalised = ((level.decibels + 10.0) / 100.0).coerceIn(0.0, 1.0)
        val params = levelBar.layoutParams as? LinearLayout.LayoutParams
        if (params != null) {
            params.weight = normalised.toFloat().coerceAtLeast(0.03f)
            levelBar.layoutParams = params
        }
        for (index in bandViews.indices) {
            val value = level.bands.getOrNull(index) ?: 0f
            val barParams = bandViews[index].layoutParams as? LinearLayout.LayoutParams
            if (barParams != null) {
                barParams.height = (4 + (value * 74)).toInt().coerceAtLeast(4)
                bandViews[index].layoutParams = barParams
            }
        }
    }

    override fun onHidden() {
        meter.stop()
    }

    companion object {
        const val REQUEST_MIC = 11
    }
}
