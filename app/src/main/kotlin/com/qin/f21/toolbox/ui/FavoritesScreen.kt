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
        val scroll = ScrollView(context).forKeypad()
        scroll.setBackgroundColor(Widgets.color(context, R.color.bg))
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 12),
            Widgets.dp(context, 12), Widgets.dp(context, 24)
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
            column.addView(Widgets.emptyState(context, context.getString(R.string.favorites_empty)))
            return
        }
        for (favorite in favorites) {
            val tool = Tools.byId(favorite.toolId)
            val row = Widgets.listRow(
                context,
                favorite.title,
                favorite.subtitle.ifEmpty { tool?.description ?: "" },
                iconRes = tool?.icon ?: R.drawable.ic_star,
                iconTint = R.color.accent,
                showChevron = false
            ) {
                if (tool != null) {
                    Toolbox.settings.pushRecentTool(tool.id)
                    host.push(tool.factory(host))
                }
            }

            val remove = ImageView(context)
            remove.setImageResource(R.drawable.ic_close)
            remove.setColorFilter(Widgets.color(context, R.color.text_muted))
            remove.setPadding(
                Widgets.dp(context, 10), Widgets.dp(context, 10),
                Widgets.dp(context, 10), Widgets.dp(context, 10)
            )
            remove.contentDescription = context.getString(R.string.action_remove)
            remove.isFocusable = true
            remove.isClickable = true
            remove.background = Widgets.roundedBackground(context, 0x00000000, 0, 10)
            remove.setOnClickListener {
                Toolbox.store.removeFavorite(favorite.id)
                refresh()
            }
            row.addView(remove, LinearLayout.LayoutParams(
                Widgets.dp(context, 36), Widgets.dp(context, 36)
            ).apply { leftMargin = Widgets.dp(context, 6) })

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = Widgets.dp(context, 8)
            row.layoutParams = params
            column.addView(row)
        }
    }
}
