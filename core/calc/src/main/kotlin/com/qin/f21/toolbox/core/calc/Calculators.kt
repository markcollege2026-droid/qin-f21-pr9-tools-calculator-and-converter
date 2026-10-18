package com.qin.f21.toolbox.core.calc

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Percentage helpers. */
object PercentCalculator {
    /** "X % of Y" */
    fun percentOf(percent: Double, of: Double): Double = of * percent / 100.0

    /** "X is what percent of Y" */
    fun whatPercent(part: Double, whole: Double): Double {
        if (whole == 0.0) throw CalcException("Cannot divide by zero")
        return part / whole * 100.0
    }

    /** Percentage change from [oldValue] to [newValue]. */
    fun change(oldValue: Double, newValue: Double): Double {
        if (oldValue == 0.0) throw CalcException("Cannot divide by zero")
        return (newValue - oldValue) / abs(oldValue) * 100.0
    }

    /** Percentage decrease/increase needed to reach [target] from [current]. */
    fun needed(current: Double, target: Double): Double {
        if (current == 0.0) throw CalcException("Cannot divide by zero")
        return (target - current) / abs(current) * 100.0
    }
}

/** Discount, tax and tip calculations. */
object PriceCalculator {
    data class Breakdown(
        val original: Double,
        val discount: Double,
        val taxed: Double,
        val total: Double,
        val saved: Double
    )

    fun breakdown(price: Double, discountPercent: Double = 0.0, taxPercent: Double = 0.0, tipPercent: Double = 0.0): Breakdown {
        if (price < 0) throw CalcException("Price cannot be negative")
        val saved = price * discountPercent / 100.0
        val afterDiscount = price - saved
        val tip = afterDiscount * tipPercent / 100.0
        val taxed = afterDiscount * taxPercent / 100.0
        return Breakdown(price, saved, taxed, afterDiscount + taxed + tip, saved)
    }

    fun split(total: Double, people: Int): List<Double> {
        if (people <= 0) throw CalcException("People must be at least 1")
        val each = total / people
        return List(people) { index -> if (index == people - 1) total - each * (people - 1) else each }
    }
}

/** Body mass index. */
object BmiCalculator {
    data class Result(val bmi: Double, val category: String, val healthyMin: Double, val healthyMax: Double)

    private fun categoryOf(bmi: Double): String = when {
        bmi < 16.0 -> "Severely underweight"
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Healthy weight"
        bmi < 30.0 -> "Overweight"
        bmi < 35.0 -> "Obese (class I)"
        bmi < 40.0 -> "Obese (class II)"
        else -> "Obese (class III)"
    }

    fun metric(weightKg: Double, heightCm: Double): Result {
        if (heightCm <= 0) throw CalcException("Height must be greater than zero")
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        return Result(bmi, categoryOf(bmi), 18.5 * heightM * heightM, 24.9 * heightM * heightM)
    }

    fun imperial(weightLb: Double, heightIn: Double): Result {
        val bmi = 703.0 * weightLb / (heightIn * heightIn)
        return Result(bmi, categoryOf(bmi), 18.5 * (heightIn * heightIn) / 703.0, 24.9 * (heightIn * heightIn) / 703.0)
    }
}

/** Loan / mortgage amortisation. */
object LoanCalculator {
    data class Payment(val monthly: Double, val totalPaid: Double, val totalInterest: Double, val months: Int)

    data class Row(val month: Int, val payment: Double, val principal: Double, val interest: Double, val balance: Double)

    fun payment(principal: Double, annualRatePercent: Double, months: Int): Payment {
        if (principal <= 0) throw CalcException("Loan amount must be greater than zero")
        if (months <= 0) throw CalcException("Term must be at least one month")
        val r = annualRatePercent / 100.0 / 12.0
        val monthly = if (r == 0.0) {
            principal / months
        } else {
            val factor = (1.0 + r).pow(months)
            principal * r * factor / (factor - 1.0)
        }
        val total = monthly * months
        return Payment(monthly, total, total - principal, months)
    }

    fun schedule(principal: Double, annualRatePercent: Double, months: Int): List<Row> {
        val r = annualRatePercent / 100.0 / 12.0
        val monthly = payment(principal, annualRatePercent, months).monthly
        var balance = principal
        val rows = ArrayList<Row>(months)
        for (month in 1..months) {
            val interest = if (r == 0.0) 0.0 else balance * r
            val principalPart = monthly - interest
            balance -= principalPart
            if (balance < 0.0) balance = 0.0
            rows.add(Row(month, monthly, principalPart, interest, balance))
        }
        return rows
    }
}

