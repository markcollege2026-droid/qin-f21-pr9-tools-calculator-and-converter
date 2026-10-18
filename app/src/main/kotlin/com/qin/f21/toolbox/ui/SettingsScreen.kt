package com.qin.f21.toolbox.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.BuildConfigCompat
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.common.JsonParser
import com.qin.f21.toolbox.core.common.Numbers

/**
 * Settings: screen behaviour, microphone calibration, offline exchange rates and the
 * local data management (export / import / clear).
 */
class SettingsScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Settings"

    private lateinit var column: LinearLayout

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val scroll = ScrollView(context).forKeypad()
        scroll.setBackgroundColor(Widgets.color(context, R.color.bg))
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 16)
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

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_display)))
        addToggle(
            context.getString(R.string.settings_keep_screen_on),
            context.getString(R.string.settings_keep_screen_on_hint),
            Toolbox.settings.keepScreenOn
        ) { value ->
            Toolbox.settings.keepScreenOn = value
            if (value) {
                host.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                host.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_audio)))
        val calibrationRow = Widgets.row(context)
        calibrationRow.gravity = Gravity.CENTER_VERTICAL
        val label = Widgets.text(context, context.getString(R.string.settings_calibration), R.style.Text_Label)
        label.setTextColor(Widgets.color(context, R.color.text_secondary))
        calibrationRow.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val field = Widgets.numericField(context, "94")
        field.setText(Toolbox.settings.micCalibrationDb.toString())
        calibrationRow.addView(field, LinearLayout.LayoutParams(Widgets.dp(context, 96), ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(calibrationRow)
        val saveCalibration = Widgets.button(context, context.getString(R.string.action_save), secondary = true)
        saveCalibration.setOnClickListener {
            val value = Numbers.parse(field.text?.toString() ?: "")
            if (value == null) {
                host.showMessage(context.getString(R.string.error_invalid_number, ""))
            } else {
                Toolbox.settings.micCalibrationDb = value
                host.showMessage(context.getString(R.string.settings_saved))
            }
        }
        column.addView(saveCalibration, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 6) })
        column.addView(Widgets.caption(context, context.getString(R.string.settings_calibration_hint)))

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_currency)))
        val codeRow = Widgets.row(context)
        val codeField = Widgets.editField(context, "EUR")
        codeField.inputType = android.text.InputType.TYPE_CLASS_TEXT
        codeRow.addView(codeField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rateField = Widgets.numericField(context, "0.92")
        codeRow.addView(rateField, LinearLayout.LayoutParams(Widgets.dp(context, 110), ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(codeRow)
        val rateActions = Widgets.row(context)
        val applyRate = Widgets.button(context, context.getString(R.string.action_update), secondary = true)
        applyRate.setOnClickListener {
            val code = (codeField.text?.toString() ?: "").trim().uppercase()
            val rate = Numbers.parse(rateField.text?.toString() ?: "")
            if (code.isEmpty() || rate == null || rate <= 0.0) {
                host.showMessage(context.getString(R.string.settings_rate_invalid))
            } else {
                val overrides = HashMap(Toolbox.settings.currencyOverrides)
                overrides[code] = rate
                Toolbox.settings.currencyOverrides = overrides
                Toolbox.reloadCurrency()
                host.showMessage(context.getString(R.string.settings_rate_saved, code))
                refresh()
            }
        }
        rateActions.addView(applyRate, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rateActions.addView(Widgets.spacer(context, 6))
        val resetRates = Widgets.button(context, context.getString(R.string.action_reset_rates), secondary = true)
        resetRates.setOnClickListener {
            Toolbox.settings.currencyOverrides = emptyMap()
            Toolbox.reloadCurrency()
            host.showMessage(context.getString(R.string.settings_rates_reset))
            refresh()
        }
        rateActions.addView(resetRates, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(rateActions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 6) })
        val overrides = Toolbox.settings.currencyOverrides
        if (overrides.isNotEmpty()) {
            column.addView(Widgets.caption(
                context,
                context.getString(
                    R.string.settings_rate_overrides,
                    overrides.entries.joinToString(", ") { "${it.key}=${Numbers.format(it.value)}" }
                )
            ))
        } else {
            column.addView(Widgets.caption(context, context.getString(R.string.settings_rate_none)))
        }

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_data)))
        addAction(context.getString(R.string.settings_import), context.getString(R.string.settings_import_hint)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            host.activity.startActivityForResult(intent, REQUEST_IMPORT)
        }
        addAction(context.getString(R.string.settings_clear_history), context.getString(R.string.settings_clear_history_hint)) {
            Toolbox.store.clearHistory()
            host.showMessage(context.getString(R.string.history_cleared))
        }
        addAction(context.getString(R.string.settings_clear_favorites), context.getString(R.string.settings_clear_favorites_hint)) {
            for (favorite in Toolbox.store.favorites.toList()) {
                Toolbox.store.removeFavorite(favorite.id)
            }
            host.showMessage(context.getString(R.string.favorites_cleared))
        }

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.settings_about)))
        column.addView(Widgets.text(
            context,
            context.getString(R.string.settings_version, BuildConfigCompat.VERSION_NAME),
            R.style.Text_Body
        ))
        column.addView(Widgets.caption(context, context.getString(R.string.settings_offline_note)))
    }

    private fun addToggle(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val context = host.activity
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 8)
        )
        row.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        val textColumn = Widgets.column(context)
        textColumn.addView(Widgets.text(context, title, R.style.Text_Body))
        textColumn.addView(Widgets.text(context, hint, R.style.Text_Caption))
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = android.widget.Switch(context)
        toggle.isChecked = value
        toggle.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(toggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 5)
        row.layoutParams = params
        column.addView(row)
    }

    private fun addAction(title: String, hint: String, onClick: () -> Unit) {
        val context = host.activity
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 8)
        )
        row.background = Widgets.focusBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        row.isFocusable = true
        row.isClickable = true
        val textColumn = Widgets.column(context)
        textColumn.addView(Widgets.text(context, title, R.style.Text_Body))
        textColumn.addView(Widgets.text(context, hint, R.style.Text_Caption))
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 5)
        row.layoutParams = params
        column.addView(row)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_IMPORT) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri: Uri = data?.data ?: return true
        return try {
            val text = host.activity.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: ""
            val json = JsonParser.parse(text)
            val result = Toolbox.store.importDocument(json, merge = true)
            if (result.error != null) {
                host.showMessage(result.error)
            } else {
                host.showMessage(
                    host.activity.getString(
                        R.string.settings_imported, result.historyAdded, result.favoritesAdded
                    )
                )
                refresh()
            }
            true
        } catch (e: Exception) {
            host.showMessage(host.activity.getString(R.string.settings_import_failed))
            true
        }
    }

    companion object {
        const val REQUEST_IMPORT = 2001
    }
}
