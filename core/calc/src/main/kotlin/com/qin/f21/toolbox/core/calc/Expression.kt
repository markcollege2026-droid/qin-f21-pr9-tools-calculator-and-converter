package com.qin.f21.toolbox.core.calc

import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

class CalcException(message: String) : Exception(message)

/**
 * A small, dependency free expression engine used by the scientific calculator.
 *
 * Supports + - * / % ^ !, parentheses, implicit multiplication (2pi, 3(4+5)), the constants
 * pi and e, and the usual scientific functions. Errors are reported with messages that fit on
 * the small screen instead of stack traces.
 */
class ExpressionEvaluator {

    private var input = ""
    private var position = 0
    private var previousAnswer = 0.0

    fun evaluate(expression: String, answer: Double = 0.0): Double {
        // Only thousand separators are removed, commas between function arguments stay.
        input = expression.replace("×", "*").replace("·", "*").replace("÷", "/")
            .replace(THOUSAND_SEPARATOR, "")
            .replace("−", "-").replace("√", "sqrt")
        position = 0
        previousAnswer = answer
        if (input.isBlank()) throw CalcException("Enter an expression")
        val value = parseExpression()
        skipSpaces()
        if (position < input.length) throw CalcException("Unexpected '${input[position]}'")
        if (!value.isFinite()) throw CalcException("Result is not a finite number")
        return value
    }

    // ---- grammar ---------------------------------------------------------------

    private fun parseExpression(): Double {
        var left = parseTerm()
        while (true) {
            skipSpaces()
            if (eat('+')) {
                left += parseTerm()
            } else if (eat('-')) {
                left -= parseTerm()
            } else {
                return left
            }
        }
    }

    private fun parseTerm(): Double {
        var left = parseUnary()
        while (true) {
            skipSpaces()
            val ch = peek()
            if (ch == '*' || ch == '×') {
                position++
                left *= parseUnary()
            } else if (ch == '/') {
                position++
                val right = parseUnary()
                if (right == 0.0) throw CalcException("Division by zero")
                left /= right
            } else if (ch == '%') {
                position++
                val right = parseUnary()
                if (right == 0.0) throw CalcException("Division by zero")
                left = left.mod(right)
            } else {
                return left
            }
        }
    }

    private fun parseUnary(): Double {
        skipSpaces()
        if (eat('-')) return -parseUnary()
        if (eat('+')) return parseUnary()
        return parsePower()
    }

    private fun parsePower(): Double {
        val base = parsePostfix()
        skipSpaces()
        if (peek() == '^') {
            position++
            val exponent = parseUnary() // right associative
            return base.pow(exponent)
        }
        return base
    }

    private fun parsePostfix(): Double {
        var value = parsePrimary()
        while (true) {
            skipSpaces()
            if (peek() == '!' && !isFunctionPosition()) {
                position++
                value = factorial(value)
                continue
            }
            // implicit multiplication: 2pi, 2(3), 3(4+5)
            val ch = peek()
            if (ch != '(' && !ch.isLetter()) return value
            if (ch == '(') {
                value *= parsePrimary()
                continue
            }
            val start = position
            val word = readWord()
            if (word.isEmpty()) {
                position = start
                return value
            }
            val lower = word.lowercase(Locale.US)
            if (lower in CONSTANTS) {
                value *= CONSTANTS[lower]!!
                continue
            }
            if (lower == "ans") {
                value *= previousAnswer
                continue
            }
            // unknown identifier directly after a number -> treat as multiplication by name
            throw CalcException("Unknown name '$word'")
        }
    }

    private fun parsePrimary(): Double {
        skipSpaces()
        if (position >= input.length) throw CalcException("Unexpected end of expression")
        val ch = input[position]
        if (ch == '(') {
            position++
            val value = parseExpression()
            skipSpaces()
            if (!eat(')')) throw CalcException("Missing ')'")
            return value
        }
        if (ch.isDigit() || ch == '.') return parseNumber()
        if (ch.isLetter()) {
            val word = readWord()
            val lower = word.lowercase(Locale.US)
            if (lower in CONSTANTS) return CONSTANTS[lower]!!
            if (lower == "ans") return previousAnswer
            skipSpaces()
            if (peek() == '(') {
                position++
                return callFunction(lower)
            }
            throw CalcException("Unknown name '$word'")
        }
        throw CalcException("Unexpected '$ch'")
    }