/** Date and time helpers (offline, using the device calendar). */
object DateCalculator {
    data class Difference(
        val years: Long,
        val months: Long,
        val days: Long,
        val totalDays: Long,
        val totalHours: Long,
        val totalMinutes: Long
    )

    fun between(start: LocalDate, end: LocalDate): Difference {
        var years = 0L
        var months = 0L
        var cursor = start
        while (true) {
            val candidate = cursor.plusYears(1)
            if (candidate.isAfter(end)) break
            cursor = candidate
            years++
        }
        while (true) {
            val candidate = cursor.plusMonths(1)
            if (candidate.isAfter(end)) break
            cursor = candidate
            months++
        }
        val days = ChronoUnit.DAYS.between(cursor, end)
        val totalDays = ChronoUnit.DAYS.between(start, end)
        return Difference(years, months, days, totalDays, totalDays * 24, totalDays * 24 * 60)
    }

    fun add(start: LocalDate, years: Int, months: Int, days: Int): LocalDate =
        start.plusYears(years.toLong()).plusMonths(months.toLong()).plusDays(days.toLong())

    fun age(birth: LocalDate, reference: LocalDate): Difference = between(birth, reference)
}

/** Number theory helpers. */
object NumberCalculator {
    fun gcd(a: Long, b: Long): Long {
        var x = abs(a)
        var y = abs(b)
        while (y != 0L) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }

    fun lcm(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        return abs(a / gcd(a, b) * b)
    }

    fun factorize(value: Long): List<Pair<Long, Int>> {
        if (value <= 1) return emptyList()
        var n = value
        val factors = ArrayList<Pair<Long, Int>>()
        var d = 2L
        while (d * d <= n) {
            if (n % d == 0L) {
                var count = 0
                while (n % d == 0L) {
                    n /= d
                    count++
                }
                factors.add(d to count)
            }
            d = if (d == 2L) 3L else d + 2
        }
        if (n > 1) factors.add(n to 1)
        return factors
    }

    fun isPrime(value: Long): Boolean = value > 1 && factorize(value).size == 1

    /** Integer to Roman numerals (1..3999). */
    fun toRoman(value: Int): String {
        if (value !in 1..3999) throw CalcException("Roman numerals support 1 to 3999")
        val table = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var remaining = value
        val sb = StringBuilder()
        for ((num, symbol) in table) {
            while (remaining >= num) {
                sb.append(symbol)
                remaining -= num
            }
        }
        return sb.toString()
    }

    /** Roman numerals back to an integer. */
    fun fromRoman(text: String): Int {
        val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        val upper = text.trim().uppercase(Locale.US)
        if (upper.isEmpty() || upper.any { it !in map }) throw CalcException("Not a roman numeral")
        var total = 0
        var previous = 0
        for (i in upper.indices.reversed()) {
            val value = map[upper[i]]!!
            if (value < previous) total -= value else total += value
            previous = max(previous, value)
        }
        return total
    }
}

/** Number base conversion (2, 8, 10, 16 and any base 2..36). */
object BaseConverter {
    val bases = listOf(2, 8, 10, 16, 32, 36)

    fun convert(value: String, fromBase: Int, toBase: Int): String {
        val parsed = parse(value, fromBase) ?: throw CalcException("Not a valid base-$fromBase number")
        return encode(parsed, toBase)
    }

    fun parse(value: String, base: Int): Long? {
        val text = value.trim().uppercase(Locale.US)
        if (text.isEmpty()) return null
        var result = 0L
        for (c in text) {
            val digit = when {
                c in '0'..'9' -> c - '0'
                c in 'A'..'Z' -> c - 'A' + 10
                else -> return null
            }
            if (digit >= base) return null
            result = result * base + digit
        }
        return result
    }

    fun encode(value: Long, base: Int): String {
        if (base < 2 || base > 36) throw CalcException("Base must be between 2 and 36")
        if (value == 0L) return "0"
        val digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val sb = StringBuilder()
        var n = if (value < 0) -value else value
        while (n > 0) {
            sb.append(digits[(n % base).toInt()])
            n /= base
        }
        if (value < 0) sb.append('-')
        return sb.reverse().toString()
    }
}

/** Random number generator (no network, seeded locally). */
object RandomCalculator {
    private val random = java.util.Random()

    fun inRange(min: Long, max: Long, count: Int, allowRepeats: Boolean = true): List<Long> {
        if (min > max) throw CalcException("Minimum must not exceed maximum")
        if (count <= 0) throw CalcException("Count must be at least 1")
        if (count > 1000) throw CalcException("At most 1000 numbers at a time")
        val values = ArrayList<Long>(count)
        if (allowRepeats) {
            repeat(count) { values.add(nextLong(min, max)) }
        } else {
            val pool = ArrayList<Long>()
            var span = max - min + 1
            if (span > count * 1000L) span = count * 1000L
            for (i in 0 until span) pool.add(min + i)
            for (i in pool.indices) {
                val j = random.nextInt(i + 1)
                val tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp
            }
            values.addAll(pool.take(count))
        }
        return values
    }

