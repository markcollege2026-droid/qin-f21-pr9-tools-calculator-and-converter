package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.calc.CalcException
import com.qin.f21.toolbox.core.calc.ExpressionEvaluator
import com.qin.f21.toolbox.core.common.Numbers
import com.qin.f21.toolbox.core.data.HistoryEntry

/**
 * Scientific calculator.
 *
 * Three bands, top to bottom: the reading (a recessed card with the live expression over
 * the big result), a scrollable row of function chips, and the keypad. The keypad is four
 * rows so each key is as tall as it can be, and the "=" key is the single filled lime
 * surface - the answer is always one thumb away.
 */
class CalculatorScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Calculator"
    override val icon: String? = "ic_calculator"

    private val evaluator = ExpressionEvaluator()
    private var expression = ""
    private var answer = 0.0

    private lateinit var expressionLabel: TextView
    private lateinit var resultLabel: TextView
    private lateinit var errorLabel: TextView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        // --- reading -------------------------------------------------------------
        val display = Widgets.displayCard(context, 14)
        val eyebrow = Widgets.eyebrow(context, "Expression")
        display.addView(eyebrow)

        expressionLabel = Widgets.text(context, "", R.style.Text_Mono)
        expressionLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        expressionLabel.setTextColor(Widgets.color(context, R.color.text_secondary))
        expressionLabel.gravity = Gravity.END
        expressionLabel.maxLines = 2
        expressionLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        expressionLabel.setPadding(0, Widgets.dp(context, 6), 0, 0)
        display.addView(expressionLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        resultLabel = Widgets.text(context, "0", R.style.Text_Display)
        resultLabel.gravity = Gravity.END
        resultLabel.maxLines = 1
        resultLabel.ellipsize = android.text.TextUtils.TruncateAt.START
        resultLabel.setPadding(0, Widgets.dp(context, 2), 0, 0)
        display.addView(resultLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        errorLabel = Widgets.text(context, "", R.style.Text_Caption)
        errorLabel.setTextColor(Widgets.color(context, R.color.danger))
        errorLabel.gravity = Gravity.END
        display.addView(errorLabel)

        root.addView(display, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 12), Widgets.dp(context, 12), 0) })

        // --- function chips -------------------------------------------------------
        val chipScroll = HorizontalScrollView(context)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chipRow = Widgets.row(context)
        chipRow.gravity = Gravity.CENTER_VERTICAL
        val functions = listOf(
            "sin(" to "sin", "cos(" to "cos", "tan(" to "tan", "ln(" to "ln",
            "log(" to "log", "√(" to "√", "^" to "xʸ", "π" to "π", "(" to "(",
            ")" to ")", "ans" to "ANS", "%" to "%", "C" to "C", "⌫" to "⌫"
        )
        for ((token, label) in functions) {
            val chip = Widgets.chip(context, label) { press(token) }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Widgets.dp(context, 32)
            )
            params.rightMargin = Widgets.dp(context, 6)
            chipRow.addView(chip, params)
        }
        chipScroll.addView(chipRow, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(chipScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 10), Widgets.dp(context, 12), 0) })

        // --- keypad ---------------------------------------------------------------
        val keypad = KeypadView(
            context,
            arrayOf(
                arrayOf("7", "8", "9", "÷"),
                arrayOf("4", "5", "6", "×"),
                arrayOf("1", "2", "3", "−"),
                arrayOf("0", ".", "=", "+")
            )
        ) { key -> press(key) }
        root.addView(keypad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 8), Widgets.dp(context, 12)) })

        updateDisplay()
        return root
    }

    private fun press(key: String) {
        when (key) {
            "C" -> {
                expression = ""
                errorLabel.text = ""
            }
            "⌫" -> {
                if (expression.isNotEmpty()) expression = expression.dropLast(1)
            }
            "=" -> evaluate()
            "÷" -> expression += "/"
            "×" -> expression += "*"
            "−" -> expression += "-"
            "√(" -> expression += "sqrt("
            "ans" -> expression += "ans"
            "π" -> expression += "pi"
            else -> expression += key
        }
        if (key != "=") {
            preview()
        }
    }

    private fun preview() {
        expressionLabel.text = expression
        if (expression.isBlank()) {
            resultLabel.text = "0"
            return
        }
        try {
            val value = evaluator.evaluate(expression, answer)
            resultLabel.text = Numbers.format(value)
            errorLabel.text = ""
        } catch (e: Exception) {
            resultLabel.text = "…"
        }
    }

    private fun evaluate() {
        if (expression.isBlank()) return
        try {
            val value = evaluator.evaluate(expression, answer)
            answer = value
            resultLabel.text = Numbers.format(value)
            errorLabel.text = ""
            expressionLabel.text = expression + " ="
            Toolbox.store.addHistory(
                HistoryEntry(
                    toolId = "calculator",
                    toolName = "Calculator",
                    title = Numbers.format(value),
                    summary = expression + " ="
                )
            )
            expression = ""
        } catch (e: CalcException) {
            errorLabel.text = e.message ?: host.activity.getString(R.string.error_generic)
            resultLabel.text = "—"
        } catch (e: Exception) {
            errorLabel.text = host.activity.getString(R.string.error_generic)
            resultLabel.text = "—"
        }
    }

    private fun updateDisplay() {
        expressionLabel.text = " "
        resultLabel.text = Numbers.format(answer)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            press((keyCode - KeyEvent.KEYCODE_0).toString())
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PERIOD || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) {
            press(".")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            press("⌫")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            press("C")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
            press("+")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MINUS) {
            press("−")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            press("×")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_SLASH) {
            press("÷")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER && host.activity.currentFocus !is android.widget.Button) {
            press("=")
            return true
        }
        return false
    }
}
