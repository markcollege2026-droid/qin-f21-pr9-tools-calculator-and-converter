package com.qin.f21.toolbox.core.conversion

/**
 * How a unit maps onto the category base unit.
 *
 * [Linear] covers every multiplicative and affine conversion (length, mass, temperature, ...).
 * [Reciprocal] covers inverse relations such as "miles per gallon" whose base unit is
 * "litres per 100 km" (the international figure).
 */
sealed class Transform {

    /** base = value * factor + offset */
    data class Linear(val factor: Double, val offset: Double = 0.0) : Transform() {
        fun toBase(value: Double): Double = value * factor + offset
        fun fromBase(base: Double): Double = (base - offset) / factor
    }

    /** base = constant / value (value must not be 0) */
    data class Reciprocal(val constant: Double) : Transform() {
        fun toBase(value: Double): Double = if (value == 0.0) Double.NaN else constant / value
        fun fromBase(base: Double): Double = if (base == 0.0) Double.NaN else constant / base
    }
}

data class UnitDef(
    val id: String,
    val name: String,
    val symbol: String,
    val transform: Transform,
    /** Optional short hint rendered under the unit name, e.g. "per 100 km". */
    val note: String = "",
    /** Search aliases (abbreviations, alternative spellings). */
    val aliases: List<String> = emptyList()
) {
    val searchable: String = (listOf(name, symbol, id) + aliases).joinToString(" ").lowercase()
}

data class Category(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val units: List<UnitDef>,
    val defaultFrom: String,
    val defaultTo: String
) {
    val byId: Map<String, UnitDef> = units.associateBy { it.id }

    fun unit(id: String): UnitDef? = byId[id]

    fun requireUnit(id: String): UnitDef = byId[id] ?: error("Unknown unit '$id' in category '$id'")
}

object UnitCatalog {

    private fun linear(id: String, name: String, symbol: String, factor: Double, note: String = "", aliases: List<String> = emptyList()) =
        UnitDef(id, name, symbol, Transform.Linear(factor), note, aliases)

    private fun affine(id: String, name: String, symbol: String, factor: Double, offset: Double, aliases: List<String> = emptyList()) =
        UnitDef(id, name, symbol, Transform.Linear(factor, offset), "", aliases)

    private fun reciprocal(id: String, name: String, symbol: String, constant: Double, note: String = "") =
        UnitDef(id, name, symbol, Transform.Reciprocal(constant), note)

    val length = Category(
        id = "length",
        title = "Length",
        icon = "ruler",
        description = "Metric, imperial and survey units",
        units = listOf(
            linear("nm", "Nanometre", "nm", 1e-9, aliases = listOf("nanometer")),
            linear("um", "Micrometre", "µm", 1e-6, aliases = listOf("micrometer", "micron")),
            linear("mm", "Millimetre", "mm", 0.001, aliases = listOf("millimeter")),
            linear("cm", "Centimetre", "cm", 0.01, aliases = listOf("centimeter")),
            linear("dm", "Decimetre", "dm", 0.1, aliases = listOf("decimeter")),
            linear("m", "Metre", "m", 1.0, aliases = listOf("meter")),
            linear("km", "Kilometre", "km", 1000.0, aliases = listOf("kilometer")),
            linear("mil", "Mil (thou)", "mil", 2.54e-5, aliases = listOf("thou", "thousandth")),
            linear("in", "Inch", "in", 0.0254, aliases = listOf("inch", "\"")),
            linear("ft", "Foot", "ft", 0.3048, aliases = listOf("foot", "feet", "'")),
            linear("yd", "Yard", "yd", 0.9144, aliases = listOf("yard")),
            linear("mi", "Mile", "mi", 1609.344, aliases = listOf("mile")),
            linear("nmi", "Nautical mile", "nmi", 1852.0, aliases = listOf("nauticalmile")),
            linear("fathom", "Fathom", "fathom", 1.8288),
            linear("cable", "International cable", "cable", 185.2),
            linear("furlong", "Furlong", "furlong", 201.168),
            linear("league", "League", "league", 4828.032),
            linear("ell", "Ell", "ell", 1.143),
            linear("hand", "Hand", "hand", 0.1016),
            linear("barleycorn", "Barleycorn", "barleycorn", 0.0084666667),
            linear("au", "Astronomical unit", "AU", 1.495978707e11),
            linear("ly", "Light year", "ly", 9.4607304725808e15, aliases = listOf("lightyear")),
            linear("pc", "Parsec", "pc", 3.0856775814913673e16)
        ),
        defaultFrom = "m",
        defaultTo = "ft"
    )

