package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Timers
import com.qin.f21.toolbox.core.calc.DurationFormatter

/**
 * Countdown timer.
 *
 * The countdown itself lives in [Timers], based on `SystemClock.elapsedRealtime`, so it
 * keeps running when the user opens another tool or turns the screen off. When it reaches
 * zero the device vibrates and a notification is posted.
 */
class TimerScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Timer"

    private lateinit var timeLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var startButton: android.widget.Button
    private lateinit var resetButton: android.widget.Button
    private lateinit var hoursField: EditText
    private lateinit var minutesField: EditText
    private lateinit var secondsField: EditText

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val timeCard = Widgets.displayCard(context, 16)
        timeCard.gravity = Gravity.CENTER
        val eyebrow = Widgets.eyebrow(context, context.getString(R.string.timer_remaining))
        timeCard.addView(eyebrow)
        timeLabel = Widgets.text(context, "05:00", R.style.Text_Display)
        timeLabel.gravity = Gravity.CENTER
        timeLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 38f)
        timeCard.addView(timeLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 6) })
        statusLabel = Widgets.caption(context, context.getString(R.string.timer_ready))
        timeCard.addView(statusLabel)
        root.addView(timeCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 12), Widgets.dp(context, 12), 0) })

        val inputRow = Widgets.row(context)
        hoursField = Widgets.numericField(context, "0")
        minutesField = Widgets.numericField(context, "5")
        secondsField = Widgets.numericField(context, "0")
        hoursField.setText((Timers.countdownTotal / 3_600_000L).toString())
        minutesField.setText((Timers.countdownTotal / 60_000L % 60L).toString())
        secondsField.setText((Timers.countdownTotal / 1000L % 60L).toString())
        inputRow.addView(hoursField, LinearLayout.LayoutParams(0, Widgets.dp(context, 44), 1f))
        inputRow.addView(Widgets.text(context, "h", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        inputRow.addView(minutesField, LinearLayout.LayoutParams(0, Widgets.dp(context, 44), 1f))
        inputRow.addView(Widgets.text(context, "m", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        inputRow.addView(secondsField, LinearLayout.LayoutParams(0, Widgets.dp(context, 44), 1f))
        inputRow.addView(Widgets.text(context, "s", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(inputRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0) })

        val actions = Widgets.row(context)
        startButton = Widgets.button(context, context.getString(R.string.action_start))
        startButton.setOnClickListener { toggle() }
        resetButton = Widgets.button(context, context.getString(R.string.action_reset), secondary = true)
        resetButton.setOnClickListener { reset() }
        actions.addView(startButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 8))
        actions.addView(resetButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0) })

        // Presets as chips in one scrollable line: five 14 sp labels never fit 320 dp.
        val presetValues = listOf(1 to "1 min", 3 to "3 min", 5 to "5 min", 10 to "10 min", 25 to "25 min")
        val presetScroll = android.widget.HorizontalScrollView(context)
        presetScroll.isHorizontalScrollBarEnabled = false
        presetScroll.clipToPadding = false
        val presets = Widgets.row(context)
        presets.gravity = Gravity.CENTER_VERTICAL
        for ((minutes, label) in presetValues) {
            val button = Widgets.chip(context, label) {
                hoursField.setText("0")
                minutesField.setText(minutes.toString())
                secondsField.setText("0")
                readInput()
            }
            presets.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Widgets.dp(context, 32)
            ).apply { rightMargin = Widgets.dp(context, 6) })
        }
        presetScroll.addView(presets)
        root.addView(presetScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0) })

        updateDisplay()
        return root
    }

    private fun readInput() {
        val hours = (hoursField.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val minutes = (minutesField.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val seconds = (secondsField.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
        Timers.setCountdownDuration((hours * 3600L + minutes * 60L + seconds) * 1000L)
        updateDisplay()
    }

    private fun toggle() {
        if (Timers.countdownRunning) {
            Timers.pauseCountdown()
        } else {
            if (Timers.countdownRemaining() <= 0L) readInput()
            if (Timers.countdownRemaining() <= 0L) {
                host.showMessage(host.activity.getString(R.string.timer_set_duration))
                return
            }
            Timers.startCountdown()
        }
        updateDisplay()
    }

    private fun reset() {
        readInput()
        Timers.resetCountdown()
        updateDisplay()
    }


    private fun updateDisplay() {
        if (!this::timeLabel.isInitialized) return
        val remaining = Timers.countdownRemaining()
        startButton.text = host.activity.getString(
            if (Timers.countdownRunning) R.string.action_pause else R.string.action_start
        )
        statusLabel.text = host.activity.getString(
            when {
                Timers.countdownRunning -> R.string.timer_running
                Timers.countdownFinished -> R.string.timer_finished
                remaining != Timers.countdownTotal -> R.string.timer_paused
                else -> R.string.timer_ready
            }
        )
        timeLabel.text = DurationFormatter.format(remaining)
        timeLabel.setTextColor(
            Widgets.color(
                host.activity,
                if (remaining in 1..10000) R.color.danger else R.color.primary
            )
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
            when {
                hoursField.hasFocus() -> hoursField.append(digit)
                minutesField.hasFocus() -> minutesField.append(digit)
                secondsField.hasFocus() -> secondsField.append(digit)
            }
            return true
        }
        return false
    }

    override fun onShown() {
        // The countdown keeps running without the screen, so the fields catch up here.
        Timers.onCountdownChanged = { updateDisplay() }
        Timers.onCountdownElapsed = { updateDisplay() }
        updateDisplay()
    }

    override fun onHidden() {
        if (Timers.onCountdownChanged != null) Timers.onCountdownChanged = null
        if (Timers.onCountdownElapsed != null) Timers.onCountdownElapsed = null
    }
}
