package com.qin.f21.toolbox.core.wallet

import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.Numbers
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Pure money model of the Wallet tool.
 *
 * Nothing in this file touches `android.*`, so the whole feature (balances, currency
 * conversion of a withdrawal, the "separate from the main balance" flag and the JSON
 * format that is written to `wallet.json`) is verified on a plain JVM by
 * `tools/selftest.sh`.
 *
 * The model:
 *
 *  * one **main** account - the balance the withdrawals come from;
 *  * any number of **cash** accounts, each with its own currency;
 *  * entries that move money: `TOP_UP` (money in), `EXPENSE` (money out) and
 *    `WITHDRAW` (a transfer from the main balance into one cash account, converted
 *    with the bundled offline rates when the two currencies differ);
 *  * every cash account carries an `includedInTotal` flag - when it is off, the account
 *    is *separate* from the main balance and is left out of the combined total.
 *
 * Balances are never stored: they are replayed from the entries, so the ledger cannot
 * drift and every operation stays reversible by removing the entry.
 */

/** Exchange rates used to value balances and to convert a withdrawal. */
interface RateSource {
    /** Units of the table base currency that one unit of [code] is worth... */
    fun rate(code: String): Double

    /** Converts [amount] from one currency into another. */
    fun convert(amount: Double, from: String, to: String): Double
}

/** A fixed rate table, used by the self test and as a fallback when no table is loaded. */
class FixedRates(rates: Map<String, Double>, val base: String = "USD") : RateSource {

    private val table: Map<String, Double> = rates.filterValues { it > 0.0 }

    override fun rate(code: String): Double = table[code] ?: 1.0

    override fun convert(amount: Double, from: String, to: String): Double {
        if (from == to) return amount
        val source = rate(from)
        if (source == 0.0) return 0.0
        return amount / source * rate(to)
    }
}

/** Money helper: every stored amount is rounded to cents, half up. */
object Money {

    const val DECIMALS = 2

    private val symbols = java.text.DecimalFormatSymbols(java.util.Locale.US)

    fun round(value: Double, decimals: Int = DECIMALS): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }

    /** Parses user input; only a positive, finite amount is a valid amount of money. */
    fun parse(text: String): Double? {
        val value = Numbers.parse(text) ?: return null
        if (!value.isFinite()) return null
        return round(value)
    }

    fun isAmount(value: Double?): Boolean = value != null && value.isFinite() && value > 0.0

    /**
     * Money is always shown with a fixed number of decimals (`1,250.50`, not `1,250.5`),
     * which is what [Numbers.format] deliberately does not do. The currency code is
     * appended when one is given.
     */
    fun format(value: Double, currency: String = "", decimals: Int = DECIMALS): String {
        val rounded = if (value.isNaN() || value.isInfinite()) 0.0 else value
        val scaled = BigDecimal(rounded).setScale(decimals, RoundingMode.HALF_UP)
        val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        val text = java.text.DecimalFormat(pattern, symbols).format(scaled)
        return if (currency.isEmpty()) text else "$text $currency"
    }
}

/** What an account is: the balance withdrawals come from, or a pocket of cash. */
enum class WalletAccountKind {
    MAIN,
    CASH;

    companion object {
        fun of(value: String): WalletAccountKind =
            values().firstOrNull { it.name == value } ?: CASH
    }
}

/**
 * One balance of the wallet.
 *
 * @property currency the currency of this balance; a cash account may use any currency,
 *   independently of the main balance.
 * @property includedInTotal when false the account is separate from the main balance and
 *   is not added to the combined total.
 */
data class WalletAccount(
    val id: String = newId(),
    val name: String,
    val currency: String,
    val opening: Double = 0.0,
    val includedInTotal: Boolean = true,
    val kind: WalletAccountKind = WalletAccountKind.CASH,
    val createdAt: Long = 0L
) {

    fun toJson(): Json = Json.obj(
        "id" to Json.of(id),
        "name" to Json.of(name),
        "currency" to Json.of(currency),
        "opening" to Json.of(Money.round(opening)),
        "includedInTotal" to Json.of(includedInTotal),
        "kind" to Json.of(kind.name),
        "createdAt" to Json.of(createdAt)
    )

    companion object {
        fun fromJson(json: Json): WalletAccount? {
            val id = json.string("id").ifEmpty { return null }
            val name = json.string("name").ifEmpty { return null }
            val currency = json.string("currency").ifEmpty { return null }
            return WalletAccount(
                id = id,
                name = name,
                currency = currency,
                opening = Money.round(json.double("opening")),
                includedInTotal = json.bool("includedInTotal", true),
                kind = WalletAccountKind.of(json.string("kind", WalletAccountKind.CASH.name)),
                createdAt = json.long("createdAt")
            )
        }
    }
}

