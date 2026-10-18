package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.calc.BaseConverter
import com.qin.f21.toolbox.core.calc.BmiCalculator
import com.qin.f21.toolbox.core.calc.CookingCalculator
import com.qin.f21.toolbox.core.calc.DateCalculator
import com.qin.f21.toolbox.core.calc.GeometryCalculator
import com.qin.f21.toolbox.core.calc.InterestCalculator
import com.qin.f21.toolbox.core.calc.LoanCalculator
import com.qin.f21.toolbox.core.calc.NumberCalculator
import com.qin.f21.toolbox.core.calc.PercentCalculator
import com.qin.f21.toolbox.core.calc.PriceCalculator
import com.qin.f21.toolbox.core.calc.RandomCalculator
import com.qin.f21.toolbox.core.calc.TravelCalculator
import com.qin.f21.toolbox.core.common.Numbers
import com.qin.f21.toolbox.core.data.HistoryEntry
import java.time.LocalDate

/**
 * Hub for the specialised calculators. Every entry opens a compact form with the
 * inputs, a prominent result and an entry in the local history.
 */
class QuickCalculatorScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Quick calculators"

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val scroll = android.widget.ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        for (entry in entries()) {
            column.addView(entryRow(entry))
        }

        scroll.addView(column, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scroll
    }

    private class Entry(
        val title: String,
        val description: String,
        val icon: Int,
        val screen: (ScreenHost) -> Screen
    )

    private fun entries(): List<Entry> = listOf(
        Entry(
            "Percentage of a number", "X % of Y and the reverse", R.drawable.ic_percent
        ) { host ->
            FormScreen(
                host,
                "Percentage",
                "Find a percentage of a value, or which percentage a value is of.",
                listOf(
                    FormScreen.FormField("percent", "Percent", "10", "%"),
                    FormScreen.FormField("value", "Value", "250")
                ),
                { values ->
                    val percent = values["percent"] ?: 0.0
                    val value = values["value"] ?: 0.0
                    val part = PercentCalculator.percentOf(percent, value)
                    val reverse = PercentCalculator.whatPercent(part, value)
                    host.activity.getString(
                        R.string.qc_percentage_result,
                        Numbers.format(percent),
                        Numbers.format(value),
                        Numbers.format(part),
                        Numbers.format(reverse)
                    )
                }
            )
        },
        Entry(
            "Percentage change", "Growth and decrease between two values", R.drawable.ic_trend
        ) { host ->
            FormScreen(
                host,
                "Percentage change",
                "How much did a value change in percent.",
                listOf(
                    FormScreen.FormField("old", "Old value", "80"),
                    FormScreen.FormField("new", "New value", "100")
                ),
                { values ->
                    val old = values["old"] ?: 0.0
                    val new = values["new"] ?: 0.0
                    val change = PercentCalculator.change(old, new)
                    host.activity.getString(
                        R.string.qc_change_result,
                        Numbers.format(change),
                        Numbers.format(new - old)
                    )
                }
            )
        },
        Entry(
            "Discount, tax and tip", "Final price of a purchase", R.drawable.ic_currency
        ) { host ->
            FormScreen(
                host,
                "Price breakdown",
                "Applies a discount, then tax and tip.",
                listOf(
                    FormScreen.FormField("price", "Price", "100"),
                    FormScreen.FormField("discount", "Discount", "10", "%"),
                    FormScreen.FormField("tax", "Tax", "8", "%"),
                    FormScreen.FormField("tip", "Tip", "5", "%")
                ),
                { values ->
                    val breakdown = PriceCalculator.breakdown(
                        values["price"] ?: 0.0,
                        values["discount"] ?: 0.0,
                        values["tax"] ?: 0.0,
                        values["tip"] ?: 0.0
                    )
                    host.activity.getString(
                        R.string.qc_price_result,
                        Numbers.format(breakdown.total),
                        Numbers.format(breakdown.saved),
                        Numbers.format(breakdown.taxed)
                    )
                }
            )
        },
        Entry(
            "Split a bill", "Share a total between several people", R.drawable.ic_apps
        ) { host ->
            FormScreen(
                host,
                "Split a bill",
                "Splits a total evenly, the remainder is added to the last share.",
                listOf(
                    FormScreen.FormField("total", "Total", "120"),
                    FormScreen.FormField("people", "People", "4")
                ),
                { values ->
                    val total = values["total"] ?: 0.0
                    val people = values["people"]?.toInt() ?: 0
                    val shares = PriceCalculator.split(total, people)
                    host.activity.getString(
                        R.string.qc_split_result,
                        Numbers.format(shares.firstOrNull() ?: 0.0),
                        shares.joinToString(", ") { Numbers.format(it) }
                    )
                }
            )
        },
        Entry(
            "Body mass index", "BMI from weight and height", R.drawable.ic_health
        ) { host ->
            FormScreen(
                host,
                "Body mass index",
                "Metric BMI with the healthy weight range for the given height.",
                listOf(
                    FormScreen.FormField("weight", "Weight", "70", "kg"),
                    FormScreen.FormField("height", "Height", "175", "cm")
                ),
                { values ->
                    val result = BmiCalculator.metric(values["weight"] ?: 0.0, values["height"] ?: 0.0)
                    host.activity.getString(
                        R.string.qc_bmi_result,
                        Numbers.format(result.bmi),
                        result.category,
                        Numbers.format(result.healthyMin),
                        Numbers.format(result.healthyMax)
                    )
                }
            )
        },
        Entry(
            "Loan payment", "Monthly instalment and total interest", R.drawable.ic_calculator
        ) { host ->
            FormScreen(
                host,
                "Loan payment",
                "Amortised loan with a constant monthly rate.",
                listOf(
                    FormScreen.FormField("principal", "Loan amount", "10000"),
                    FormScreen.FormField("rate", "Annual rate", "6", "%"),
                    FormScreen.FormField("months", "Term (months)", "60")
                ),
                { values ->
                    val payment = LoanCalculator.payment(
                        values["principal"] ?: 0.0,
                        values["rate"] ?: 0.0,
                        values["months"]?.toInt() ?: 0
                    )
                    host.activity.getString(
                        R.string.qc_loan_result,
                        Numbers.format(payment.monthly),
                        Numbers.format(payment.totalPaid),
                        Numbers.format(payment.totalInterest)
                    )
                }
            )
        },
        Entry(
            "Interest", "Simple and compound interest", R.drawable.ic_trend
        ) { host ->
            FormScreen(
                host,
                "Interest",
                "Compares simple and compound interest over a number of years.",
                listOf(
                    FormScreen.FormField("principal", "Principal", "1000"),
                    FormScreen.FormField("rate", "Annual rate", "5", "%"),
                    FormScreen.FormField("years", "Years", "10")
                ),
                { values ->
                    val principal = values["principal"] ?: 0.0
                    val rate = values["rate"] ?: 0.0
                    val years = values["years"] ?: 0.0
                    val simple = InterestCalculator.simpleInterest(principal, rate, years)
                    val compound = InterestCalculator.compoundInterest(principal, rate, years)
                    host.activity.getString(
                        R.string.qc_interest_result,
                        Numbers.format(simple),
                        Numbers.format(compound)
                    )
                }
            )
        },
        Entry(
            "GCD, LCM and primes", "Number theory helpers", R.drawable.ic_flask
        ) { host ->
            FormScreen(
                host,
                "GCD, LCM and primes",
                "Greatest common divisor, least common multiple and prime factorisation.",
                listOf(
                    FormScreen.FormField("a", "First number", "12"),
                    FormScreen.FormField("b", "Second number", "18")
                ),
                { values ->
                    val a = (values["a"] ?: 0.0).toLong()
                    val b = (values["b"] ?: 0.0).toLong()
                    val factors = NumberCalculator.factorize(a)
                    // toString(): the resource uses %s slots, and lint rejects
                    // raw Long arguments for them (StringFormatMatches)
                    host.activity.getString(
                        R.string.qc_number_result,
                        NumberCalculator.gcd(a, b).toString(),
                        NumberCalculator.lcm(a, b).toString(),
                        if (factors.isEmpty()) "-" else factors.joinToString(" × ") { (it.first.toString() + if (it.second > 1) "^" + it.second else "") },
                        if (NumberCalculator.isPrime(a)) host.activity.getString(R.string.yes) else host.activity.getString(R.string.no)
                    )
                }
            )
        },
        Entry(
            "Roman numerals", "Convert between integers and Roman numerals", R.drawable.ic_history
        ) { host ->
            FormScreen(
                host,
                "Roman numerals",
                "Type a number between 1 and 3999 to see it in Roman numerals.",
                listOf(FormScreen.FormField("number", "Number", "1994")),
                { values ->
                    val number = (values["number"] ?: 0.0).toInt()
                    host.activity.getString(
                        R.string.qc_roman_result,
                        NumberCalculator.toRoman(number),
                        NumberCalculator.fromRoman(NumberCalculator.toRoman(number))
                    )
                }
            )
        },
        Entry(
            "Number base", "Binary, octal, decimal and hexadecimal", R.drawable.ic_memory
        ) { host ->
            FormScreen(
                host,
                "Number base",
                "Converts a number between any two bases from 2 to 36.",
                listOf(
                    FormScreen.FormField("value", "Value", "255"),
                    FormScreen.FormField("from", "From base", "10"),
                    FormScreen.FormField("to", "To base", "16")
                ),
                { values ->
                    val value = values["value"]?.toLong() ?: 0L
                    val from = values["from"]?.toInt() ?: 10
                    val to = values["to"]?.toInt() ?: 10
                    val converted = BaseConverter.convert(value.toString(), from, to)
                    host.activity.getString(
                        R.string.qc_base_result,
                        converted,
                        BaseConverter.encode(value, 2),
                        BaseConverter.encode(value, 8),
                        BaseConverter.encode(value, 16).uppercase()
                    )
                }
            )
        },
        Entry(
            "Random numbers", "Pick random values in a range", R.drawable.ic_dice
        ) { host ->
            FormScreen(
                host,
                "Random numbers",
                "Generates random values between a minimum and a maximum.",
                listOf(
                    FormScreen.FormField("min", "Minimum", "1"),
                    FormScreen.FormField("max", "Maximum", "100"),
                    FormScreen.FormField("count", "How many", "5")
                ),
                { values ->
                    val numbers = RandomCalculator.inRange(
                        (values["min"] ?: 0.0).toLong(),
                        (values["max"] ?: 0.0).toLong(),
                        (values["count"] ?: 1.0).toInt()
                    )
                    host.activity.getString(
                        R.string.qc_random_result,
                        numbers.joinToString(", ")
                    )
                }
            )
        },
        Entry(
            "Fuel cost", "Cost of a trip", R.drawable.ic_fuel
        ) { host ->
            FormScreen(
                host,
                "Fuel cost",
                "Distance multiplied by consumption and the price per litre.",
                listOf(
                    FormScreen.FormField("distance", "Distance", "450", "km"),
                    FormScreen.FormField("consumption", "Consumption", "6.5", "L/100km"),
                    FormScreen.FormField("price", "Price per litre", "1.85")
                ),
                { values ->
                    val cost = TravelCalculator.fuelCost(
                        values["distance"] ?: 0.0,
                        values["consumption"] ?: 0.0,
                        values["price"] ?: 0.0
                    )
                    host.activity.getString(
                        R.string.qc_fuel_result,
                        Numbers.format(cost),
                        Numbers.format(cost / ((values["distance"] ?: 1.0).coerceAtLeast(0.001)))
                    )
                }
            )
        },
        Entry(
            "Speed and pace", "Average speed and minutes per kilometre", R.drawable.ic_speed
        ) { host ->
            FormScreen(
                host,
                "Speed and pace",
                "Average speed and pace for a distance and a duration.",
                listOf(
                    FormScreen.FormField("distance", "Distance", "10", "km"),
                    FormScreen.FormField("minutes", "Duration", "50", "min")
                ),
                { values ->
                    val distance = values["distance"] ?: 0.0
                    val minutes = values["minutes"] ?: 0.0
                    val speed = TravelCalculator.averageSpeed(distance, minutes)
                    val pace = TravelCalculator.pace(distance, minutes)
                    host.activity.getString(
                        R.string.qc_speed_result,
                        Numbers.format(speed),
                        Numbers.format(pace)
                    )
                }
            )
        },
        Entry(
            "Cooking time", "Scale a recipe to another pan", R.drawable.ic_flask
        ) { host ->
            FormScreen(
                host,
                "Cooking time",
                "Adjusts a cooking time when the pan size changes.",
                listOf(
                    FormScreen.FormField("time", "Original time", "30", "min"),
                    FormScreen.FormField("oldArea", "Original pan area", "20", "cm²"),
                    FormScreen.FormField("newArea", "New pan area", "28", "cm²")
                ),
                { values ->
                    val scaled = CookingCalculator.scaled(
                        values["time"] ?: 0.0,
                        values["oldArea"] ?: 0.0,
                        values["newArea"] ?: 0.0
                    )
                    host.activity.getString(
                        R.string.qc_cooking_result,
                        Numbers.format(scaled),
                        Numbers.format(CookingCalculator.ovenToFan(values["time"] ?: 0.0))
                    )
                }
            )
        },
        Entry(
            "Age", "Age from a date of birth", R.drawable.ic_calendar
        ) { host ->
            FormScreen(
                host,
                "Age",
                "Calculates the age and the number of days since a date of birth.",
                listOf(
                    FormScreen.FormField("year", "Year", "1990"),
                    FormScreen.FormField("month", "Month", "6"),
                    FormScreen.FormField("day", "Day", "15")
                ),
                { values ->
                    val birth = LocalDate.of(
                        (values["year"] ?: 1990.0).toInt(),
                        (values["month"] ?: 1.0).toInt().coerceIn(1, 12),
                        (values["day"] ?: 1.0).toInt().coerceIn(1, 28)
                    )
                    val difference = DateCalculator.age(birth, LocalDate.now())
                    host.activity.getString(
                        R.string.qc_age_result,
                        difference.years,
                        difference.months,
                        difference.days,
                        difference.totalDays
                    )
                }
            )
        }
    )

    private fun entryRow(entry: Entry): View {
        val context = host.activity
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
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

        val icon = ImageView(context)
        icon.setImageResource(entry.icon)
        icon.setColorFilter(Widgets.color(context, R.color.primary))
        row.addView(icon, LinearLayout.LayoutParams(Widgets.dp(context, 22), Widgets.dp(context, 22)))
        row.addView(Widgets.spacer(context, 10))

        val column = Widgets.column(context)
        val title = Widgets.text(context, entry.title, R.style.Text_Body)
        column.addView(title)
        column.addView(Widgets.text(context, entry.description, R.style.Text_Caption))
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.setOnClickListener {
            val screen = entry.screen(host)
            host.push(screen)
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 6)
        row.layoutParams = params
        return row
    }
}