    private fun callFunction(name: String): Double {
        val args = ArrayList<Double>()
        skipSpaces()
        if (peek() == ')') {
            position++
            return unaryFunction(name, 0.0)
        }
        while (true) {
            args.add(parseExpression())
            skipSpaces()
            if (eat(',')) continue
            if (eat(')')) break
            throw CalcException("Missing ')' in $name()")
        }
        return when (name) {
            "min" -> args.min()!!
            "max" -> args.max()!!
            "pow" -> {
                if (args.size != 2) throw CalcException("pow() needs 2 values")
                args[0].pow(args[1])
            }
            "atan2" -> {
                if (args.size != 2) throw CalcException("atan2() needs 2 values")
                atan2(args[0], args[1])
            }
            "hypot" -> hypot(args[0], args[1])
            "mod" -> {
                if (args.size != 2) throw CalcException("mod() needs 2 values")
                args[0].mod(args[1])
            }
            "round" -> {
                val digits = if (args.size > 1) args[1].toInt() else 0
                roundTo(args[0], digits)
            }
            else -> {
                if (args.size != 1) throw CalcException("$name() needs 1 value")
                unaryFunction(name, args[0])
            }
        }
    }

    private fun unaryFunction(name: String, x: Double): Double = when (name) {
        "sin" -> sin(x)
        "cos" -> cos(x)
        "tan" -> tan(x)
        "asin" -> asin(x)
        "acos" -> acos(x)
        "atan" -> atan(x)
        "sinh" -> sinh(x)
        "cosh" -> cosh(x)
        "tanh" -> tanh(x)
        "asinh" -> asinh(x)
        "acosh" -> acosh(x)
        "atanh" -> atanh(x)
        "ln" -> ln(x)
        "log" -> log10(x)
        "log2" -> log2(x)
        "sqrt" -> {
            if (x < 0) throw CalcException("√ of a negative number")
            sqrt(x)
        }
        "cbrt" -> cbrt(x)
        "exp" -> exp(x)
        "abs" -> abs(x)
        "floor" -> floor(x)
        "ceil" -> ceil(x)
        "sign" -> sign(x)
        "fact" -> factorial(x)
        "deg" -> Math.toDegrees(x)
        "rad" -> Math.toRadians(x)
        else -> throw CalcException("Unknown function '$name'")
    }

    private fun parseNumber(): Double {
        val start = position
        while (position < input.length && (input[position].isDigit() || input[position] == '.')) position++
        if (position < input.length && (input[position] == 'e' || input[position] == 'E')) {
            val save = position
            position++
            if (position < input.length && (input[position] == '+' || input[position] == '-')) position++
            if (position < input.length && input[position].isDigit()) {
                while (position < input.length && input[position].isDigit()) position++
            } else {
                position = save
            }
        }
        val text = input.substring(start, position)
        return text.toDoubleOrNull() ?: throw CalcException("Bad number '$text'")
    }

    private fun readWord(): String {
        val start = position
        while (position < input.length && input[position].isLetter()) position++
        return input.substring(start, position)
    }

    private fun isFunctionPosition(): Boolean = false

    private fun skipSpaces() {
        while (position < input.length && input[position].isWhitespace()) position++
    }

    private fun peek(): Char = if (position < input.length) input[position] else '\u0000'

    private fun eat(ch: Char): Boolean {
        if (position < input.length && input[position] == ch) {
            position++
            return true
        }
        return false
    }

    private fun factorial(x: Double): Double {
        if (x < 0 || x != floor(x)) throw CalcException("Factorial needs a whole number ≥ 0")
        if (x > 170) throw CalcException("Factorial too large")
        var result = 1.0
        var i = 2
        while (i <= x) {
            result *= i
            i++
        }
        return result
    }

    private fun roundTo(x: Double, digits: Int): Double {
        var factor = 1.0
        repeat(digits.coerceIn(0, 12)) { factor *= 10.0 }
        return round(x * factor) / factor
    }

    companion object {
        /** "1,234" becomes "1234", but "mod(7,3)" keeps its argument separator. */
        private val THOUSAND_SEPARATOR = Regex("(?<=\\d),(?=\\d{3}(?!\\d))")
        val CONSTANTS: Map<String, Double> = mapOf(
            "pi" to Math.PI,
            "π" to Math.PI,
            "e" to Math.E,
            "tau" to Math.PI * 2
        )
    }
}
