package com.kutumbam.app.parse

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class VaccineStatus { DONE, DUE, UPCOMING, OVERDUE }

/**
 * A point on India's Universal Immunization Programme schedule: when a dose falls due, and when it counts as
 * overdue. Fixed offsets from the date of birth, exactly like a fixed table: no model involved.
 */
enum class Milestone(val ageLabel: String, private val dueAt: (LocalDate) -> LocalDate, private val overdueAfter: (LocalDate) -> LocalDate) {
    BIRTH("At birth", { it }, { it.plusWeeks(6) }),
    WEEK_6("6 weeks", { it.plusWeeks(6) }, { it.plusWeeks(10) }),
    WEEK_10("10 weeks", { it.plusWeeks(10) }, { it.plusWeeks(14) }),
    WEEK_14("14 weeks", { it.plusWeeks(14) }, { it.plusWeeks(18) }),
    MONTH_9("9 months", { it.plusMonths(9) }, { it.plusMonths(12) }),
    MONTH_16("16–24 months", { it.plusMonths(16) }, { it.plusMonths(24) }),
    YEAR_5("5–6 years", { it.plusYears(5) }, { it.plusYears(6) }),
    YEAR_10("10 years", { it.plusYears(10) }, { it.plusYears(11) }),
    YEAR_16("16 years", { it.plusYears(16) }, { it.plusYears(17) });

    fun dueDate(dob: LocalDate): LocalDate = dueAt(dob)
    fun overdueAfter(dob: LocalDate): LocalDate = overdueAfter.invoke(dob)
}

data class Vaccine(val id: String, val label: String, val milestone: Milestone)

object UipSchedule {
    val vaccines: List<Vaccine> = listOf(
        Vaccine("BCG", "BCG", Milestone.BIRTH),
        Vaccine("OPV-0", "OPV-0", Milestone.BIRTH),
        Vaccine("HEPB-0", "Hepatitis B (birth dose)", Milestone.BIRTH),

        Vaccine("OPV-1", "OPV-1", Milestone.WEEK_6),
        Vaccine("PENTA-1", "Pentavalent-1", Milestone.WEEK_6),
        Vaccine("ROTA-1", "Rotavirus-1", Milestone.WEEK_6),
        Vaccine("FIPV-1", "fIPV-1", Milestone.WEEK_6),
        Vaccine("PCV-1", "PCV-1", Milestone.WEEK_6),

        Vaccine("OPV-2", "OPV-2", Milestone.WEEK_10),
        Vaccine("PENTA-2", "Pentavalent-2", Milestone.WEEK_10),
        Vaccine("ROTA-2", "Rotavirus-2", Milestone.WEEK_10),

        Vaccine("OPV-3", "OPV-3", Milestone.WEEK_14),
        Vaccine("PENTA-3", "Pentavalent-3", Milestone.WEEK_14),
        Vaccine("ROTA-3", "Rotavirus-3", Milestone.WEEK_14),
        Vaccine("FIPV-2", "fIPV-2", Milestone.WEEK_14),
        Vaccine("PCV-2", "PCV-2", Milestone.WEEK_14),

        Vaccine("MR-1", "MR-1 (Measles-Rubella)", Milestone.MONTH_9),
        Vaccine("FIPV-3", "fIPV-3", Milestone.MONTH_9),
        Vaccine("PCV-B", "PCV booster", Milestone.MONTH_9),

        Vaccine("MR-2", "MR-2 (Measles-Rubella)", Milestone.MONTH_16),
        Vaccine("DPT-B1", "DPT booster-1", Milestone.MONTH_16),
        Vaccine("OPV-B", "OPV booster", Milestone.MONTH_16),

        Vaccine("DPT-B2", "DPT booster-2", Milestone.YEAR_5),
        Vaccine("TD-10", "Td (10 years)", Milestone.YEAR_10),
        Vaccine("TD-16", "Td (16 years)", Milestone.YEAR_16),
    )

    fun byId(id: String): Vaccine? = vaccines.firstOrNull { it.id == id }
}

