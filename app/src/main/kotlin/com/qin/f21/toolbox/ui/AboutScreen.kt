package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.BuildConfigCompat
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.hardware.SupportState

/**
 * About page: what the app does, what it deliberately does not do, which hardware of the
 * current device was detected and the keyboard shortcuts.
 */
class AboutScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "About"

    private lateinit var column: LinearLayout

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val scroll = ScrollView(context)
        scroll.setBackgroundColor(Widgets.color(context, R.color.bg))
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 16)
        )
        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        build()
        return scroll
    }

    private fun build() {
        val context = host.activity
        val title = Widgets.text(
            context,
            context.getString(R.string.app_name),
            R.style.Text_Title
        )
        title.gravity = Gravity.CENTER
        column.addView(title)
        val version = Widgets.caption(
            context,
            context.getString(R.string.about_version, BuildConfigCompat.VERSION_NAME)
        )
        version.gravity = Gravity.CENTER
        column.addView(version)
        val tagline = Widgets.caption(context, context.getString(R.string.about_tagline))
        tagline.gravity = Gravity.CENTER
        column.addView(tagline)

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_offline_title)))
        column.addView(Widgets.text(context, context.getString(R.string.about_offline_body), R.style.Text_Caption))

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_hardware_title)))
        for (capability in Toolbox.capabilities.list()) {
            val row = Widgets.row(context)
            row.gravity = Gravity.CENTER_VERTICAL
            val name = Widgets.text(context, capability.title, R.style.Text_Label)
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val value = Widgets.text(
                context,
                when (capability.state) {
                    SupportState.AVAILABLE -> context.getString(R.string.diag_available)
                    SupportState.NOT_PRESENT -> context.getString(R.string.diag_not_present)
                    SupportState.PERMISSION_REQUIRED -> context.getString(R.string.diag_permission)
                    SupportState.DISABLED -> context.getString(R.string.diag_disabled)
                },
                R.style.Text_Caption
            )
            value.setTextColor(
                Widgets.color(
                    context,
                    when (capability.state) {
                        SupportState.AVAILABLE -> R.color.success
                        SupportState.PERMISSION_REQUIRED -> R.color.accent
                        SupportState.DISABLED -> R.color.danger
                        SupportState.NOT_PRESENT -> R.color.text_muted
                    }
                )
            )
            row.addView(value)
            column.addView(row)
        }

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_keys_title)))
        val shortcuts = listOf(
            context.getString(R.string.about_keys_dpad),
            context.getString(R.string.about_keys_enter),
            context.getString(R.string.about_keys_numbers),
            context.getString(R.string.about_keys_back),
            context.getString(R.string.about_keys_star),
            context.getString(R.string.about_keys_hash)
        )
        for (shortcut in shortcuts) {
            column.addView(Widgets.caption(context, "•  $shortcut"))
        }

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_licenses_title)))
        column.addView(Widgets.caption(context, context.getString(R.string.about_licenses_body)))
    }
}
