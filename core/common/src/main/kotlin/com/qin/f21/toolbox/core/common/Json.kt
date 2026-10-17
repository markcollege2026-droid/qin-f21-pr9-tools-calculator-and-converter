package com.qin.f21.toolbox.core.common

/**
 * Minimal, dependency-free JSON model + parser + writer.
 *
 * The toolbox deliberately avoids third-party libraries (and even the platform `org.json`
 * package) so that the pure-JVM modules stay unit-testable off-device and the APK stays small.
 */
sealed class Json {
    object Null : Json()

    data class Bool(val value: Boolean) : Json()

    data class Num(val value: Double) : Json()

    data class Str(val value: String) : Json()

    data class Arr(val items: List<Json>) : Json()

    data class Obj(val entries: Map<String, Json>) : Json() {
        operator fun get(key: String): Json? = entries[key]
    }

    // ---- convenience accessors -------------------------------------------------

    fun asDouble(default: Double = 0.0): Double = (this as? Num)?.value ?: default

    fun asInt(default: Int = 0): Int = (this as? Num)?.value?.toInt() ?: default

    fun asLong(default: Long = 0L): Long = (this as? Num)?.value?.toLong() ?: default

    fun asString(default: String = ""): String = (this as? Str)?.value ?: default

    fun asBoolean(default: Boolean = false): Boolean = (this as? Bool)?.value ?: default

    fun asArray(): List<Json> = (this as? Arr)?.items ?: emptyList()

    fun asObject(): Map<String, Json> = (this as? Obj)?.entries ?: emptyMap()

    fun string(key: String, default: String = ""): String = (asObject()[key] as? Str)?.value ?: default

    fun double(key: String, default: Double = 0.0): Double = (asObject()[key] as? Num)?.value ?: default

    fun int(key: String, default: Int = 0): Int = (asObject()[key] as? Num)?.value?.toInt() ?: default

    fun long(key: String, default: Long = 0L): Long = (asObject()[key] as? Num)?.value?.toLong() ?: default

    fun bool(key: String, default: Boolean = false): Boolean = (asObject()[key] as? Bool)?.value ?: default

    fun obj(key: String): Json = asObject()[key] ?: Obj(emptyMap())

    fun arr(key: String): List<Json> = (asObject()[key] as? Arr)?.items ?: emptyList()

    companion object {
        fun of(value: String): Json = Str(value)
        fun of(value: Double): Json = Num(value)
        fun of(value: Int): Json = Num(value.toDouble())
        fun of(value: Long): Json = Num(value.toDouble())
        fun of(value: Boolean): Json = Bool(value)
        fun array(items: List<Json>): Json = Arr(items)
        fun obj(vararg pairs: Pair<String, Json>): Json = Obj(linkedMapOf(*pairs))
    }
}

class JsonParseException(message: String) : RuntimeException(message)

/** Recursive-descent JSON parser. Accepts the standard JSON grammar. */
object JsonParser {

    fun parse(text: String): Json {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw JsonParseException("Trailing data at ${reader.position}")
        return value
    }

    fun parseOrNull(text: String): Json? = try {
        parse(text)
    } catch (e: Exception) {
        null
    }

