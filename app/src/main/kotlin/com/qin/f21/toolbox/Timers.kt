package com.qin.f21.toolbox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Countdown and stopwatch state that outlives the screen that started it.
 *
 * The screen is only a view of this object. Pressing Back used to remove the ticker with
 * its views, which silently cancelled a running countdown; now the calculation and the
 * ticker live here, so the countdown fires while the user is in another tool.
 *
 * The ticker runs on the main looper, which keeps working while the activity is stopped
 * and the screen is off. It does **not** survive the process being killed: the toolbox has
 * no foreground service on purpose (a service would add a permanent notification and a
 * second process lifecycle for a feature that a phone that stays awake does not need).
 */
object Timers {

    private val handler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null

    /** The application context is enough to post the alarm; no activity is kept alive. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    // ---- countdown -----------------------------------------------------------

    /** Last duration set by the user, in milliseconds. */
    var countdownTotal: Long = 5 * 60 * 1000L
        private set

    private var countdownRemaining: Long = countdownTotal
    private var countdownEndAt: Long = 0L

    var countdownRunning: Boolean = false
        private set

    /** True after the countdown reached zero, until it is started or reset again. */
    var countdownFinished: Boolean = false
        private set

    /** Called on every tick and every state change; the visible screen sets this. */
    var onCountdownChanged: (() -> Unit)? = null

    /** Called once when the countdown reaches zero, whether a screen is visible or not. */
    var onCountdownElapsed: (() -> Unit)? = null

    fun countdownRemaining(): Long =
        if (countdownRunning) {
            (countdownEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            countdownRemaining
        }

    fun setCountdownDuration(millis: Long) {
        if (countdownRunning) return
        countdownTotal = millis.coerceAtLeast(0L)
        countdownRemaining = countdownTotal
        countdownFinished = false
        onCountdownChanged?.invoke()
    }

    fun startCountdown() {
        if (countdownRunning) return
        if (countdownRemaining <= 0L) countdownRemaining = countdownTotal
        if (countdownRemaining <= 0L) return
        countdownEndAt = SystemClock.elapsedRealtime() + countdownRemaining
        countdownRunning = true
        countdownFinished = false
        handler.removeCallbacks(countdownTick)
        handler.post(countdownTick)
        onCountdownChanged?.invoke()
    }

    fun pauseCountdown() {
        if (!countdownRunning) return
        countdownRemaining = countdownRemaining()
        countdownRunning = false
        handler.removeCallbacks(countdownTick)
        onCountdownChanged?.invoke()
    }

    fun resetCountdown() {
        countdownRunning = false
        countdownFinished = false
        handler.removeCallbacks(countdownTick)
        countdownRemaining = countdownTotal
        onCountdownChanged?.invoke()
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (!countdownRunning) return
            val remaining = countdownRemaining()
            if (remaining <= 0L) {
                countdownRunning = false
                countdownFinished = true
                countdownRemaining = 0L
                onCountdownChanged?.invoke()
                // The alarm belongs to the timer, not to the screen: it must ring even when
                // the user is in another tool and nothing is listening to the callbacks.
                val context = appContext
                if (context != null) {
                    CountdownAlarm.fire(
                        context,
                        context.getString(R.string.timer_channel),
                        context.getString(R.string.timer_notification_title),
                        context.getString(R.string.timer_notification_text)
                    )
                }
                onCountdownElapsed?.invoke()
                return
            }
            onCountdownChanged?.invoke()
            handler.postDelayed(this, 100L)
        }
    }

    // ---- stopwatch -----------------------------------------------------------

    private var stopwatchBase: Long = 0L
    private var stopwatchStartedAt: Long = 0L
    private val lapMarks = ArrayList<Long>()

    var stopwatchRunning: Boolean = false
        private set

    var onStopwatchChanged: (() -> Unit)? = null

    fun stopwatchElapsed(): Long =
        stopwatchBase + if (stopwatchRunning) SystemClock.elapsedRealtime() - stopwatchStartedAt else 0L

    /** Cumulative times at each lap mark, oldest first. */
    fun laps(): List<Long> = lapMarks

    fun startStopwatch() {
        if (stopwatchRunning) return
        stopwatchStartedAt = SystemClock.elapsedRealtime()
        stopwatchRunning = true
        handler.removeCallbacks(stopwatchTick)
        handler.post(stopwatchTick)
        onStopwatchChanged?.invoke()
    }

    fun pauseStopwatch() {
        if (!stopwatchRunning) return
        stopwatchBase = stopwatchElapsed()
        stopwatchRunning = false
        handler.removeCallbacks(stopwatchTick)
        onStopwatchChanged?.invoke()
    }

    fun resetStopwatch() {
        stopwatchRunning = false
        stopwatchBase = 0L
        lapMarks.clear()
        handler.removeCallbacks(stopwatchTick)
        onStopwatchChanged?.invoke()
    }

    /** Adds a lap and returns the cumulative time at that mark. */
    fun addLap(): Long {
        val elapsed = stopwatchElapsed()
        lapMarks.add(elapsed)
        onStopwatchChanged?.invoke()
        return elapsed
    }

    /** Stops the ticker when the whole app goes away. */
    fun detach() {
        onCountdownChanged = null
        onStopwatchChanged = null
        onCountdownElapsed = null
    }

    private val stopwatchTick = object : Runnable {
        override fun run() {
            if (!stopwatchRunning) return
            onStopwatchChanged?.invoke()
            handler.postDelayed(this, 50L)
        }
    }
}

/** Notification and vibration used when the countdown reaches zero. */
object CountdownAlarm {

    private const val CHANNEL_ID = "timer"
    private const val NOTIFICATION_ID = 42

    fun fire(context: Context, channelName: String, title: String, text: String) {
        val app = context.applicationContext
        Toolbox.capabilities.vibrate(600)
        try {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH)
            )
            val intent = Intent(app, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pending = PendingIntent.getActivity(
                app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = android.app.Notification.Builder(app, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_timer)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // The vibration is the primary signal; a notification is best effort.
        }
    }
}
