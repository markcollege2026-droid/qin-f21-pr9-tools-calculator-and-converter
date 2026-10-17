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
import com.qin.f21.toolbox.Tools

/**
 * Favourites: pinned tools and unit pairs. Stored locally in the toolbox store.
 */
class FavoritesScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Favourites"

    private lateinit var column: LinearLayout

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val scroll = ScrollView(context)
        scroll.setBackgroundColor(Widgets.color(context, R.color.bg))
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 8), Widgets.dp(context, 6),
            Widgets.dp(context, 8), Widgets.dp(context, 12)
        )
        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        refresh()
        return scroll
    }

    private fun refresh() {
        val context = host.activity
        column.removeAllViews()
        val favorites = Toolbox.store.favorites
        if (favorites.isEmpty()) {
            val empty = Widgets.column(context)
            empty.gravity = Gravity.CENTER
            val text = Widgets.text(context, context.getString(R.string.favorites_empty), R.style.Text_Caption)
            text.gravity = Gravity.CENTER
            text.setPadding(0, Widgets.dp(context, 40), 0, Widgets.dp(context, 40))
            empty.addView(text)
            column.addView(empty)
            return
        }
        for (favorite in favorites) {
            val tool = Tools.byId(favorite.toolId)
            val row = Widgets.row(context)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(
                Widgets.dp(context, 10), Widgets.dp(context, 8),
                Widgets.dp(context, 10), Widgets.dp(context, 8)
            )
            row.background = Widgets.roundedBackground(
                context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
            )
            row.isFocusable = true
            row.isClickable = true

            val icon = ImageView(context)
            icon.setImageResource(tool?.icon ?: R.drawable.ic_star)
            icon.setColorFilter(Widgets.color(context, R.color.accent))
            row.addView(icon, LinearLayout.LayoutParams(Widgets.dp(context, 22), Widgets.dp(context, 22)))
            row.addView(Widgets.spacer(context, 10))

            val textColumn = Widgets.column(context)
            textColumn.addView(Widgets.text(context, favorite.title, R.style.Text_Body))
            if (favorite.subtitle.isNotEmpty()) {
                textColumn.addView(Widgets.text(context, favorite.subtitle, R.style.Text_Caption))
            }
            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val remove = Widgets.button(context, context.getString(R.string.action_remove), secondary = true)
            remove.setOnClickListener {
                Toolbox.store.removeFavorite(favorite.id)
                refresh()
            }
            row.addView(remove, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            row.setOnClickListener {
                if (tool != null) {
                    Toolbox.settings.pushRecentTool(tool.id)
                    host.push(tool.factory(host))
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = Widgets.dp(context, 5)
            row.layoutParams = params
            column.addView(row)
        }
    }
}
