package com.qin.f21.toolbox.ui

import android.app.DatePickerDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.calc.DateCalculator
import com.qin.f21.toolbox.core.common.Numbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date calculator: difference between two dates and adding or subtracting intervals.
 * Uses the device calendar, no network involved.
 */
class DateCalculatorScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Date & time"

    private var from = LocalDate.now().minusYears(1)
    private var to = LocalDate.now()

    private lateinit var fromLabel: TextView
    private lateinit var toLabel: TextView
    private lateinit var differenceLabel: TextView
    private lateinit var adjustedLabel: TextView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val scroll = android.widget.ScrollView(context).forKeypad()
        scroll.setBackgroundColor(Widgets.color(context, R.color.bg))
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        column.addView(dateRow(context.getString(R.string.dc_from), true))
        column.addView(dateRow(context.getString(R.string.dc_to), false))

        val differenceCard = Widgets.column(context)
        differenceCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 10),
            Widgets.dp(context, 12), Widgets.dp(context, 10)
        )
        differenceCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.primary_dim), Widgets.color(context, R.color.primary)
        )
        differenceLabel = Widgets.text(context, "", R.style.Text_Subtitle)
        differenceCard.addView(differenceLabel)
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.topMargin = Widgets.dp(context, 8)
        differenceCard.layoutParams = cardParams
        column.addView(differenceCard)

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.dc_adjust)))
        column.addView(intervalRow())

        val adjustedCard = Widgets.column(context)
        adjustedCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 12), Widgets.dp(context, 8)
        )
        adjustedCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        adjustedLabel = Widgets.text(context, "", R.style.Text_Body)
        adjustedCard.addView(adjustedLabel)
        column.addView(adjustedCard)

        val actions = Widgets.row(context)
        val applyButton = Widgets.button(context, context.getString(R.string.dc_apply))
        applyButton.setOnClickListener { applyInterval() }
        actions.addView(applyButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 8))
        val todayButton = Widgets.button(context, context.getString(R.string.dc_today), secondary = true)
        todayButton.setOnClickListener {
            from = LocalDate.now().minusYears(1)
            to = LocalDate.now()
            update()
        }
        actions.addView(todayButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 8) })

        scroll.addView(column, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        update()
        return scroll
    }

    private fun dateRow(label: String, isFrom: Boolean): View {
        val context = host.activity
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
        val text = Widgets.text(context, label, R.style.Text_Label)
        text.setTextColor(Widgets.color(context, R.color.text_secondary))
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = Widgets.text(context, "", R.style.Text_Body)
        value.setTextColor(Widgets.color(context, R.color.primary))
        row.addView(value)
        if (isFrom) fromLabel = value else toLabel = value
        row.setOnClickListener { pickDate(isFrom) }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 6)
        row.layoutParams = params
        return row
    }

    private var yearsField: android.widget.EditText? = null
    private var monthsField: android.widget.EditText? = null
    private var daysField: android.widget.EditText? = null
    private var subtract = false

    private fun intervalRow(): View {
        val context = host.activity
        val column = Widgets.column(context)
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL

        val years = Widgets.numericField(context, "0")
        val months = Widgets.numericField(context, "0")
        val days = Widgets.numericField(context, "0")
        yearsField = years
        monthsField = months
        daysField = days
        row.addView(years, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Widgets.text(context, "y", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        row.addView(months, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Widgets.text(context, "m", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        row.addView(days, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Widgets.text(context, "d", R.style.Text_Caption), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        column.addView(row)

        val directionRow = Widgets.row(context)
        val addButton = Widgets.button(context, context.getString(R.string.dc_add), secondary = true)
        addButton.setOnClickListener {
            subtract = false
            applyInterval()
        }
        val subtractButton = Widgets.button(context, context.getString(R.string.dc_subtract), secondary = true)
        subtractButton.setOnClickListener {
            subtract = true
            applyInterval()
        }
        directionRow.addView(addButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        directionRow.addView(Widgets.spacer(context, 8))
        directionRow.addView(subtractButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(directionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 6) })
        return column
    }

    private fun pickDate(isFrom: Boolean) {
        val target = if (isFrom) from else to
        DatePickerDialog(
            host.activity,
            { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day)
                if (isFrom) from = date else to = date
                update()
            },
            target.year, target.monthValue - 1, target.dayOfMonth
        ).show()
    }

    private fun applyInterval() {
        val years = (Numbers.parse(yearsField?.text?.toString() ?: "0") ?: 0.0).toInt()
        val months = (Numbers.parse(monthsField?.text?.toString() ?: "0") ?: 0.0).toInt()
        val days = (Numbers.parse(daysField?.text?.toString() ?: "0") ?: 0.0).toInt()
        val sign = if (subtract) -1 else 1
        val result = DateCalculator.add(from, years * sign, months * sign, days * sign)
        adjustedLabel.text = host.activity.getString(
            R.string.dc_result,
            from.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)),
            result.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
        )
    }

    private fun update() {
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
        fromLabel.text = from.format(formatter)
        toLabel.text = to.format(formatter)
        val difference = DateCalculator.between(from, to)
        differenceLabel.text = host.activity.getString(
            R.string.dc_difference,
            difference.years,
            difference.months,
            difference.days,
            difference.totalDays,
            difference.totalHours
        )
        applyInterval()
    }
}
