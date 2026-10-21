package com.qin.f21.toolbox.selftest

import com.qin.f21.toolbox.core.calc.BaseConverter
import com.qin.f21.toolbox.core.calc.BmiCalculator
import com.qin.f21.toolbox.core.calc.CookingCalculator
import com.qin.f21.toolbox.core.calc.DateCalculator
import com.qin.f21.toolbox.core.calc.ExpressionEvaluator
import com.qin.f21.toolbox.core.calc.GeometryCalculator
import com.qin.f21.toolbox.core.calc.InterestCalculator
import com.qin.f21.toolbox.core.calc.LoanCalculator
import com.qin.f21.toolbox.core.calc.NumberCalculator
import com.qin.f21.toolbox.core.calc.PercentCalculator
import com.qin.f21.toolbox.core.calc.PriceCalculator
import com.qin.f21.toolbox.core.calc.RandomCalculator
import com.qin.f21.toolbox.core.calc.TravelCalculator
import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.JsonParser
import com.qin.f21.toolbox.core.data.Favorite
import com.qin.f21.toolbox.core.data.HistoryEntry
import com.qin.f21.toolbox.core.common.JsonWriter
import com.qin.f21.toolbox.core.common.Numbers
import com.qin.f21.toolbox.core.conversion.ConversionEngine
import com.qin.f21.toolbox.core.conversion.CurrencyTable
import com.qin.f21.toolbox.core.conversion.UnitCatalog
import com.qin.f21.toolbox.core.wallet.FixedRates
import com.qin.f21.toolbox.core.wallet.Money
import com.qin.f21.toolbox.core.wallet.RateSource
import com.qin.f21.toolbox.core.wallet.WalletAccount
import com.qin.f21.toolbox.core.wallet.WalletAccountKind
import com.qin.f21.toolbox.core.wallet.WalletChange
import com.qin.f21.toolbox.core.wallet.WalletEntryKind
import com.qin.f21.toolbox.core.wallet.WalletErrorCode
import com.qin.f21.toolbox.core.wallet.WalletLedger
import java.time.LocalDate

/**
 * Self test for the pure Kotlin modules.
 *
 * The conversion, calculator and JSON modules do not touch the Android framework, so they
 * can be verified on a plain JVM. Run it with `tools/selftest.sh`.
 */
object Selftest {

    private var checks = 0
    private var failures = 0
    private val failuresList = ArrayList<String>()

    @JvmStatic
    fun main(args: Array<String>) {
        jsonTests()
        numbersTests()
        conversionTests()
        currencyTests()
        timeZoneTests()
        expressionTests()
        calculatorTests()
        geometryTests()
        storeTests()
        walletTests()

        println()
        println("checks : $checks")
        println("failed : $failures")
        if (failures > 0) {
            println()
            for (failure in failuresList) println("  FAILED  $failure")
            System.exit(1)
        }
        println("ALL TESTS PASSED")
    }

    // ---- helpers ---------------------------------------------------------------

    private fun check(name: String, expected: Any?, actual: Any?) {
        checks++
        val ok = when {
            expected is Double && actual is Double ->
                kotlin.math.abs(expected - actual) <= kotlin.math.max(1e-6, kotlin.math.abs(expected) * 1e-3)
            expected is Float && actual is Float -> kotlin.math.abs(expected - actual) <= 1e-4f
            else -> expected == actual
        }
        if (!ok) {
            failures++
            failuresList.add("$name: expected <$expected> but was <$actual>")
        }
    }

    private fun checkTrue(name: String, value: Boolean) = check(name, true, value)

    private fun done(name: String, change: WalletChange): WalletLedger {
        val result = change as? WalletChange.Done
        if (result == null) {
            failures++
            failuresList.add("$name: expected a recorded movement but it was refused ($change)")
            return WalletLedger.EMPTY
        }
        return result.ledger
    }

    private fun failureCode(change: WalletChange): WalletErrorCode? =
        (change as? WalletChange.Failed)?.code