    val mass = Category(
        id = "mass",
        title = "Mass",
        icon = "scale",
        description = "Metric, imperial and troy units",
        units = listOf(
            linear("mg", "Milligram", "mg", 1e-6, aliases = listOf("milligram")),
            linear("g", "Gram", "g", 0.001, aliases = listOf("gram", "gramme")),
            linear("kg", "Kilogram", "kg", 1.0, aliases = listOf("kilogram", "kilo")),
            linear("t", "Tonne", "t", 1000.0, aliases = listOf("metricton", "tonne")),
            linear("gr", "Grain", "gr", 6.479891e-5),
            linear("dr", "Drachm", "dr", 0.0017718451953125),
            linear("oz", "Ounce", "oz", 0.028349523125, aliases = listOf("ounce")),
            linear("lb", "Pound", "lb", 0.45359237, aliases = listOf("pound", "lbs")),
            linear("st", "Stone", "st", 6.35029318),
            linear("cwt_us", "Hundredweight (US)", "cwt", 45.359237),
            linear("cwt_uk", "Hundredweight (UK)", "cwt", 50.80234544),
            linear("ton_us", "Short ton (US)", "ton", 907.18474),
            linear("ton_uk", "Long ton (UK)", "ton", 1016.0469088),
            linear("ct", "Metric carat", "ct", 0.0002, aliases = listOf("carat")),
            linear("slug", "Slug", "slug", 14.5939029372),
            linear("quintal", "Quintal", "q", 100.0)
        ),
        defaultFrom = "kg",
        defaultTo = "lb"
    )

    val area = Category(
        id = "area",
        title = "Area",
        icon = "square",
        description = "Metric and imperial surfaces",
        units = listOf(
            linear("mm2", "Square millimetre", "mm²", 1e-6),
            linear("cm2", "Square centimetre", "cm²", 1e-4),
            linear("dm2", "Square decimetre", "dm²", 0.01),
            linear("m2", "Square metre", "m²", 1.0, aliases = listOf("sqm", "squaremeter")),
            linear("a", "Are", "a", 100.0),
            linear("ha", "Hectare", "ha", 10000.0, aliases = listOf("hectare")),
            linear("km2", "Square kilometre", "km²", 1e6),
            linear("in2", "Square inch", "in²", 0.00064516),
            linear("ft2", "Square foot", "ft²", 0.09290304),
            linear("yd2", "Square yard", "yd²", 0.83612736),
            linear("acre", "Acre", "acre", 4046.8564224),
            linear("rood", "Rood", "rood", 1011.7141056),
            linear("perch", "Perch", "perch", 25.29285264),
            linear("mi2", "Square mile", "mi²", 2589988.110336),
            linear("barn", "Barn", "barn", 1e-28)
        ),
        defaultFrom = "m2",
        defaultTo = "ft2"
    )

