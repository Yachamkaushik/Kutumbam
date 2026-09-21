package com.kutumbam.app.parse

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RefillStatus { OK, SOON, TODAY, OUT, COURSE_ENDS }

data class RefillEstimate(val runOut: LocalDate, val daysLeft: Long, val status: RefillStatus) {
    /** True when the family should be told now. */
    val needsReminder get() = status == RefillStatus.SOON || status == RefillStatus.TODAY || status == RefillStatus.OUT
}

/**
 * Estimates when a supply runs out from the tablets counted on [asOf] and the doses per day on the schedule.
 * It assumes one tablet per dose and no missed or extra doses, so it is an estimate and is worded as one.
 */
object RefillPredictor {
    /** The family is reminded from this many days before the last dose. */
    const val LEAD_DAYS = 3L

    /** As-needed medicines have no schedule to count against, so they are not predicted. */
    fun dosesPerDay(frequencyCode: String, timesCsv: String): Int? {
        if (frequencyCode == FrequencyCode.SOS.name) return null
        return timesCsv.split(",").count { it.isNotBlank() }.takeIf { it > 0 }
    }

    fun estimate(stock: Int, asOf: LocalDate, dosesPerDay: Int, courseEnd: LocalDate?, today: LocalDate): RefillEstimate? {
        if (stock < 0 || dosesPerDay <= 0) return null
        // The supply covers whole days: the run-out date is the first day without a full set of doses.
        val runOut = asOf.plusDays((stock / dosesPerDay).toLong())
        val daysLeft = ChronoUnit.DAYS.between(today, runOut)
        val status = when {
            courseEnd != null && !courseEnd.isAfter(runOut) -> RefillStatus.COURSE_ENDS
            daysLeft < 0 -> RefillStatus.OUT
            daysLeft == 0L -> RefillStatus.TODAY
            daysLeft <= LEAD_DAYS -> RefillStatus.SOON
            else -> RefillStatus.OK
        }
        return RefillEstimate(runOut, daysLeft, status)
    }
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

    /** One line per medicine that needs a refill, or null when nothing does. */
    fun digest(items: List<Pair<String, RefillEstimate>>): String? {
        val due = items.filter { it.second.needsReminder }.sortedBy { it.second.runOut }
        if (due.isEmpty()) return null
        return due.joinToString("\n") { (name, e) -> "$name ${phrase(e)}." } + "\nTime to get a refill."
    }
}
