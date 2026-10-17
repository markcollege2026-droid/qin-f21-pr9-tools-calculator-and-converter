package com.qin.f21.toolbox.core.data

import android.content.Context
import android.content.SharedPreferences
import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.JsonParser

/**
 * Small typed wrapper around [SharedPreferences].
 *
 * All settings are local; the toolbox never talks to a server.
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE = "qin_f21_toolbox_settings"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_MIC_CALIBRATION = "mic_calibration_db"
        private const val KEY_CURRENCY_OVERRIDES = "currency_overrides"
        private const val KEY_UNIT_PREFIX = "unit_"
        private const val KEY_RECENT = "recent_tools"
        private const val KEY_FLAG_PREFIX = "flag_"

        fun get(context: Context): Settings =
            Settings(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var micCalibrationDb: Double
        get() = prefs.getString(KEY_MIC_CALIBRATION, "94.0")?.toDoubleOrNull() ?: 94.0
        set(value) = prefs.edit().putString(KEY_MIC_CALIBRATION, value.toString()).apply()

    var currencyOverrides: Map<String, Double>
        get() {
            val raw = prefs.getString(KEY_CURRENCY_OVERRIDES, null) ?: return emptyMap()
            return try {
                val parsed = JsonParser.parse(raw).asObject()
                parsed.mapNotNull { (code, value) ->
                    val rate = (value as? Json.Num)?.value ?: return@mapNotNull null
                    code to rate
                }.toMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
        set(value) {
            val json = Json.Obj(value.entries.associate { (code, rate) -> code to Json.of(rate) })
            prefs.edit().putString(KEY_CURRENCY_OVERRIDES, com.qin.f21.toolbox.core.common.JsonWriter.write(json)).apply()
        }

    fun lastUnit(categoryId: String, default: String): String =
        prefs.getString(KEY_UNIT_PREFIX + categoryId, default) ?: default

    fun setLastUnit(categoryId: String, unitId: String) {
        prefs.edit().putString(KEY_UNIT_PREFIX + categoryId, unitId).apply()
    }

    fun recentTools(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split('|').filter { it.isNotEmpty() }
    }

    fun pushRecentTool(toolId: String) {
        val list = ArrayList(recentTools())
        list.remove(toolId)
        list.add(0, toolId)
        while (list.size > 8) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENT, list.joinToString("|")).apply()
    }

    fun flag(name: String, default: Boolean = false): Boolean = prefs.getBoolean(KEY_FLAG_PREFIX + name, default)

    fun setFlag(name: String, value: Boolean) {
        prefs.edit().putBoolean(KEY_FLAG_PREFIX + name, value).apply()
    }

}
