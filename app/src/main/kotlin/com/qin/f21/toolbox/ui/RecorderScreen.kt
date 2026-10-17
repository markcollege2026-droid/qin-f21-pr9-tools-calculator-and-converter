package com.qin.f21.toolbox.ui

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.data.ToolboxFileProvider
import com.qin.f21.toolbox.core.hardware.VoiceRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Voice recorder. Clips are written as WAV files into the app specific external storage
 * and can be shared with other apps through the content provider.
 */
class RecorderScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Voice recorder"

    private val recorder = VoiceRecorder()
    private lateinit var timeLabel: TextView
    private lateinit var levelBar: View
    private lateinit var recordButton: android.widget.Button
    private lateinit var listColumn: LinearLayout
    private var startedAt = 0L

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        if (!Toolbox.capabilities.hasMicrophone) {
            root.addView(unavailableCard())
            return root
        }

        val card = Widgets.column(context)
        card.gravity = Gravity.CENTER
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 14),
            Widgets.dp(context, 12), Widgets.dp(context, 14)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        timeLabel = Widgets.text(context, "00:00", R.style.Text_Display)
        timeLabel.gravity = Gravity.CENTER
        timeLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f)
        card.addView(timeLabel)

        val barTrack = Widgets.row(context)
        barTrack.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_high), 0, 6
        )
        levelBar = View(context)
        levelBar.background = Widgets.roundedBackground(context, Widgets.color(context, R.color.danger), 0, 6)
        barTrack.addView(levelBar, LinearLayout.LayoutParams(0, Widgets.dp(context, 8), 0.02f))
        barTrack.addView(View(context), LinearLayout.LayoutParams(0, Widgets.dp(context, 8), 0.98f))
        val barParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 8)
        )
        barParams.topMargin = Widgets.dp(context, 10)
        barTrack.layoutParams = barParams
        card.addView(barTrack)
        root.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 8), 0) })

        recordButton = Widgets.button(context, context.getString(R.string.action_record))
        recordButton.setOnClickListener { toggle() }
        root.addView(recordButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 4), Widgets.dp(context, 8), 0) })

        root.addView(Widgets.sectionHeader(context, context.getString(R.string.recorder_files)))
        val scroll = ScrollView(context)
        listColumn = Widgets.column(context)
        scroll.addView(listColumn, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(Widgets.dp(context, 8), 0, Widgets.dp(context, 8), Widgets.dp(context, 8)) })

        refreshList()
        return root
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
        card.addView(Widgets.text(context, context.getString(R.string.recorder_unavailable_title), R.style.Text_Subtitle))
        card.addView(Widgets.caption(context, context.getString(R.string.sound_unavailable_message)))
        return card
    }

    private fun recordingDirectory(): File {
        val dir = File(host.activity.getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun toggle() {
        if (recorder.isRecording) {
            val file = recorder.stop()
            recordButton.text = host.activity.getString(R.string.action_record)
            timeLabel.text = "00:00"
            if (file != null) {
                host.showMessage(host.activity.getString(R.string.recorder_saved, file.name))
                refreshList()
            }
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(recordingDirectory(), "recording-$stamp.wav")
        if (recorder.start(file)) {
            startedAt = System.currentTimeMillis()
            recordButton.text = host.activity.getString(R.string.action_stop)
            host.activity.window.decorView.post(tick)
        } else {
            host.showMessage(host.activity.getString(R.string.sound_unavailable_message))
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!recorder.isRecording) return
            val elapsed = System.currentTimeMillis() - startedAt
            timeLabel.text = com.qin.f21.toolbox.core.calc.DurationFormatter.format(elapsed)
            val params = levelBar.layoutParams as? LinearLayout.LayoutParams
            if (params != null) {
                params.weight = recorder.amplitude.coerceIn(0.02, 1.0).toFloat()
                levelBar.layoutParams = params
            }
            host.activity.window.decorView.postDelayed(this, 80)
        }
    }

    private fun refreshList() {
        val context = host.activity
        listColumn.removeAllViews()
        val files = recordingDirectory().listFiles { file -> file.extension.equals("wav", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (files.isEmpty()) {
            listColumn.addView(Widgets.caption(context, context.getString(R.string.recorder_no_files)))
            return
        }
        for (file in files) {
            val row = Widgets.row(context)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(
                Widgets.dp(context, 12), Widgets.dp(context, 8),
                Widgets.dp(context, 12), Widgets.dp(context, 8)
            )
            row.background = Widgets.roundedBackground(
                context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
            )
            row.isFocusable = true
            row.isClickable = true
            val column = Widgets.column(context)
            column.addView(Widgets.text(context, file.name, R.style.Text_Label))
            column.addView(Widgets.text(
                context,
                com.qin.f21.toolbox.core.hardware.DeviceInfo.formatBytes(file.length()),
                R.style.Text_Caption
            ))
            row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val share = Widgets.button(context, context.getString(R.string.action_share), secondary = true)
            share.setOnClickListener { share(file) }
            row.addView(share, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = Widgets.dp(context, 4)
            row.layoutParams = params
            listColumn.addView(row)
        }
    }

    private fun share(file: File) {
        try {
            val uri: Uri = androidxstyleUri(file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "audio/wav"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            host.activity.startActivity(Intent.createChooser(intent, host.activity.getString(R.string.action_share)))
        } catch (e: Exception) {
            host.showMessage(host.activity.getString(R.string.recorder_share_failed))
        }
    }

    private fun androidxstyleUri(file: File): Uri {
        val authority = "${host.activity.packageName}.fileprovider"
        return Uri.parse("content://$authority/${file.name}")
    }

    override fun onHidden() {
        host.activity.window.decorView.removeCallbacks(tick)
        if (recorder.isRecording) {
            recorder.stop()
            recordButton.text = host.activity.getString(R.string.action_record)
        }
    }
}
