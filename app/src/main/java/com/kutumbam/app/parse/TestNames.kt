package com.kutumbam.app.parse

/** A bundled fallback used only when a report prints no range. Never a clinical judgement. */
data class FallbackRange(val low: Double?, val high: Double?, val unit: String)

/**
 * Different labs print the same test under different names; this maps them to one key so a value can be
 * followed across reports, and holds the small fallback table for a few very common tests.
 */
object TestNames {
    private val ALIASES = listOf(
        Regex("""(fasting|fbs|fbg).*(glucose|sugar)|glucose.*fasting|^fbs$|^fbg$""", RegexOption.IGNORE_CASE) to "fasting_glucose",
        Regex("""hba1c|hb\s*a1c|glyc(o)?sylated\s+h(a)?emoglobin|glycated\s+h(a)?emoglobin|\ba1c\b""", RegexOption.IGNORE_CASE) to "hba1c",
        Regex("""^(serum\s+)?(total\s+)?cholesterol(\s+total)?$""", RegexOption.IGNORE_CASE) to "total_cholesterol",
        Regex("""\btsh\b|thyroid\s+stimulating""", RegexOption.IGNORE_CASE) to "tsh",
    )

    private val FALLBACKS = mapOf(
        "fasting_glucose" to FallbackRange(70.0, 100.0, "mg/dL"),
        "hba1c" to FallbackRange(null, 5.6, "%"),
        "total_cholesterol" to FallbackRange(null, 200.0, "mg/dL"),
        "tsh" to FallbackRange(0.4, 4.0, "uIU/mL"),
    )

    fun key(testName: String): String {
        val name = testName.trim()
        ALIASES.firstOrNull { (regex, _) -> regex.containsMatchIn(name) }?.let { return it.second }
        return name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }

    /** Returns the fallback only when the report's own unit matches, so a mmol/L glucose is never judged against mg/dL. */
    fun fallback(testName: String, unit: String?): FallbackRange? {
        val f = FALLBACKS[key(testName)] ?: return null
        return if (sameUnit(f.unit, unit)) f else null
    }

    private fun sameUnit(expected: String, actual: String?): Boolean {
        fun norm(u: String) = u.lowercase().replace("µ", "u").replace("μ", "u").replace(" ", "")
        val a = actual?.let(::norm) ?: return false
        val e = norm(expected)
        val iuPerMl = Regex("^u[il1|]u/m[l1]$").matches(a) || Regex("^m[il1|]u/[l1]$").matches(a)
        return a == e || (e == "uiu/ml" && iuPerMl)
    }
}
