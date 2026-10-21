package com.qin.f21.toolbox.ui

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.common.Numbers

/**
 * A generic numeric form: labelled inputs on top, one prominent result below.
 *
 * Used by the specialised calculators (percentage, BMI, loan, geometry, ...) so that every
 * one of them shares the same compact layout, the same error handling and the same
 * history behaviour.
 */
class FormScreen(
    host: ScreenHost,
    override val title: String,
    private val description: String,
    private val fields: List<FormField>,
    private val compute: (Map<String, Double>) -> String,
    private val toolId: String = "quickcalc",
    private val onResult: ((String, List<String>) -> Unit)? = null
) : Screen(host) {

    data class FormField(
        val id: String,
        val label: String,
        val default: String = "",
        val unit: String = ""
    )

    private val inputs = LinkedHashMap<String, EditText>()
    private lateinit var resultLabel: TextView
    private lateinit var errorLabel: TextView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 12),
            Widgets.dp(context, 12), Widgets.dp(context, 16)
        )

        val descriptionView = Widgets.caption(context, description)
        column.addView(descriptionView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        for (field in fields) {
            column.addView(fieldRow(field))
        }

        val resultCard = Widgets.resultCard(context)
        val resultEyebrow = Widgets.eyebrow(context, context.getString(R.string.form_result))
        resultEyebrow.setTextColor(Widgets.color(context, R.color.primary))
        resultCard.addView(resultEyebrow)
        resultLabel = Widgets.text(context, "—", R.style.Text_Display)
        resultLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
        resultLabel.maxLines = 3
        resultLabel.setPadding(0, Widgets.dp(context, 2), 0, 0)
        resultCard.addView(resultLabel)
        errorLabel = Widgets.text(context, "", R.style.Text_Caption)
        errorLabel.setTextColor(Widgets.color(context, R.color.danger))
        resultCard.addView(errorLabel)
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.topMargin = Widgets.dp(context, 16)
        resultCard.layoutParams = cardParams
        column.addView(resultCard)

        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val actions = Widgets.row(context)
        val calcButton = Widgets.button(context, context.getString(R.string.action_calculate))
        calcButton.setOnClickListener { calculate() }
        actions.addView(calcButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Widgets.spacer(context, 8))
        val clearButton = Widgets.button(context, context.getString(R.string.action_clear), secondary = true)
        clearButton.setOnClickListener {
            for ((id, field) in inputs) {
                val definition = fields.first { it.id == id }
                field.setText(definition.default)
            }
            resultLabel.text = "—"
            errorLabel.text = ""
        }
        actions.addView(clearButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 12), Widgets.dp(context, 8), Widgets.dp(context, 12), Widgets.dp(context, 12))
        })

        calculate()
        return root
    }

    private fun fieldRow(field: FormField): View {
        val context = host.activity
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL

        val label = Widgets.text(context, field.label, R.style.Text_Label)
        label.setTextColor(Widgets.color(context, R.color.text_secondary))
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val edit = Widgets.editField(context, field.default)
        edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED
        edit.gravity = Gravity.END
        edit.setText(field.default)
        edit.setTextColor(Widgets.color(context, R.color.text_primary))
        edit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) calculate()
        }
        inputs[field.id] = edit
        val width = if (field.unit.isEmpty()) 124 else 100
        row.addView(edit, LinearLayout.LayoutParams(
            Widgets.dp(context, width), ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        if (field.unit.isNotEmpty()) {
            val unit = Widgets.text(context, field.unit, R.style.Text_Caption)
            row.addView(unit, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = Widgets.dp(context, 6) })
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = Widgets.dp(context, 8)
        row.layoutParams = params
        return row
    }

    private fun calculate() {
        val values = HashMap<String, Double>()
        for ((id, field) in inputs) {
            val parsed = Numbers.parse(field.text?.toString() ?: "")
            if (parsed == null) {
                errorLabel.text = host.activity.getString(
                    R.string.error_invalid_number,
                    fields.first { it.id == id }.label
                )
                resultLabel.text = "—"
                return
            }
            values[id] = parsed
        }
        errorLabel.text = ""
        val result = try {
            compute(values)
        } catch (e: Exception) {
            errorLabel.text = e.message ?: host.activity.getString(R.string.error_generic)
            resultLabel.text = "—"
            return
        }
        resultLabel.text = result
    }

    override fun onShown() {
        val values = HashMap<String, Double>()
        for ((id, field) in inputs) {
            Numbers.parse(field.text?.toString() ?: "")?.let { values[id] = it }
        }
        val result = try {
            compute(values)
        } catch (e: Exception) {
            return
        }
        onResult?.invoke(result, values.map { (key, value) -> "$key=${Numbers.format(value)}" })
        com.qin.f21.toolbox.Toolbox.store.addHistory(
            com.qin.f21.toolbox.core.data.HistoryEntry(
                toolId = toolId,
                toolName = title,
                title = result,
                summary = values.entries.joinToString(", ") { (key, value) ->
                    val label = fields.firstOrNull { it.id == key }?.label ?: key
                    "$label ${Numbers.format(value)}"
                }
            )
        )
    }
}
