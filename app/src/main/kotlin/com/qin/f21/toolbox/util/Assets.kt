package com.qin.f21.toolbox.util

import android.content.Context
import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.JsonParser

/**
 * Loader for the bundled offline reference data (exchange rates, infrared codes, ...).
 * The files are packaged in the APK, so this never touches the network.
 */
object Assets {

    fun read(context: Context, path: String): String? = try {
        context.assets.open(path).use { input ->
            input.bufferedReader().readText()
        }
    } catch (e: Exception) {
        null
    }

    fun readJson(context: Context, path: String): Json? {
        val text = read(context, path) ?: return null
        return JsonParser.parseOrNull(text)
    }
}