    val volume = Category(
        id = "volume",
        title = "Volume",
        icon = "cube",
        description = "Kitchen, imperial and large volumes",
        units = listOf(
            linear("ml", "Millilitre", "mL", 0.001, aliases = listOf("milliliter", "millilitre")),
            linear("cl", "Centilitre", "cL", 0.01),
            linear("dl", "Decilitre", "dL", 0.1),
            linear("l", "Litre", "L", 1.0, aliases = listOf("liter", "litre")),
            linear("m3", "Cubic metre", "m³", 1000.0),
            linear("cm3", "Cubic centimetre", "cm³", 0.001),
            linear("km3", "Cubic kilometre", "km³", 1e12),
            linear("mm3", "Cubic millimetre", "mm³", 1e-6),
            linear("tsp_us", "Teaspoon (US)", "tsp", 0.00492892159375),
            linear("tbsp_us", "Tablespoon (US)", "tbsp", 0.01478676478125),
            linear("floz_us", "Fluid ounce (US)", "fl oz", 0.0295735295625),
            linear("cup_us", "Cup (US)", "cup", 0.2365882365),
            linear("pint_us", "Pint (US)", "pt", 0.473176473),
            linear("quart_us", "Quart (US)", "qt", 0.946352946),
            linear("gal_us", "Gallon (US)", "gal", 3.785411784, aliases = listOf("usgallon")),
            linear("tsp_uk", "Teaspoon (UK)", "tsp", 0.005919388020833333),
            linear("tbsp_uk", "Tablespoon (UK)", "tbsp", 0.0177581640625),
            linear("floz_uk", "Fluid ounce (UK)", "fl oz", 0.0284130625),
            linear("pint_uk", "Pint (UK)", "pt", 0.56826125),
            linear("quart_uk", "Quart (UK)", "qt", 1.1365225),
            linear("gal_uk", "Gallon (UK)", "gal", 4.54609, aliases = listOf("imperialgallon")),
            linear("gill_us", "Gill (US)", "gi", 0.11829411825),
            linear("dram_us", "Fluid dram (US)", "fl dr", 0.003696691195),
            linear("minim_us", "Minim (US)", "m", 0.00006161151992),
            linear("in3", "Cubic inch", "in³", 0.016387064),
            linear("ft3", "Cubic foot", "ft³", 28.316846592),
            linear("yd3", "Cubic yard", "yd³", 764.554857984),
            linear("bbl", "Oil barrel", "bbl", 158.987294928),
            linear("bushel", "Bushel (US)", "bu", 35.23907016688),
            linear("peck", "Peck (US)", "pk", 8.80976754172),
            linear("cord", "Cord (firewood)", "cord", 3.644554521536),
            linear("stere", "Stere", "st", 1.0)
        ),
        defaultFrom = "l",
        defaultTo = "gal_us"
    )

    val speed = Category(
        id = "speed",
        title = "Speed",
        icon = "speedometer",
        description = "Road, marine and scientific speed",
        units = listOf(
            linear("mps", "Metre per second", "m/s", 1.0),
            linear("kmh", "Kilometre per hour", "km/h", 1.0 / 3.6),
            linear("kmmin", "Kilometre per minute", "km/min", 1000.0 / 60.0),
            linear("kmday", "Kilometre per day", "km/day", 1000.0 / 86400.0),
            linear("mph", "Mile per hour", "mph", 0.44704),
            linear("fps", "Foot per second", "ft/s", 0.3048),
            linear("kn", "Knot", "kn", 0.5144444444444444, aliases = listOf("knot")),
            linear("mach", "Mach (sea level)", "Ma", 340.29),
            linear("c", "Speed of light", "c", 299792458.0),
            linear("mmin", "Metre per minute", "m/min", 1.0 / 60.0)
        ),
        defaultFrom = "kmh",
        defaultTo = "mph"
    )

    val time = Category(
        id = "time",
        title = "Time",
        icon = "clock",
        description = "Durations and calendar averages",
        units = listOf(
            linear("ns", "Nanosecond", "ns", 1e-9),
            linear("us", "Microsecond", "µs", 1e-6),
            linear("ms", "Millisecond", "ms", 0.001),
            linear("s", "Second", "s", 1.0),
            linear("min", "Minute", "min", 60.0),
            linear("h", "Hour", "h", 3600.0),
            linear("d", "Day", "d", 86400.0),
            linear("wk", "Week", "wk", 604800.0),
            linear("fortnight", "Fortnight", "fortnight", 1209600.0),
            linear("month", "Month (avg)", "mo", 2629800.0, note = "30.4375 days"),
            linear("year", "Year (avg)", "y", 31557600.0, note = "365.25 days"),
            linear("decade", "Decade", "dec", 315576000.0),
            linear("century", "Century", "c", 3155760000.0)
        ),
        defaultFrom = "h",
        defaultTo = "min"
    )

