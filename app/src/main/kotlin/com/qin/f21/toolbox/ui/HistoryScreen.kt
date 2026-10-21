package com.qin.f21.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.qin.f21.toolbox.core.common.JsonWriter
import com.qin.f21.toolbox.core.data.HistoryEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local history. Everything is stored on the device in a small JSON file and can be
 * exported as JSON or CSV.
 */
class HistoryScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "History"

    private lateinit var column: LinearLayout
    private var filter: String? = null

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val actions = Widgets.row(context)
        val exportJson = Widgets.button(context, context.getString(R.string.action_export_json), secondary = true)
        exportJson.setOnClickListener { export("json") }
        actions.addView(exportJson, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 6))
        val exportCsv = Widgets.button(context, context.getString(R.string.action_export_csv), secondary = true)
        exportCsv.setOnClickListener { export("csv") }
        actions.addView(exportCsv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 6))
        val clear = Widgets.button(context, context.getString(R.string.action_clear), secondary = true)
        clear.setOnClickListener {
            Toolbox.store.clearHistory()
            refresh()
            host.showMessage(context.getString(R.string.history_cleared))
        }
        actions.addView(clear, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0) })

        val scroll = ScrollView(context).forKeypad()
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 8), Widgets.dp(context, 4),
            Widgets.dp(context, 8), Widgets.dp(context, 12)
        )
        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        refresh()
        return root
    }

    private fun refresh() {
        val context = host.activity
        column.removeAllViews()
        val entries = Toolbox.store.history
        if (entries.isEmpty()) {
            val empty = Widgets.column(context)
            empty.gravity = Gravity.CENTER
            val text = Widgets.text(context, context.getString(R.string.history_empty), R.style.Text_Caption)
            text.gravity = Gravity.CENTER
            text.setPadding(0, Widgets.dp(context, 40), 0, Widgets.dp(context, 40))
            empty.addView(text)
            column.addView(empty)
            return
        }
        val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        for (entry in entries) {
            val row = Widgets.row(context)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(
                Widgets.dp(context, 10), Widgets.dp(context, 8),
                Widgets.dp(context, 10), Widgets.dp(context, 8)
            )
            row.background = Widgets.focusBackground(
                context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
            )
            row.isFocusable = true
            row.isClickable = true
            val textColumn = Widgets.column(context)
            val title = Widgets.text(context, entry.title, R.style.Text_Body)
            textColumn.addView(title)
            val summary = Widgets.text(context, entry.summary, R.style.Text_Caption)
            summary.maxLines = 2
            textColumn.addView(summary)
            val stamp = Widgets.text(context, formatter.format(Date(entry.timestamp)), R.style.Text_Caption)
            textColumn.addView(stamp)
            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { copy(entry) }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = Widgets.dp(context, 5)
            row.layoutParams = params
            column.addView(row)
        }
    }

    private fun copy(entry: HistoryEntry) {
        val context = host.activity
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("toolbox", entry.title))
        host.showMessage(context.getString(R.string.history_copied))
    }

    private fun export(format: String) {
        val context = host.activity
        try {
            val directory = context.getExternalFilesDir(null) ?: context.filesDir
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val file = File(directory, "toolbox-history-$stamp.$format")
            val content = if (format == "csv") toCsv(Toolbox.store.history) else {
                // the backup carries the wallet next to the history and the favourites, so
                // one file restores the whole app state (see SettingsScreen.onActivityResult)
                val document = Toolbox.store.exportDocument()
                val withWallet = com.qin.f21.toolbox.core.common.Json.Obj(
                    document.asObject() + ("wallet" to Toolbox.wallet.ledger.toJson())
                )
                JsonWriter.write(withWallet, pretty = true)
            }
            file.writeText(content)
            val uri = Uri.parse("content://${context.packageName}.fileprovider/${file.name}")
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = if (format == "csv") "text/csv" else "application/json"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_export)))
            host.showMessage(context.getString(R.string.history_exported, file.name))
        } catch (e: Exception) {
            host.showMessage(context.getString(R.string.error_generic))
        }
    }

    private fun toCsv(entries: List<HistoryEntry>): String {
        val builder = StringBuilder()
        builder.append("date,tool,value,details\n")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (entry in entries) {
            builder.append(csv(formatter.format(Date(entry.timestamp)))).append(',')
            builder.append(csv(entry.toolName)).append(',')
            builder.append(csv(entry.title)).append(',')
            builder.append(csv(entry.summary)).append('\n')
        }
        return builder.toString()
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
