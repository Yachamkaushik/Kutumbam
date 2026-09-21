package com.kutumbam.app.export

import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.parse.DoseUnits
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import com.kutumbam.app.visit.PrepInput
import com.kutumbam.app.visit.PrepSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SummarySection(val heading: String, val lines: List<String>)

/** What goes on the one-page summary, as plain text, so the layout code only has to draw it. */
data class SummaryContent(val title: String, val subtitle: String, val sections: List<SummarySection>, val footer: List<String>)

/**
 * The one-page health summary for a new doctor: what is being taken, the latest lab values with the range each report
 * printed, vaccination and growth for a child, and the questions from the visit sheet. Stored facts only.
 */
object SummaryBuilder {
    private val LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    const val MAX_MEDICINES = 10
    const val MAX_LABS = 10
    const val MAX_QUESTIONS = 5

    fun build(i: PrepInput, sheet: PrepSheet): SummaryContent {
        val sections = mutableListOf<SummarySection>()
        sections += SummarySection("Medicines now", medicines(i))
        labs(i)?.let { sections += it }
        if (i.isChild) {
            growth(i)?.let { sections += it }
            vaccines(i)?.let { sections += it }
        }
        val questions = sheet.sections.flatMap { it.items }.map { it.text }
        if (questions.isNotEmpty()) {
            val shown = questions.take(MAX_QUESTIONS)
            val more = questions.size - shown.size
            sections += SummarySection("Questions for this visit", if (more > 0) shown + "+ $more more on the visit sheet in the app" else shown)
        }
        val age = i.dob?.let { ImmunizationEngine.ageText(it, i.today) }
        return SummaryContent(
            title = "Health summary: ${i.person}",
            subtitle = listOfNotNull(age, "prepared ${i.today.format(LONG)}").joinToString(" · "),
            sections = sections,
            footer = listOf(
                "Allergies: ______________________     Blood group: ________     Doctor's notes: ______________________",
                "Made by Kutumbam from records saved on this phone. Every value is copied from a prescription or report. Not medical advice.",
            ),
        )
    }

    private fun active(m: MedRecord, today: LocalDate) =
        !today.isBefore(m.startDate) && (m.durationDays == null || today.isBefore(m.startDate.plusDays(m.durationDays.toLong())))

    private fun meal(m: MealTiming) = when (m) {
        MealTiming.BEFORE_FOOD -> "before food"; MealTiming.AFTER_FOOD -> "after food"; MealTiming.WITH_FOOD -> "with food"
        MealTiming.EMPTY_STOMACH -> "empty stomach"; MealTiming.UNSPECIFIED -> null
    }

    private fun medicines(i: PrepInput): List<String> {
        val list = i.medicines.filter { it.sos || active(it, i.today) }
        if (list.isEmpty()) return listOf("No medicines are saved.")
        val lines = list.take(MAX_MEDICINES).map { m ->
            val when_ = if (m.sos) "only when needed" else m.times.mapIndexed { n, t ->
                val u = m.units.getOrNull(n) ?: 1.0
                t.format(CLOCK) + if (u != 1.0) " (${DoseUnits.label(u)})" else ""
            }.joinToString(", ")
            val until = m.durationDays?.let { "until ${m.startDate.plusDays(it.toLong() - 1).format(LONG)}" }
            listOfNotNull(listOfNotNull(m.name, m.strength).joinToString(" "), when_, meal(m.meal), until).joinToString(" · ")
        }
        return if (list.size > MAX_MEDICINES) lines + "+ ${list.size - MAX_MEDICINES} more in the app" else lines
    }

    private fun labs(i: PrepInput): SummarySection? {
        val latest = i.labs.groupBy { it.key }.map { (_, r) -> r.maxBy { it.date } }
        if (latest.isEmpty()) return null
        fun flagged(l: com.kutumbam.app.locker.LabRecord) = RangeCheck.status(l.value, l.low, l.high).let { it == RangeStatus.ABOVE || it == RangeStatus.BELOW }
        val sorted = latest.sortedWith(compareByDescending<com.kutumbam.app.locker.LabRecord> { flagged(it) }.thenByDescending { it.date })
        val lines = sorted.take(MAX_LABS).map { l ->
            val status = when (RangeCheck.status(l.value, l.low, l.high)) {
                RangeStatus.ABOVE -> "above"; RangeStatus.BELOW -> "below"; RangeStatus.IN_RANGE -> "within range"; RangeStatus.NO_RANGE -> null
            }
            val range = describeRange(l.low, l.high, l.unit)
            val basis = if (l.standardReference) "standard range (report printed none)" else "range on report"
            val judged = when {
                status == null || range == null -> "no range printed"
                status == "within range" -> "within $basis $range"
                else -> "$status $basis $range"
            }
            "${l.testName}: ${formatNumber(l.value)}${l.unit?.let { " $it" }.orEmpty()} (${l.date.format(LONG)}) · $judged"
        }
        return SummarySection("Latest lab values", if (latest.size > MAX_LABS) lines + "+ ${latest.size - MAX_LABS} more in the app" else lines)
    }

    private fun growth(i: PrepInput): SummarySection? {
        val last = i.growth.maxByOrNull { it.date } ?: return null
        val parts = listOfNotNull(
            last.weightKg?.let { "weight ${formatNumber(it)} kg" }, last.heightCm?.let { "height ${formatNumber(it)} cm" }, last.headCm?.let { "head ${formatNumber(it)} cm" },
        )
        return SummarySection("Growth", listOf("Last recorded ${last.date.format(LONG)}: ${parts.joinToString(", ")}"))
    }

    private fun vaccines(i: PrepInput): SummarySection? {
        val plan = i.immunization ?: return null
        val all = plan.flatMap { it.vaccines }
        val given = all.count { it.status == VaccineStatus.DONE }
        val lines = mutableListOf("$given of ${all.size} scheduled doses recorded (India UIP schedule)")
        plan.filter { it.status == VaccineStatus.OVERDUE }.forEach { lines += "Overdue: ${it.pendingTitle}" }
        plan.filter { it.status == VaccineStatus.DUE }.forEach { lines += "Due now: ${it.pendingTitle}" }
        plan.firstOrNull { it.status == VaccineStatus.UPCOMING }?.let { lines += "Next: ${it.pendingTitle} (${ImmunizationEngine.relativeText(it, i.today)})" }
        return SummarySection("Vaccinations", lines)
    }
}