    val temperature = Category(
        id = "temperature",
        title = "Temperature",
        icon = "thermometer",
        description = "Absolute and everyday scales",
        units = listOf(
            affine("c", "Celsius", "°C", 1.0, 0.0, aliases = listOf("centigrade")),
            affine("f", "Fahrenheit", "°F", 5.0 / 9.0, -160.0 / 9.0, aliases = listOf("fahrenheit")),
            affine("k", "Kelvin", "K", 1.0, -273.15),
            affine("r", "Rankine", "°R", 1.8, -273.15),
            affine("re", "Réaumur", "°Ré", 0.8, 0.0),
            affine("de", "Delisle", "°De", -1.5, 100.0)
        ),
        defaultFrom = "c",
        defaultTo = "f"
    )

    val digital = Category(
        id = "digital",
        title = "Digital storage",
        icon = "drive",
        description = "Decimal and binary size units",
        units = listOf(
            linear("bit", "Bit", "bit", 0.125),
            linear("byte", "Byte", "B", 1.0),
            linear("kb", "Kilobyte (1000)", "kB", 1e3),
            linear("mb", "Megabyte (1000)", "MB", 1e6),
            linear("gb", "Gigabyte (1000)", "GB", 1e9),
            linear("tb", "Terabyte (1000)", "TB", 1e12),
            linear("pb", "Petabyte (1000)", "PB", 1e15),
            linear("eb", "Exabyte (1000)", "EB", 1e18),
            linear("kib", "Kibibyte (1024)", "KiB", 1024.0),
            linear("mib", "Mebibyte (1024)", "MiB", 1048576.0),
            linear("gib", "Gibibyte (1024)", "GiB", 1073741824.0),
            linear("tib", "Tebibyte (1024)", "TiB", 1099511627776.0),
            linear("pib", "Pebibyte (1024)", "PiB", 1125899906842624.0),
            linear("eib", "Exbibyte (1024)", "EiB", 1152921504606846976.0),
            linear("floppy", "Floppy disk (1.44 MB)", "FD", 1440000.0),
            linear("cd", "CD (700 MB)", "CD", 700000000.0),
            linear("dvd", "DVD (4.7 GB)", "DVD", 4700000000.0)
        ),
        defaultFrom = "gb",
        defaultTo = "mib"
    )

    val fuel = Category(
        id = "fuel",
        title = "Fuel economy",
        icon = "fuel",
        description = "Base unit: litres per 100 km",
        units = listOf(
            linear("l100km", "Litre per 100 km", "L/100km", 1.0),
            linear("lkm", "Litre per km", "L/km", 10.0),
            reciprocal("mpg_us", "Miles per gallon (US)", "mpg", 235.2145833),
            reciprocal("mpg_uk", "Miles per gallon (UK)", "mpg (UK)", 282.4809363),
            reciprocal("kmpl", "Kilometre per litre", "km/L", 100.0)
        ),
        defaultFrom = "l100km",
        defaultTo = "mpg_us"
    )

    val pressure = Category(
        id = "pressure",
        title = "Pressure",
        icon = "gauge",
        description = "Atmospheric, technical and fluid units",
        units = listOf(
            linear("pa", "Pascal", "Pa", 1.0),
            linear("hpa", "Hectopascal", "hPa", 100.0),
            linear("kpa", "Kilopascal", "kPa", 1000.0),
            linear("mpa", "Megapascal", "MPa", 1e6),
            linear("bar", "Bar", "bar", 100000.0),
            linear("mbar", "Millibar", "mbar", 100.0),
            linear("atm", "Atmosphere", "atm", 101325.0),
            linear("psi", "Pound per square inch", "psi", 6894.757293168361),
            linear("torr", "Torr", "Torr", 133.32236842105263),
            linear("mmhg", "Millimetre of mercury", "mmHg", 133.322387415),
            linear("inhg", "Inch of mercury", "inHg", 3386.388640341),
            linear("mmh2o", "Millimetre of water", "mmH₂O", 9.80665),
            linear("inh2o", "Inch of water", "inH₂O", 248.8430048),
            linear("kgfcm2", "Kilogram-force per cm²", "kgf/cm²", 98066.5)
        ),
        defaultFrom = "bar",
        defaultTo = "psi"
    )

