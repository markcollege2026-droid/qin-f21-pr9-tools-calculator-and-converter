package com.qin.f21.toolbox

import android.app.Activity
import android.app.Application
import android.content.Context
import android.widget.Toast
import com.qin.f21.toolbox.core.conversion.ConversionEngine
import com.qin.f21.toolbox.core.conversion.CurrencyTable
import com.qin.f21.toolbox.core.data.Settings
import com.qin.f21.toolbox.core.data.ToolboxStore
import com.qin.f21.toolbox.core.hardware.HardwareCapabilities

/**
 * Application level singletons.
 *
 * The toolbox deliberately keeps a very small object graph: one conversion engine, one
 * local store, one settings object and the hardware capability registry.
 */
class ToolboxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timers.attach(this)
        // The exchange rates are bundled with the APK, they are read once at startup.
        val json = com.qin.f21.toolbox.util.Assets.readJson(this, "data/currency.json")
        if (json != null) {
            Toolbox.loadCurrency(json)
        }
    }

    companion object {
        lateinit var instance: ToolboxApp
            private set
    }
}

/** Convenience accessors used across the UI. */
object Toolbox {
    val engine: ConversionEngine by lazy { ConversionEngine() }
    val store: ToolboxStore by lazy { ToolboxStore(ToolboxApp.instance) }
    val settings: Settings by lazy { Settings.get(ToolboxApp.instance) }
    var capabilities: HardwareCapabilities = HardwareCapabilities.get(ToolboxApp.instance)
        private set

    fun refreshCapabilities() {
        capabilities = HardwareCapabilities.refresh(ToolboxApp.instance)
    }
    var currency: CurrencyTable = CurrencyTable.EMPTY
        private set

    fun loadCurrency(json: com.qin.f21.toolbox.core.common.Json) {
        currency = CurrencyTable.parseWithOverrides(json, settings.currencyOverrides)
    }

    fun reloadCurrency() {
        currency = currency.withOverrides(settings.currencyOverrides)
    }

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

fun Activity.appContext(): Context = applicationContext
