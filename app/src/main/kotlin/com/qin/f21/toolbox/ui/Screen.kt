package com.qin.f21.toolbox.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.hardware.HardwareCapabilities

/** Host services every screen can use. */
interface ScreenHost {
    val activity: Activity
    val capabilities: HardwareCapabilities
    fun push(screen: Screen)
    fun pop()
    fun replace(screen: Screen)
    fun setTitle(title: String, subtitle: String? = null)
    fun showMessage(message: String)
    fun askPermissions(permissions: Array<String>, requestCode: Int)
    fun onScreenResumed(screen: Screen)
}

/**
 * Base class of every tool screen.
 *
 * Screens are plain views hosted by a single activity, which keeps navigation instant
 * and the APK small. Each screen owns its own key handling so the physical D-pad and
 * keypad can be supported precisely.
 */
abstract class Screen(val host: ScreenHost) {

    /** Title shown in the header. */
    open val title: String = ""

    /** Optional hint shown next to the title. */
    open val subtitle: String? = null

    /** Drawable name used for the header icon (without the "ic_" prefix). */
    open val icon: String? = null

    private var contentView: View? = null

    fun createView(parent: ViewGroup): View {
        val view = onCreateView(parent)
        contentView = view
        return view
    }

    protected abstract fun onCreateView(parent: ViewGroup): View

    /** Called after the screen became visible. */
    open fun onShown() {}

    /** Called when the screen is no longer visible. */
    open fun onHidden() {}

    /** @return true when the key was consumed. */
    open fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false

    /** @return true when the key was consumed (long press). */
    open fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean = false

    open fun onBackPressed(): Boolean = false

    /** @return true when the result was consumed by the screen. */
    open fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean = false

    val view: View? get() = contentView
}

/** Small helper factory for the common widget styles used across the screens. */
object Widgets {

    fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    fun sp(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics
    )

    fun text(context: Context, value: CharSequence, style: Int = R.style.Text_Body): TextView {
        val view = TextView(context)
        view.setText(value)
        view.setTextAppearance(context, style)
        view.includeFontPadding = false
        return view
    }

    fun sectionHeader(context: Context, value: CharSequence): TextView {
        val view = text(context, value, R.style.Text_Section)
        view.setPadding(0, dp(context, 14), 0, dp(context, 6))
        return view
    }

    fun title(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Title)

    fun body(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Body)

    fun caption(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Caption)

    fun mono(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Mono)

    fun row(context: Context, orientation: Int = LinearLayout.HORIZONTAL): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = orientation
        layout.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return layout
    }

    fun column(context: Context): LinearLayout = row(context, LinearLayout.VERTICAL)

    fun spacer(context: Context, width: Int = 0, height: Int = 0): View {
        val view = View(context)
        view.layoutParams = LinearLayout.LayoutParams(dp(context, width), dp(context, height))
        return view
    }

    fun weightSpacer(context: Context): View {
        val view = View(context)
        view.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        return view
    }

    fun button(context: Context, label: CharSequence, secondary: Boolean = false): Button {
        val button = Button(context)
        button.text = label
        button.setTextAppearance(context, if (secondary) R.style.Widget_App_Button_Secondary else R.style.Widget_App_Button)
        button.isAllCaps = false
        button.setOnClickListener { }
        button.minWidth = 0
        button.minHeight = 0
        return button
    }

    fun editField(context: Context, hint: CharSequence = ""): EditText {
        val field = EditText(context)
        field.setTextAppearance(context, R.style.Widget_App_Field)
        field.hint = hint
        field.setHintTextColor(color(context, R.color.text_muted))
        field.setTextColor(color(context, R.color.text_primary))
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        field.isSingleLine = true
        return field
    }

    fun numericField(context: Context, hint: CharSequence = ""): EditText {
        val field = editField(context, hint)
        field.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED
        return field
    }

    fun color(context: Context, id: Int): Int {
        val typed = TypedValue()
        context.theme.resolveAttribute(id, typed, true)
        return typed.data
    }

    fun roundedBackground(context: Context, fill: Int, stroke: Int = 0, radiusDp: Int = 10): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(fill)
        drawable.cornerRadius = dp(context, radiusDp).toFloat()
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke)
        return drawable
    }

    fun iconCircle(context: Context, iconRes: Int, tint: Int): View {
        val view = android.widget.ImageView(context)
        view.setImageResource(iconRes)
        view.setColorFilter(tint)
        view.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(context, 8)
        view.setPadding(pad, pad, pad, pad)
        view.background = roundedBackground(context, tint and 0x00FFFFFF or 0x33000000, 0, 12)
        val params = LinearLayout.LayoutParams(dp(context, 38), dp(context, 38))
        view.layoutParams = params
        return view
    }

    fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }
}

/** Key codes the physical Qin keypad delivers. */
object HardwareKeys {
    const val KEY_DPAD_CENTER = KeyEvent.KEYCODE_DPAD_CENTER
    const val KEY_ENTER = KeyEvent.KEYCODE_ENTER
    const val KEY_DEL = KeyEvent.KEYCODE_DEL
    const val KEY_STAR = KeyEvent.KEYCODE_STAR
    const val KEY_POUND = KeyEvent.KEYCODE_POUND
    const val KEY_CLEAR = KeyEvent.KEYCODE_CLEAR
    const val KEY_CALL = KeyEvent.KEYCODE_CALL
    const val KEY_ENDCALL = KeyEvent.KEYCODE_ENDCALL
    const val KEY_SOFT_LEFT = KeyEvent.KEYCODE_SOFT_LEFT
    const val KEY_SOFT_RIGHT = KeyEvent.KEYCODE_SOFT_RIGHT
}