    val energy = Category(
        id = "energy",
        title = "Energy",
        icon = "bolt",
        description = "Mechanical, electrical and thermal energy",
        units = listOf(
            linear("j", "Joule", "J", 1.0),
            linear("kj", "Kilojoule", "kJ", 1000.0),
            linear("mj", "Megajoule", "MJ", 1e6),
            linear("cal", "Calorie", "cal", 4.184),
            linear("kcal", "Kilocalorie", "kcal", 4184.0),
            linear("wh", "Watt hour", "Wh", 3600.0),
            linear("kwh", "Kilowatt hour", "kWh", 3.6e6, aliases = listOf("kilowatthour")),
            linear("mwh", "Megawatt hour", "MWh", 3.6e9),
            linear("btu", "British thermal unit", "BTU", 1055.05585262),
            linear("therm", "Therm", "therm", 105505585.262),
            linear("ftlb", "Foot-pound force", "ft·lbf", 1.3558179483314004),
            linear("inlb", "Inch-pound force", "in·lbf", 0.11298482902761671),
            linear("erg", "Erg", "erg", 1e-7),
            linear("ev", "Electronvolt", "eV", 1.602176634e-19),
            linear("kgtnt", "Kilogram of TNT", "kg TNT", 4.184e6),
            linear("tontnt", "Tonne of TNT", "t TNT", 4.184e9)
        ),
        defaultFrom = "kj",
        defaultTo = "kcal"
    )

    val power = Category(
        id = "power",
        title = "Power",
        icon = "power",
        description = "Engine, electrical and thermal power",
        units = listOf(
            linear("w", "Watt", "W", 1.0),
            linear("mw", "Milliwatt", "mW", 0.001),
            linear("kw", "Kilowatt", "kW", 1000.0),
            linear("mw2", "Megawatt", "MW", 1e6),
            linear("hp", "Horsepower (mechanical)", "hp", 745.6998715822702),
            linear("hp_metric", "Horsepower (metric)", "PS", 735.49875),
            linear("btuh", "BTU per hour", "BTU/h", 293.0710701722222),
            linear("cals", "Calorie per second", "cal/s", 4.184),
            linear("kjmin", "Kilojoule per minute", "kJ/min", 1000.0 / 60.0),
            linear("tr", "Ton of refrigeration", "TR", 3516.8528420667006)
        ),
        defaultFrom = "kw",
        defaultTo = "hp"
    )

    val frequency = Category(
        id = "frequency",
        title = "Frequency",
        icon = "wave",
        description = "Oscillation and rotation rates",
        units = listOf(
            linear("hz", "Hertz", "Hz", 1.0),
            linear("khz", "Kilohertz", "kHz", 1e3),
            linear("mhz", "Megahertz", "MHz", 1e6),
            linear("ghz", "Gigahertz", "GHz", 1e9),
            linear("rpm", "Revolution per minute", "rpm", 1.0 / 60.0),
            linear("rads", "Radian per second", "rad/s", 1.0 / (2.0 * Math.PI)),
            linear("bpm", "Beat per minute", "bpm", 1.0 / 60.0)
        ),
        defaultFrom = "hz",
        defaultTo = "khz"
    )

