package com.qin.f21.toolbox.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button

/**
 * Compact keypad used by the calculator style screens.
 *
 * The keypad is fully navigable with the D-pad: left/right moves inside a row, up/down
 * moves between rows and the focused key keeps its focus ring. The same keys can also be
 * triggered with the physical numeric keypad of the device.
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
                val button = Button(context)
                button.text = key
                button.setTextAppearance(context, com.qin.f21.toolbox.R.style.Widget_App_Button_Key)
                button.isAllCaps = false
                button.setOnClickListener { onKey(key) }
                buttons.add(button)
                addView(button)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val columns = keys.maxOf { it.size }
        val cellWidth = width / columns
        val cellHeight = height / keys.size
        val childWidth = MeasureSpec.makeMeasureSpec(cellWidth - dp(2), MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(cellHeight - dp(2), MeasureSpec.EXACTLY)
        for (child in buttons) {
            child.measure(childWidth, childHeight)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val columns = keys.maxOf { it.size }
        val cellWidth = (r - l) / columns
        val cellHeight = (b - t) / keys.size
        var index = 0
        for (row in keys.indices) {
            for (column in keys[row].indices) {
                val child = buttons.getOrNull(index) ?: break
                val left = column * cellWidth
                val top = row * cellHeight
                child.layout(left, top, left + cellWidth, top + cellHeight)
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