    private fun nextLong(min: Long, max: Long): Long {
        if (min == max) return min
        val range = max - min
        return if (range < Long.MAX_VALUE) min + (random.nextDouble() * (range + 1)).toLong().coerceAtMost(range)
        else min + random.nextLong()
    }
}

/** Plane and solid geometry. */
object GeometryCalculator {
    fun circleArea(radius: Double): Double = Math.PI * radius * radius
    fun circleCircumference(radius: Double): Double = 2.0 * Math.PI * radius
    fun sphereVolume(radius: Double): Double = 4.0 / 3.0 * Math.PI * radius * radius * radius
    fun sphereArea(radius: Double): Double = 4.0 * Math.PI * radius * radius
    fun cylinderVolume(radius: Double, height: Double): Double = Math.PI * radius * radius * height
    fun rectangleArea(width: Double, height: Double): Double = width * height
    fun rectanglePerimeter(width: Double, height: Double): Double = 2.0 * (width + height)
    fun triangleArea(base: Double, height: Double): Double = base * height / 2.0
    fun rightTriangleHypotenuse(a: Double, b: Double): Double = sqrt(a * a + b * b)
    fun trapezoidArea(a: Double, b: Double, height: Double): Double = (a + b) * height / 2.0
}

/** Pace, speed and fuel cost helpers. */
object TravelCalculator {
    /** Average speed for a distance (km) and duration (minutes). */
    fun averageSpeed(distanceKm: Double, minutes: Double): Double {
        if (minutes <= 0) throw CalcException("Time must be greater than zero")
        return distanceKm / (minutes / 60.0)
    }

    /** Minutes per kilometre. */
    fun pace(distanceKm: Double, minutes: Double): Double {
        if (distanceKm <= 0) throw CalcException("Distance must be greater than zero")
        return minutes / distanceKm
    }

    /** Fuel cost for a trip. */
    fun fuelCost(distanceKm: Double, consumptionPer100Km: Double, pricePerLitre: Double): Double =
        distanceKm / 100.0 * consumptionPer100Km * pricePerLitre
}

/** Duration formatting helpers used by the timer and stopwatch. */
object DurationFormatter {
    fun format(millis: Long, showMillis: Boolean = false): String {
        val negative = millis < 0
        val total = abs(millis)
        val hours = total / 3_600_000
        val minutes = (total % 3_600_000) / 60_000
        val seconds = (total % 60_000) / 1000
        val ms = total % 1000
        val sb = StringBuilder()
        if (negative) sb.append('-')
        if (hours > 0) sb.append(String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds))
        else sb.append(String.format(Locale.US, "%02d:%02d", minutes, seconds))
        if (showMillis) sb.append(String.format(Locale.US, ".%03d", ms))
        return sb.toString()
    }

    fun formatStopwatch(millis: Long): String {
        val minutes = millis / 60_000
        val seconds = (millis % 60_000) / 1000
        val ms = millis % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, ms)
    }
}

/** Cooking time scaling (e.g. double the pan size -> half the time). */
object CookingCalculator {
    fun scaled(originalMinutes: Double, originalPanArea: Double, newPanArea: Double): Double {
        if (newPanArea <= 0) throw CalcException("Pan area must be greater than zero")
        return originalMinutes * originalPanArea / newPanArea
    }

    fun ovenToFan(originalMinutes: Double): Double = originalMinutes * 0.8

    fun toFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0
}

/** Simple interest helper. */
object InterestCalculator {
    fun simpleInterest(principal: Double, ratePercent: Double, years: Double): Double =
        principal * ratePercent / 100.0 * years

    fun compoundInterest(principal: Double, ratePercent: Double, years: Double): Double {
        val n = years
        val r = ratePercent / 100.0
        return principal * Math.pow(1.0 + r, n) - principal
    }

    fun monthlyPayment(principal: Double, annualRatePercent: Double, years: Int): Double =
        LoanCalculator.payment(principal, annualRatePercent, years * 12).monthly
}

/** Rounding helpers shared by the calculator screens. */
object Rounding {
    fun round(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals.coerceIn(0, 12)) { factor *= 10.0 }
        return (value * factor).roundToInt() / factor
    }

    fun clamp(value: Double, min: Double, max: Double): Double = min(max(value, min), max)
}
