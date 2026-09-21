package com.kutumbam.app.parse

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TrendPoint(val date: LocalDate, val value: Double)

enum class Direction { UP, DOWN, STEADY }

/** Everything the trend text says is computed here from the stored numbers; the model only rephrases it. */
object TrendSummary {

    fun direction(points: List<TrendPoint>): Direction {
        if (points.size < 2) return Direction.STEADY
        val first = points.first().value
        val last = points.last().value
        val tolerance = kotlin.math.abs(first) * 0.03
        return when {
            last - first > tolerance -> Direction.UP
            first - last > tolerance -> Direction.DOWN
            else -> Direction.STEADY
        }
    }

    fun template(person: String, test: String, points: List<TrendPoint>, unit: String?, low: Double?, high: Double?): String {
        if (points.isEmpty()) return ""
        val u = unit?.let { " $it" }.orEmpty()
        val last = points.last()
        val status = RangeCheck.status(last.value, low, high)
        val range = describeRange(low, high, unit)
        val latest = when (status) {
            RangeStatus.ABOVE -> "The latest reading is above the range printed on the report ($range)."
            RangeStatus.BELOW -> "The latest reading is below the range printed on the report ($range)."
            RangeStatus.IN_RANGE -> "The latest reading is within the range printed on the report ($range)."
            RangeStatus.NO_RANGE -> "No range was printed on the report, so this can't be compared."
        }
        val doctor = if (status == RangeStatus.ABOVE || status == RangeStatus.BELOW) " Please talk to a doctor about it." else ""
        if (points.size == 1) {
            return "Only one report so far: $person's $test was ${formatNumber(last.value)}$u on ${last.date.format(DAY)}. $latest$doctor Add more reports to see a trend."
        }
        val verb = when (direction(points)) {
            Direction.UP -> "has risen"
            Direction.DOWN -> "has fallen"
            Direction.STEADY -> "has stayed about the same"
        }
        val over = if (direction(points) == Direction.STEADY) "" else ", from ${formatNumber(points.first().value)} to ${formatNumber(last.value)}$u"
        return "$person's $test $verb over the last ${countWord(points.size)} reports$over. $latest$doctor"
    }

    /** The structured facts handed to the on-device model, so it can only restate them. */
    fun facts(person: String, test: String, points: List<TrendPoint>, unit: String?, low: Double?, high: Double?): String {
        val readings = points.joinToString("; ") { "${formatNumber(it.value)} on ${it.date.format(DAY)}" }
        val status = RangeCheck.status(points.last().value, low, high)
        return buildString {
            appendLine("Person: $person")
            appendLine("Test: $test")
            appendLine("Readings in date order: $readings")
            unit?.let { appendLine("Unit: $it") }
            describeRange(low, high, unit)?.let { appendLine("Range printed on the report: $it") }
            appendLine("Overall movement (computed): ${direction(points).name.lowercase()}")
            appendLine("Latest reading versus printed range (computed): ${status.name.lowercase().replace('_', ' ')}")
        }
    }

    /** A model answer is only shown if it states the latest value and stays short; otherwise the template wins. */
    fun isFaithful(text: String, points: List<TrendPoint>): Boolean {
        if (points.isEmpty()) return false
        val t = text.trim()
        return t.length in 20..500 && t.contains(formatNumber(points.last().value))
    }

    private fun countWord(n: Int) = when (n) { 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> n.toString() }

    private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
}
