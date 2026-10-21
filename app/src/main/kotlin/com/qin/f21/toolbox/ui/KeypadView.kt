package com.qin.f21.toolbox.ui

import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import com.qin.f21.toolbox.R

/**
 * Compact keypad used by the calculator style screens.
 *
 * The keypad is fully navigable with the D-pad: left/right moves inside a row, up/down
 * moves between rows and the focused key keeps its lime ring. The same keys can also be
 * triggered with the physical numeric keypad of the device.
 *
 * Keys carry one of three roles so the eye can find them without reading: operators are
 * recessed with lime glyphs, the commit key ("=") is the only filled lime surface on the
 * screen, everything else is a neutral key.
 */
class KeypadView(
    context: Context,
    private val keys: Array<Array<String>>,
    private val onKey: (String) -> Unit
) : ViewGroup(context) {

    private val buttons = ArrayList<Button>()

    init {
        // The keypad itself is not a focus target, the individual keys are, so the
        // D-pad walks through the keys instead of stopping on the container.
        isFocusable = false
        isFocusableInTouchMode = false
        for (row in keys) {
            for (key in row) {
                val role = roleOf(key)
                val button = Button(context)
                button.text = key
                button.setTextAppearance(
                    context,
                    when (role) {
                        Role.COMMIT -> R.style.Widget_App_Button_Key_Accent
                        Role.OPERATOR -> R.style.Widget_App_Button_Key_Operator
                        Role.NEUTRAL -> R.style.Widget_App_Button_Key
                    }
                )
                // TextAppearance does not carry the background, apply it directly.
                button.background = context.getDrawable(
                    when (role) {
                        Role.COMMIT -> R.drawable.bg_key_accent
                        Role.OPERATOR -> R.drawable.bg_key_operator
                        Role.NEUTRAL -> R.drawable.bg_key
                    }
                )
                button.setTextColor(
                    context.getColor(
                        when (role) {
                            Role.COMMIT -> R.color.on_primary
                            Role.OPERATOR -> R.color.primary
                            Role.NEUTRAL -> R.color.text_primary
                        }
                    )
                )
                button.isAllCaps = false
                button.stateListAnimator = null
                button.elevation = 0f
                button.includeFontPadding = false
                // the keypad measures the keys itself, the style minimums would clip them
                button.minHeight = 0
                button.minWidth = 0
                button.setPadding(0, 0, 0, 0)
                button.setOnClickListener { onKey(key) }
                buttons.add(button)
                addView(button)
            }
        }
    }

    private enum class Role { NEUTRAL, OPERATOR, COMMIT }

    private fun roleOf(key: String): Role = when (key) {
        "=", "→" -> Role.COMMIT
        "÷", "×", "−", "+", "-", "*", "/", "^", "%", "±" -> Role.OPERATOR
        else -> Role.NEUTRAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val columns = keys.maxOf { it.size }
        val cellWidth = width / columns
        val cellHeight = height / keys.size
        val gap = dp(4)
        val childWidth = MeasureSpec.makeMeasureSpec(cellWidth - gap, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(cellHeight - gap, MeasureSpec.EXACTLY)
        for (child in buttons) {
            child.measure(childWidth, childHeight)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val columns = keys.maxOf { it.size }
        val cellWidth = (r - l) / columns
        val cellHeight = (b - t) / keys.size
        val gap = dp(4)
        val inset = gap / 2
        var index = 0
        for (row in keys.indices) {
            for (column in keys[row].indices) {
                val child = buttons.getOrNull(index) ?: break
                val left = column * cellWidth + inset
                val top = row * cellHeight + inset
                child.layout(left, top, left + cellWidth - gap, top + cellHeight - gap)
                index++
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val focused = findFocus() as? Button
        val index = buttons.indexOf(focused)
        if (index < 0) return super.onKeyDown(keyCode, event)
        val columns = keys.maxOf { it.size }
        var target = -1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (index % columns > 0) target = index - 1
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (index % columns < columns - 1) target = index + 1
            KeyEvent.KEYCODE_DPAD_UP -> if (index >= columns) target = index - columns
            KeyEvent.KEYCODE_DPAD_DOWN -> if (index + columns < buttons.size) target = index + columns
        }
        if (target >= 0) {
            buttons[target].requestFocus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
