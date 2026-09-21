package com.kutumbam.app.parse

/** Tablets per dose, kept alongside the reminder times (same order). "1-0-2" means 1 tablet in the morning and 2 at night. */
object DoseUnits {
    fun fromToken(token: String): Double? = if (token == "½") 0.5 else token.toDoubleOrNull()

    fun toCsv(units: List<Double>): String = units.joinToString(",") { format(it) }

    /** Falls back to one tablet per dose when nothing is stored or the count does not match the times. */
    fun fromCsv(csv: String?, slots: Int): List<Double> {
        val parsed = csv?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }.orEmpty()
        return if (parsed.size == slots) parsed else List(slots) { 1.0 }
    }

    fun allOne(units: List<Double>) = units.all { it == 1.0 }

    fun label(u: Double): String = when (u) {
        0.5 -> "½"
        1.5 -> "1½"
        2.5 -> "2½"
        else -> format(u)
    }

    private fun format(u: Double) = if (u % 1.0 == 0.0) u.toLong().toString() else u.toString()

    /** "2 tablets" / "½ tablet", or null for the ordinary one-tablet dose and for forms that are not counted in tablets. */
    fun phrase(u: Double, form: String?): String? {
        if (u == 1.0) return null
        if (form != null && form != "Tablet" && form != "Capsule") return null
        return "${label(u)} ${if (u <= 1.0) "tablet" else "tablets"}"
    }
}
