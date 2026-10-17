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
    private lateinit var favouriteButton: ImageView
    private lateinit var swapButton: ImageView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        // --- category + favourite row -------------------------------------------
        val topRow = Widgets.row(context)
        categorySpinner = Spinner(context)
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
        topRow.addView(categorySpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        favouriteButton = ImageView(context)
        favouriteButton.setImageResource(R.drawable.ic_star)
        favouriteButton.setColorFilter(Widgets.color(context, R.color.text_muted))
        favouriteButton.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline)
        )
        favouriteButton.contentDescription = context.getString(R.string.action_favourite)
        favouriteButton.setOnClickListener { toggleFavourite() }
        topRow.addView(favouriteButton, LinearLayout.LayoutParams(
            Widgets.dp(context, 40),
            Widgets.dp(context, 40)
        ))
        root.addView(topRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0)
        })

        // --- value + swap --------------------------------------------------------
        val valueCard = Widgets.row(context)
        valueCard.gravity = Gravity.CENTER_VERTICAL
        valueCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 6), Widgets.dp(context, 8)
        )
        valueCard.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline)
        )
        valueLabel = Widgets.text(context, input, R.style.Text_Display)
        valueLabel.gravity = Gravity.CENTER_VERTICAL
        valueLabel.maxLines = 1
        valueLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        valueCard.addView(valueLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        swapButton = ImageView(context)
        swapButton.setImageResource(R.drawable.ic_swap)
        swapButton.setColorFilter(Widgets.color(context, R.color.primary))
        swapButton.background = Widgets.roundedBackground(context, Widgets.color(context, R.color.surface_alt))
        swapButton.contentDescription = context.getString(R.string.action_swap)
        swapButton.setOnClickListener { swapUnits() }
        valueCard.addView(swapButton, LinearLayout.LayoutParams(
            Widgets.dp(context, 40),
            Widgets.dp(context, 40)
        ))
        root.addView(valueCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0)
        })

        // --- unit pickers --------------------------------------------------------
        val unitRow = Widgets.row(context)
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
        unitRow.addView(fromSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = TextView(context)
        arrow.text = "→"
        arrow.setTextAppearance(context, R.style.Text_Subtitle)
        arrow.setTextColor(Widgets.color(context, R.color.text_muted))
        arrow.gravity = Gravity.CENTER
        unitRow.addView(arrow, LinearLayout.LayoutParams(Widgets.dp(context, 22), ViewGroup.LayoutParams.WRAP_CONTENT))
        unitRow.addView(toSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(unitRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0)
        })

        // --- result --------------------------------------------------------------
        val resultCard = Widgets.row(context)
        resultCard.gravity = Gravity.CENTER_VERTICAL
        resultCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 12), Widgets.dp(context, 8)
        )
        resultCard.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.primary_dim),
            Widgets.color(context, R.color.primary)
        )
        resultLabel = Widgets.text(context, "", R.style.Text_Display)
        resultLabel.maxLines = 1
        resultLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        resultLabel.setTextColor(Widgets.color(context, R.color.primary))
        resultCard.addView(resultLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resultUnit = Widgets.text(context, "", R.style.Text_Label)
        resultUnit.setTextColor(Widgets.color(context, R.color.text_secondary))
        resultUnit.gravity = Gravity.END
        resultUnit.maxLines = 2
        resultCard.addView(resultUnit)
        root.addView(resultCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8), 0)
        })

        // --- all units -----------------------------------------------------------
        fromLabel = Widgets.caption(context, "")
        root.addView(fromLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), 0)
        })
        val listScroll = ScrollView(context)
        listScroll.isVerticalScrollBarEnabled = false
        unitList = Widgets.column(context)
        listScroll.addView(unitList, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(listScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            setMargins(Widgets.dp(context, 8), 0, Widgets.dp(context, 8), 0)
        })

        // --- keypad ---------------------------------------------------------------
        keypad = KeypadView(
            context,
            arrayOf(
                arrayOf("7", "8", "9", "⌫"),
                arrayOf("4", "5", "6", "C"),
                arrayOf("1", "2", "3", "±"),
                arrayOf("0", ".", "−", " ")
            )
        ) { key -> onKeypadKey(key.first()) }
        keypad.visibility = View.VISIBLE
        root.addView(keypad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Widgets.dp(context, 132)
        ).apply {
            setMargins(Widgets.dp(context, 4), 0, Widgets.dp(context, 4), Widgets.dp(context, 4))
        })

        updateSpinners()
        update()
        return root
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
            category.units.map { "${it.name} (${it.symbol})" }
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
        val names = category.units.map { "${it.name} (${it.symbol})" }
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

    private fun onKeypadKey(key: Char) {
        when (key) {
            'C' -> {
                input = "0"
            }
            '⌫' -> {
                if (input.isNotEmpty()) input = input.dropLast(1)
                if (input.isEmpty() || input == "-") input = "0"
            }
            '±' -> {
                input = if (input.startsWith("-")) input.substring(1) else "-$input"
            }
            '−' -> {
                if (!input.startsWith("-")) input = "-$input"
            }
            else -> {
                if (key.isDigit()) {
                    if (input == "0") input = key.toString() else input += key
                } else if (key == '.') {
                    if (!input.contains('.')) input += "."
                }
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
        resultUnit.text = toUnit.name + "\n" + toUnit.symbol
        fromLabel.text = host.activity.getString(R.string.converter_all_units, fromUnit.name)
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
            onKeypadKey('0' + (keyCode - KeyEvent.KEYCODE_0))
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PERIOD || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) {
            onKeypadKey('.')
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onKeypadKey('⌫')
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            onKeypadKey('C')
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MINUS) {
            onKeypadKey('−')
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            onKeypadKey('±')
            return true
        }
        return false
    }
}
