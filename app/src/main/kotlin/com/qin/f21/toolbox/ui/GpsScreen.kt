package com.qin.f21.toolbox.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.hardware.LocationTracker
import java.util.Locale

/**
 * GPS / GNSS information.
 *
 * Uses the platform location manager only, so it works without Google Play services and
 * without any network access.
 */
class GpsScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "GPS"

    private var tracker: LocationTracker? = null
    private lateinit var coordinateLabel: TextView
    private lateinit var detailLabel: TextView
    private lateinit var satelliteLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var actionRow: LinearLayout

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context)
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        val card = Widgets.column(context)
        card.gravity = Gravity.CENTER
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 16),
            Widgets.dp(context, 12), Widgets.dp(context, 16)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        coordinateLabel = Widgets.text(context, "—", R.style.Text_Display)
        coordinateLabel.gravity = Gravity.CENTER
        coordinateLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
        card.addView(coordinateLabel)
        detailLabel = Widgets.caption(context, context.getString(R.string.gps_waiting))
        detailLabel.gravity = Gravity.CENTER
        detailLabel.setPadding(0, Widgets.dp(context, 8), 0, 0)
        card.addView(detailLabel)
        satelliteLabel = Widgets.caption(context, "")
        satelliteLabel.gravity = Gravity.CENTER
        card.addView(satelliteLabel)
        column.addView(card)

        statusLabel = Widgets.caption(context, "")
        column.addView(statusLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 8) })

        actionRow = Widgets.row(context)
        column.addView(actionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 8) })

        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        if (!Toolbox.capabilities.hasGpsProvider) {
            statusLabel.text = context.getString(R.string.gps_unavailable)
            return root
        }
        if (!Toolbox.capabilities.hasLocationPermission) {
            val button = Widgets.button(context, context.getString(R.string.action_grant_permission))
            button.setOnClickListener {
                host.askPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQUEST_LOCATION
                )
            }
            actionRow.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            statusLabel.text = context.getString(R.string.gps_permission_needed)
            return root
        }
        startTracking()
        return root
    }

    private fun startTracking() {
        val context = host.activity
        val localTracker = LocationTracker(context, object : LocationTracker.Listener {
            override fun onLocationChanged(location: Location, satellitesInView: Int, satellitesUsed: Int) {
                showLocation(location, satellitesInView, satellitesUsed)
            }

            override fun onProviderState(provider: String, enabled: Boolean) {
                statusLabel.text = context.getString(
                    if (enabled) R.string.gps_provider_on else R.string.gps_provider_off,
                    provider
                )
            }
        })
        tracker = localTracker
        if (localTracker.start()) {
            statusLabel.text = context.getString(R.string.gps_searching)
        } else {
            statusLabel.text = context.getString(R.string.gps_unavailable)
        }
        localTracker.lastKnownLocation()?.let { showLocation(it, 0, 0) }
    }

    private fun showLocation(location: Location, inView: Int, used: Int) {
        val context = host.activity
        coordinateLabel.text = String.format(
            Locale.US, "%.6f\n%.6f", location.latitude, location.longitude
        )
        val parts = ArrayList<String>()
        if (location.hasAltitude()) parts.add(context.getString(R.string.gps_altitude, location.altitude))
        if (location.hasSpeed()) parts.add(context.getString(R.string.gps_speed, location.speed * 3.6))
        if (location.hasBearing()) parts.add(context.getString(R.string.gps_bearing, location.bearing))
        if (location.hasAccuracy()) parts.add(context.getString(R.string.gps_accuracy, location.accuracy))
        parts.add(context.getString(R.string.gps_time, java.text.DateFormat.getTimeInstance().format(java.util.Date(location.time))))
        detailLabel.text = parts.joinToString("  ·  ")
        satelliteLabel.text = context.getString(R.string.gps_satellites, used, inView)

        if (actionRow.childCount == 0) {
            val copy = Widgets.button(context, context.getString(R.string.action_copy))
            copy.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText(
                        "location",
                        String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
                    )
                )
                host.showMessage(context.getString(R.string.gps_copied))
            }
            actionRow.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    override fun onHidden() {
        tracker?.stop()
    }

    override fun onShown() {
        if (Toolbox.capabilities.hasLocationPermission && tracker == null) {
            startTracking()
        }
    }

    companion object {
        const val REQUEST_LOCATION = 1001
    }
}
