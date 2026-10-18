package com.qin.f21.toolbox.ui

import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.calc.DurationFormatter

/**
 * Stopwatch with lap times.
 *
 * The timer is based on [SystemClock.elapsedRealtime], so it keeps running accurately even
 * when the screen is off or the device is busy.
 */
class StopwatchScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Stopwatch"

    private var running = false
    private var baseElapsed = 0L
    private var startedAt = 0L
    private val laps = ArrayList<Long>()

    private lateinit var timeLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var lapColumn: LinearLayout
    private lateinit var startButton: android.widget.Button
    private lateinit var lapButton: android.widget.Button
    private lateinit var resetButton: android.widget.Button

    private val ticker = object : Runnable {
        override fun run() {
            updateDisplay()
            if (running) {
                host.activity.window.decorView.postDelayed(this, 33)
            }
        }
    }

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val timeCard = Widgets.column(context)
        timeCard.gravity = Gravity.CENTER
        timeCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 16),
            Widgets.dp(context, 12), Widgets.dp(context, 16)
        )
        timeCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        timeLabel = Widgets.text(context, "00:00.000", R.style.Text_Display)
        timeLabel.gravity = Gravity.CENTER
        timeLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f)
        timeCard.addView(timeLabel)
        statusLabel = Widgets.caption(context, context.getString(R.string.stopwatch_ready))
        timeCard.addView(statusLabel)
        root.addView(timeCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 8), 0) })

        val actions = Widgets.row(context)
        startButton = Widgets.button(context, context.getString(R.string.action_start))
        startButton.setOnClickListener { toggle() }
        lapButton = Widgets.button(context, context.getString(R.string.action_lap), secondary = true)
        lapButton.setOnClickListener { lap() }
        lapButton.isEnabled = false
        resetButton = Widgets.button(context, context.getString(R.string.action_reset), secondary = true)
        resetButton.setOnClickListener { reset() }
        actions.addView(startButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 6))
        actions.addView(lapButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 6))
        actions.addView(resetButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0) })

        root.addView(Widgets.sectionHeader(context, context.getString(R.string.stopwatch_laps)))
        val scroll = ScrollView(context).forKeypad()
        lapColumn = Widgets.column(context)
        scroll.addView(lapColumn, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(Widgets.dp(context, 8), 0, Widgets.dp(context, 8), Widgets.dp(context, 8)) })

        updateDisplay()
        return root
    }

    private fun elapsed(): Long = baseElapsed + if (running) SystemClock.elapsedRealtime() - startedAt else 0L

    private fun toggle() {
        if (running) {
            baseElapsed = elapsed()
            running = false
            startButton.text = host.activity.getString(R.string.action_start)
            statusLabel.text = host.activity.getString(R.string.stopwatch_paused)
            host.activity.window.decorView.removeCallbacks(ticker)
        } else {
            startedAt = SystemClock.elapsedRealtime()
            running = true
            startButton.text = host.activity.getString(R.string.action_stop)
            statusLabel.text = host.activity.getString(R.string.stopwatch_running)
            host.activity.window.decorView.post(ticker)
        }
        updateLaps()
    }

    private fun lap() {
        if (!running) return
        laps.add(elapsed())
        updateLaps()
    }

    private fun reset() {
        running = false
        baseElapsed = 0
        laps.clear()
        startButton.text = host.activity.getString(R.string.action_start)
        statusLabel.text = host.activity.getString(R.string.stopwatch_ready)
        host.activity.window.decorView.removeCallbacks(ticker)
        updateDisplay()
        updateLaps()
    }

    private fun updateDisplay() {
        timeLabel.text = DurationFormatter.formatStopwatch(elapsed())
    }

    private fun updateLaps() {
        lapColumn.removeAllViews()
        if (laps.isEmpty()) {
            lapColumn.addView(Widgets.caption(host.activity, host.activity.getString(R.string.stopwatch_no_laps)))
            return
        }
        var previous = 0L
        for (index in laps.indices.reversed()) {
            val total = laps[index]
            val split = total - previous
            previous = total
            val row = Widgets.row(host.activity)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(
                Widgets.dp(host.activity, 12), Widgets.dp(host.activity, 8),
                Widgets.dp(host.activity, 12), Widgets.dp(host.activity, 8)
            )
            row.background = Widgets.roundedBackground(
                host.activity,
                Widgets.color(host.activity, R.color.surface),
                Widgets.color(host.activity, R.color.outline)
            )
            val number = Widgets.text(host.activity, "#" + (index + 1), R.style.Text_Label)
            number.setTextColor(Widgets.color(host.activity, R.color.text_muted))
            row.addView(number, LinearLayout.LayoutParams(Widgets.dp(host.activity, 34), ViewGroup.LayoutParams.WRAP_CONTENT))
            val totalView = Widgets.text(host.activity, DurationFormatter.formatStopwatch(total), R.style.Text_Mono)
            row.addView(totalView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val splitView = Widgets.text(host.activity, "+" + DurationFormatter.formatStopwatch(split), R.style.Text_Caption)
            splitView.setTextColor(Widgets.color(host.activity, R.color.accent))
            row.addView(splitView)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = Widgets.dp(host.activity, 4)
            row.layoutParams = params
            lapColumn.addView(row)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (host.activity.currentFocus !is android.widget.Button) {
                    toggle()
                    return true
                }
            }
            KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_SPACE -> {
                lap()
                return true
            }
        }
        return false
    }

    override fun onHidden() {
        host.activity.window.decorView.removeCallbacks(ticker)
    }
}
