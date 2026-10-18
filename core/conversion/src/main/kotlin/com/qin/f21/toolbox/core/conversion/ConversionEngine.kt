package com.qin.f21.toolbox.core.conversion

import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.common.Numbers
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The conversion engine.
 *
 * Every conversion goes through the category base unit:
 *
 *     base = from.toBase(value) ; result = to.fromBase(base)
 *
 * which keeps the arithmetic exact for affine scales (temperature) and for inverse scales
 * (fuel economy) instead of hard-coding special cases in the UI layer.
 */
class ConversionEngine(val categories: List<Category> = UnitCatalog.all) {

    private val byId: Map<String, Category> = categories.associateBy { it.id }

    fun category(id: String): Category? = byId[id]

    fun categories(): List<Category> = categories

    /** Converts between two units of the same category. */
    fun convert(value: Double, category: Category, fromId: String, toId: String): Double {
        val from = category.requireUnit(fromId)
        val to = category.requireUnit(toId)
        return convert(value, from, to)
    }

    fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        if (from.id == to.id) return value
        val base = when (val t = from.transform) {
            is Transform.Linear -> t.toBase(value)
            is Transform.Reciprocal -> t.toBase(value)
        }
        return when (val t = to.transform) {
            is Transform.Linear -> t.fromBase(base)
            is Transform.Reciprocal -> t.fromBase(base)
        }
    }

    /** Full-precision conversion for every unit of a category (used for the "all units" table). */
    fun convertToAll(value: Double, category: Category, fromId: String): List<Pair<UnitDef, Double>> {
        val from = category.requireUnit(fromId)
        return category.units.map { it to convert(value, from, it) }
    }

    fun format(value: Double, unit: UnitDef): String = Numbers.format(value) + " " + unit.symbol

    /** Case-insensitive search over categories and units. */
    fun search(query: String): List<SearchHit> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for (category in categories) {
            if (category.title.lowercase(Locale.US).contains(q) || category.id.contains(q)) {
                hits.add(SearchHit(category, category.requireUnit(category.defaultFrom), category.title))
            }
            for (unit in category.units) {
                if (unit.searchable.contains(q)) {
                    hits.add(SearchHit(category, unit, unit.name))
                }
            }
        }
        return hits.distinctBy { it.category.id + "|" + it.unit.id }.take(40)
    }
}

data class SearchHit(val category: Category, val unit: UnitDef, val label: String)

/**
 * Offline currency table.
 *
 * Rates are indicative and bundled with the app - the toolbox never contacts the network.
 * Users can edit any rate in Settings; the overrides are stored separately and take priority.
 */
class CurrencyTable(
    val base: String,
    val rates: Map<String, Double>,
    val updated: String,
    val overrides: Map<String, Double> = emptyMap()
) {

    val currencies: List<String> get() = (rates.keys + overrides.keys).sorted()

    fun rate(code: String): Double = overrides[code] ?: rates[code] ?: 1.0

    fun convert(amount: Double, from: String, to: String): Double {
        if (from == to) return amount
        return amount / rate(from) * rate(to)
    }

    fun withOverrides(newOverrides: Map<String, Double>): CurrencyTable =
        CurrencyTable(base, rates, updated, newOverrides)

    companion object {
        val EMPTY = CurrencyTable("USD", mapOf("USD" to 1.0), "")

        fun parse(json: Json): CurrencyTable {
            val base = json.string("base", "USD")
            val rates = LinkedHashMap<String, Double>()
            for ((code, value) in json.obj("rates").asObject()) {
                val rate = (value as? Json.Num)?.value ?: continue
                if (rate > 0.0) rates[code] = rate
            }
            rates[base] = 1.0
            return CurrencyTable(base, rates, json.string("updated"))
        }

        fun parseWithOverrides(json: Json, overrides: Map<String, Double>): CurrencyTable {
            val table = parse(json)
            return table.withOverrides(overrides)
        }
    }
}

/**
 * Time zone conversion backed by the device's own IANA database (no network involved).
 */
