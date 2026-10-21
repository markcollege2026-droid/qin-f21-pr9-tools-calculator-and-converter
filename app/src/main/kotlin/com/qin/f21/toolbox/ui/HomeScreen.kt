package com.qin.f21.toolbox.ui

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.Tools
import com.qin.f21.toolbox.core.data.Favorite

/**
 * Home screen.
 *
 * Layout, top to bottom:
 *   1. a pill search field (the fastest way into 21 tools),
 *   2. "Quick access" - the four tools that are opened most often,
 *   3. pinned tools and recently used tools, as horizontal strips,
 *   4. the full catalogue, grouped by category, each row a 56 dp card.
 *
 * Everything is reachable with the D-pad and confirmed with the centre key; the digits
 * 1-9 jump straight into the n-th listed tool.
 */
class HomeScreen(host: ScreenHost) : Screen(host) {

    override val title: String = host.activity.getString(R.string.home_title)
    override val subtitle: String = host.activity.getString(R.string.home_subtitle, Tools.all.size)
    override val icon: String? = null

    private lateinit var list: LinearLayout
    private lateinit var searchField: EditText
    private var visibleTools: List<Tools.ToolDef> = emptyList()

    private val quickIds = listOf("calculator", "converter", "currency", "stopwatch")

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        searchField = Widgets.editField(context, context.getString(R.string.search_hint))
        searchField.setSingleLine(true)
        searchField.isFocusable = true
        searchField.isFocusableInTouchMode = false
        searchField.inputType = android.text.InputType.TYPE_CLASS_TEXT
        searchField.setTextAppearance(context, R.style.Widget_App_Field)
        searchField.setBackgroundResource(R.drawable.bg_search)
        searchField.setPadding(
            Widgets.dp(context, 14), 0, Widgets.dp(context, 14), 0
        )
        searchField.minimumHeight = Widgets.dp(context, 44)
        val icon = context.getDrawable(R.drawable.ic_search)?.mutate()
        icon?.setTint(Widgets.color(context, R.color.text_muted))
        searchField.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        searchField.compoundDrawablePadding = Widgets.dp(context, 10)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refresh()
            }
        })
        val searchParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Widgets.dp(context, 44)
        )
        searchParams.setMargins(
            Widgets.dp(context, 12), Widgets.dp(context, 12), Widgets.dp(context, 12), 0
        )
        root.addView(searchField, searchParams)

        val scroll = ScrollView(context).forKeypad()
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        list = Widgets.column(context)
        list.setPadding(
            Widgets.dp(context, 12), 0, Widgets.dp(context, 12), Widgets.dp(context, 24)
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

    // ---- content -----------------------------------------------------------------

    private fun refresh() {
        list.removeAllViews()
        val query = searchField.text?.toString()?.trim() ?: ""
        val capabilities = Toolbox.capabilities.list()

        if (query.isEmpty()) {
            val quick = quickIds.mapNotNull { Tools.byId(it) }
            if (quick.isNotEmpty()) {
                list.addView(Widgets.sectionHeader(host.activity, host.activity.getString(R.string.section_quick)))
                list.addView(strip(quick.map { tool ->
                    tile(tool.title, tool.icon, true) { openTool(tool.id) }
                }))
            }

            val favorites = Toolbox.store.favorites
            if (favorites.isNotEmpty()) {
                list.addView(Widgets.sectionHeader(host.activity, host.activity.getString(R.string.section_favorites)))
                list.addView(strip(favorites.map { favorite ->
                    val tool = Tools.byId(favorite.toolId)
                    tile(
                        favorite.title.ifEmpty { tool?.title ?: favorite.toolId },
                        tool?.icon ?: R.drawable.ic_star,
                        false
                    ) { openTool(favorite.toolId) }
                }))
            }

            val recent = Toolbox.settings.recentTools()
                .filter { Tools.byId(it) != null }
                .distinct()
                .take(6)
                .mapNotNull { Tools.byId(it) }
            if (recent.isNotEmpty()) {
                list.addView(Widgets.sectionHeader(host.activity, host.activity.getString(R.string.section_recent)))
                list.addView(strip(recent.map { tool ->
                    tile(tool.title, tool.icon, false) { openTool(tool.id) }
                }))
            }

            for (category in Tools.categories) {
                val tools = Tools.inCategory(category.id)
                if (tools.isEmpty()) continue
                list.addView(Widgets.sectionHeader(host.activity, category.title, tools.size))
                for (tool in tools) {
                    list.addView(toolRow(tool, capabilities))
                }
            }
            visibleTools = Tools.all
        } else {
            val results = Tools.search(query)
            visibleTools = results
            if (results.isEmpty()) {
                list.addView(space(8))
                list.addView(Widgets.emptyState(host.activity, host.activity.getString(R.string.search_empty)))
            } else {
                list.addView(Widgets.sectionHeader(
                    host.activity,
                    host.activity.getString(R.string.search_results, results.size)
                ))
                for (tool in results) {
                    list.addView(toolRow(tool, capabilities))
                }
            }
        }
    }

    private fun space(dp: Int): View = Widgets.spacer(host.activity, 0, dp)

    /** A horizontally scrolling strip of tiles, gap 8 dp. */
    private fun strip(tiles: List<View>): View {
        val scrollView = HorizontalScrollView(host.activity)
        scrollView.isHorizontalScrollBarEnabled = false
        scrollView.clipToPadding = false
        val row = Widgets.row(host.activity)
        row.setPadding(0, 0, Widgets.dp(host.activity, 4), 0)
        for (tile in tiles) {
            row.addView(tile)
            row.addView(Widgets.spacer(host.activity, 8))
        }
        scrollView.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scrollView
    }

    /** 76 x 72 dp tile: icon above a one-line label, on the standard card surface. */
    private fun tile(title: String, icon: Int, accent: Boolean, onClick: () -> Unit): View {
        val context = host.activity
        val tile = LinearLayout(context)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.background = context.getDrawable(R.drawable.bg_tile)
        tile.isFocusable = true
        tile.isClickable = true
        tile.setPadding(Widgets.dp(context, 6), Widgets.dp(context, 8), Widgets.dp(context, 6), Widgets.dp(context, 8))
        tile.layoutParams = LinearLayout.LayoutParams(Widgets.dp(context, 76), Widgets.dp(context, 72))

        val iconView = ImageView(context)
        iconView.setImageResource(icon)
        iconView.setColorFilter(
            Widgets.color(context, if (accent) R.color.primary else R.color.text_secondary)
        )
        iconView.layoutParams = LinearLayout.LayoutParams(
            Widgets.dp(context, 22),
            Widgets.dp(context, 22)
        )
        tile.addView(iconView)

        val label = Widgets.text(
            context,
            title,
            if (accent) R.style.Text_Label else R.style.Text_Caption
        )
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        label.setTextColor(
            Widgets.color(context, if (accent) R.color.text_primary else R.color.text_secondary)
        )
        label.setPadding(0, Widgets.dp(context, 6), 0, 0)
        tile.addView(label)

        tile.setOnClickListener { onClick() }
        return tile
    }

    private fun toolRow(
        tool: Tools.ToolDef,
        capabilities: List<com.qin.f21.toolbox.core.hardware.Capability>
    ): View {
        val usable = Tools.isUsable(tool, capabilities)
        val capability = Tools.capabilityOf(tool, capabilities)
        val note = when {
            usable -> tool.description
            capability?.state == com.qin.f21.toolbox.core.hardware.SupportState.PERMISSION_REQUIRED ->
                host.activity.getString(R.string.tool_needs_permission)
            else -> host.activity.getString(R.string.tool_not_available)
        }
        val row = Widgets.listRow(
            host.activity,
            tool.title,
            note,
            iconRes = tool.icon,
            iconTint = if (usable) R.color.text_secondary else R.color.text_muted,
            titleColor = if (usable) R.color.text_primary else R.color.text_muted,
            enabled = usable
        ) {
            if (usable) {
                openTool(tool.id)
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
        params.bottomMargin = Widgets.dp(host.activity, 8)
        row.layoutParams = params
        return row
    }

    private fun openTool(toolId: String) {
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
                    openTool(tool.id)
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
        if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            searchField.setText("")
            return true
        }
        return false
    }
}
