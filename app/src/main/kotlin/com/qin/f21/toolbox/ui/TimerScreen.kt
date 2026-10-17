package com.qin.f21.toolbox.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.MainActivity
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.calc.DurationFormatter

/**
 * Countdown timer.
 *
 * The countdown is based on [SystemClock.elapsedRealtime]. When it reaches zero the device
 * vibrates and a notification is posted (Android 11 requires the notification permission
 * for API 33+, which the app asks for when a timer is started).
 */
class TimerScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Timer"

    private var totalMillis = 5 * 60 * 1000L
    private var remaining = totalMillis
    private var running = false
    private var endAt = 0L

    private lateinit var timeLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var startButton: android.widget.Button
    private lateinit var resetButton: android.widget.Button
    private lateinit var hoursField: EditText
    private lateinit var minutesField: EditText
    private lateinit var secondsField: EditText
    private var finished = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            remaining = endAt - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                remaining = 0
                finish()
            }
            updateDisplay()
            host.activity.window.decorView.postDelayed(this, 100)
        }
    }

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val timeCard = Widgets.column(context)
        timeCard.gravity = Gravity.CENTER
        timeCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 18),
            Widgets.dp(context, 12), Widgets.dp(context, 18)
        )
        timeCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        timeLabel = Widgets.text(context, "05:00", R.style.Text_Display)
        timeLabel.gravity = Gravity.CENTER
        timeLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 38f)
        timeCard.addView(timeLabel)
        statusLabel = Widgets.caption(context, context.getString(R.string.timer_ready))
        timeCard.addView(statusLabel)
        root.addView(timeCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 8), 0) })

        val inputRow = Widgets.row(context)
        hoursField = Widgets.numericField(context, "0")
        minutesField = Widgets.numericField(context, "5")
        secondsField = Widgets.numericField(context, "0")
        hoursField.setText("0")
        minutesField.setText("5")
        secondsField.setText("0")
        inputRow.addView(hoursField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(Widgets.text(context, "h", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        inputRow.addView(minutesField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(Widgets.text(context, "m", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        inputRow.addView(secondsField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 8), 0) })

        val presets = Widgets.row(context)
        val presetValues = listOf(1 to "1 min", 3 to "3 min", 5 to "5 min", 10 to "10 min", 25 to "25 min")
        for ((minutes, label) in presetValues) {
            val button = Widgets.button(context, label, secondary = true)
            button.setOnClickListener {
                hoursField.setText("0")
                minutesField.setText(minutes.toString())
                secondsField.setText("0")
                readInput()
            }
            presets.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            presets.addView(Widgets.spacer(context, 4))
        }
        root.addView(presets, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), 0, Widgets.dp(context, 8), 0) })

        updateDisplay()
        return root
    }

    private fun readInput() {
        val hours = (hoursField.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val minutes = (minutesField.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val seconds = (secondsField.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
        totalMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L
        remaining = totalMillis
        finished = false
        updateDisplay()
    }

    private fun toggle() {
        if (running) {
            remaining = endAt - SystemClock.elapsedRealtime()
            running = false
            host.activity.window.decorView.removeCallbacks(ticker)
            startButton.text = host.activity.getString(R.string.action_start)
            statusLabel.text = host.activity.getString(R.string.timer_paused)
        } else {
            if (remaining <= 0) readInput()
            if (remaining <= 0) {
                host.showMessage(host.activity.getString(R.string.timer_set_duration))
                return
            }
            endAt = SystemClock.elapsedRealtime() + remaining
            running = true
            startButton.text = host.activity.getString(R.string.action_pause)
            statusLabel.text = host.activity.getString(R.string.timer_running)
            host.activity.window.decorView.post(ticker)
        }
    }

    private fun reset() {
        running = false
        finished = false
        host.activity.window.decorView.removeCallbacks(ticker)
        readInput()
        startButton.text = host.activity.getString(R.string.action_start)
        statusLabel.text = host.activity.getString(R.string.timer_ready)
    }

    private fun finish() {
        running = false
        finished = true
        startButton.text = host.activity.getString(R.string.action_start)
        statusLabel.text = host.activity.getString(R.string.timer_finished)
        Toolbox.capabilities.vibrate(600)
        notifyFinished()
    }

    private fun notifyFinished() {
        try {
            val manager = host.activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    host.activity.getString(R.string.timer_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
            val intent = Intent(host.activity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pending = PendingIntent.getActivity(
                host.activity, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = android.app.Notification.Builder(host.activity, CHANNEL_ID)
                .setContentTitle(host.activity.getString(R.string.timer_notification_title))
                .setContentText(host.activity.getString(R.string.timer_notification_text))
                .setSmallIcon(R.drawable.ic_timer)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // notifications are optional
        }
    }

    private fun updateDisplay() {
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

    override fun onHidden() {
        host.activity.window.decorView.removeCallbacks(ticker)
    }

    companion object {
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 42
    }
}