/** The three movements the wallet knows. */
enum class WalletEntryKind {
    /** Money in from the outside world. */
    TOP_UP,

    /** Money out to the outside world. */
    EXPENSE,

    /** A transfer from the main balance into a cash account, converted if needed. */
    WITHDRAW;

    companion object {
        fun of(value: String): WalletEntryKind? = values().firstOrNull { it.name == value }
    }
}

/**
 * One recorded movement.
 *
 * [amount] is always positive and always expressed in the currency of [accountId]; the
 * direction comes from [kind]. For a [WalletEntryKind.WITHDRAW] the other side of the
 * transfer is carried in [counterAccountId] / [counterAmount] (the main balance and the
 * amount debited from it, in the main currency) together with the [rate] that was used,
 * so the entry stays understandable after the rates changed.
 */
data class WalletEntry(
    val id: String = newId(),
    val timestamp: Long = 0L,
    val kind: WalletEntryKind,
    val accountId: String,
    val amount: Double,
    val note: String = "",
    val counterAccountId: String? = null,
    val counterAmount: Double? = null,
    val rate: Double? = null
) {

    fun toJson(): Json {
        val entries = linkedMapOf(
            "id" to Json.of(id),
            "timestamp" to Json.of(timestamp),
            "kind" to Json.of(kind.name),
            "accountId" to Json.of(accountId),
            "amount" to Json.of(Money.round(amount)),
            "note" to Json.of(note)
        )
        if (counterAccountId != null) entries["counterAccountId"] = Json.of(counterAccountId)
        if (counterAmount != null) entries["counterAmount"] = Json.of(Money.round(counterAmount))
        if (rate != null) entries["rate"] = Json.of(rate)
        return Json.Obj(entries)
    }

    companion object {
        fun fromJson(json: Json): WalletEntry? {
            val id = json.string("id").ifEmpty { return null }
            val kind = WalletEntryKind.of(json.string("kind")) ?: return null
            val accountId = json.string("accountId").ifEmpty { return null }
            val counter = json.string("counterAccountId").ifEmpty { null }
            val counterValue = json.asObject()["counterAmount"]
            val rateValue = json.asObject()["rate"]
            return WalletEntry(
                id = id,
                timestamp = json.long("timestamp"),
                kind = kind,
                accountId = accountId,
                amount = Money.round(json.double("amount")),
                note = json.string("note"),
                counterAccountId = counter,
                counterAmount = (counterValue as? Json.Num)?.let { Money.round(it.value) },
                rate = (rateValue as? Json.Num)?.value
            )
        }
    }
}

/** Why a movement was refused. The UI maps a code onto a localised message. */
enum class WalletErrorCode {
    /** The amount is missing, not a number or not above zero. */
    AMOUNT,

    /** The account does not exist or has the wrong kind. */
    ACCOUNT,

    /** No main balance exists yet. */
    NO_MAIN,

    /** No cash account exists yet. */
    NO_CASH,

    /** The debited balance does not hold the amount. */
    INSUFFICIENT
}

/** Result of a money movement: either the new ledger, or the reason it was refused. */
sealed class WalletChange {
    data class Done(val ledger: WalletLedger, val entry: WalletEntry) : WalletChange()
    data class Failed(val code: WalletErrorCode, val detail: String = "") : WalletChange()
}

/**
 * The immutable wallet: the accounts plus every movement, newest first.
 *
 * Every mutation returns a new ledger, which keeps the UI simple (rebuild the screen from
 * the returned value) and makes the logic trivial to test.
 */
