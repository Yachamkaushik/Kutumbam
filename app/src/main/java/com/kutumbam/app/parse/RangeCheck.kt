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
