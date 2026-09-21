package com.kutumbam.app.visit

import com.kutumbam.app.locker.LabRecord
import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.parse.DuplicateCheck
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MedRef
import com.kutumbam.app.parse.MedicineSalts
import com.kutumbam.app.parse.MilestoneState
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import com.kutumbam.app.vitals.Limits
import com.kutumbam.app.vitals.Reading
import com.kutumbam.app.vitals.VitalSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class GrowthPoint(val date: LocalDate, val weightKg: Double?, val heightCm: Double?, val headCm: Double?)

data class PrepInput(
    val person: String,
    val isChild: Boolean,
    val dob: LocalDate?,
    val today: LocalDate,
    val medicines: List<MedRecord>,
    val labs: List<LabRecord>,
    val immunization: List<MilestoneState>?,
    val growth: List<GrowthPoint>,
    /** True when the sheet is for the person using the phone, so it is written in the first person. */
    val self: Boolean = false,
    val readings: List<Reading> = emptyList(),
    val limits: Limits = emptyMap(),
    val notes: List<NoteItem> = emptyList(),
    /** Only filled in for the person using the phone: their own tapping is a reliable record, someone else's may not be. */
    val adherence: Adherence? = null,
)

/** [source] says where the fact behind the question came from, so it can be checked. */
data class PrepItem(val text: String, val source: String? = null)

data class PrepSection(val heading: String, val items: List<PrepItem>, val note: String? = null)

data class PrepSheet(val title: String, val subtitle: String, val sections: List<PrepSection>, val footer: String) {
    val questionCount get() = sections.sumOf { it.items.size }

    /** Plain text for sharing or printing, questions numbered straight through. */
    fun asText(): String = buildString {
        appendLine(title)
        appendLine(subtitle)
        var n = 1
        sections.forEach { s ->
            appendLine()
            appendLine(s.heading.uppercase())
            s.note?.let { appendLine(it) }
            s.items.forEach { i ->
                appendLine("${n++}. ${i.text}")
                i.source?.let { appendLine("   (from: $it)") }
            }
        }
        appendLine()
        append(footer)
    }
}

/**
 * Turns the saved records into questions for the next visit: flagged lab values, medicine changes between the last two
 * prescriptions, and, for a child, growth and immunization. Plain templates over stored facts. It asks; it never says what a
 * result means or what to do about it.
 */
object VisitPrep {
    private val LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** How far back a flagged value still counts as recent. */
    private const val RECENT_LAB_DAYS = 180L

    fun build(i: PrepInput): PrepSheet {
        val sections = mutableListOf<PrepSection>()
        if (i.isChild) {
            sections += growth(i)
            i.immunization?.let { sections += vaccinations(i, it) }
        }
        labs(i)?.let { sections += it }
        readings(i)?.let { sections += it }
        notes(i)?.let { sections += it }
        sections += medicines(i)
        sections += PrepSection("Also ask", alsoAsk(i).map { PrepItem(it) })
        val age = i.dob?.let { ImmunizationEngine.ageText(it, i.today) }
        return PrepSheet(
            title = if (i.isChild) "Pediatrician visit prep: ${i.person}" else if (i.self) "My doctor visit prep" else "Doctor visit prep: ${i.person}",
            subtitle = "Prepared ${i.today.format(LONG)} from the records saved on this phone${age?.let { " · $it" } ?: ""}. These are questions to ask, not medical advice.",
            sections = sections,
            footer = "Bring: the latest prescription, recent lab reports${if (i.isChild) " and the vaccination card" else ""}.\nNew since the last visit (write here): ____________________________",
        )
    }

    // ---- labs

    private fun labs(i: PrepInput): PrepSection? {
        val since = i.today.minusDays(RECENT_LAB_DAYS)
        val items = i.labs.groupBy { it.key }.mapNotNull { (_, readings) ->
            val sorted = readings.sortedBy { it.date }
            val latest = sorted.last()
            val status = RangeCheck.status(latest.value, latest.low, latest.high)
            if (latest.date.isBefore(since) || (status != RangeStatus.ABOVE && status != RangeStatus.BELOW)) return@mapNotNull null
            val before = sorted.getOrNull(sorted.lastIndex - 1)
            val unit = latest.unit?.let { " $it" }.orEmpty()
            val basis = if (latest.standardReference) "a standard reference range (the report printed none)" else "the range printed on the report"
            val range = describeRange(latest.low, latest.high, latest.unit)
            val side = if (status == RangeStatus.ABOVE) "above" else "below"
            val history = before?.let { " Before that it was ${formatNumber(it.value)}${it.unit?.let { u -> " $u" }.orEmpty()} on ${it.date.format(LONG)}." } ?: " It is the only reading saved."
            latest.date to PrepItem(
                "${cap(poss(i))} ${latest.testName} was ${formatNumber(latest.value)}$unit on ${latest.date.format(LONG)}, $side $basis${range?.let { " ($it)" }.orEmpty()}.$history " +
                    "What does this mean for ${obj(i)}, and is any follow-up test or change needed?",
                latest.source.label,
            )
        }.sortedByDescending { it.first }.map { it.second }
        return if (items.isEmpty()) null else PrepSection("Lab results to discuss", items)
    }

