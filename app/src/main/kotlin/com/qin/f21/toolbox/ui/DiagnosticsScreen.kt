package com.qin.f21.toolbox.ui

import android.hardware.Sensor
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.hardware.DeviceInfo
import com.qin.f21.toolbox.core.hardware.SupportState

/**
 * Device diagnostics: hardware, memory, storage, display, battery, sensors and network.
 * Every value is read from the platform APIs at the moment the screen is shown.
 */
class DiagnosticsScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Diagnostics"

    private val context: android.content.Context get() = host.activity

    private val info = DeviceInfo(host.activity)
    private lateinit var column: LinearLayout

    override fun onCreateView(parent: ViewGroup): View {
                val scroll = ScrollView(context).forKeypad()
        scroll.setBackgroundColor(Widgets.color(context, R.color.bg))
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 4),
            Widgets.dp(context, 12), Widgets.dp(context, 24)
        )
        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        refresh()
        return scroll
    }

    private fun refresh() {
                column.removeAllViews()

        addSection(host.activity.getString(R.string.diag_device))
        info.deviceRows().forEach { addRow(it.label, it.value, it.note) }

        addSection(host.activity.getString(R.string.diag_display))
        info.displayRows().forEach { addRow(it.label, it.value, it.note) }

        addSection(host.activity.getString(R.string.diag_memory))
        info.memoryRows().forEach { addRow(it.label, it.value, it.note) }

        addSection(host.activity.getString(R.string.diag_storage))
        info.storageRows().forEach { addRow(it.label, it.value, it.note) }

        addSection(host.activity.getString(R.string.diag_battery))
        info.batteryRows().forEach { addRow(it.label, it.value, it.note) }

        addSection(host.activity.getString(R.string.diag_sensors))
        val capabilities = Toolbox.capabilities
        capabilities.list().forEach { capability ->
            val stateText = when (capability.state) {
                SupportState.AVAILABLE -> host.activity.getString(R.string.diag_available)
                SupportState.NOT_PRESENT -> host.activity.getString(R.string.diag_not_present)
                SupportState.PERMISSION_REQUIRED -> host.activity.getString(R.string.diag_permission)
                SupportState.DISABLED -> host.activity.getString(R.string.diag_disabled)
            }
            addRow(capability.title, stateText, capability.detail, stateColor(capability.state))
        }
        if (capabilities.sensorInventory.isEmpty()) {
            addRow(host.activity.getString(R.string.diag_no_sensors), "—", "")
        } else {
            for (sensor in capabilities.sensorInventory) {
                addRow(
                    sensor.name,
                    sensorTypeName(sensor),
                    "${sensor.vendor} · ${sensor.power}mAh · ${sensor.minDelay}µs"
                )
            }
        }

        addSection(host.activity.getString(R.string.diag_network))
        info.networkRows().forEach { addRow(it.label, it.value, it.note) }
    }

    private fun stateColor(state: SupportState): Int = when (state) {
        SupportState.AVAILABLE -> R.color.success
        SupportState.PERMISSION_REQUIRED -> R.color.accent
        SupportState.DISABLED -> R.color.danger
        SupportState.NOT_PRESENT -> R.color.text_muted
    }

    private fun sensorTypeName(sensor: Sensor): String = when (sensor.type) {
        Sensor.TYPE_ACCELEROMETER -> host.activity.getString(R.string.sensor_accelerometer)
        Sensor.TYPE_GYROSCOPE -> host.activity.getString(R.string.sensor_gyroscope)
        Sensor.TYPE_MAGNETIC_FIELD -> host.activity.getString(R.string.sensor_magnetometer)
        Sensor.TYPE_LIGHT -> host.activity.getString(R.string.sensor_light)
        Sensor.TYPE_PROXIMITY -> host.activity.getString(R.string.sensor_proximity)
        Sensor.TYPE_PRESSURE -> host.activity.getString(R.string.sensor_pressure)
        Sensor.TYPE_STEP_COUNTER -> host.activity.getString(R.string.sensor_step_counter)
        Sensor.TYPE_HEART_RATE -> host.activity.getString(R.string.sensor_heart_rate)
        else -> host.activity.getString(R.string.sensor_other)
    }

    private fun addSection(title: String) {
        column.addView(Widgets.sectionHeader(host.activity, title))
    }

    /**
     * One diagnostic reading: the key and its note on the left, the value right-aligned in
     * monospace so the numbers form a clean column down the screen.
     */
    private fun addRow(label: String, value: String, note: String = "", valueColor: Int = R.color.text_primary) {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 10),
            Widgets.dp(context, 12), Widgets.dp(context, 10)
        )
        row.minimumHeight = Widgets.dp(context, 52)
        row.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline), 12
        )
        val textColumn = Widgets.column(context)
        val labelView = Widgets.text(context, label, R.style.Text_Label)
        labelView.setTextColor(Widgets.color(context, R.color.text_secondary))
        textColumn.addView(labelView)
        if (note.isNotEmpty()) {
            val noteView = Widgets.text(context, note, R.style.Text_Caption)
            noteView.maxLines = 2
            noteView.setPadding(0, Widgets.dp(context, 2), 0, 0)
            textColumn.addView(noteView)
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = Widgets.text(context, value, R.style.Text_Mono)
        valueView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        valueView.setTextColor(Widgets.color(context, valueColor))
        valueView.gravity = Gravity.END
        valueView.maxLines = 2
        row.addView(valueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = Widgets.dp(context, 12) })
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 8)
        row.layoutParams = params
        column.addView(row)
    }

    override fun onShown() {
        refresh()
    }
}
