package com.kutumbam.app.parse

enum class RangeStatus { IN_RANGE, BELOW, ABOVE, NO_RANGE }

/** Compares a value with the range printed on the same report. No range printed means no judgement at all. */
object RangeCheck {
    fun status(value: Double, low: Double?, high: Double?): RangeStatus = when {
        low == null && high == null -> RangeStatus.NO_RANGE
        low != null && value < low -> RangeStatus.BELOW
        high != null && value > high -> RangeStatus.ABOVE
        else -> RangeStatus.IN_RANGE
    }
}

fun formatNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

/** "70 - 100 mg/dL", "below 200 mg/dL" or "above 40 mg/dL", as a person would read the printed range. */
fun describeRange(low: Double?, high: Double?, unit: String?): String? {
    val u = unit?.let { " $it" }.orEmpty()
    return when {
        low != null && high != null -> "${formatNumber(low)} – ${formatNumber(high)}$u"
        high != null -> "below ${formatNumber(high)}$u"
        low != null -> "above ${formatNumber(low)}$u"
        else -> null
    }
}