data class VaccineState(val vaccine: Vaccine, val status: VaccineStatus, val givenOn: LocalDate?)

data class MilestoneState(
    val milestone: Milestone,
    val dueDate: LocalDate,
    val overdueAfter: LocalDate,
    val vaccines: List<VaccineState>,
) {
    val status: VaccineStatus
        get() = when {
            vaccines.all { it.status == VaccineStatus.DONE } -> VaccineStatus.DONE
            vaccines.any { it.status == VaccineStatus.OVERDUE } -> VaccineStatus.OVERDUE
            vaccines.any { it.status == VaccineStatus.DUE } -> VaccineStatus.DUE
            else -> VaccineStatus.UPCOMING
        }
    val title: String get() = vaccines.joinToString(", ") { it.vaccine.label }

    /** Only the doses still to be given, for the "due now" and "next" lines. */
    val pendingTitle: String get() = vaccines.filter { it.status != VaccineStatus.DONE }.joinToString(", ") { it.vaccine.label }
}

object ImmunizationEngine {

    /** [given] maps a vaccine id to the date it was administered (from a scanned card or marked by hand). */
    fun plan(dob: LocalDate, given: Map<String, LocalDate>, today: LocalDate): List<MilestoneState> =
        Milestone.entries.map { m ->
            val due = m.dueDate(dob)
            val late = m.overdueAfter(dob)
            MilestoneState(
                milestone = m, dueDate = due, overdueAfter = late,
                vaccines = UipSchedule.vaccines.filter { it.milestone == m }.map { v ->
                    val on = given[v.id]
                    val status = when {
                        on != null -> VaccineStatus.DONE
                        today.isAfter(late) -> VaccineStatus.OVERDUE
                        !today.isBefore(due) -> VaccineStatus.DUE
                        else -> VaccineStatus.UPCOMING
                    }
                    VaccineState(v, status, on)
                },
            )
        }

    fun relativeText(s: MilestoneState, today: LocalDate): String = when (s.status) {
        VaccineStatus.DONE -> ""
        VaccineStatus.UPCOMING -> "due in ${span(ChronoUnit.DAYS.between(today, s.dueDate))}"
        VaccineStatus.DUE -> "due now"
        VaccineStatus.OVERDUE -> "overdue by ${span(ChronoUnit.DAYS.between(s.overdueAfter, today))}"
    }

    fun ageText(dob: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(dob, today).coerceAtLeast(0)
        val months = ChronoUnit.MONTHS.between(dob, today)
        return when {
            days < 14 -> "$days days old"
            days < 84 -> "${days / 7} weeks old"
            months < 24 -> "$months months old"
            else -> "${ChronoUnit.YEARS.between(dob, today)} years old"
        }
    }

    private fun span(days: Long): String {
        val d = days.coerceAtLeast(1)
        return when {
            d < 14 -> if (d == 1L) "1 day" else "$d days"
            d < 60 -> "${d / 7} weeks"
            d < 730 -> "${d / 30} months"
            else -> "${d / 365} years"
        }
    }
}

/** The text of the daily reminder: what is due within a few days and what is already overdue. Null means stay quiet. */
object VaccineDigest {
    fun build(plan: List<MilestoneState>, today: LocalDate, aheadDays: Long = 3): String? {
        val dueSoon = plan.flatMap { ms ->
            ms.vaccines.filter { v ->
                v.status == VaccineStatus.DUE || (v.status == VaccineStatus.UPCOMING && ChronoUnit.DAYS.between(today, ms.dueDate) <= aheadDays)
            }.map { it to ms }
        }
        val overdue = plan.flatMap { ms -> ms.vaccines.filter { it.status == VaccineStatus.OVERDUE } }
        if (dueSoon.isEmpty() && overdue.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (dueSoon.isNotEmpty()) parts += "Due soon: ${dueSoon.joinToString(", ") { it.first.vaccine.label }}."
        if (overdue.isNotEmpty()) parts += "Overdue: ${overdue.joinToString(", ") { it.vaccine.label }}."
        return parts.joinToString(" ") + " Please check with your child's doctor."
    }
}
