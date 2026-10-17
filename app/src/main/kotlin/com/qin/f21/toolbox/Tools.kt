package com.qin.f21.toolbox

import com.qin.f21.toolbox.core.hardware.Capability
import com.qin.f21.toolbox.core.hardware.SupportState
import com.qin.f21.toolbox.ui.AboutScreen
import com.qin.f21.toolbox.ui.CalculatorScreen
import com.qin.f21.toolbox.ui.CameraScreen
import com.qin.f21.toolbox.ui.ConverterScreen
import com.qin.f21.toolbox.ui.CurrencyScreen
import com.qin.f21.toolbox.ui.DateCalculatorScreen
import com.qin.f21.toolbox.ui.DiagnosticsScreen
import com.qin.f21.toolbox.ui.FavoritesScreen
import com.qin.f21.toolbox.ui.FlashlightScreen
import com.qin.f21.toolbox.ui.GeometryScreen
import com.qin.f21.toolbox.ui.GpsScreen
import com.qin.f21.toolbox.ui.HistoryScreen
import com.qin.f21.toolbox.ui.InfraredScreen
import com.qin.f21.toolbox.ui.QuickCalculatorScreen
import com.qin.f21.toolbox.ui.RecorderScreen
import com.qin.f21.toolbox.ui.ScannerScreen
import com.qin.f21.toolbox.ui.Screen
import com.qin.f21.toolbox.ui.ScreenHost
import com.qin.f21.toolbox.ui.SettingsScreen
import com.qin.f21.toolbox.ui.SoundMeterScreen
import com.qin.f21.toolbox.ui.StopwatchScreen
import com.qin.f21.toolbox.ui.TimeZoneScreen
import com.qin.f21.toolbox.ui.TimerScreen

/**
 * Registry of every tool in the toolbox.
 *
 * The registry is the single source of truth for the home screen, the search, the
 * favourites and the history, and it carries the hardware requirement of each tool so
 * unsupported features can be shown as such instead of failing at runtime.
 */
object Tools {

    data class ToolDef(
        val id: String,
        val title: String,
        val category: String,
        val icon: Int,
        val description: String,
        /** Id of the hardware capability the tool needs, or null when it is pure software. */
        val requires: String? = null,
        val factory: (ScreenHost) -> Screen
    )

    data class CategoryDef(val id: String, val title: String, val icon: Int)

    val categories: List<CategoryDef> = listOf(
        CategoryDef("convert", "Convert", R.drawable.ic_convert),
        CategoryDef("calculate", "Calculate", R.drawable.ic_calculator),
        CategoryDef("measure", "Measure", R.drawable.ic_stopwatch),
        CategoryDef("media", "Media", R.drawable.ic_camera),
        CategoryDef("device", "Device", R.drawable.ic_diagnostics)
    )

