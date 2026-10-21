package com.qin.f21.toolbox.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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

/**
 * The shared component kit.
 *
 * Every screen is assembled from these builders so that spacing, radii, type and the
 * keypad focus ring are identical everywhere. The scale is the 4 dp grid from
 * `dimens.xml`: 4 / 8 / 12 / 16 / 24 / 32.
 *
 * Two rules keep the interface calm on the 320 x 427 dp display:
 *  - colour is a signal, not decoration: lime marks the primary action, the current
 *    reading and the focused control, everything else is graphite;
 *  - a control that can take the D-pad focus always answers with the 2 dp lime ring.
 */
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

    fun title(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Title)

    fun body(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Body)

    fun caption(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Caption)

    fun label(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Label)

    fun mono(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Mono)

    fun eyebrow(context: Context, value: CharSequence): TextView = text(context, value, R.style.Text_Eyebrow)

    /** A section label with a hairline rule that fills the remaining width. */
    fun sectionHeader(context: Context, value: CharSequence, count: Int? = null): View {
        val row = row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(context, 4), dp(context, 16), dp(context, 4), dp(context, 6))
        val label = text(context, value, R.style.Text_Section)
        label.isAllCaps = true
        row.addView(label, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        if (count != null) {
            val badge = text(context, count.toString(), R.style.Text_Section)
            badge.isAllCaps = true
            badge.setTextColor(color(context, R.color.text_secondary))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = dp(context, 8)
            row.addView(badge, params)
        }
        val rule = View(context)
        rule.setBackgroundColor(color(context, R.color.divider))
        val ruleParams = LinearLayout.LayoutParams(0, dp(context, 1), 1f)
        ruleParams.leftMargin = dp(context, 12)
        row.addView(rule, ruleParams)
        return row
    }

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

    fun divider(context: Context): View {
        val view = View(context)
        view.setBackgroundColor(color(context, R.color.divider))
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)
        )
        return view
    }

    /**
     * A card: the default container for a group of related controls.
     *
     * Padding is 12 dp and the radius 16 dp, which is the one radius used for every
     * surface that holds content; controls inside it use the smaller 12 dp radius so the
     * nesting reads as depth rather than as noise.
     */
    fun card(context: Context, paddingDp: Int = 12): LinearLayout {
        val layout = column(context)
        layout.setPadding(
            dp(context, paddingDp), dp(context, paddingDp),
            dp(context, paddingDp), dp(context, paddingDp)
        )
        layout.background = roundedBackground(
            context, color(context, R.color.surface), color(context, R.color.outline), 16
        )
        return layout
    }

    /** A recessed reading area (calculator display, converter result). */
    fun displayCard(context: Context, paddingDp: Int = 12): LinearLayout {
        val layout = column(context)
        layout.setPadding(
            dp(context, paddingDp), dp(context, paddingDp),
            dp(context, paddingDp), dp(context, paddingDp)
        )
        layout.background = roundedBackground(
            context, color(context, R.color.surface_sunken), color(context, R.color.outline), 16
        )
        return layout
    }

    /** The lime card that carries the final answer of a form. */
    fun resultCard(context: Context): LinearLayout {
        val layout = column(context)
        layout.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        layout.background = roundedBackground(
            context, color(context, R.color.primary_dim), color(context, R.color.primary), 16
        )
        return layout
    }

    /** A 32 dp rounded square behind a tool icon, so the icon column stays aligned. */
    fun iconChip(context: Context, iconRes: Int, tint: Int, sizeDp: Int = 32): View {
        val holder = android.widget.FrameLayout(context)
        holder.background = roundedBackground(context, color(context, R.color.surface_high), 0, 8)
        val icon = ImageView(context)
        icon.setImageResource(iconRes)
        icon.setColorFilter(tint)
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val inner = dp(context, if (sizeDp >= 40) 22 else 18)
        val params = android.widget.FrameLayout.LayoutParams(inner, inner)
        params.gravity = Gravity.CENTER
        holder.addView(icon, params)
        holder.layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        return holder
    }

    fun chevron(context: Context): View {
        val view = ImageView(context)
        view.setImageResource(R.drawable.ic_back)
        view.setColorFilter(color(context, R.color.text_muted))
        view.rotation = 180f
        view.scaleX = -1f
        view.scaleType = ImageView.ScaleType.CENTER_INSIDE
        view.layoutParams = LinearLayout.LayoutParams(dp(context, 14), dp(context, 14))
        return view
    }

    /**
     * The standard tappable row: leading icon chip, title, optional note, chevron.
     *
     * It is focusable, so the D-pad can reach it, and it answers with the lime ring.
     */
    fun listRow(
        context: Context,
        titleText: CharSequence,
        subtitle: CharSequence? = null,
        iconRes: Int? = null,
        trailing: CharSequence? = null,
        iconTint: Int = R.color.text_secondary,
        titleColor: Int = R.color.text_primary,
        enabled: Boolean = true,
        showChevron: Boolean = true,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val item = row(context)
        item.gravity = Gravity.CENTER_VERTICAL
        item.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
        item.background = focusBackground(
            context,
            color(context, R.color.surface),
            color(context, R.color.outline),
            12
        )
        item.minimumHeight = dp(context, 56)
        item.isFocusable = true
        item.isClickable = true
        if (!enabled) item.alpha = 0.55f

        if (iconRes != null) {
            item.addView(iconChip(context, iconRes, color(context, iconTint), 32))
            item.addView(spacer(context, 10))
        }
        val column = column(context)
        val titleView = text(context, titleText, R.style.Text_Body)
        titleView.setTextColor(color(context, titleColor))
        titleView.maxLines = 1
        titleView.ellipsize = android.text.TextUtils.TruncateAt.END
        column.addView(titleView)
        if (!subtitle.isNullOrEmpty()) {
            val noteView = text(context, subtitle, R.style.Text_Caption)
            noteView.maxLines = 2
            noteView.ellipsize = android.text.TextUtils.TruncateAt.END
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(context, 2)
            column.addView(noteView, params)
        }
        item.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) {
            val trailingView = text(context, trailing, R.style.Text_Label)
            trailingView.setTextColor(color(context, R.color.text_secondary))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = dp(context, 8)
            item.addView(trailingView, params)
        } else if (showChevron) {
            item.addView(chevron(context))
        }
        if (onClick != null) item.setOnClickListener { onClick() }
        return item
    }

    /** The empty-state block: a quiet dashed card with a single line of explanation. */
    fun emptyState(context: Context, message: CharSequence): View {
        val card = column(context)
        card.gravity = Gravity.CENTER
        card.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 24))
        card.background = context.getDrawable(R.drawable.bg_unavailable)
        val text = text(context, message, R.style.Text_Caption)
        text.gravity = Gravity.CENTER
        text.maxLines = 4
        card.addView(text)
        return card
    }

    fun badge(context: Context, value: CharSequence, tint: Int = R.color.primary): TextView {
        val view = text(context, value, R.style.Text_Eyebrow)
        view.setTextColor(color(context, tint))
        view.setPadding(dp(context, 6), dp(context, 3), dp(context, 6), dp(context, 3))
        view.background = roundedBackground(context, color(context, R.color.surface_high), 0, 6)
        return view
    }

    /**
     * A filled button.
     *
     * `TextView.setTextAppearance` only carries text attributes, never a background, so the
     * drawable is applied here explicitly - otherwise every button would fall back to the
     * grey Material chrome and the whole colour system would be invisible.
     */
    fun button(context: Context, labelText: CharSequence, secondary: Boolean = false): Button {
        val button = Button(context)
        button.text = labelText
        button.setTextAppearance(
            context,
            if (secondary) R.style.Widget_App_Button_Secondary else R.style.Widget_App_Button
        )
        button.background = context.getDrawable(
            if (secondary) R.drawable.bg_button_secondary else R.drawable.bg_button_primary
        )
        button.setTextColor(
            color(context, if (secondary) R.color.text_primary else R.color.on_primary)
        )
        button.isAllCaps = false
        button.stateListAnimator = null
        button.elevation = 0f
        button.includeFontPadding = false
        button.setOnClickListener { }
        button.minWidth = 0
        button.minHeight = 0
        button.gravity = Gravity.CENTER
        return button
    }

    /** A pill-shaped chip for the function rows above a keypad. */
    fun chip(context: Context, labelText: CharSequence, onClick: (() -> Unit)? = null): Button {
        val button = Button(context)
        button.text = labelText
        button.setTextAppearance(context, R.style.Widget_App_Chip)
        button.background = context.getDrawable(R.drawable.bg_chip)
        button.setTextColor(color(context, R.color.text_secondary))
        button.isAllCaps = false
        button.stateListAnimator = null
        button.elevation = 0f
        button.includeFontPadding = false
        button.minWidth = 0
        button.minHeight = 0
        button.maxLines = 1
        button.gravity = Gravity.CENTER
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0)
        if (onClick != null) button.setOnClickListener { onClick() }
        return button
    }

    /** A button for the narrow slots (function rows, preset chips). */
    fun compactButton(context: Context, labelText: CharSequence, secondary: Boolean = true): Button {
        val button = button(context, labelText, secondary)
        button.setPadding(0, 0, 0, 0)
        button.maxLines = 1
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

    /**
     * Resolves a colour resource.
     *
     * This used to call `Theme.resolveAttribute`, which only accepts *attribute* ids: for a
     * plain `@color/...` resource it returns false and leaves the value at 0, which would
     * have painted the whole interface black. Every caller passes a colour resource, so a
     * direct lookup is both correct and faster.
     */
    fun color(context: Context, id: Int): Int = context.getColor(id)

    fun roundedBackground(
        context: Context,
        fill: Int,
        stroke: Int = 0,
        radiusDp: Int = 12,
        strokeDp: Int = 1
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(fill)
        drawable.cornerRadius = dp(context, radiusDp).toFloat()
        if (stroke != 0) drawable.setStroke(dp(context, strokeDp), stroke)
        return drawable
    }

    /**
     * Background for a control that can hold the D-pad focus.
     *
     * A static [GradientDrawable] looks identical whatever the state of its view, so a
     * focusable row drawn with one gives a keypad user no feedback at all. This builds a
     * state list instead: the pressed and focused states get the 2 dp lime ring and one
     * step up the surface ladder, the resting state keeps the surface and the hairline.
     */
    fun focusBackground(
        context: Context,
        fill: Int,
        stroke: Int,
        radiusDp: Int = 12
    ): StateListDrawable {
        val focusFill = color(context, R.color.surface_high)
        val ring = color(context, R.color.primary)
        val drawable = StateListDrawable()
        drawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            roundedBackground(context, focusFill, ring, radiusDp, 2)
        )
        drawable.addState(
            intArrayOf(android.R.attr.state_focused),
            roundedBackground(context, focusFill, ring, radiusDp, 2)
        )
        drawable.addState(intArrayOf(), roundedBackground(context, fill, stroke, radiusDp))
        return drawable
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

/**
 * Makes a scroll container reachable with the physical D-pad.
 *
 * A [ScrollView] only responds to the arrow keys while it holds the focus, and its default
 * descendant focusability would let it take the focus before its own children. With
 * [ViewGroup.FOCUS_AFTER_DESCENDANTS] the children keep winning, so this only becomes a
 * focus stop on screens whose content has nothing focusable inside it - the read-only
 * screens such as Diagnostics and About, where the D-pad is otherwise unable to scroll.
 */
fun ScrollView.forKeypad(): ScrollView {
    isFocusable = true
    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    return this
}