    private class Reader(private val text: String) {
        var position = 0

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readValue(): Json {
            if (atEnd()) throw JsonParseException("Unexpected end of input")
            return when (val c = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> Json.Str(readString())
                't' -> readLiteral("true", Json.Bool(true))
                'f' -> readLiteral("false", Json.Bool(false))
                'n' -> readLiteral("null", Json.Null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw JsonParseException("Unexpected '$c' at $position")
            }
        }

        private fun readLiteral(literal: String, value: Json): Json {
            if (!text.startsWith(literal, position)) throw JsonParseException("Invalid literal at $position")
            position += literal.length
            return value
        }

        private fun readObject(): Json {
            position++ // {
            val entries = LinkedHashMap<String, Json>()
            skipWhitespace()
            if (!atEnd() && text[position] == '}') {
                position++
                return Json.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[position] != '"') throw JsonParseException("Expected key at $position")
                val key = readString()
                skipWhitespace()
                if (atEnd() || text[position] != ':') throw JsonParseException("Expected ':' at $position")
                position++
                skipWhitespace()
                entries[key] = readValue()
                skipWhitespace()
                if (atEnd()) throw JsonParseException("Unterminated object")
                when (text[position]) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return Json.Obj(entries)
                    }
                    else -> throw JsonParseException("Expected ',' or '}' at $position")
                }
            }
        }

        private fun readArray(): Json {
            position++ // [
            val items = ArrayList<Json>()
            skipWhitespace()
            if (!atEnd() && text[position] == ']') {
                position++
                return Json.Arr(items)
            }
            while (true) {
                skipWhitespace()
                items.add(readValue())
                skipWhitespace()
                if (atEnd()) throw JsonParseException("Unterminated array")
                when (text[position]) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return Json.Arr(items)
                    }
                    else -> throw JsonParseException("Expected ',' or ']' at $position")
                }
            }
        }

        private fun readString(): String {
            position++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonParseException("Unterminated string")
                when (val c = text[position]) {
                    '"' -> {
                        position++
                        return sb.toString()
                    }
                    '\\' -> {
                        position++
                        if (atEnd()) throw JsonParseException("Unterminated escape")
                        when (val esc = text[position]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (position + 4 >= text.length) throw JsonParseException("Bad unicode escape")
                                val hex = text.substring(position + 1, position + 5)
                                sb.append(hex.toInt(16).toChar())
                                position += 4
                            }
                            else -> throw JsonParseException("Bad escape '\\$esc'")
                        }
                        position++
                    }
                    else -> {
                        sb.append(c)
                        position++
                    }
                }
            }
        }

        private fun readNumber(): Json {
            val start = position
            if (!atEnd() && text[position] == '-') position++
            while (!atEnd() && text[position].isDigit()) position++
            if (!atEnd() && text[position] == '.') {
                position++
                while (!atEnd() && text[position].isDigit()) position++
            }
            if (!atEnd() && (text[position] == 'e' || text[position] == 'E')) {
                position++
                if (!atEnd() && (text[position] == '+' || text[position] == '-')) position++
                while (!atEnd() && text[position].isDigit()) position++
            }
            val slice = text.substring(start, position)
            return Json.Num(slice.toDoubleOrNull() ?: throw JsonParseException("Bad number '$slice'"))
        }
    }
}

/** Compact JSON writer used for history/favourites export and for persisted state files. */
object JsonWriter {
    fun write(value: Json, pretty: Boolean = false): String {
        val sb = StringBuilder()
        writeValue(sb, value, pretty, 0)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, value: Json, pretty: Boolean, depth: Int) {
        when (value) {
            is Json.Null -> sb.append("null")
            is Json.Bool -> sb.append(if (value.value) "true" else "false")
            is Json.Num -> sb.append(formatNumber(value.value))
            is Json.Str -> writeString(sb, value.value)
            is Json.Arr -> {
                if (value.items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    if (pretty) newline(sb, depth + 1)
                    writeValue(sb, item, pretty, depth + 1)
                }
                if (pretty) newline(sb, depth)
                sb.append(']')
            }
            is Json.Obj -> {
                if (value.entries.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                var index = 0
                for ((key, item) in value.entries) {
                    if (index++ > 0) sb.append(',')
                    if (pretty) newline(sb, depth + 1)
                    writeString(sb, key)
                    sb.append(':')
                    if (pretty) sb.append(' ')
                    writeValue(sb, item, pretty, depth + 1)
                }
                if (pretty) newline(sb, depth)
                sb.append('}')
            }
        }
    }

    private fun newline(sb: StringBuilder, depth: Int) {
        sb.append('\n')
        repeat(depth) { sb.append("  ") }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "null"
        if (value == Math.floor(value) && Math.abs(value) < 1e15) return value.toLong().toString()
        return value.toString()
    }

    private fun writeString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == '\u2028' || c == '\u2029') {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}