    // ---- home readings

    private fun readings(i: PrepInput): PrepSection? {
        val facts = VitalSummary.lines(i.readings, i.limits, i.today)
        if (facts.isEmpty()) return null
        val questions = VitalSummary.questions(i.readings, i.limits, i.today, i.self, i.person)
        return PrepSection("Home readings", questions.map { PrepItem(it, "Home readings") }, note = facts.joinToString("\n"))
    }

    // ---- notes

    /** How far back a note still counts as something to mention. */
    private const val RECENT_NOTE_DAYS = 45L

    private fun notes(i: PrepInput): PrepSection? {
        val recent = i.notes.filter { !it.date.isBefore(i.today.minusDays(RECENT_NOTE_DAYS)) }.sortedBy { it.date }
        if (recent.isEmpty()) return null
        val lead = if (i.self) "I noticed" else "Noticed"
        val items = recent.map { PrepItem("$lead on ${it.date.format(SHORT)}: ${it.text}", "Notes") } +
            PrepItem("Is any of this worth checking?")
        return PrepSection("Things to mention", items)
    }

    // ---- medicines

    private fun obj(i: PrepInput) = if (i.self) "me" else i.person
    private fun subj(i: PrepInput) = if (i.self) "I" else i.person
    private fun isSubj(i: PrepInput) = if (i.self) "Am I" else "Is ${i.person}"
    private fun poss(i: PrepInput) = if (i.self) "my" else "${i.person}'s"
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    private fun name(m: MedRecord) = listOfNotNull(m.name, m.strength).joinToString(" ")

    private fun freq(m: MedRecord) = if (m.sos) "only when needed" else when (m.times.size) {
        1 -> "once a day"; 2 -> "twice a day"; 3 -> "three times a day"; 4 -> "four times a day"; else -> "${m.times.size} times a day"
    }

    private fun active(m: MedRecord, today: LocalDate) =
        !today.isBefore(m.startDate) && (m.durationDays == null || today.isBefore(m.startDate.plusDays(m.durationDays.toLong())))

    private fun sameDrug(a: MedRecord, b: MedRecord) = (MedicineSalts.ingredients(a.name) intersect MedicineSalts.ingredients(b.name)).isNotEmpty()

    private fun medicines(i: PrepInput): PrepSection {
        val items = mutableListOf<PrepItem>()
        var note: String? = null
        val visits = i.medicines.groupBy { it.source.documentId }.entries.sortedBy { it.key ?: 0L }.map { it.value }
        val latest = visits.lastOrNull().orEmpty()
        val previous = visits.getOrNull(visits.lastIndex - 1).orEmpty()

        if (visits.size >= 2) {
            val latestDate = latest.minOf { it.startDate }
            val previousDate = previous.minOf { it.startDate }
            val p = subj(i)
            val src = latest.first().source.label
            latest.filter { m -> previous.none { sameDrug(it, m) } }.forEach { m ->
                items += PrepItem("${name(m)} is new on the ${latestDate.format(LONG)} prescription. What is it for, and how long should $p take it?", src)
            }
            previous.filter { m -> latest.none { sameDrug(it, m) } }.forEach { m ->
                items += PrepItem("${name(m)} was on the ${previousDate.format(LONG)} prescription but is not on the latest one. Was it stopped on purpose, or should $p still take it?", m.source.label)
            }
            latest.forEach { now ->
                val was = previous.firstOrNull { sameDrug(it, now) } ?: return@forEach
                val a = was.strength?.replace(" ", "")?.lowercase()
                val b = now.strength?.replace(" ", "")?.lowercase()
                if (a != null && b != null && a != b) {
                    items += PrepItem("${now.name}'s strength changed from ${was.strength} to ${now.strength}. What was the reason for the change?", src)
                }
                if (freq(was) != freq(now)) {
                    items += PrepItem("${now.name} changed from ${freq(was)} to ${freq(now)}. What was the reason for the change?", src)
                }
            }
            if (items.isEmpty()) note = "The last two prescriptions (${previousDate.format(SHORT)} and ${latestDate.format(SHORT)}) list the same medicines."
        } else if (visits.size == 1) {
            note = "Only one prescription is saved, so there are no changes to compare."
            items += PrepItem("What is each of ${poss(i)} medicines for, and when should each course stop?", latest.first().source.label)
        } else {
            note = "No medicines are saved yet."
        }

        i.adherence?.takeIf { it.scheduled > 0 && it.missed > 0 }?.let { a ->
            items += PrepItem("In the last ${Adherence.WINDOW_DAYS} days I marked ${a.taken} of ${a.scheduled} scheduled doses as taken. What should I do when I miss a dose?", "Dose log")
        }
        // Courses that have run their length.
        i.medicines.filter { !it.sos && it.durationDays != null }.forEach { m ->
            val end = m.startDate.plusDays(m.durationDays!!.toLong() - 1)
            if (end.isBefore(i.today)) items += PrepItem("The ${m.durationDays}-day course of ${name(m)} ended on ${end.format(LONG)}. Should it continue, stop or change?", m.source.label)
        }
        // Overlapping ingredients among what is being taken now.
        DuplicateCheck.find(i.medicines.filter { active(it, i.today) }.map { MedRef(it.name, it.strength) }).forEach { w ->
            val what = w.shared.filterNot { it.startsWith("~") }.sorted().joinToString(" and ")
            items += PrepItem("${w.first.label} and ${w.second.label} ${if (what.isEmpty()) "are the same medicine" else "both contain $what"}. ${isSubj(i)} meant to take both?")
        }
        // Running low.
        i.medicines.filter { active(it, i.today) && it.supply?.needsRefill == true }.forEach { m ->
            items += PrepItem("${name(m)} ${m.supply!!.phrase}. Can ${subj(i)} get a new prescription or a refill?", m.source.label)
        }
        return PrepSection("Medicines", items, note)
    }