class WalletLedger(
    val accounts: List<WalletAccount>,
    val entries: List<WalletEntry>
) {

    /** The balance the withdrawals come from; there is at most one. */
    val mainAccount: WalletAccount? get() = accounts.firstOrNull { it.kind == WalletAccountKind.MAIN }

    /** Every cash pocket, in creation order. */
    val cashAccounts: List<WalletAccount> get() = accounts.filter { it.kind == WalletAccountKind.CASH }

    fun account(id: String?): WalletAccount? = accounts.firstOrNull { it.id == id }

    fun entriesFor(id: String): List<WalletEntry> =
        entries.filter { it.accountId == id || it.counterAccountId == id }

    // ---- balances -------------------------------------------------------------

    /**
     * Balance of one account in its own currency: the opening amount plus every movement
     * that touches the account. A withdrawal credits the cash account and debits the main
     * balance by the converted amount.
     */
    fun balanceOf(id: String): Double {
        val account = account(id) ?: return 0.0
        var total = account.opening
        for (entry in entries) {
            if (entry.accountId == id) {
                total += if (entry.kind == WalletEntryKind.EXPENSE) -entry.amount else entry.amount
            }
            if (entry.counterAccountId == id) {
                total -= entry.counterAmount ?: 0.0
            }
        }
        return Money.round(total)
    }

    fun balances(): Map<String, Double> = accounts.associate { it.id to balanceOf(it.id) }

    /** Accounts that are part of the combined total: the main balance and every cash account that is not separate. */
    fun includedAccounts(): List<WalletAccount> =
        accounts.filter { it.kind == WalletAccountKind.MAIN || it.includedInTotal }

    /** Cash accounts that were separated from the main balance. */
    fun separateAccounts(): List<WalletAccount> =
        accounts.filter { it.kind == WalletAccountKind.CASH && !it.includedInTotal }

    /** Balance of one account expressed in another currency. */
    fun valueOf(id: String, currency: String, rates: RateSource): Double {
        val account = account(id) ?: return 0.0
        return Money.round(rates.convert(balanceOf(id), account.currency, currency))
    }

    /**
     * The combined total in [currency]: the main balance plus every cash account that is
     * not separated, each converted with [rates].
     */
    fun totalIn(currency: String, rates: RateSource): Double {
        var total = 0.0
        for (account in includedAccounts()) {
            total += rates.convert(balanceOf(account.id), account.currency, currency)
        }
        return Money.round(total)
    }

    // ---- movements ------------------------------------------------------------

    /** Money in from the outside world. */
    fun topUp(
        accountId: String,
        amount: Double,
        note: String = "",
        timestamp: Long = 0L
    ): WalletChange {
        val account = account(accountId) ?: return WalletChange.Failed(WalletErrorCode.ACCOUNT)
        if (!Money.isAmount(amount)) return WalletChange.Failed(WalletErrorCode.AMOUNT)
        val ledger = withEntry(
            WalletEntry(
                timestamp = timestamp,
                kind = WalletEntryKind.TOP_UP,
                accountId = account.id,
                amount = Money.round(amount),
                note = note.trim()
            )
        )
        return WalletChange.Done(ledger, ledger.entries.first())
    }

    /** Money out to the outside world; refused when the balance does not hold the amount. */
    fun expense(
        accountId: String,
        amount: Double,
        note: String = "",
        timestamp: Long = 0L
    ): WalletChange {
        val account = account(accountId) ?: return WalletChange.Failed(WalletErrorCode.ACCOUNT)
        if (!Money.isAmount(amount)) return WalletChange.Failed(WalletErrorCode.AMOUNT)
        val value = Money.round(amount)
        if (value > balanceOf(account.id) + TOLERANCE) {
            return WalletChange.Failed(WalletErrorCode.INSUFFICIENT, account.currency)
        }
        val ledger = withEntry(
            WalletEntry(
                timestamp = timestamp,
                kind = WalletEntryKind.EXPENSE,
                accountId = account.id,
                amount = value,
                note = note.trim()
            )
        )
        return WalletChange.Done(ledger, ledger.entries.first())
    }

    /**
     * Takes [amount] of the cash account's currency out of the main balance and puts it
     * into that cash account.
     *
     * The amount is entered in the currency of the pocket the money ends up in (that is
     * what you count in your hand); the debit from the main balance is converted with the
     * bundled rates and the rate is recorded on the entry. When both accounts use the same
     * currency no conversion happens and the rate is 1.
     */
    fun withdraw(
        cashAccountId: String,
        amount: Double,
        note: String = "",
        timestamp: Long = 0L,
        rates: RateSource = FixedRates(emptyMap())
    ): WalletChange {
        val main = mainAccount ?: return WalletChange.Failed(WalletErrorCode.NO_MAIN)
        val cash = account(cashAccountId)
        if (cash == null || cash.kind != WalletAccountKind.CASH) {
            return WalletChange.Failed(WalletErrorCode.ACCOUNT)
        }
        if (!Money.isAmount(amount)) return WalletChange.Failed(WalletErrorCode.AMOUNT)
        val value = Money.round(amount)
        val rate = rates.convert(1.0, cash.currency, main.currency)
        val debit = Money.round(rates.convert(value, cash.currency, main.currency))
        if (debit > balanceOf(main.id) + TOLERANCE) {
            return WalletChange.Failed(WalletErrorCode.INSUFFICIENT, main.currency)
        }
        val ledger = withEntry(
            WalletEntry(
                timestamp = timestamp,
                kind = WalletEntryKind.WITHDRAW,
                accountId = cash.id,
                amount = value,
                note = note.trim(),
                counterAccountId = main.id,
                counterAmount = debit,
                rate = rate
            )
        )
        return WalletChange.Done(ledger, ledger.entries.first())
    }

    /** The main-currency cost of withdrawing [amount] of a cash account's currency. */
    fun withdrawalCost(cashAccountId: String, amount: Double, rates: RateSource): Double {
        val main = mainAccount ?: return 0.0
        val cash = account(cashAccountId) ?: return 0.0
        return Money.round(rates.convert(amount, cash.currency, main.currency))
    }

    /** The rate line shown next to a withdrawal: main-currency units per one cash unit. */
    fun withdrawalRate(cashAccountId: String, rates: RateSource): Double? {
        val main = mainAccount ?: return null
        val cash = account(cashAccountId) ?: return null
        return rates.convert(1.0, cash.currency, main.currency)
    }

    // ---- accounts -------------------------------------------------------------

    fun withAccounts(newAccounts: List<WalletAccount>): WalletLedger =
        WalletLedger(newAccounts, entries)

    fun withAccount(account: WalletAccount): WalletLedger {
        val updated = accounts.map { if (it.id == account.id) account else it }
        val list = if (updated.any { it.id == account.id }) updated else accounts + account
        return WalletLedger(list, entries)
    }

    /**
     * Removes an account.
     *
     * Refused for the main balance and for any account that still has movements, so the
     * ledger can never lose the other side of a recorded withdrawal.
     */
    fun removeAccount(id: String): WalletLedger {
        val account = account(id) ?: return this
        if (account.kind == WalletAccountKind.MAIN) return this
        if (entriesFor(id).isNotEmpty()) return this
        return WalletLedger(accounts.filter { it.id != id }, entries)
    }

    fun canRemove(id: String): Boolean {
        val account = account(id) ?: return false
        return account.kind != WalletAccountKind.MAIN && entriesFor(id).isEmpty()
    }

    // ---- json -----------------------------------------------------------------

    fun toJson(): Json = Json.obj(
        "version" to Json.of(1),
        "accounts" to Json.array(accounts.map { it.toJson() }),
        "entries" to Json.array(entries.map { it.toJson() })
    )

    private fun withEntry(entry: WalletEntry): WalletLedger {
        val list = ArrayList<WalletEntry>(entries.size + 1)
        list.add(entry)
        list.addAll(entries)
        return WalletLedger(accounts, list)
    }

    companion object {
        /** Rounding slack: 0.1 of the smallest unit of the currency. */
        const val TOLERANCE = 0.005

        val EMPTY = WalletLedger(emptyList(), emptyList())

        /** A wallet with nothing but a main balance in [currency]. */
        fun starting(mainName: String, currency: String, opening: Double = 0.0, createdAt: Long = 0L): WalletLedger =
            WalletLedger(
                listOf(
                    WalletAccount(
                        name = mainName,
                        currency = currency,
                        opening = Money.round(opening),
                        kind = WalletAccountKind.MAIN,
                        createdAt = createdAt
                    )
                ),
                emptyList()
            )

        fun fromJson(json: Json): WalletLedger {
            val accounts = json.arr("accounts").mapNotNull { WalletAccount.fromJson(it) }
            val entries = json.arr("entries").mapNotNull { WalletEntry.fromJson(it) }
                .sortedByDescending { it.timestamp }
            return WalletLedger(accounts, entries)
        }
    }
}

private fun newId(): String = UUID.randomUUID().toString()
