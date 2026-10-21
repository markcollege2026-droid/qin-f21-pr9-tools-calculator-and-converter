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
        build()
        return scroll
    }

    private fun build() {
        val context = host.activity

        // Hero: name, version and the one-line promise, on a lime-tinted card.
        val hero = Widgets.column(context)
        hero.setPadding(
            Widgets.dp(context, 16), Widgets.dp(context, 16),
            Widgets.dp(context, 16), Widgets.dp(context, 16)
        )
        hero.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.primary_dim), Widgets.color(context, R.color.primary), 16
        )
        val name = Widgets.text(context, context.getString(R.string.app_name), R.style.Text_Title)
        name.setTextColor(Widgets.color(context, R.color.primary))
        hero.addView(name)
        val tagline = Widgets.text(context, context.getString(R.string.about_tagline), R.style.Text_Label)
        tagline.setTextColor(Widgets.color(context, R.color.text_primary))
        tagline.setPadding(0, Widgets.dp(context, 6), 0, 0)
        hero.addView(tagline)
        val meta = Widgets.text(
            context,
            context.getString(R.string.about_version, BuildConfigCompat.VERSION_NAME) + " · " +
                context.getString(R.string.about_min_os),
            R.style.Text_Caption
        )
        meta.setTextColor(Widgets.color(context, R.color.text_secondary))
        meta.setPadding(0, Widgets.dp(context, 8), 0, 0)
        hero.addView(meta)
        val heroParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        heroParams.bottomMargin = Widgets.dp(context, 8)
        hero.layoutParams = heroParams
        column.addView(hero)

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_offline_title)))
        val offline = Widgets.card(context)
        offline.addView(Widgets.text(context, context.getString(R.string.about_offline_body), R.style.Text_Caption))
        column.addView(offline)

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_hardware_title)))
        for (capability in Toolbox.capabilities.list()) {
            val row = Widgets.row(context)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(Widgets.dp(context, 4), Widgets.dp(context, 6), Widgets.dp(context, 4), Widgets.dp(context, 6))
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
            val line = Widgets.text(context, shortcut, R.style.Text_Caption)
            line.setPadding(Widgets.dp(context, 4), Widgets.dp(context, 4), 0, Widgets.dp(context, 4))
            column.addView(line)
        }

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.about_licenses_title)))
        val licenses = Widgets.card(context)
        licenses.addView(Widgets.text(context, context.getString(R.string.about_licenses_body), R.style.Text_Caption))
        column.addView(licenses)
    }
}