    // ---- tests -----------------------------------------------------------------

    private fun jsonTests() {
        val text = """
            {"name":"toolbox","count":3,"ratio":1.5,"ok":true,"list":[1,2,3],
             "nested":{"a":"b"},"nothing":null}
        """.trimIndent()
        val json = JsonParser.parse(text)
        check("json.name", "toolbox", json.string("name"))
        check("json.count", 3, json.int("count"))
        check("json.ratio", 1.5, json.double("ratio"))
        check("json.ok", true, json.bool("ok"))
        check("json.list.size", 3, json.arr("list").size)
        check("json.nested", "b", json.obj("nested").string("a"))
        check("json.missing", "fallback", json.string("missing", "fallback"))

        val written = JsonWriter.write(json)
        val reparsed = JsonParser.parse(written)
        check("json.roundtrip.name", "toolbox", reparsed.string("name"))
        check("json.roundtrip.count", 3, reparsed.int("count"))
        check("json.roundtrip.list", 3, reparsed.arr("list").size)
    }

    private fun numbersTests() {
        check("numbers.parse", 12.5, Numbers.parse("12.5"))
        check("numbers.parseSpaces", 3.0, Numbers.parse(" 3 "))
        checkTrue("numbers.parseInvalid", Numbers.parse("abc") == null)
        check("numbers.format", "1,234.5", Numbers.format(1234.5))
        check("numbers.formatSmall", "0.5", Numbers.format(0.5))
        check("numbers.formatZero", "0", Numbers.format(0.0))
    }

    private fun conversionTests() {
        val engine = ConversionEngine()
        val length = engine.category("length")!!
        check("length.m", 1000.0, engine.convert(1.0, length, "m", "mm"))
        check("length.kmToMile", 0.621371, engine.convert(1.0, length, "km", "mi"))
        check("length.inchToCm", 2.54, engine.convert(1.0, length, "in", "cm"))

        val mass = engine.category("mass")!!
        check("mass.kgToLb", 2.204623, engine.convert(1.0, mass, "kg", "lb"))
        check("mass.tonneToKg", 1000.0, engine.convert(1.0, mass, "t", "kg"))

        val temperature = engine.category("temperature")!!
        check("temp.cToF", 212.0, engine.convert(100.0, temperature, "c", "f"))
        check("temp.fToC", 0.0, engine.convert(32.0, temperature, "f", "c"))
        check("temp.kToC", 0.0, engine.convert(273.15, temperature, "k", "c"))

        val volume = engine.category("volume")!!
        check("volume.literToGal", 0.264172, engine.convert(1.0, volume, "l", "gal_us"))
        check("volume.m3ToL", 1000.0, engine.convert(1.0, volume, "m3", "l"))

        val speed = engine.category("speed")!!
        check("speed.kmhToMph", 62.1371, engine.convert(100.0, speed, "kmh", "mph"))

        val area = engine.category("area")!!
        check("area.haToM2", 10000.0, engine.convert(1.0, area, "ha", "m2"))

        val data = engine.category("digital")!!
        check("data.gbToMb", 1000.0, engine.convert(1.0, data, "gb", "mb"))
        check("data.gibToMib", 1024.0, engine.convert(1.0, data, "gib", "mib"))

        val time = engine.category("time")!!
        check("time.hourToMinute", 60.0, engine.convert(1.0, time, "h", "min"))
        check("time.weekToDay", 7.0, engine.convert(1.0, time, "wk", "d"))

        check("catalog.size", true, UnitCatalog.all.size >= 18)
        for (category in UnitCatalog.all) {
            checkTrue("catalog.${category.id}.units", category.units.size >= 2)
            checkTrue("catalog.${category.id}.default", category.unit(category.defaultFrom) != null)
            checkTrue("catalog.${category.id}.defaultTo", category.unit(category.defaultTo) != null)
            val converted = engine.convert(1.0, category, category.defaultFrom, category.defaultTo)
            checkTrue("catalog.${category.id}.finite", !converted.isNaN() && !converted.isInfinite())
        }
    }