    val angle = Category(
        id = "angle",
        title = "Angle",
        icon = "angle",
        description = "Rotational and navigational angles",
        units = listOf(
            linear("deg", "Degree", "°", 1.0, aliases = listOf("degree")),
            linear("rad", "Radian", "rad", 180.0 / Math.PI),
            linear("grad", "Gradian", "gon", 0.9, aliases = listOf("gon")),
            linear("turn", "Turn", "turn", 360.0),
            linear("arcmin", "Arcminute", "'", 1.0 / 60.0),
            linear("arcsec", "Arcsecond", "\"", 1.0 / 3600.0),
            linear("mil", "NATO mil", "mil", 0.05625),
            linear("quadrant", "Quadrant", "quad", 90.0),
            linear("sextant", "Sextant", "sex", 60.0)
        ),
        defaultFrom = "deg",
        defaultTo = "rad"
    )

    val density = Category(
        id = "density",
        title = "Density",
        icon = "density",
        description = "Mass per unit volume",
        units = listOf(
            linear("kgm3", "Kilogram per cubic metre", "kg/m³", 1.0),
            linear("gcm3", "Gram per cubic centimetre", "g/cm³", 1000.0),
            linear("kgl", "Kilogram per litre", "kg/L", 1.0),
            linear("gl", "Gram per litre", "g/L", 0.001),
            linear("t_m3", "Tonne per cubic metre", "t/m³", 1000.0),
            linear("lbft3", "Pound per cubic foot", "lb/ft³", 16.01846337396014),
            linear("lbin3", "Pound per cubic inch", "lb/in³", 27679.904710203125),
            linear("ozgal", "Ounce per US gallon", "oz/gal", 7.48981550635)
        ),
        defaultFrom = "kgm3",
        defaultTo = "gcm3"
    )

    val flow = Category(
        id = "flow",
        title = "Flow rate",
        icon = "flow",
        description = "Volumetric flow",
        units = listOf(
            linear("m3s", "Cubic metre per second", "m³/s", 1.0),
            linear("ls", "Litre per second", "L/s", 0.001),
            linear("lmin", "Litre per minute", "L/min", 0.001 / 60.0),
            linear("lh", "Litre per hour", "L/h", 0.001 / 3600.0),
            linear("m3h", "Cubic metre per hour", "m³/h", 1.0 / 3600.0),
            linear("gpm_us", "US gallon per minute", "gpm", 6.30901964e-5),
            linear("gpm_uk", "UK gallon per minute", "gpm (UK)", 7.578816e-5),
            linear("cfm", "Cubic foot per minute", "cfm", 0.0004719474432)
        ),
        defaultFrom = "lmin",
        defaultTo = "gpm_us"
    )

    val concentration = Category(
        id = "concentration",
        title = "Concentration",
        icon = "flask",
        description = "Mass fraction units",
        units = listOf(
            linear("percent", "Percent", "%", 10000.0),
            linear("permille", "Per mille", "‰", 1000.0),
            linear("ppm", "Parts per million", "ppm", 1.0),
            linear("ppb", "Parts per billion", "ppb", 0.001),
            linear("ppt", "Parts per trillion", "ppt", 1e-6)
        ),
        defaultFrom = "percent",
        defaultTo = "ppm"
    )

    val torque = Category(
        id = "torque",
        title = "Torque",
        icon = "torque",
        description = "Rotational force",
        units = listOf(
            linear("nm", "Newton metre", "N·m", 1.0),
            linear("ncm", "Newton centimetre", "N·cm", 0.01),
            linear("kgfm", "Kilogram-force metre", "kgf·m", 9.80665),
            linear("ftlbf", "Foot-pound force", "ft·lbf", 1.3558179483314004),
            linear("inlbf", "Inch-pound force", "in·lbf", 0.11298482902761671),
            linear("blbf", "Pound-force inch", "lbf·in", 0.11298482902761671)
        ),
        defaultFrom = "nm",
        defaultTo = "ftlbf"
    )

    val all: List<Category> = listOf(
        length, mass, area, volume, speed, time, temperature, digital, fuel,
        pressure, energy, power, frequency, angle, density, flow, concentration, torque
    )

    val byId: Map<String, Category> = all.associateBy { it.id }
}
