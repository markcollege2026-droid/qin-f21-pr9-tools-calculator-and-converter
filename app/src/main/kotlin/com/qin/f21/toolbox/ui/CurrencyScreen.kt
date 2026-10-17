package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.common.Numbers
import com.qin.f21.toolbox.core.data.HistoryEntry
import com.qin.f21.toolbox.util.Assets

/**
 * Currency converter with the bundled offline rate table.
 *
 * The rates are indicative reference values shipped inside the APK - the app has no
 * INTERNET permission, so it can never download a live rate. Every rate can be overridden
 * by the user in Settings.
 */
class CurrencyScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Currency"

    private var input = "1"
    private var from = "USD"
    private var to = "EUR"

    private lateinit var amountLabel: TextView
    private lateinit var amountUnitLabel: TextView
    private lateinit var resultLabel: TextView
    private lateinit var resultUnit: TextView
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var noteLabel: TextView
    private lateinit var rateLabel: TextView
    private lateinit var keypad: KeypadView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        if (Toolbox.currency == com.qin.f21.toolbox.core.conversion.CurrencyTable.EMPTY) {
            Assets.readJson(context, "data/currency.json")?.let { Toolbox.loadCurrency(it) }
        }
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        amountLabel = Widgets.text(context, input, R.style.Text_Display)
        amountLabel.gravity = Gravity.CENTER_VERTICAL
        val amountCard = Widgets.row(context)
        amountCard.gravity = Gravity.CENTER_VERTICAL
        amountCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 12), Widgets.dp(context, 8)
        )
        amountCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        amountCard.addView(amountLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        amountUnitLabel = Widgets.text(context, from, R.style.Text_Subtitle)
        amountUnitLabel.setTextColor(Widgets.color(context, R.color.text_secondary))
        amountCard.addView(amountUnitLabel)
        root.addView(amountCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0) })

        val unitRow = Widgets.row(context)
        fromSpinner = spinner()
        toSpinner = spinner()
        fromSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                from = Toolbox.currency.currencies.getOrNull(position) ?: return
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        toSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                to = Toolbox.currency.currencies.getOrNull(position) ?: return
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        unitRow.addView(fromSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = Widgets.text(context, "→", R.style.Text_Subtitle)
        arrow.gravity = Gravity.CENTER
        arrow.setTextColor(Widgets.color(context, R.color.text_muted))
        unitRow.addView(arrow, LinearLayout.LayoutParams(Widgets.dp(context, 22), ViewGroup.LayoutParams.WRAP_CONTENT))
        unitRow.addView(toSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(unitRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0) })

        val resultCard = Widgets.row(context)
        resultCard.gravity = Gravity.CENTER_VERTICAL
        resultCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 10),
            Widgets.dp(context, 12), Widgets.dp(context, 10)
        )
        resultCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.primary_dim), Widgets.color(context, R.color.primary)
        )
        resultLabel = Widgets.text(context, "", R.style.Text_Display)
        resultLabel.maxLines = 1
        resultLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        resultCard.addView(resultLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resultUnit = Widgets.text(context, "", R.style.Text_Label)
        resultUnit.setTextColor(Widgets.color(context, R.color.text_secondary))
        resultUnit.gravity = Gravity.END
        resultCard.addView(resultUnit)
        root.addView(resultCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0) })

        val noteScroll = ScrollView(context)
        val noteColumn = Widgets.column(context)
        noteLabel = Widgets.caption(context, "")
        noteColumn.addView(noteLabel)
        rateLabel = Widgets.text(context, "", R.style.Text_Caption)
        noteColumn.addView(rateLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        noteScroll.addView(noteColumn, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(noteScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0) })

        keypad = KeypadView(
            context,
            arrayOf(
                arrayOf("7", "8", "9", "⌫"),
                arrayOf("4", "5", "6", "C"),
                arrayOf("1", "2", "3", "±"),
                arrayOf("0", ".", "−", " ")
            )
        ) { key -> onKey(key.first()) }
        root.addView(keypad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 132)
        ).apply { setMargins(Widgets.dp(context, 4), 0, Widgets.dp(context, 4), Widgets.dp(context, 4)) })

        refreshSpinners()
        update()
        return root
    }

    private fun spinner(): Spinner {
        val spinner = Spinner(host.activity)
        spinner.setBackground(Widgets.roundedBackground(
            host.activity,
            Widgets.color(host.activity, R.color.surface),
            Widgets.color(host.activity, R.color.outline),
            6
        ))
        spinner.adapter = ArrayAdapter(
            host.activity, R.layout.item_spinner, R.id.text1, Toolbox.currency.currencies
        )
        return spinner
    }

    private fun refreshSpinners() {
        val currencies = Toolbox.currency.currencies
        fromSpinner.adapter = ArrayAdapter(host.activity, R.layout.item_spinner, R.id.text1, currencies)
        toSpinner.adapter = ArrayAdapter(host.activity, R.layout.item_spinner, R.id.text1, currencies)
        fromSpinner.setSelection(currencies.indexOf(from).coerceAtLeast(0))
        toSpinner.setSelection(currencies.indexOf(to).coerceAtLeast(0))
    }

    private fun onKey(key: Char) {
        when (key) {
            'C' -> input = "0"
            '⌫' -> {
                input = input.dropLast(1)
                if (input.isEmpty() || input == "-") input = "0"
            }
            '±' -> input = if (input.startsWith("-")) input.substring(1) else "-$input"
            '−' -> if (!input.startsWith("-")) input = "-$input"
            else -> {
                if (key.isDigit()) {
                    input = if (input == "0") key.toString() else input + key
                } else if (key == '.' && !input.contains('.')) {
                    input += "."
                }
            }
        }
        update()
    }

    private fun update() {
        val amount = Numbers.parse(input) ?: 0.0
        amountLabel.text = input
        amountUnitLabel.text = from
        val result = Toolbox.currency.convert(amount, from, to)
        resultLabel.text = Numbers.format(result)
        resultUnit.text = to
        noteLabel.text = host.activity.getString(
            R.string.currency_offline_note,
            Toolbox.currency.updated.ifEmpty { "-" }
        )
        rateLabel.text = host.activity.getString(
            R.string.currency_rate_line,
            from,
            to,
            Numbers.format(Toolbox.currency.rate(from)),
            Numbers.format(Toolbox.currency.rate(to))
        )
        recordHistory()
    }

    private var lastRecorded = ""
    private var lastRecordAt = 0L

    /** One history entry per conversion, not one per key press. */
    private fun recordHistory() {
        val amount = Numbers.parse(input) ?: 0.0
        val key = "$from:$to:${Numbers.format(amount)}"
        val now = android.os.SystemClock.elapsedRealtime()
        if (key == lastRecorded || now - lastRecordAt < 4000L) return
        lastRecorded = key
        lastRecordAt = now
        Toolbox.store.addHistory(
            HistoryEntry(
                toolId = "currency",
                toolName = "Currency",
                title = "${Numbers.format(amount)} $from = ${Numbers.format(Toolbox.currency.convert(amount, from, to))} $to",
                summary = host.activity.getString(R.string.currency_offline_short)
            )
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            onKey('0' + (keyCode - KeyEvent.KEYCODE_0))
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PERIOD || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onKey('⌫')
            return true
        }
        return false
    }
}