    val all: List<ToolDef> = listOf(
        // --- convert ------------------------------------------------------------
        ToolDef(
            "converter", "Unit converter", "convert", R.drawable.ic_convert,
            "Length, mass, area, volume, speed, temperature and 14 more categories",
            factory = { ConverterScreen(it) }
        ),
        ToolDef(
            "currency", "Currency", "convert", R.drawable.ic_currency,
            "Offline exchange rates with editable values",
            factory = { CurrencyScreen(it) }
        ),
        ToolDef(
            "timezone", "Time zones", "convert", R.drawable.ic_timezone,
            "World clock using the device time zone database",
            factory = { TimeZoneScreen(it) }
        ),
        ToolDef(
            "datecalc", "Date & time", "convert", R.drawable.ic_calendar,
            "Difference between dates, add or subtract intervals",
            factory = { DateCalculatorScreen(it) }
        ),

        // --- calculate ----------------------------------------------------------
        ToolDef(
            "calculator", "Calculator", "calculate", R.drawable.ic_calculator,
            "Scientific calculator with history and memory",
            factory = { CalculatorScreen(it) }
        ),
        ToolDef(
            "quickcalc", "Quick calculators", "calculate", R.drawable.ic_percent,
            "Percentage, discount, tip, BMI, loan, GCD, bases and more",
            factory = { QuickCalculatorScreen(it) }
        ),
        ToolDef(
            "geometry", "Geometry", "calculate", R.drawable.ic_geometry,
            "Circle, triangle, rectangle, sphere and Pythagoras",
            factory = { GeometryScreen(it) }
        ),

        // --- measure ------------------------------------------------------------
        ToolDef(
            "stopwatch", "Stopwatch", "measure", R.drawable.ic_stopwatch,
            "Lap timer with split and reset",
            factory = { StopwatchScreen(it) }
        ),
        ToolDef(
            "timer", "Timer", "measure", R.drawable.ic_timer,
            "Countdown with an alert when the time is up",
            factory = { TimerScreen(it) }
        ),
        ToolDef(
            "soundmeter", "Sound meter", "measure", R.drawable.ic_mic,
            "Microphone level and spectrum, calibrated in dB",
            requires = "microphone",
            factory = { SoundMeterScreen(it) }
        ),

        // --- media --------------------------------------------------------------
        ToolDef(
            "scanner", "QR & barcode", "media", R.drawable.ic_scan,
            "Scan QR codes and barcodes with the camera",
            requires = "camera",
            factory = { ScannerScreen(it) }
        ),
        ToolDef(
            "camera", "Camera", "media", R.drawable.ic_camera,
            "Take a photo and keep it on the device",
            requires = "camera",
            factory = { CameraScreen(it) }
        ),
        ToolDef(
            "recorder", "Voice recorder", "media", R.drawable.ic_mic,
            "Record WAV clips on the device",
            requires = "microphone",
            factory = { RecorderScreen(it) }
        ),
        ToolDef(
            "irremote", "Infrared remote", "media", R.drawable.ic_remote,
            "Send infrared codes from the bundled offline code list",
            requires = "ir",
            factory = { InfraredScreen(it) }
        ),
        ToolDef(
            "flashlight", "Flashlight", "media", R.drawable.ic_flashlight,
            "Turn the camera flash into a torch",
            requires = "torch",
            factory = { FlashlightScreen(it) }
        ),

        // --- device -------------------------------------------------------------
        ToolDef(
            "gps", "GPS", "device", R.drawable.ic_location,
            "Position, altitude, speed, bearing and satellites",
            requires = "gps",
            factory = { GpsScreen(it) }
        ),
        ToolDef(
            "diagnostics", "Diagnostics", "device", R.drawable.ic_diagnostics,
            "Device, memory, storage, battery, sensors and network",
            factory = { DiagnosticsScreen(it) }
        ),
        ToolDef(
            "history", "History", "device", R.drawable.ic_history,
            "Everything you converted or calculated, stored locally",
            factory = { HistoryScreen(it) }
        ),
        ToolDef(
            "favorites", "Favourites", "device", R.drawable.ic_star,
            "Your pinned tools and unit pairs",
            factory = { FavoritesScreen(it) }
        ),
        ToolDef(
            "settings", "Settings", "device", R.drawable.ic_settings,
            "Rates, calibration, data import and export",
            factory = { SettingsScreen(it) }
        ),
        ToolDef(
            "about", "About & help", "device", R.drawable.ic_info,
            "Version, offline guarantees and keypad shortcuts",
            factory = { AboutScreen(it) }
        )
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): ToolDef? = byId[id]

    fun inCategory(categoryId: String): List<ToolDef> = all.filter { it.category == categoryId }

    fun search(query: String): List<ToolDef> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all.filter {
            it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.id.contains(q)
        }
    }

    /** Resolves the runtime state of the capability a tool depends on. */
    fun capabilityOf(tool: ToolDef, capabilities: List<Capability>): Capability? {
        val id = tool.requires ?: return null
        return capabilities.firstOrNull { it.id == id }
    }

    fun isUsable(tool: ToolDef, capabilities: List<Capability>): Boolean {
        val id = tool.requires ?: return true
        val capability = capabilities.firstOrNull { it.id == id } ?: return false
        return capability.state == SupportState.AVAILABLE
    }
}
