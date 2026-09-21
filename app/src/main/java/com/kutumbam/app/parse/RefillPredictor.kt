package com.kutumbam.app.parse

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

enum class RefillStatus { OK, SOON, TODAY, OUT, COURSE_ENDS }

data class DoseSlot(val time: LocalTime, val units: Double)

/**
 * @property tabletsLeft what the schedule says should remain right now.
 * @property countedAt when the family last counted the tablets.
 */
data class RefillEstimate(
    val runOut: LocalDate,
    val daysLeft: Long,
    val status: RefillStatus,
    val tabletsLeft: Double,
    val countedAt: LocalDateTime,
) {
    /** True when the family should be told now. */
    val needsReminder get() = status == RefillStatus.SOON || status == RefillStatus.TODAY || status == RefillStatus.OUT

    /** After this many days the estimate has had time to drift (missed or extra doses), so the card asks for a recount. */
    fun countIsStale(now: LocalDateTime) = ChronoUnit.DAYS.between(countedAt, now) >= STALE_DAYS

    companion object { const val STALE_DAYS = 30L }
}

/**
 * Estimates when a supply runs out by walking forward through the scheduled doses from the moment the tablets were
 * counted, using each dose's own tablet count. It assumes every scheduled dose is taken, so a skipped dose makes the
 * real date later than the estimate: it errs towards reminding early, and a recount corrects any drift.
 */
object RefillPredictor {
    /** The family is reminded from this many days before the last dose. */
    const val LEAD_DAYS = 3L

    private const val MAX_DAYS = 4000

    /** As-needed medicines have no schedule to count against, so they get no slots. */
    fun slots(frequencyCode: String, times: List<LocalTime>, units: List<Double>): List<DoseSlot> {
        if (frequencyCode == FrequencyCode.SOS.name) return emptyList()
        return times.mapIndexed { i, t -> DoseSlot(t, units.getOrNull(i) ?: 1.0) }.filter { it.units > 0 }.sortedBy { it.time }
    }

    fun unitsPerDay(slots: List<DoseSlot>) = slots.sumOf { it.units }

    fun estimate(stock: Int, countedAt: LocalDateTime, slots: List<DoseSlot>, courseEnd: LocalDate?, now: LocalDateTime): RefillEstimate? {
        if (stock < 0 || slots.isEmpty()) return null
        var remaining = stock.toDouble()
        var leftNow = remaining
        var day = countedAt.toLocalDate()
        repeat(MAX_DAYS) {
            if (courseEnd != null && !day.isBefore(courseEnd)) return build(courseEnd, RefillStatus.COURSE_ENDS, leftNow, countedAt, now)
            for (s in slots) {
                val at = day.atTime(s.time)
                if (at.isBefore(countedAt)) continue
                if (remaining + EPS < s.units) return build(day, null, leftNow, countedAt, now)
                remaining -= s.units
                if (!at.isAfter(now)) leftNow = remaining
            }
            day = day.plusDays(1)
        }
        return null
    }

    private fun build(runOut: LocalDate, forced: RefillStatus?, left: Double, countedAt: LocalDateTime, now: LocalDateTime): RefillEstimate {
        val daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), runOut)
        val status = forced ?: when {
            daysLeft < 0 -> RefillStatus.OUT
            daysLeft == 0L -> RefillStatus.TODAY
            daysLeft <= LEAD_DAYS -> RefillStatus.SOON
            else -> RefillStatus.OK
        }
        return RefillEstimate(runOut, daysLeft, status, left, countedAt)
    }

    /** A starting suggestion for a medicine with no count yet: the whole prescribed course. Null when no course length is known. */
    fun courseSupply(slots: List<DoseSlot>, durationDays: Int?): Int? {
        if (durationDays == null || slots.isEmpty()) return null
        return ceil(unitsPerDay(slots) * durationDays).toInt().takeIf { it in 1..999 }
    }

    private const val EPS = 1e-9
}

object RefillText {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** Lower-case phrase that follows a medicine name: "runs out in 2 days (24 Sep)". */
    fun phrase(e: RefillEstimate): String {
        val date = e.runOut.format(DAY_MONTH)
        return when (e.status) {
            RefillStatus.COURSE_ENDS -> "has enough for the whole course"
            RefillStatus.OUT -> "should have run out on $date"
            RefillStatus.TODAY -> "runs out today"
            else -> if (e.daysLeft == 1L) "runs out tomorrow ($date)" else "runs out in ${e.daysLeft} days ($date)"
        }
    }

    /** "About 6 tablets left", or null once there is nothing useful to say. */
    fun left(e: RefillEstimate): String? {
        if (e.status == RefillStatus.OUT) return null
        val n = kotlin.math.floor(e.tabletsLeft + 1e-9).toInt()
        return "about $n ${if (n == 1) "tablet" else "tablets"} left"
    }

    /** One line per medicine that needs a refill, or null when nothing does. */
    fun digest(items: List<Pair<String, RefillEstimate>>): String? {
        val due = items.filter { it.second.needsReminder }.sortedBy { it.second.runOut }
        if (due.isEmpty()) return null
        return due.joinToString("\n") { (name, e) -> "$name ${phrase(e)}." } + "\nTime to get a refill."
    }
}