    private fun currencyTests() {
        val json = JsonParser.parse(
            """{"base":"USD","updated":"2025-01-01","rates":{"USD":1.0,"EUR":0.5,"JPY":100.0}}"""
        )
        val table = CurrencyTable.parse(json)
        check("currency.usd", 1.0, table.rate("USD"))
        check("currency.eur", 0.5, table.rate("EUR"))
        check("currency.convert", 50.0, table.convert(100.0, "USD", "EUR"))
        check("currency.same", 12.0, table.convert(12.0, "EUR", "EUR"))
        val overridden = table.withOverrides(mapOf("EUR" to 0.25))
        check("currency.override", 25.0, overridden.convert(100.0, "USD", "EUR"))
        check("currency.currencies", true, table.currencies.containsAll(listOf("EUR", "JPY", "USD")))
    }

    private fun timeZoneTests() {
        val engine = ConversionEngine()
        val category = engine.category("time")!!
        check("timezone.seconds", 3600.0, engine.convert(1.0, category, "h", "s"))
    }

    private fun expressionTests() {
        val evaluator = ExpressionEvaluator()
        check("expr.add", 7.0, evaluator.evaluate("3+4"))
        check("expr.precedence", 14.0, evaluator.evaluate("2+3*4"))
        check("expr.parens", 20.0, evaluator.evaluate("(2+3)*4"))
        check("expr.power", 8.0, evaluator.evaluate("2^3"))
        check("expr.sqrt", 3.0, evaluator.evaluate("sqrt(9)"))
        check("expr.sin", 1.0, evaluator.evaluate("sin(pi/2)"))
        check("expr.cos", 0.0, evaluator.evaluate("cos(pi/2)"))
        check("expr.deg", 180.0, evaluator.evaluate("deg(pi)"))
        check("expr.min", 2.0, evaluator.evaluate("min(2,7)"))
        check("expr.max", 7.0, evaluator.evaluate("max(2,7)"))
        check("expr.round", 3.0, evaluator.evaluate("round(3.4)"))
        check("expr.mod", 1.0, evaluator.evaluate("mod(7,3)"))
        check("expr.factorial", 120.0, evaluator.evaluate("5!"))
        check("expr.ln", 1.0, evaluator.evaluate("ln(e)"))
        check("expr.pi", Math.PI, evaluator.evaluate("pi"))
        check("expr.modOperator", 1.0, evaluator.evaluate("7%3"))
        check("expr.unaryMinus", -5.0, evaluator.evaluate("-5"))
        check("expr.implicit", 12.0, evaluator.evaluate("2(3+3)"))
        check("expr.ans", 42.0, evaluator.evaluate("ans", 42.0))

        var failed = false
        try {
            evaluator.evaluate("2+")
        } catch (e: Exception) {
            failed = true
        }
        checkTrue("expr.error", failed)
    }

