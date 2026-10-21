package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.conversion.Category
import com.qin.f21.toolbox.core.conversion.UnitDef
import com.qin.f21.toolbox.core.common.Numbers
import com.qin.f21.toolbox.core.data.Favorite
import com.qin.f21.toolbox.core.data.HistoryEntry

/**
 * The unit converter.
 *
 * Laid out for the small 4:3 screen of the Qin F21 Pro: a compact header with the category
 * picker, a large read-out for the value and the result, and a numeric keypad that can be
 * operated with the touch screen or with the physical keypad of the device.
 */
class ConverterScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Unit converter"

    private var category: Category = Toolbox.engine.categories().first()
    private var fromUnit: UnitDef = category.requireUnit(category.defaultFrom)
    private var toUnit: UnitDef = category.requireUnit(category.defaultTo)
    private var input: String = "1"
    private var lastResult: Double = 0.0
    private var lastRecorded = ""
    private var lastRecordAt = 0L

    private lateinit var categorySpinner: Spinner
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var valueLabel: TextView
    private lateinit var fromLabel: TextView
    private lateinit var resultLabel: TextView
    private lateinit var resultUnit: TextView
    private lateinit var unitList: LinearLayout
    private lateinit var keypad: KeypadView
    private lateinit var keypadToggle: android.widget.Button
    private lateinit var favouriteButton: ImageView
    private lateinit var swapButton: ImageView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = FrameLayout(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val column = Widgets.column(context)
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // --- category + favourite ------------------------------------------------
        val topRow = Widgets.row(context)
        topRow.gravity = Gravity.CENTER_VERTICAL
        categorySpinner = Spinner(context)
        categorySpinner.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline), 12
        )
        categorySpinner.adapter = ArrayAdapter(
            context,
            R.layout.item_spinner,
            R.id.text1,
            Toolbox.engine.categories().map { it.title }
        )
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectCategory(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        topRow.addView(categorySpinner, LinearLayout.LayoutParams(0, Widgets.dp(context, 44), 1f))

        favouriteButton = ImageView(context)
        favouriteButton.setImageResource(R.drawable.ic_star)
        favouriteButton.setColorFilter(Widgets.color(context, R.color.text_muted))
        favouriteButton.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline),
            12
        )
        favouriteButton.setPadding(
            Widgets.dp(context, 11), Widgets.dp(context, 11),
            Widgets.dp(context, 11), Widgets.dp(context, 11)
        )
        favouriteButton.isFocusable = true
        favouriteButton.contentDescription = context.getString(R.string.action_favourite)
        favouriteButton.setOnClickListener { toggleFavourite() }
        topRow.addView(favouriteButton, LinearLayout.LayoutParams(
            Widgets.dp(context, 44),
            Widgets.dp(context, 44)
        ).apply { leftMargin = Widgets.dp(context, 8) })
        column.addView(topRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 12), Widgets.dp(context, 10), Widgets.dp(context, 12), 0)
        })

        // --- unit pickers ---------------------------------------------------------
        val unitRow = Widgets.row(context)
        unitRow.gravity = Gravity.CENTER_VERTICAL
        fromSpinner = unitSpinner()
        toSpinner = unitSpinner()
        fromSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val unit = category.units.getOrNull(position) ?: return
                if (unit.id != fromUnit.id) {
                    fromUnit = unit
                    update()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        toSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val unit = category.units.getOrNull(position) ?: return
                if (unit.id != toUnit.id) {
                    toUnit = unit
                    update()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        unitRow.addView(fromSpinner, LinearLayout.LayoutParams(0, Widgets.dp(context, 44), 1f))
        val arrow = TextView(context)
        arrow.text = "→"
        arrow.setTextAppearance(context, R.style.Text_Subtitle)
        arrow.setTextColor(Widgets.color(context, R.color.text_muted))
        arrow.gravity = Gravity.CENTER
        unitRow.addView(arrow, LinearLayout.LayoutParams(Widgets.dp(context, 28), ViewGroup.LayoutParams.WRAP_CONTENT))
        unitRow.addView(toSpinner, LinearLayout.LayoutParams(0, Widgets.dp(context, 44), 1f))
        column.addView(unitRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0)
        })

        // --- reading: input and result on one card --------------------------------
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 14), Widgets.dp(context, 10),
            Widgets.dp(context, 14), Widgets.dp(context, 10)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_sunken), Widgets.color(context, R.color.outline), 16
        )

        val valueRow = Widgets.row(context)
        valueRow.gravity = Gravity.CENTER_VERTICAL
        valueRow.addView(Widgets.eyebrow(context, context.getString(R.string.converter_value)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        swapButton = ImageView(context)
        swapButton.setImageResource(R.drawable.ic_swap)
        swapButton.setColorFilter(Widgets.color(context, R.color.primary))
        swapButton.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_high), 0, 10
        )
        swapButton.setPadding(
            Widgets.dp(context, 8), Widgets.dp(context, 8),
            Widgets.dp(context, 8), Widgets.dp(context, 8)
        )
        swapButton.isFocusable = true
        swapButton.contentDescription = context.getString(R.string.action_swap)
        swapButton.setOnClickListener { swapUnits() }
        valueRow.addView(swapButton, LinearLayout.LayoutParams(
            Widgets.dp(context, 32), Widgets.dp(context, 32)
        ))
        card.addView(valueRow)

        valueLabel = Widgets.text(context, input, R.style.Text_Mono)
        valueLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
        valueLabel.maxLines = 1
        valueLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        valueLabel.setPadding(0, Widgets.dp(context, 4), 0, 0)
        card.addView(valueLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val rule = View(context)
        rule.setBackgroundColor(Widgets.color(context, R.color.outline))
        card.addView(rule, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 1)
        ).apply { setMargins(0, Widgets.dp(context, 8), 0, Widgets.dp(context, 8)) })

        val resultTop = Widgets.row(context)
        resultTop.gravity = Gravity.CENTER_VERTICAL
        val resultEyebrow = Widgets.eyebrow(context, context.getString(R.string.converter_result))
        resultEyebrow.setTextColor(Widgets.color(context, R.color.primary))
        resultTop.addView(resultEyebrow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resultUnit = Widgets.text(context, "", R.style.Text_Label)
        resultUnit.setTextColor(Widgets.color(context, R.color.text_secondary))
        resultUnit.gravity = Gravity.END
        resultUnit.maxLines = 1
        resultTop.addView(resultUnit)
        card.addView(resultTop)

        resultLabel = Widgets.text(context, "", R.style.Text_Display)
        resultLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
        resultLabel.maxLines = 1
        resultLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        resultLabel.setTextColor(Widgets.color(context, R.color.primary))
        resultLabel.setPadding(0, Widgets.dp(context, 2), 0, 0)
        card.addView(resultLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        column.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0)
        })

        // --- list header + keypad toggle ------------------------------------------
        val toolbar = Widgets.row(context)
        toolbar.gravity = Gravity.CENTER_VERTICAL
        fromLabel = Widgets.caption(context, "")
        fromLabel.maxLines = 1
        toolbar.addView(fromLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        keypadToggle = Widgets.chip(context, context.getString(R.string.converter_keypad)) { toggleKeypad() }
        toolbar.addView(keypadToggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Widgets.dp(context, 32)
        ).apply { leftMargin = Widgets.dp(context, 8) })
        column.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 16), Widgets.dp(context, 12), Widgets.dp(context, 12), Widgets.dp(context, 4))
        })

        // --- all units ------------------------------------------------------------
        val listScroll = ScrollView(context).forKeypad()
        listScroll.isVerticalScrollBarEnabled = false
        unitList = Widgets.column(context)
        listScroll.addView(unitList, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        column.addView(listScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            setMargins(Widgets.dp(context, 12), 0, Widgets.dp(context, 12), Widgets.dp(context, 12))
        })

        // --- keypad: an overlay, so opening it never squeezes the list -------------
        keypad = KeypadView(
            context,
            arrayOf(
                arrayOf("7", "8", "9", "⌫"),
                arrayOf("4", "5", "6", "C"),
                arrayOf("1", "2", "3", "−"),
                arrayOf("0", "00", ".", "±")
            )
        ) { key -> onKeypadKey(key) }
        keypad.setBackgroundColor(Widgets.color(context, R.color.bg))
        keypad.visibility = View.GONE
        root.addView(keypad, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Widgets.dp(context, 152),
            Gravity.BOTTOM
        ).apply {
            setMargins(Widgets.dp(context, 8), 0, Widgets.dp(context, 8), Widgets.dp(context, 8))
        })

        updateSpinners()
        update()
        return root
    }

    /** Shows or hides the on-screen keypad and keeps the chip label in sync. */
    private fun toggleKeypad() {
        val visible = keypad.visibility != View.VISIBLE
        keypad.visibility = if (visible) View.VISIBLE else View.GONE
        keypadToggle.isSelected = visible
        keypadToggle.setTextColor(
            Widgets.color(host.activity, if (visible) R.color.on_primary else R.color.text_secondary)
        )
        keypadToggle.background = host.activity.getDrawable(
            if (visible) R.drawable.bg_key_accent else R.drawable.bg_chip
        )
        if (visible) keypad.requestFocus()
    }

    private fun unitSpinner(): Spinner {
        val spinner = Spinner(host.activity)
        spinner.setBackground(Widgets.roundedBackground(
            host.activity,
            Widgets.color(host.activity, R.color.surface),
            Widgets.color(host.activity, R.color.outline),
            6
        ))
        spinner.adapter = ArrayAdapter(
            host.activity,
            R.layout.item_spinner,
            R.id.text1,
            category.units.map { it.name }
        )
        return spinner
    }

    private fun selectCategory(position: Int) {
        val categories = Toolbox.engine.categories()
        val selected = categories.getOrNull(position) ?: return
        if (selected.id == category.id) return
        category = selected
        val saved = Toolbox.settings.lastUnit(category.id, category.defaultFrom)
        fromUnit = category.unit(saved) ?: category.requireUnit(category.defaultFrom)
        toUnit = category.unit(category.defaultTo) ?: category.requireUnit(category.defaultTo)
        input = "1"
        updateSpinners()
        update()
    }

    private fun updateSpinners() {
        val names = category.units.map { it.name }
        fromSpinner.adapter = ArrayAdapter(host.activity, R.layout.item_spinner, R.id.text1, names)
        toSpinner.adapter = ArrayAdapter(host.activity, R.layout.item_spinner, R.id.text1, names)
        fromSpinner.setSelection(category.units.indexOfFirst { it.id == fromUnit.id }.coerceAtLeast(0))
        toSpinner.setSelection(category.units.indexOfFirst { it.id == toUnit.id }.coerceAtLeast(0))
    }

    private fun swapUnits() {
        val previousFrom = fromUnit
        fromUnit = toUnit
        toUnit = previousFrom
        fromSpinner.setSelection(category.units.indexOfFirst { it.id == fromUnit.id }.coerceAtLeast(0))
        toSpinner.setSelection(category.units.indexOfFirst { it.id == toUnit.id }.coerceAtLeast(0))
        update()
    }

    private fun onKeypadKey(key: String) {
        when (key) {
            "C" -> input = "0"
            "⌫" -> {
                input = input.dropLast(1)
                if (input.isEmpty() || input == "-") input = "0"
            }
            "±" -> input = if (input.startsWith("-")) input.substring(1) else "-$input"
            "−" -> if (!input.startsWith("-")) input = "-$input"
            "." -> if (!input.contains('.')) input += "."
            "00" -> input = if (input == "0") "0" else input + "00"
            else -> if (key.isNotEmpty() && key[0].isDigit()) {
                input = if (input == "0") key else input + key
            }
        }
        update()
    }

    private fun currentValue(): Double = Numbers.parse(input) ?: 0.0

    private fun update() {
        // remember the last used unit per category so the next start is where the user left
        Toolbox.settings.setLastUnit(category.id, fromUnit.id)
        val value = currentValue()
        valueLabel.text = if (input == "0") "0" else input
        val result = Toolbox.engine.convert(value, fromUnit, toUnit)
        lastResult = result
        resultLabel.text = Numbers.format(result)
        resultUnit.text = toUnit.name + " (" + toUnit.symbol + ")"
        fromLabel.text = host.activity.getString(
            R.string.converter_all_units,
            fromUnit.name,
            category.units.size
        )
        updateUnitList(value)
        updateFavouriteIcon()
        recordHistory()
    }

    private fun updateUnitList(value: Double) {
        unitList.removeAllViews()
        val conversions = Toolbox.engine.convertToAll(value, category, fromUnit.id)
        for ((unit, converted) in conversions) {
            val row = host.activity.layoutInflater.inflate(R.layout.item_unit_row, unitList, false)
            val valueText = row.findViewById<TextView>(R.id.unit_value)
            val nameText = row.findViewById<TextView>(R.id.unit_name)
            valueText.text = Numbers.format(converted)
            nameText.text = unit.name
            row.isFocusable = true
            row.isClickable = true
            row.setOnClickListener {
                fromUnit = unit
                fromSpinner.setSelection(category.units.indexOfFirst { it.id == unit.id }.coerceAtLeast(0))
                update()
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            unitList.addView(row, params)
        }
    }

    private fun updateFavouriteIcon() {
        val payload = category.id + ":" + fromUnit.id + ":" + toUnit.id
        val favourite = Toolbox.store.isFavorite("converter", payload)
        favouriteButton.setImageResource(if (favourite) R.drawable.ic_star_filled else R.drawable.ic_star)
        favouriteButton.setColorFilter(
            Widgets.color(host.activity, if (favourite) R.color.accent else R.color.text_muted)
        )
    }

    private fun toggleFavourite() {
        val payload = category.id + ":" + fromUnit.id + ":" + toUnit.id
        val added = Toolbox.store.toggleFavorite(
            Favorite(
                toolId = "converter",
                title = "${category.title}: ${fromUnit.symbol} → ${toUnit.symbol}",
                subtitle = category.title,
                payload = payload
            )
        )
        updateFavouriteIcon()
        host.showMessage(
            host.activity.getString(if (added) R.string.favourite_added else R.string.favourite_removed)
        )
    }

    /**
     * Every change is written to the local history, but at most one entry per conversion
     * so that typing a value does not flood the history.
     */
    private fun recordHistory() {
        val value = currentValue()
        val result = Toolbox.engine.convert(value, fromUnit, toUnit)
        val key = "${category.id}:${fromUnit.id}:${toUnit.id}:${Numbers.format(value)}"
        val now = android.os.SystemClock.elapsedRealtime()
        if (key == lastRecorded || now - lastRecordAt < 4000L) return
        lastRecorded = key
        lastRecordAt = now
        Toolbox.store.addHistory(
            HistoryEntry(
                toolId = "converter",
                toolName = "Unit converter",
                title = "${Numbers.format(value)} ${fromUnit.symbol} = ${Numbers.format(result)} ${toUnit.symbol}",
                summary = "${category.title} · ${fromUnit.name} → ${toUnit.name}"
            )
        )
    }

    /** The physical keypad types straight into the value field. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            onKeypadKey((keyCode - KeyEvent.KEYCODE_0).toString())
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PERIOD || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) {
            onKeypadKey(".")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onKeypadKey("⌫")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            onKeypadKey("C")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MINUS) {
            onKeypadKey("−")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            onKeypadKey("±")
            return true
        }
        return false
    }
}
