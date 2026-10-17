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
import com.qin.f21.toolbox.core.common.JsonWriter
import com.qin.f21.toolbox.core.common.Numbers
import com.qin.f21.toolbox.core.conversion.ConversionEngine
import com.qin.f21.toolbox.core.conversion.CurrencyTable
import com.qin.f21.toolbox.core.conversion.UnitCatalog
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
}