    private fun calculatorTests() {
        check("percent.of", 25.0, PercentCalculator.percentOf(10.0, 250.0))
        check("percent.what", 40.0, PercentCalculator.whatPercent(40.0, 100.0))
        check("percent.change", 25.0, PercentCalculator.change(80.0, 100.0))

        val breakdown = PriceCalculator.breakdown(100.0, 10.0, 8.0, 5.0)
        check("price.total", 101.7, breakdown.total)
        check("price.taxed", 7.2, breakdown.taxed)
        check("price.saved", 10.0, breakdown.saved)

        val shares = PriceCalculator.split(100.0, 3)
        check("price.split.count", 3, shares.size)
        check("price.split.sum", 100.0, shares.sum())

        val bmi = BmiCalculator.metric(70.0, 175.0)
        check("bmi.value", 22.86, bmi.bmi)
        checkTrue("bmi.category", bmi.category.isNotEmpty())

        val loan = LoanCalculator.payment(10000.0, 6.0, 60)
        check("loan.monthly", 193.33, loan.monthly)
        check("loan.total", 11599.8, loan.totalPaid)

        check("interest.simple", 500.0, InterestCalculator.simpleInterest(1000.0, 5.0, 10.0))
        check("interest.compound", 628.89, InterestCalculator.compoundInterest(1000.0, 5.0, 10.0))

        check("number.gcd", 6L, NumberCalculator.gcd(12L, 18L))
        check("number.lcm", 36L, NumberCalculator.lcm(12L, 18L))
        checkTrue("number.prime", NumberCalculator.isPrime(97L))
        checkTrue("number.notPrime", !NumberCalculator.isPrime(91L))
        check("number.roman", "MCMXCIV", NumberCalculator.toRoman(1994))
        check("number.fromRoman", 1994, NumberCalculator.fromRoman("MCMXCIV"))
        check("number.factors", 2, NumberCalculator.factorize(12L).size)

        check("base.hex", "FF", BaseConverter.convert("255", 10, 16))
        check("base.bin", "11111111", BaseConverter.convert("255", 10, 2))
        check("base.back", 255L, BaseConverter.parse("FF", 16))

        val random = RandomCalculator.inRange(1L, 1L, 5)
        check("random.count", 5, random.size)
        checkTrue("random.range", random.all { it == 1L })

        val difference = DateCalculator.between(LocalDate.of(2000, 1, 1), LocalDate.of(2020, 3, 15))
        check("date.years", 20L, difference.years)
        check("date.months", 2L, difference.months)
        check("date.days", 14L, difference.days)
        check("date.totalDays", 7379L, difference.totalDays)
        check("date.totalHours", 177096L, difference.totalHours)

        check("date.add", LocalDate.of(2020, 3, 15), DateCalculator.add(LocalDate.of(2020, 1, 15), 0, 2, 0))
    }

    private fun storeTests() {
        val entry = HistoryEntry(
            toolId = "converter",
            toolName = "Unit converter",
            title = "1 km = 0.621 mi",
            summary = "Length · Kilometre → Mile"
        )
        val json = entry.toJson()
        val restored = HistoryEntry.fromJson(json)
        checkTrue("store.history.restored", restored != null)
        check("store.history.toolId", "converter", restored?.toolId)
        check("store.history.title", "1 km = 0.621 mi", restored?.title)
        check("store.history.timestamp", entry.timestamp, restored?.timestamp)

        val favorite = Favorite(
            toolId = "converter",
            title = "Length: km → mi",
            subtitle = "Length",
            payload = "length:km:mi"
        )
        val favoriteJson = favorite.toJson()
        val restoredFavorite = Favorite.fromJson(favoriteJson)
        checkTrue("store.favorite.restored", restoredFavorite != null)
        check("store.favorite.payload", "length:km:mi", restoredFavorite?.payload)
        check("store.favorite.title", "Length: km → mi", restoredFavorite?.title)
    }

    private fun geometryTests() {
        check("geo.circleArea", Math.PI * 25, GeometryCalculator.circleArea(5.0))
        check("geo.circleCircumference", 2 * Math.PI * 5, GeometryCalculator.circleCircumference(5.0))
        check("geo.rectangleArea", 24.0, GeometryCalculator.rectangleArea(4.0, 6.0))
        check("geo.triangleArea", 12.0, GeometryCalculator.triangleArea(8.0, 3.0))
        check("geo.hypotenuse", 5.0, GeometryCalculator.rightTriangleHypotenuse(3.0, 4.0))
        check("geo.trapezoid", 25.0, GeometryCalculator.trapezoidArea(6.0, 4.0, 5.0))
        check("geo.sphereVolume", 113.097, GeometryCalculator.sphereVolume(3.0))
        check("geo.cylinderVolume", 197.92, GeometryCalculator.cylinderVolume(3.0, 7.0))

        check("travel.speed", 12.0, TravelCalculator.averageSpeed(10.0, 50.0))
        check("travel.pace", 5.0, TravelCalculator.pace(10.0, 50.0))
        check("travel.fuel", 54.11, TravelCalculator.fuelCost(450.0, 6.5, 1.85))
        // a larger pan holds the same food in a thinner layer, so it needs less time
        check("cooking.scaled", 21.4286, CookingCalculator.scaled(30.0, 20.0, 28.0))
    }

