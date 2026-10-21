package com.qin.f21.toolbox.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.R

/**
 * A single "add" button that pops the individual actions out of itself.
 *
 * The wallet used to need one button per movement; with the movements growing to three
 * (expense, top up, withdraw) they are folded into one round button at the bottom right.
 * Tapping it springs the actions out above it - staggered, with an overshoot - and dims
 * the rest of the screen; tapping it again (or the dimmed area, or Back) folds them back.
 *
 * The overlay is a normal part of the view tree: the actions are laid out at their final
 * position and only animated in, and every one of them is a D-pad stop, so the physical
 * keypad of the Qin F21 can use the menu exactly like a touch user.
 */
class SpeedDial(
    private val context: Context,
    private val actions: List<Action>
) {

    data class Action(
        val label: String,
        val icon: Int,
        val tint: Int,
        val onClick: () -> Unit
    )

    /** The overlay; add it on top of the screen content. */
    val root: FrameLayout = FrameLayout(context)

    private val scrim = View(context)
    private val column = LinearLayout(context)
    private val items = ArrayList<View>()
    private lateinit var fab: FrameLayout
    private lateinit var fabIcon: ImageView

    var isExpanded: Boolean = false
        private set

    init {
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        scrim.setBackgroundColor(Widgets.color(context, R.color.scrim))
        scrim.alpha = 0f
        scrim.visibility = View.INVISIBLE
        scrim.isClickable = true
        scrim.setOnClickListener { collapse() }
        root.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.END
        val margin = Widgets.dp(context, 12)
        val columnParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        columnParams.gravity = Gravity.BOTTOM or Gravity.END
        columnParams.setMargins(margin, margin, margin, margin)
        root.addView(column, columnParams)

        // the actions sit above the button, so they are added first and the last one ends
        // up right next to it
        for (action in actions.reversed()) {
            val item = actionRow(action)
            items.add(0, item)
            column.addView(item)
        }
        fab = buildFab()
        column.addView(fab, LinearLayout.LayoutParams(
            Widgets.dp(context, 56), Widgets.dp(context, 56)
        ))
    }

    /** The views that take part in the animation, nearest to the button first. */
    private fun actionRow(action: Action): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 10)
        row.layoutParams = params

        val label = TextView(context)
        label.text = action.label
        label.setTextAppearance(context, R.style.Text_Label)
        label.setTextColor(Widgets.color(context, R.color.text_primary))
        label.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 7),
            Widgets.dp(context, 10), Widgets.dp(context, 7)
        )
        label.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface_high),
            Widgets.color(context, R.color.outline_strong),
            16
        )
        row.addView(label, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = Widgets.dp(context, 8) })

        val circle = FrameLayout(context)
        circle.background = Widgets.roundedBackground(context, action.tint, 0, 22)
        val icon = ImageView(context)
        icon.setImageResource(action.icon)
        icon.setColorFilter(Widgets.color(context, R.color.white))
        val pad = Widgets.dp(context, 9)
        icon.setPadding(pad, pad, pad, pad)
        circle.addView(icon, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        row.addView(circle, LinearLayout.LayoutParams(
            Widgets.dp(context, 42),
            Widgets.dp(context, 42)
        ))

        row.isClickable = true
        row.isFocusable = true
        row.contentDescription = action.label
        row.background = Widgets.focusBackground(
            context,
            Widgets.color(context, R.color.transparent),
            Widgets.color(context, R.color.transparent),
            22
        )
        row.setOnClickListener {
            collapse()
            action.onClick()
        }
        // start folded: invisible, small and pulled down towards the button
        row.alpha = 0f
        row.scaleX = 0.4f
        row.scaleY = 0.4f
        row.visibility = View.INVISIBLE
        return row
    }

    private fun buildFab(): FrameLayout {
        val button = FrameLayout(context)
        button.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.primary),
            0,
            28
        )
        val icon = ImageView(context)
        icon.setImageResource(R.drawable.ic_plus)
        icon.setColorFilter(Widgets.color(context, R.color.on_primary))
        val pad = Widgets.dp(context, 14)
        icon.setPadding(pad, pad, pad, pad)
        button.addView(icon, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        button.isClickable = true
        button.isFocusable = true
        button.contentDescription = context.getString(R.string.wallet_add)
        button.setOnClickListener { toggle() }
        fabIcon = icon
        return button
    }

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    /** Springs the actions out of the button. */
    fun expand() {
        if (isExpanded) return
        isExpanded = true
        scrim.visibility = View.VISIBLE
        scrim.animate().cancel()
        scrim.animate().alpha(1f).setDuration(160).setListener(null).start()
        fabIcon.animate().rotation(135f).setDuration(220)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        for ((index, item) in items.withIndex()) {
            item.translationY = Widgets.dp(context, 14).toFloat()
            item.visibility = View.VISIBLE
            item.animate().cancel()
            item.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay((index * 45).toLong())
                .setDuration(230)
                .setInterpolator(OvershootInterpolator(1.5f))
                .setListener(null)
                .start()
        }
        items.firstOrNull()?.let { first ->
            first.post { if (isExpanded) first.requestFocus() }
        }
    }

    /** Folds the actions back into the button. */
    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        scrim.animate().cancel()
        scrim.animate().alpha(0f).setDuration(140).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isExpanded) scrim.visibility = View.INVISIBLE
            }
        }).start()
        fabIcon.animate().rotation(0f).setDuration(180)
            .setInterpolator(AccelerateInterpolator()).start()
        val last = items.size - 1
        for ((index, item) in items.withIndex()) {
            item.animate().cancel()
            item.animate()
                .alpha(0f)
                .scaleX(0.4f)
                .scaleY(0.4f)
                .translationY(Widgets.dp(context, 14).toFloat())
                .setStartDelay(((last - index) * 30).toLong())
                .setDuration(150)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isExpanded) item.visibility = View.INVISIBLE
                    }
                })
                .start()
        }
        fab.post { fab.requestFocus() }
    }
}
