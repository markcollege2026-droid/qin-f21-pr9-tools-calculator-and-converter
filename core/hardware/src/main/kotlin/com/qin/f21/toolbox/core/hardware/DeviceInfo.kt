package com.qin.f21.toolbox.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

/** A single diagnostic row: label + value (+ optional note). */
data class DiagnosticRow(val label: String, val value: String, val note: String = "")

/**
 * Collects device, battery, storage, display and network diagnostics.
 * Everything is read from the platform APIs - no network access, no analytics.
 */
class DeviceInfo(context: Context) {

    private val appContext = context.applicationContext

    fun deviceRows(): List<DiagnosticRow> {
        val rows = ArrayList<DiagnosticRow>()
        rows.add(DiagnosticRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}", Build.DEVICE ?: ""))
        rows.add(DiagnosticRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Build.VERSION.SECURITY_PATCH))
        rows.add(DiagnosticRow("Build", Build.DISPLAY ?: Build.ID, Build.FINGERPRINT.take(48)))
        rows.add(DiagnosticRow("Chipset", Build.HARDWARE ?: "unknown", Build.VERSION.INCREMENTAL ?: ""))
        rows.add(DiagnosticRow("ABIs", Build.SUPPORTED_ABIS.joinToString(", "), ""))
        rows.add(DiagnosticRow("CPU", cpuDescription(), "${cores()} core(s)"))
        rows.add(DiagnosticRow("Uptime", formatUptime(SystemClock.elapsedRealtime()), ""))
        rows.add(DiagnosticRow("App heap", formatBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()), "of ${formatBytes(Runtime.getRuntime().maxMemory())}"))
        return rows
    }

    fun memoryRows(): List<DiagnosticRow> {
        val rows = ArrayList<DiagnosticRow>()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        try {
            manager?.getMemoryInfo(info)
        } catch (e: Exception) {
            // ignore
        }
        rows.add(DiagnosticRow("Total RAM", formatBytes(info.totalMem)))
        rows.add(DiagnosticRow("Available", formatBytes(info.availMem)))
        rows.add(DiagnosticRow("Used", formatBytes(info.totalMem - info.availMem), percentage(info.totalMem, info.availMem)))
        rows.add(DiagnosticRow("Low memory", if (info.lowMemory) "Yes" else "No", "threshold ${formatBytes(info.threshold)}"))
        val perApp = manager?.memoryClass ?: 0
        rows.add(DiagnosticRow("Per-app limit", formatBytes(perApp * 1024L * 1024L), "memoryClass $perApp"))
        return rows
    }

    fun storageRows(): List<DiagnosticRow> {
        val rows = ArrayList<DiagnosticRow>()
        rows.addAll(storageFor("Internal storage", Environment.getDataDirectory()))
        val external = Environment.getExternalStorageDirectory()
        if (external != null && external != Environment.getDataDirectory()) {
            rows.addAll(storageFor("External storage", external))
        }
        val files = appContext.filesDir
        val cache = appContext.cacheDir
        rows.add(DiagnosticRow("App data", formatBytes(dirSize(files)), files.absolutePath))
        rows.add(DiagnosticRow("App cache", formatBytes(dirSize(cache)), cache.absolutePath))
        return rows
    }

    fun displayRows(): List<DiagnosticRow> {
        val rows = ArrayList<DiagnosticRow>()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.getMetrics(metrics)
        rows.add(DiagnosticRow("Resolution", "${metrics.widthPixels} x ${metrics.heightPixels} px", ""))
        rows.add(DiagnosticRow("Density", "${metrics.densityDpi} dpi", "scale ${metrics.density}"))
        rows.add(DiagnosticRow("Logical size", "${(metrics.widthPixels / metrics.density).toInt()} x ${(metrics.heightPixels / metrics.density).toInt()} dp", ""))
        rows.add(DiagnosticRow("Smallest width", "${appContext.resources.configuration.smallestScreenWidthDp} dp", ""))
        rows.add(DiagnosticRow("Refresh rate", "${displayRefreshRate().toInt()} Hz", ""))
        rows.add(DiagnosticRow("Night mode", if (appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES) "Yes" else "No", ""))
        return rows
    }

    fun batteryRows(): List<DiagnosticRow> {
        val rows = ArrayList<DiagnosticRow>()
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100.0 / scale else -1.0
        val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            else -> "Unknown"
        }
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        rows.add(DiagnosticRow("Level", if (percent >= 0) "${percent.toInt()}%" else "unknown", status))
        rows.add(DiagnosticRow("Health", health, ""))
        rows.add(DiagnosticRow("Temperature", formatTemperature(intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0)))
        rows.add(DiagnosticRow("Voltage", formatMilliVolts(intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)))
        rows.add(DiagnosticRow("Capacity", capacityText(manager)))
        rows.add(DiagnosticRow("Technology", intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown", ""))
        rows.add(DiagnosticRow("Charge counter", chargeCounter(manager)))
        return rows
    }

    fun networkRows(): List<DiagnosticRow> {
        val rows = ArrayList<DiagnosticRow>()
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var transport = "Offline"
        var validated = false
        try {
            val network = connectivity?.activeNetwork
            val caps = network?.let { connectivity.getNetworkCapabilities(it) }
            if (caps != null) {
                transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Connected"
                }
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        } catch (e: Exception) {
            // ignore
        }
        rows.add(DiagnosticRow("Connection", transport, if (validated) "validated" else ""))
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        rows.add(DiagnosticRow("Wi-Fi", if (wifi?.isWifiEnabled == true) "Enabled" else "Disabled", ""))
        rows.add(DiagnosticRow("Wi-Fi state", wifiState(wifi), ""))
        rows.add(DiagnosticRow("Bluetooth", if (HardwareCapabilities.get(appContext).hasBluetooth) "Present" else "Absent", ""))
        return rows
    }

    private fun wifiState(wifi: WifiManager?): String = try {
        @Suppress("DEPRECATION")
        val info = wifi?.connectionInfo
        when {
            info == null -> "Unknown"
            info.networkId != -1 -> "Connected"
            info.ipAddress != 0 -> "Connected"
            else -> "Idle"
        }
    } catch (e: Exception) {
        "Unknown"
    }

    private fun capacityText(manager: BatteryManager?): String {
        val counter = try {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
        return if (counter != null && counter > 0) "$counter mAh" else "not reported"
    }

    private fun chargeCounter(manager: BatteryManager?): String {
        val value = try {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (e: Exception) {
            -1
        }
        return if (value != null && value > 0) "${formatMicroAmpHours(value)}" else "not reported"
    }

    private fun displayRefreshRate(): Float {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appContext.display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.refreshRate ?: 60f
            }
        } catch (e: Exception) {
            60f
        }
    }

    private fun storageFor(label: String, path: File): List<DiagnosticRow> {
        return try {
            val stat = StatFs(path.absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            listOf(
                DiagnosticRow(label, formatBytes(free) + " free", "of ${formatBytes(total)}")
            )
        } catch (e: Exception) {
            listOf(DiagnosticRow(label, "unavailable", path.absolutePath))
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    private fun cpuDescription(): String {
        return try {
            val info = File("/proc/cpuinfo")
            if (info.exists()) {
                val text = info.readText()
                val name = text.lineSequence().firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") || it.startsWith("Processor") }
                    ?.substringAfter(":")?.trim()
                name?.takeIf { it.isNotEmpty() } ?: Build.HARDWARE ?: "unknown"
            } else {
                Build.HARDWARE ?: "unknown"
            }
        } catch (e: Exception) {
            Build.HARDWARE ?: "unknown"
        }
    }

    private fun cores(): Int = try {
        File("/proc/cpuinfo").readText().lineSequence().count { it.startsWith("processor") }
    } catch (e: Exception) {
        Runtime.getRuntime().availableProcessors()
    }

    private fun percentage(total: Long, available: Long): String {
        if (total <= 0) return ""
        val used = total - available
        return "${(used * 100.0 / total).toInt()}% used"
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024.0
            var index = 0
            while (value >= 1024.0 && index < units.size - 1) {
                value /= 1024.0
                index++
            }
            return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
        }

        fun formatTemperature(tenths: Int): String =
            if (tenths <= 0) "not reported" else String.format(java.util.Locale.US, "%.1f °C", tenths / 10.0)

        fun formatMilliVolts(millivolts: Int): String =
            if (millivolts <= 0) "not reported" else String.format(java.util.Locale.US, "%.2f V", millivolts / 1000.0)

        fun formatMicroAmpHours(value: Int): String =
            String.format(java.util.Locale.US, "%d mAh", value / 1000)

        fun formatUptime(millis: Long): String {
            val seconds = millis / 1000
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
        }
    }
}