    // ---- child: growth

    private fun ago(days: Long) = when {
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        else -> "${days / 30} months ago"
    }

    private fun growth(i: PrepInput): PrepSection {
        val p = i.person
        val points = i.growth.sortedBy { it.date }
        val items = mutableListOf<PrepItem>()
        val last = points.lastOrNull()
        if (last == null) {
            items += PrepItem("No weight or length has been recorded for $p. Can weight, length and head size be measured at this visit and plotted on the growth chart?")
            return PrepSection("Growth", items)
        }
        val ageDays = i.dob?.let { ChronoUnit.DAYS.between(it, i.today) } ?: 0
        val gap = ChronoUnit.DAYS.between(last.date, i.today)
        val limit = if (ageDays < 730) 60 else 180
        if (gap > limit) items += PrepItem("The last recorded measurement was on ${last.date.format(LONG)} (${ago(gap)}). Can $p be weighed and measured again at this visit?")

        fun trend(label: String, unit: String, pick: (GrowthPoint) -> Double?) {
            val seen = points.filter { pick(it) != null }
            if (seen.size < 2) return
            val a = seen[seen.lastIndex - 1]; val b = seen.last()
            val va = pick(a)!!; val vb = pick(b)!!
            val move = when { vb > va -> "went from"; vb < va -> "went down from"; else -> "stayed at" }
            val text = if (vb == va) "$label $move ${formatNumber(vb)} $unit between ${a.date.format(SHORT)} and ${b.date.format(SHORT)}."
            else "$label $move ${formatNumber(va)} $unit (${a.date.format(SHORT)}) to ${formatNumber(vb)} $unit (${b.date.format(SHORT)})."
            val question = if (vb < va) " Should this be checked?" else " Is this on track for ${p}'s age?"
            items += PrepItem(text + question, "Growth entries")
        }
        trend("Weight", "kg") { it.weightKg }
        trend("Height", "cm") { it.heightCm }
        trend("Head size", "cm") { it.headCm }

        items += PrepItem("Can these measurements be plotted on the growth chart, and can the doctor say how $p is growing for age?", "Growth entries")
        val kg = points.lastOrNull { it.weightKg != null }
        if (kg != null && i.medicines.any { active(it, i.today) }) {
            items += PrepItem("$p's weight was last recorded as ${formatNumber(kg.weightKg!!)} kg on ${kg.date.format(LONG)}. Can the doctor confirm each medicine's dose still suits that weight?", "Growth entries")
        }
        return PrepSection("Growth", items)
    }

    // ---- child: vaccinations

    private fun vaccinations(i: PrepInput, plan: List<MilestoneState>): PrepSection {
        val items = mutableListOf<PrepItem>()
        plan.filter { it.status == VaccineStatus.OVERDUE }.forEach { m ->
            items += PrepItem("Overdue: ${m.pendingTitle} (${ImmunizationEngine.relativeText(m, i.today)}). Can these be given at this visit, and is a catch-up plan needed?", "Immunization schedule")
        }
        plan.filter { it.status == VaccineStatus.DUE }.forEach { m ->
            items += PrepItem("Due now: ${m.pendingTitle}. Can these be given at this visit?", "Immunization schedule")
        }
        plan.firstOrNull { it.status == VaccineStatus.UPCOMING }?.let { m ->
            items += PrepItem("Coming up: ${m.pendingTitle} (${ImmunizationEngine.relativeText(m, i.today)}). Where and when should these be given?", "Immunization schedule")
        }
        val note = if (plan.none { it.status == VaccineStatus.OVERDUE || it.status == VaccineStatus.DUE }) "Every dose due so far is recorded on this phone." else null
        return PrepSection("Vaccinations", items, note)
    }

    // ---- closing questions

    private fun alsoAsk(i: PrepInput): List<String> = buildList {
        if (i.isChild) add("Is ${i.person}'s feeding, sleep and development on track for this age?")
        add("Is there anything to watch for before the next visit, and when should ${subj(i)} come back?")
        if (i.labs.isNotEmpty()) add("Which tests should be repeated, and when?")
    }
}