    /**
     * The wallet: balances, movements, several cash accounts in their own currencies,
     * the conversion a withdrawal needs, the "separate from the main balance" flag and
     * the JSON format written to `wallet.json`.
     */
    private fun walletTests() {
        // 1 USD = 3.70 ILS = 0.92 EUR, so 1 EUR = 4.0217 ILS
        val rates: RateSource = FixedRates(mapOf("USD" to 1.0, "ILS" to 3.7, "EUR" to 0.92), "USD")

        // ---- money helpers --------------------------------------------------------
        check("money.roundUp", 10.13, Money.round(10.125))
        check("money.roundDown", 10.12, Money.round(10.124))
        check("money.roundNaN", 0.0, Money.round(Double.NaN))
        check("money.parse", 12.5, Money.parse("12.504"))
        checkTrue("money.parseEmpty", Money.parse("") == null)
        checkTrue("money.parseWords", Money.parse("abc") == null)
        checkTrue("money.zeroIsNoAmount", !Money.isAmount(Money.parse("0")))
        // a movement is always positive - the direction comes from its kind - while an
        // opening balance may still be negative (an overdraft)
        checkTrue("money.negativeIsNoAmount", !Money.isAmount(Money.parse("-5")))
        check("money.parseNegativeOpening", -5.0, Money.parse("-5"))
        check("money.format", "1,250.50 ILS", Money.format(1250.5, "ILS"))
        check("money.formatZero", "0.00", Money.format(0.0))
        check("money.formatKeepsCents", "10.00", Money.format(10.0))
        check("money.formatRate", "4.0217", Money.format(4.021739, "", 4))

        // ---- the main balance -----------------------------------------------------
        val main = WalletLedger.starting("Main", "ILS", opening = 1000.0, createdAt = 1L)
        check("wallet.accountCount", 1, main.accounts.size)
        check("wallet.mainKind", WalletAccountKind.MAIN, main.mainAccount?.kind)
        val mainId = main.mainAccount!!.id
        check("wallet.opening", 1000.0, main.balanceOf(mainId))

        // ---- top up and expense ---------------------------------------------------
        val topped = done("wallet.topUp", main.topUp(mainId, 250.50, "salary", 2L))
        check("wallet.topUpBalance", 1250.5, topped.balanceOf(mainId))
        val spent = done("wallet.expense", topped.expense(mainId, 100.25, "groceries", 3L))
        check("wallet.expenseBalance", 1150.25, spent.balanceOf(mainId))
        check("wallet.entryCount", 2, spent.entries.size)
        check(
            "wallet.expenseRefused",
            WalletErrorCode.INSUFFICIENT,
            failureCode(spent.expense(mainId, 5000.0, "", 4L))
        )
        check("wallet.expenseZero", WalletErrorCode.AMOUNT, failureCode(spent.expense(mainId, 0.0, "", 5L)))
        check(
            "wallet.topUpUnknown",
            WalletErrorCode.ACCOUNT,
            failureCode(spent.topUp("nope", 10.0, "", 6L))
        )
        check("wallet.unknownBalance", 0.0, spent.balanceOf("nope"))

        // ---- several cash accounts, each in its own currency ----------------------
        val cashEur = WalletAccount(name = "Holiday", currency = "EUR", createdAt = 10L)
        val cashUsd = WalletAccount(
            name = "Trip",
            currency = "USD",
            includedInTotal = false,
            createdAt = 11L
        )
        val withCash = spent.withAccount(cashEur).withAccount(cashUsd)
        check("wallet.cashCount", 2, withCash.cashAccounts.size)
        check("wallet.cashCurrency", "EUR", withCash.account(cashEur.id)?.currency)
        checkTrue("wallet.cashCurrencyDiffers", withCash.account(cashEur.id)?.currency != withCash.mainAccount?.currency)

        // ---- withdrawal converts into the main currency ---------------------------
        val withdrawChange = withCash.withdraw(cashEur.id, 10.0, "airport", 12L, rates)
        val withdrawn = done("wallet.withdraw", withdrawChange)
        val withdrawEntry = (withdrawChange as? WalletChange.Done)?.entry
        check("wallet.withdrawCash", 10.0, withdrawn.balanceOf(cashEur.id))
        // 10 EUR = 10 / 0.92 * 3.70 = 40.22 ILS leave the main balance
        check("wallet.withdrawDebit", 40.22, withdrawEntry?.counterAmount)
        check("wallet.withdrawMainBalance", 1110.03, withdrawn.balanceOf(mainId))
        check("wallet.withdrawRate", 4.0217, withdrawEntry?.rate)
        check("wallet.withdrawKind", WalletEntryKind.WITHDRAW, withdrawEntry?.kind)
        check("wallet.withdrawNote", "airport", withdrawEntry?.note)
        check("wallet.withdrawCost", 40.22, withCash.withdrawalCost(cashEur.id, 10.0, rates))
        check("wallet.withdrawRateLine", 4.0217, withCash.withdrawalRate(cashEur.id, rates))

        // a pocket in the currency of the main balance needs no conversion
        val cashIls = WalletAccount(name = "Pocket", currency = "ILS", createdAt = 13L)
        val plainChange = withdrawn.withAccount(cashIls).withdraw(cashIls.id, 50.0, "", 14L, rates)
        val plain = done("wallet.withdrawSameCurrency", plainChange)
        check("wallet.plainDebit", 50.0, (plainChange as? WalletChange.Done)?.entry?.counterAmount)
        check("wallet.plainRate", 1.0, (plainChange as? WalletChange.Done)?.entry?.rate)
        check("wallet.plainMain", 1060.03, plain.balanceOf(mainId))
        check("wallet.plainCash", 50.0, plain.balanceOf(cashIls.id))

        // refused withdrawals
        check(
            "wallet.withdrawTooBig",
            WalletErrorCode.INSUFFICIENT,
            failureCode(withdrawn.withdraw(cashEur.id, 100000.0, "", 15L, rates))
        )
        check(
            "wallet.withdrawUnknownAccount",
            WalletErrorCode.ACCOUNT,
            failureCode(withdrawn.withdraw("nope", 5.0, "", 16L, rates))
        )
        check(
            "wallet.withdrawIntoMain",
            WalletErrorCode.ACCOUNT,
            failureCode(withdrawn.withdraw(mainId, 5.0, "", 17L, rates))
        )
        check(
            "wallet.withdrawNoMain",
            WalletErrorCode.NO_MAIN,
            failureCode(WalletLedger(listOf(cashEur), emptyList()).withdraw(cashEur.id, 5.0, "", 18L, rates))
        )
        check(
            "wallet.withdrawZero",
            WalletErrorCode.AMOUNT,
            failureCode(withdrawn.withdraw(cashEur.id, 0.0, "", 19L, rates))
        )
        check(
            "wallet.withdrawNegative",
            WalletErrorCode.AMOUNT,
            failureCode(withdrawn.withdraw(cashEur.id, -5.0, "", 20L, rates))
        )

        // ---- cash pockets take money in and pay things out too --------------------
        val pocketTopped = done("wallet.cashTopUp", withdrawn.topUp(cashEur.id, 20.0, "gift", 21L))
        check("wallet.cashTopUpBalance", 30.0, pocketTopped.balanceOf(cashEur.id))
        val pocketSpent = done("wallet.cashExpense", pocketTopped.expense(cashEur.id, 5.5, "coffee", 22L))
        check("wallet.cashExpenseBalance", 24.5, pocketSpent.balanceOf(cashEur.id))

        // ---- the combined total and the "separate" flag ---------------------------
        check("wallet.totalIncludesCash", 1150.25, withdrawn.totalIn("ILS", rates))
        check("wallet.valueOfMain", 300.01, withdrawn.valueOf(mainId, "USD", rates))
        val separated = withdrawn.withAccount(cashEur.copy(includedInTotal = false))
        check("wallet.totalExcludesSeparate", 1110.03, separated.totalIn("ILS", rates))
        check("wallet.separateCount", 2, separated.separateAccounts().size)
        check("wallet.includedCount", 1, separated.includedAccounts().size)
        check("wallet.separateBalanceKept", 10.0, separated.balanceOf(cashEur.id))
        val included = separated.withAccount(cashEur.copy(includedInTotal = true))
        check("wallet.includeAgain", 1150.25, included.totalIn("ILS", rates))

        // ---- removing the entry undoes the movement -------------------------------
        val withoutWithdraw = WalletLedger(
            withdrawn.accounts,
            withdrawn.entries.filter { it.kind != WalletEntryKind.WITHDRAW }
        )
        check("wallet.undoMain", 1150.25, withoutWithdraw.balanceOf(mainId))
        check("wallet.undoCash", 0.0, withoutWithdraw.balanceOf(cashEur.id))

        // ---- account removal rules -------------------------------------------------
        checkTrue("wallet.mainNotRemovable", !withdrawn.canRemove(mainId))
        checkTrue("wallet.usedNotRemovable", !withdrawn.canRemove(cashEur.id))
        checkTrue("wallet.emptyRemovable", withdrawn.canRemove(cashUsd.id))
        check("wallet.removeEmpty", null, withdrawn.removeAccount(cashUsd.id).account(cashUsd.id))
        check(
            "wallet.removeMainNoop",
            withdrawn.accounts.size,
            withdrawn.removeAccount(mainId).accounts.size
        )
        check(
            "wallet.removeUsedNoop",
            withdrawn.accounts.size,
            withdrawn.removeAccount(cashEur.id).accounts.size
        )

        // ---- the persisted document -------------------------------------------------
        val restored = WalletLedger.fromJson(withdrawn.toJson())
        check("wallet.jsonAccounts", withdrawn.accounts.size, restored.accounts.size)
        check("wallet.jsonEntries", withdrawn.entries.size, restored.entries.size)
        check("wallet.jsonMainBalance", withdrawn.balanceOf(mainId), restored.balanceOf(mainId))
        check("wallet.jsonCashBalance", withdrawn.balanceOf(cashEur.id), restored.balanceOf(cashEur.id))
        check("wallet.jsonCurrency", "EUR", restored.account(cashEur.id)?.currency)
        check("wallet.jsonSeparateFlag", false, restored.account(cashUsd.id)?.includedInTotal)
        check("wallet.jsonMainFlag", true, restored.account(mainId)?.includedInTotal)
        check(
            "wallet.jsonStillUsable",
            true,
            restored.withdraw(cashEur.id, 1.0, "", 30L, rates) is WalletChange.Done
        )

        // the same document after it went through the writer and the parser
        val reloaded = WalletLedger.fromJson(
            JsonParser.parse(JsonWriter.write(withdrawn.toJson(), pretty = true))
        )
        check("wallet.textRoundTripMain", withdrawn.balanceOf(mainId), reloaded.balanceOf(mainId))
        check("wallet.textRoundTripCash", withdrawn.balanceOf(cashEur.id), reloaded.balanceOf(cashEur.id))
        val recorded = reloaded.entries.first { it.kind == WalletEntryKind.WITHDRAW }
        check("wallet.textRoundTripNote", "airport", recorded.note)
        check("wallet.textRoundTripCounter", 40.22, recorded.counterAmount)
        check("wallet.textRoundTripRate", 4.0217, recorded.rate)
        check("wallet.entriesNewestFirst", true, reloaded.entries.first().timestamp >= reloaded.entries.last().timestamp)
    }
}
