package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.conversion.TimeZones
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * World clock. The conversion uses the IANA time zone database that ships with Android,
 * so it works offline and handles daylight saving correctly.
 */
class TimeZoneScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Time zones"

    private var fromZone: ZoneId = ZoneId.systemDefault()
    private var toZone: ZoneId = TimeZones.zone("Europe/London")
    private var fromIndex = 0
    private var toIndex = 0

    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var fromTime: TextView
    private lateinit var toTime: TextView
    private lateinit var fromDateLabel: TextView
    private lateinit var toDateLabel: TextView
    private lateinit var offsetLabel: TextView
    private lateinit var differenceLabel: TextView
    private lateinit var listColumn: LinearLayout

    /** The curated zones, with the zone of the device always at the top. */
    private val zoneNames: List<String> by lazy {
        val system = java.time.ZoneId.systemDefault().id
        if (system in TimeZones.curated) TimeZones.curated else listOf(system) + TimeZones.curated
    }

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = android.widget.ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        val pickerRow = Widgets.row(context)
        fromSpinner = spinner()
        toSpinner = spinner()
        fromSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fromIndex = position
                fromZone = TimeZones.zone(zoneNames.getOrNull(position) ?: "UTC")
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        toSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                toIndex = position
                toZone = TimeZones.zone(zoneNames.getOrNull(position) ?: "UTC")
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        pickerRow.addView(fromSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pickerRow.addView(Widgets.spacer(context, 6))
        pickerRow.addView(toSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(pickerRow)

        val fromCard = zoneCard(context, context.getString(R.string.tz_from))
        fromTime = fromCard.time
        fromDateLabel = fromCard.date
        column.addView(fromCard.root)

        val toCard = zoneCard(context, context.getString(R.string.tz_to))
        toTime = toCard.time
        toDateLabel = toCard.date
        column.addView(toCard.root)

        offsetLabel = Widgets.text(context, "", R.style.Text_Caption)
        column.addView(offsetLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 8) })

        differenceLabel = Widgets.text(context, "", R.style.Text_Label)
        differenceLabel.setTextColor(Widgets.color(context, R.color.accent))
        column.addView(differenceLabel)

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.tz_other_zones)))
        listColumn = Widgets.column(context)
        column.addView(listColumn)

        scroll.addView(column, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        fromSpinner.adapter = ArrayAdapter(context, R.layout.item_spinner, R.id.text1, zoneNames)
        toSpinner.adapter = ArrayAdapter(context, R.layout.item_spinner, R.id.text1, zoneNames)
        fromIndex = zoneNames.indexOf(TimeZones.zone(ZoneId.systemDefault().id).id).coerceAtLeast(0)
        toIndex = zoneNames.indexOf("Europe/London").coerceAtLeast(0)
        fromSpinner.setSelection(fromIndex)
        toSpinner.setSelection(toIndex)
        fromZone = TimeZones.zone(zoneNames.getOrNull(fromIndex) ?: "UTC")
        toZone = TimeZones.zone(zoneNames.getOrNull(toIndex) ?: "UTC")
        update()
        return root
    }

    private class ZoneCard(val root: View, val time: TextView, val date: TextView)

    private fun zoneCard(context: android.content.Context, label: String): ZoneCard {
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 12), Widgets.dp(context, 8)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = Widgets.dp(context, 8)
        card.layoutParams = params
        val caption = Widgets.caption(context, label)
        card.addView(caption)
        val time = Widgets.text(context, "--:--", R.style.Text_Title)
        time.setTextColor(Widgets.color(context, R.color.primary))
        card.addView(time)
        val date = Widgets.caption(context, "")
        card.addView(date)
        return ZoneCard(card, time, date)
    }

    private fun spinner(): Spinner {
        val spinner = Spinner(host.activity)
        spinner.setBackground(Widgets.roundedBackground(
            host.activity,
            Widgets.color(host.activity, R.color.surface),
            Widgets.color(host.activity, R.color.outline),
            6
        ))
        return spinner
    }

    private fun update() {
        val now = Instant.now()
        val fromDate = TimeZones.convert(now, fromZone, fromZone)
        val toDate = TimeZones.convert(now, fromZone, toZone)
        fromTime.text = fromDate.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        toTime.text = toDate.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        fromDateLabel.text = fromDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US))
        toDateLabel.text = toDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US))
        offsetLabel.text = host.activity.getString(
            R.string.tz_offsets,
            TimeZones.formatOffset(fromZone, now),
            TimeZones.formatOffset(toZone, now)
        )
        val minutes = java.time.Duration.between(fromDate, toDate).toMinutes()
        differenceLabel.text = if (minutes == 0L) {
            host.activity.getString(R.string.tz_same)
        } else {
            val sign = if (minutes > 0) "+" else "-"
            val abs = kotlin.math.abs(minutes)
            host.activity.getString(
                R.string.tz_difference,
                sign,
                abs / 60,
                abs % 60
            )
        }
        updateWorldList(now)
    }

    private fun updateWorldList(now: Instant) {
        listColumn.removeAllViews()
        for (name in zoneNames) {
            val zone = TimeZones.zone(name)
            val date = TimeZones.convert(now, fromZone, zone)
            val row = Widgets.row(context())
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(
                Widgets.dp(context(), 10), Widgets.dp(context(), 6),
                Widgets.dp(context(), 10), Widgets.dp(context(), 6)
            )
            row.background = Widgets.focusBackground(
                context(), Widgets.color(context(), R.color.surface), Widgets.color(context(), R.color.outline)
            )
            val nameView = Widgets.text(context(), name.replace('_', ' '), R.style.Text_Label)
            row.addView(nameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val timeView = Widgets.text(
                context(),
                date.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)),
                R.style.Text_Label
            )
            timeView.setTextColor(Widgets.color(context(), R.color.text_secondary))
            row.addView(timeView)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = Widgets.dp(context(), 4)
            row.layoutParams = params
            row.isFocusable = true
            row.isClickable = true
            row.setOnClickListener {
                toIndex = zoneNames.indexOf(name).coerceAtLeast(0)
                toSpinner.setSelection(toIndex)
                toZone = zone
                update()
            }
            listColumn.addView(row)
        }
    }

    private fun context() = host.activity
}
