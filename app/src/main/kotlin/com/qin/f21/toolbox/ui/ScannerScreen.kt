package com.qin.f21.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.CameraActivity
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.data.HistoryEntry

/**
 * QR and barcode scanner.
 *
 * The decoding itself runs entirely on the device with the vendored ZXing core, so no
 * image ever leaves the phone.
 */
class ScannerScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Scanner"

    private lateinit var resultLabel: TextView
    private lateinit var formatLabel: TextView
    private lateinit var actionRow: LinearLayout
    private var lastText: String = ""

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 12),
            Widgets.dp(context, 12), Widgets.dp(context, 24)
        )

        val card = Widgets.displayCard(context, 14)
        card.addView(Widgets.eyebrow(context, context.getString(R.string.scanner_last)))
        resultLabel = Widgets.text(context, context.getString(R.string.scanner_empty), R.style.Text_Mono)
        resultLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        resultLabel.setTextColor(Widgets.color(context, R.color.primary))
        resultLabel.setPadding(0, Widgets.dp(context, 6), 0, 0)
        resultLabel.setTextIsSelectable(true)
        card.addView(resultLabel)
        formatLabel = Widgets.caption(context, "")
        card.addView(formatLabel)
        column.addView(card)

        actionRow = Widgets.row(context)
        val start = Widgets.button(context, context.getString(R.string.scanner_start))
        start.setOnClickListener { startScanner() }
        actionRow.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(actionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 8) })

        val note = Widgets.caption(context, context.getString(R.string.scanner_privacy_note))
        note.setPadding(0, Widgets.dp(context, 12), 0, 0)
        column.addView(note)

        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        if (!Toolbox.capabilities.hasCamera) {
            column.addView(unavailableCard())
        }
        return root
    }

    private fun unavailableCard(): View {
        val context = host.activity
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 14),
            Widgets.dp(context, 12), Widgets.dp(context, 14)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_alt), Widgets.color(context, R.color.outline_strong)
        )
        card.addView(Widgets.text(context, context.getString(R.string.scanner_unavailable_title), R.style.Text_Subtitle))
        card.addView(Widgets.caption(context, context.getString(R.string.scanner_unavailable_message)))
        return card
    }

    private fun startScanner() {
        if (!Toolbox.capabilities.hasCamera) {
            host.showMessage(host.activity.getString(R.string.scanner_unavailable_message))
            return
        }
        val intent = Intent(host.activity, CameraActivity::class.java)
        intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_SCAN)
        host.activity.startActivityForResult(intent, REQUEST_SCAN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_SCAN) return false
        if (resultCode != android.app.Activity.RESULT_OK) return true
        val text = data?.getStringExtra(CameraActivity.RESULT_TEXT) ?: return true
        val format = data.getStringExtra(CameraActivity.RESULT_FORMAT) ?: ""
        showResult(text, format)
        return true
    }

    private fun showResult(text: String, format: String) {
        lastText = text
        resultLabel.text = text
        formatLabel.text = context().getString(R.string.scanner_format, format)
        rebuildActions()
        Toolbox.store.addHistory(
            HistoryEntry(
                toolId = "scanner",
                toolName = "Scanner",
                title = text.take(80),
                summary = context().getString(R.string.scanner_format, format)
            )
        )
    }

    private fun rebuildActions() {
        actionRow.removeAllViews()
        if (lastText.isEmpty()) return
        val copy = Widgets.button(context(), context().getString(R.string.action_copy), secondary = true)
        copy.setOnClickListener {
            val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("scan", lastText))
            host.showMessage(context().getString(R.string.scanner_copied))
        }
        actionRow.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(Widgets.spacer(context(), 6))
        val start = Widgets.button(context(), context().getString(R.string.scanner_start), secondary = true)
        start.setOnClickListener { startScanner() }
        actionRow.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun context() = host.activity

    companion object {
        const val REQUEST_SCAN = 3003
    }
}
