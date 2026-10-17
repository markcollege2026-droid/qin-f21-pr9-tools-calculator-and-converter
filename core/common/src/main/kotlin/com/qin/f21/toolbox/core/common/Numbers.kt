package com.qin.f21.toolbox.core.common

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Number formatting shared by every tool.
 *
 * The device screen is 480x640, so results are formatted to a readable number of significant
 * digits instead of dumping full double precision.
 */
object Numbers {

    private val symbols = DecimalFormatSymbols(Locale.US)

    private fun formatter(pattern: String) = DecimalFormat(pattern, symbols)

    /** Human friendly number with up to [maxSignificant] significant digits, trailing zeros removed. */
    fun format(value: Double, maxSignificant: Int = 10, maxDecimals: Int = 6): String {
        if (value.isNaN()) return "—"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        if (value == 0.0) return "0"
        val abs = Math.abs(value)
        if (abs >= 1e15 || abs < 1e-9) return engineering(value)
        val rounded = BigDecimal(value).round(MathContext(maxSignificant, RoundingMode.HALF_UP))
        val pattern = if (rounded.stripTrailingZeros().scale() <= maxDecimals) {
            "#,##0.##"
        } else {
            "#,##0.0"
        }
        var text = formatter(pattern).format(rounded)
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        if (text == "-0") text = "0"
        return text
    }

    /** Compact scientific-ish rendering for extreme magnitudes: 1.23e18 / 4.56e-7. */
    fun engineering(value: Double): String {
        if (value == 0.0) return "0"
        val exp = Math.floor(Math.log10(Math.abs(value)))
        val mantissa = value / Math.pow(10.0, exp)
        val mantissaText = BigDecimal(mantissa).round(MathContext(6, RoundingMode.HALF_UP))
            .stripTrailingZeros().toPlainString()
        return "${mantissaText}e${exp.toInt()}"
    }

    /** Fixed decimal rendering, used for money and for the big result read-out. */
    fun formatFixed(value: Double, decimals: Int): String {
        val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        val text = formatter(pattern).format(BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP))
        return if (decimals > 0 && text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    /** Parses user input, tolerating spaces, commas and a leading '+'. */
    fun parse(text: String): Double? {
        val cleaned = text.trim().replace(" ", "").replace(",", "")
        if (cleaned.isEmpty()) return null
        val lower = cleaned.lowercase(Locale.US)
        if (lower == "-" || lower == "+" || lower == "." || lower == "-.") return null
        return cleaned.toDoubleOrNull()
    }
}
