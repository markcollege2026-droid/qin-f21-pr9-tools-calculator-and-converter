package com.qin.f21.toolbox.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.Tools
import com.qin.f21.toolbox.core.data.Favorite

/**
 * Home screen: search, favourites, recent tools and the categorised tool list.
 *
 * Designed for the 480x640 screen of the Qin F21 Pro: a single scrolling column with
 * compact rows, every row reachable with the D-pad and activatable with Enter.
 */
class HomeScreen(host: ScreenHost) : Screen(host) {

    override val title: String = ""
    override val icon: String? = null

    private lateinit var list: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var scroll: ScrollView
    private var visibleTools: List<Tools.ToolDef> = emptyList()

    override fun onCreateView(parent: ViewGroup): View {
        val root = Widgets.column(host.activity)
        root.setBackgroundColor(Widgets.color(host.activity, R.color.bg))

        searchField = Widgets.editField(host.activity, host.activity.getString(R.string.search_hint))
        searchField.setSingleLine(true)
        searchField.isFocusable = true
        searchField.isFocusableInTouchMode = false
        searchField.inputType = android.text.InputType.TYPE_CLASS_TEXT
        searchField.setTextAppearance(host.activity, R.style.Widget_App_Field)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refresh()
            }
        })
        val searchParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        root.addView(searchField, searchParams)

        scroll = ScrollView(host.activity).forKeypad()
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Widgets.color(host.activity, R.color.bg))
        list = Widgets.column(host.activity)
        list.setPadding(
            Widgets.dp(host.activity, 8),
            Widgets.dp(host.activity, 6),
            Widgets.dp(host.activity, 8),
            Widgets.dp(host.activity, 16)
        )
        scroll.addView(list, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    override fun onShown() {
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val query = searchField.text?.toString() ?: ""
        val capabilities = Toolbox.capabilities.list()

        val favorites = Toolbox.store.favorites
        if (favorites.isNotEmpty() && query.isEmpty()) {
            list.addView(Widgets.sectionHeader(host.activity, host.activity.getString(R.string.section_favorites)))
            list.addView(favoriteStrip(favorites))
        }

        val recentIds = Toolbox.settings.recentTools().filter { id -> Tools.byId(id) != null }
        if (recentIds.isNotEmpty() && query.isEmpty()) {
            list.addView(Widgets.sectionHeader(host.activity, host.activity.getString(R.string.section_recent)))
            list.addView(recentStrip(recentIds.mapNotNull { Tools.byId(it) }))
        }

        if (query.isEmpty()) {
            for (category in Tools.categories) {
                val tools = Tools.inCategory(category.id)
                if (tools.isEmpty()) continue
                list.addView(Widgets.sectionHeader(host.activity, category.title))
                for (tool in tools) {
                    list.addView(toolRow(tool, capabilities))
                }
            }
            visibleTools = Tools.all
        } else {
            val results = Tools.search(query)
            visibleTools = results
            if (results.isEmpty()) {
                list.addView(emptyState(host.activity.getString(R.string.search_empty)))
            } else {
                list.addView(Widgets.sectionHeader(host.activity, host.activity.getString(R.string.search_results, results.size)))
                for (tool in results) {
                    list.addView(toolRow(tool, capabilities))
                }
            }
        }
    }

    private fun favoriteStrip(favorites: List<Favorite>): View {
        val scrollView = HorizontalScrollView(host.activity)
        scrollView.isHorizontalScrollBarEnabled = false
        val row = Widgets.row(host.activity)
        row.setPadding(0, 0, 0, 0)
        for (favorite in favorites) {
            val tool = Tools.byId(favorite.toolId)
            val tile = tile(
                title = favorite.title.ifEmpty { tool?.title ?: favorite.toolId },
                icon = tool?.icon ?: R.drawable.ic_star,
                onClick = { openTool(favorite.toolId, favorite.payload) }
            )
            row.addView(tile, LinearLayout.LayoutParams(
                Widgets.dp(host.activity, 96),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            row.addView(Widgets.spacer(host.activity, 8))
        }
        scrollView.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scrollView
    }

    private fun recentStrip(tools: List<Tools.ToolDef>): View {
        val scrollView = HorizontalScrollView(host.activity)
        scrollView.isHorizontalScrollBarEnabled = false
        val row = Widgets.row(host.activity)
        for (tool in tools) {
            val tile = tile(tool.title, tool.icon) { openTool(tool.id, "") }
            row.addView(tile, LinearLayout.LayoutParams(
                Widgets.dp(host.activity, 96),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            row.addView(Widgets.spacer(host.activity, 8))
        }
        scrollView.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scrollView
    }

    private fun tile(title: String, icon: Int, onClick: () -> Unit): View {
        val tile = LinearLayout(host.activity)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.background = Widgets.roundedBackground(
            host.activity,
            Widgets.color(host.activity, R.color.surface_alt),
            Widgets.color(host.activity, R.color.outline)
        )
        tile.isFocusable = true
        tile.isClickable = true
        val iconView = ImageView(host.activity)
        iconView.setImageResource(icon)
        iconView.setColorFilter(Widgets.color(host.activity, R.color.primary))
        iconView.layoutParams = LinearLayout.LayoutParams(
            Widgets.dp(host.activity, 24),
            Widgets.dp(host.activity, 24)
        )
        tile.addView(iconView)
        val label = Widgets.text(host.activity, title, R.style.Text_Caption)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        label.setPadding(0, Widgets.dp(host.activity, 4), 0, 0)
        tile.addView(label)
        tile.setOnClickListener { onClick() }
        return tile
    }

    private fun toolRow(tool: Tools.ToolDef, capabilities: List<com.qin.f21.toolbox.core.hardware.Capability>): View {
        val usable = Tools.isUsable(tool, capabilities)
        val capability = Tools.capabilityOf(tool, capabilities)

        val row = LinearLayout(host.activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(host.activity, 10),
            Widgets.dp(host.activity, 8),
            Widgets.dp(host.activity, 10),
            Widgets.dp(host.activity, 8)
        )
        row.background = Widgets.roundedBackground(
            host.activity,
            Widgets.color(host.activity, R.color.surface),
            Widgets.color(host.activity, R.color.outline)
        )
        row.isFocusable = true
        row.isClickable = true

        val iconHolder = android.widget.FrameLayout(host.activity)
        val icon = ImageView(host.activity)
        icon.setImageResource(tool.icon)
        icon.setColorFilter(
            Widgets.color(
                host.activity,
                if (usable) R.color.primary else R.color.text_muted
            )
        )
        val iconParams = android.widget.FrameLayout.LayoutParams(
            Widgets.dp(host.activity, 24),
            Widgets.dp(host.activity, 24)
        )
        iconParams.gravity = Gravity.CENTER
        iconHolder.addView(icon, iconParams)
        row.addView(iconHolder, LinearLayout.LayoutParams(
            Widgets.dp(host.activity, 36),
            Widgets.dp(host.activity, 36)
        ))
        row.addView(Widgets.spacer(host.activity, 10))

        val textColumn = Widgets.column(host.activity)
        val title = Widgets.text(host.activity, tool.title, R.style.Text_Body)
        title.setTextColor(
            Widgets.color(
                host.activity,
                if (usable) R.color.text_primary else R.color.text_muted
            )
        )
        textColumn.addView(title)
        val note = when {
            usable -> tool.description
            capability?.state == com.qin.f21.toolbox.core.hardware.SupportState.PERMISSION_REQUIRED ->
                host.activity.getString(R.string.tool_needs_permission)
            else -> host.activity.getString(R.string.tool_not_available)
        }
        val noteView = Widgets.text(host.activity, note, R.style.Text_Caption)
        noteView.maxLines = 2
        noteView.ellipsize = android.text.TextUtils.TruncateAt.END
        textColumn.addView(noteView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chevron = ImageView(host.activity)
        chevron.setImageResource(R.drawable.ic_back)
        chevron.setColorFilter(Widgets.color(host.activity, R.color.text_muted))
        chevron.rotation = 180f
        chevron.scaleX = -1f
        row.addView(chevron, LinearLayout.LayoutParams(
            Widgets.dp(host.activity, 16),
            Widgets.dp(host.activity, 16)
        ))

        row.setOnClickListener {
            if (usable) {
                openTool(tool.id, "")
            } else {
                host.showMessage(
                    capability?.detail?.ifEmpty { host.activity.getString(R.string.tool_not_available) }
                        ?: host.activity.getString(R.string.tool_not_available)
                )
            }
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(host.activity, 6)
        row.layoutParams = params
        return row
    }

    private fun emptyState(message: String): View {
        val column = Widgets.column(host.activity)
        column.gravity = Gravity.CENTER
        val text = Widgets.text(host.activity, message, R.style.Text_Caption)
        text.gravity = Gravity.CENTER
        text.setPadding(0, Widgets.dp(host.activity, 32), 0, Widgets.dp(host.activity, 32))
        column.addView(text)
        return column
    }

    private fun openTool(toolId: String, payload: String) {
        val tool = Tools.byId(toolId) ?: return
        Toolbox.settings.pushRecentTool(tool.id)
        host.push(tool.factory(host))
    }

    /**
     * Physical keypad shortcut: the digits 1-9 open the n-th visible tool.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // While the search field has the focus the digits belong to the search text.
        if (searchField.hasFocus()) return false
        if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
            val index = keyCode - KeyEvent.KEYCODE_1
            val tool = visibleTools.getOrNull(index)
            if (tool != null) {
                if (Tools.isUsable(tool, Toolbox.capabilities.list())) {
                    openTool(tool.id, "")
                } else {
                    host.showMessage(host.activity.getString(R.string.tool_not_available))
                }
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            searchField.requestFocus()
            return true
        }
        return false
    }
}