object TimeZones {

    /** A curated, searchable list covering the most used zones. */
    val curated: List<String> = listOf(
        "UTC",
        "Africa/Cairo", "Africa/Johannesburg", "Africa/Lagos", "Africa/Nairobi",
        "America/Anchorage", "America/Argentina/Buenos_Aires", "America/Bogota",
        "America/Chicago", "America/Denver", "America/Halifax", "America/Lima",
        "America/Los_Angeles", "America/Mexico_City", "America/New_York",
        "America/Phoenix", "America/Santiago", "America/Sao_Paulo", "America/Toronto",
        "America/Vancouver", "America/Tehran",
        "Asia/Baghdad", "Asia/Bangkok", "Asia/Beirut", "Asia/Colombo", "Asia/Dhaka",
        "Asia/Dubai", "Asia/Ho_Chi_Minh", "Asia/Hong_Kong", "Asia/Jakarta",
        "Asia/Jerusalem", "Asia/Kabul", "Asia/Karachi", "Asia/Kathmandu",
        "Asia/Kolkata", "Asia/Kuala_Lumpur", "Asia/Manila", "Asia/Riyadh",
        "Asia/Seoul", "Asia/Shanghai", "Asia/Singapore", "Asia/Taipei", "Asia/Tehran",
        "Asia/Tokyo", "Asia/Yangon",
        "Atlantic/Azores", "Atlantic/Reykjavik",
        "Australia/Adelaide", "Australia/Brisbane", "Australia/Darwin",
        "Australia/Melbourne", "Australia/Perth", "Australia/Sydney",
        "Europe/Amsterdam", "Europe/Athens", "Europe/Berlin", "Europe/Brussels",
        "Europe/Bucharest", "Europe/Budapest", "Europe/Copenhagen", "Europe/Dublin",
        "Europe/Helsinki", "Europe/Istanbul", "Europe/Kyiv", "Europe/Kiev",
        "Europe/Lisbon", "Europe/London", "Europe/Madrid", "Europe/Minsk",
        "Europe/Moscow", "Europe/Oslo", "Europe/Paris", "Europe/Prague",
        "Europe/Rome", "Europe/Sofia", "Europe/Stockholm", "Europe/Vienna",
        "Europe/Warsaw", "Europe/Zurich",
        "Pacific/Auckland", "Pacific/Fiji", "Pacific/Guam", "Pacific/Honolulu"
    )

    fun zone(id: String): ZoneId = try {
        ZoneId.of(id)
    } catch (e: Exception) {
        ZoneOffset.UTC
    }

    fun allZones(): List<String> {
        val available = try {
            ZoneId.getAvailableZoneIds()
        } catch (e: Exception) {
            emptySet()
        }
        val extras = available.filter { it !in curated }.sorted()
        return curated + extras
    }

    fun search(query: String): List<String> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return curated
        return allZones().filter { it.lowercase(Locale.US).contains(q) }.take(40)
    }

    fun formatOffset(zoneId: ZoneId, at: Instant): String {
        val offset = zoneId.rules.getOffset(at)
        val totalMinutes = offset.totalSeconds / 60
        val sign = if (totalMinutes < 0) "-" else "+"
        val abs = Math.abs(totalMinutes)
        return String.format(Locale.US, "UTC%s%02d:%02d", sign, abs / 60, abs % 60)
    }

    fun isDaylightSaving(zoneId: ZoneId, at: Instant): Boolean =
        zoneId.rules.getStandardOffset(at) != zoneId.rules.getOffset(at)

    /** Converts an instant between two zones. */
    fun convert(instant: Instant, from: ZoneId, to: ZoneId): ZonedDateTime =
        instant.atZone(from).withZoneSameInstant(to)

    fun format(dateTime: ZonedDateTime, pattern: String = "EEE d MMM yyyy, HH:mm"): String =
        dateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.US))

    fun localDateTimeToInstant(local: LocalDateTime, zone: ZoneId): Instant = local.atZone(zone).toInstant()
}
