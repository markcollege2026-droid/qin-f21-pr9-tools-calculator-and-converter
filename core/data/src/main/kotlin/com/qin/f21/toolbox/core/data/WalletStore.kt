package com.qin.f21.toolbox.core.data

import android.content.Context
import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.JsonParser
import com.qin.f21.toolbox.core.common.JsonWriter
import com.qin.f21.toolbox.core.wallet.WalletLedger
import java.io.File
import java.util.Locale

/**
 * File backed storage for the wallet.
 *
 * The ledger lives in one small JSON document (`wallet.json`) in the app-private storage
 * and is rewritten atomically (temp file + rename), exactly like the history. Nothing is
 * uploaded: the app has no network permission at all.
 *
 * The store also owns the "first run" state: when no wallet file exists yet it creates a
 * main balance in the currency of the device locale, falling back to the base of the
 * bundled rate table when that currency is not in the table.
 */
class WalletStore(context: Context) {

    companion object {
        const val MAX_ENTRIES = 500
        private const val WALLET_FILE = "wallet.json"
    }

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, WALLET_FILE)

    var ledger: WalletLedger = WalletLedger.EMPTY
        private set

    init {
        ledger = load()
    }

    /** Replaces the in-memory ledger and writes it to disk. */
    fun update(newLedger: WalletLedger) {
        val trimmed = if (newLedger.entries.size > MAX_ENTRIES) {
            WalletLedger(newLedger.accounts, newLedger.entries.take(MAX_ENTRIES))
        } else {
            newLedger
        }
        ledger = trimmed
        save()
    }

    /** Drops every account and every movement. */
    fun clear() {
        ledger = WalletLedger.EMPTY
        if (file.exists()) file.delete()
    }

    /** The JSON document used by the backup export/import of Settings. */
    fun exportDocument(): Json = Json.obj(
        "version" to Json.of(1),
        "app" to Json.of("qin-f21-toolbox"),
        "exported" to Json.of(System.currentTimeMillis()),
        "wallet" to ledger.toJson()
    )

    /** @return true when the file held a wallet document that could be read. */
    fun importDocument(json: Json): Boolean {
        val wallet = json.asObject()["wallet"] ?: return false
        val imported = WalletLedger.fromJson(wallet)
        if (imported.accounts.isEmpty()) return false
        update(imported)
        return true
    }

    /**
     * Creates the main balance on first run, if there is none yet.
     *
     * @param knownCurrencies the codes of the bundled rate table; a locale currency that is
     *   not in the table would be converted at rate 1.0, so the table base is used instead.
     */
    fun ensureMainAccount(name: String, knownCurrencies: Collection<String>, fallback: String): Boolean {
        if (ledger.mainAccount != null) return false
        val locale = try {
            java.util.Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (e: Exception) {
            null
        }
        val currency = when {
            locale != null && knownCurrencies.contains(locale) -> locale
            knownCurrencies.contains(fallback) -> fallback
            knownCurrencies.isNotEmpty() -> knownCurrencies.sorted().first()
            else -> "USD"
        }
        update(WalletLedger.starting(name, currency, createdAt = System.currentTimeMillis()))
        return true
    }

    // ---- persistence ----------------------------------------------------------

    private fun load(): WalletLedger {
        if (!file.exists()) return WalletLedger.EMPTY
        return try {
            WalletLedger.fromJson(JsonParser.parse(file.readText()))
        } catch (e: Exception) {
            // a damaged file must never stop the app from starting
            WalletLedger.EMPTY
        }
    }

    private fun save() {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(JsonWriter.write(ledger.toJson(), pretty = true))
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (e: Exception) {
            // storage is best effort; never crash the app because of it
        }
    }
}
