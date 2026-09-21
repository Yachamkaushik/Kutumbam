package com.kutumbam.app.locker

import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.TestNames
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Picks the stored records that bear on a question, with plain rules. The model never searches or guesses what
 * exists: it is handed exactly these facts and told to answer from them alone.
 */
object Retrieval {

    private val ADVICE = Regex(
        """\b(should i|should we|should (he|she|they)|can i (stop|skip|take|give|double|increase)|is it (safe|ok|okay|bad|serious|dangerous)|safe to|stop taking|stopping|skip(ping)?|double (the )?dose|increase|decrease|reduce|change (the )?dose|dosage|side effects?|interact(s|ion|ions)?|cure|treat(ment)?|diagnos\w*|what disease|what does .* mean|diabetic|diabetes|cancer|worried|worry|normal for|what should i|is (this|that|it) (a )?(problem|serious|high|low))\b""",
        RegexOption.IGNORE_CASE,
    )

    private val STOP = setOf(
        "what", "when", "does", "did", "do", "is", "was", "the", "a", "an", "of", "for", "to", "my", "his", "her", "their", "me", "i", "and",
        "in", "on", "at", "last", "latest", "recent", "reading", "value", "tell", "about", "please", "much", "many", "which", "how", "any",
        "are", "were", "has", "have", "had", "it", "this", "that", "with", "from", "show", "give", "get", "there", "who", "why", "s",
    )

    private val LAB_SYNONYMS = mapOf(
        "fasting_glucose" to setOf("sugar", "glucose", "fbs", "fasting"),
        "hba1c" to setOf("hba1c", "a1c", "hb1c", "glycated", "glycosylated"),
        "total_cholesterol" to setOf("cholesterol", "lipid", "lipids"),
        "tsh" to setOf("tsh", "thyroid"),
    )

    private val LAB_WORDS = setOf("report", "reports", "lab", "test", "tests", "result", "results", "blood")
    private val MED_WORDS = setOf(
        "medicine", "medicines", "medication", "medications", "tablet", "tablets", "pill", "pills", "capsule", "capsules", "syrup", "dose", "doses",
        "drug", "drugs", "prescription", "prescribed", "morning", "afternoon", "evening", "night", "bedtime", "food", "breakfast", "lunch", "dinner",
        "today", "now", "schedule", "take", "takes", "taking", "refill",
    )
    private val VACCINE_WORDS = setOf(
        "vaccine", "vaccines", "vaccination", "vaccinations", "immunization", "immunisation", "injection", "injections", "shot", "shots",
        "overdue", "due", "bcg", "opv", "polio", "penta", "pentavalent", "rotavirus", "mmr", "measles", "rubella", "pcv", "ipv", "fipv",
        "hepatitis", "dpt", "booster",
    )

    private val LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun isAdviceQuestion(question: String) = ADVICE.containsMatchIn(question)

    fun retrieve(question: String, data: LockerData): Retrieved {
        val tokens = tokens(question, data.person)
        val advice = isAdviceQuestion(question)
        val facts = mutableListOf<Fact>()
        val topics = mutableSetOf<Topic>()
        val direct = mutableListOf<String>()

        labs(tokens, data)?.let { (f, text) -> facts += f; direct += text; topics += Topic.LAB }
        medicines(tokens, data)?.let { (f, text) -> facts += f; direct += text; topics += Topic.MEDICINE }
        vaccines(tokens, data)?.let { (f, text) -> facts += f; direct += text; topics += Topic.VACCINE }

        val answer = if (facts.isEmpty()) {
            "I couldn't find that in ${data.person}'s stored records. I can answer questions about the medicines, lab reports and vaccinations saved here."
        } else direct.joinToString("\n\n")
        return Retrieved(facts, topics, answer, advice)
    }

    // ---- labs

    private fun labs(tokens: Set<String>, data: LockerData): Pair<List<Fact>, String>? {
        if (data.labs.isEmpty()) return null
        val groups = data.labs.groupBy { it.key }
        val matched = groups.filter { (key, rows) ->
            val words = LAB_SYNONYMS[key].orEmpty() + rows.first().testName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in GENERIC_TEST_WORDS }
            tokens.any { it in words }
        }
        val generic = tokens.any { it in LAB_WORDS }
        if (matched.isEmpty() && !generic) return null

        val chosen: Map<String, List<LabRecord>> = if (matched.isNotEmpty()) matched else {
            val latestDate = data.labs.maxOf { it.date }
            data.labs.filter { it.date == latestDate }.groupBy { it.key }
        }

        val facts = mutableListOf<Fact>()
        val lines = mutableListOf<String>()
        for ((_, rows) in chosen) {
            val ordered = rows.sortedBy { it.date }.takeLast(6)
            val latest = ordered.last()
            val range = describeRange(latest.low, latest.high, latest.unit)
            for (r in ordered) {
                facts += Fact(labFactText(r), labShort(r), r.source)
            }
            val status = statusWords(latest)
            lines += buildString {
                append("${data.person}'s latest ${latest.testName} was ${formatNumber(latest.value)}${latest.unit?.let { " $it" }.orEmpty()} on ${latest.date.format(LONG)}")
                if (status != null && range != null) append(", $status ${if (latest.standardReference) "the standard reference range" else "the range printed on the report"} ($range)")
                append(".")
                if (ordered.size > 1) append(" Earlier: " + ordered.dropLast(1).joinToString(", ") { "${formatNumber(it.value)} (${it.date.format(SHORT)})" } + ".")
                if (status == "above" || status == "below") append(" Please talk to a doctor about it.")
            }
        }
        return facts to lines.joinToString("\n")
    }

    private fun labFactText(r: LabRecord): String {
        val range = describeRange(r.low, r.high, r.unit)
        val status = statusWords(r)
        return "${r.testName} on ${r.date.format(LONG)}: ${formatNumber(r.value)}${r.unit?.let { " $it" }.orEmpty()}" +
            (if (range != null) " (${if (r.standardReference) "standard reference range" else "range printed on the report"}: $range${status?.let { "; the value is $it that range" }.orEmpty()})" else " (no range printed on the report)")
    }

    private fun labShort(r: LabRecord) =
        "${r.testName}: ${formatNumber(r.value)}${r.unit?.let { " $it" }.orEmpty()} on ${r.date.format(LONG)}"

    private fun statusWords(r: LabRecord): String? = when (RangeCheck.status(r.value, r.low, r.high)) {
        RangeStatus.ABOVE -> "above"
        RangeStatus.BELOW -> "below"
        RangeStatus.IN_RANGE -> "within"
        RangeStatus.NO_RANGE -> null
    }

    // ---- medicines

    private fun medicines(tokens: Set<String>, data: LockerData): Pair<List<Fact>, String>? {
        if (data.medicines.isEmpty()) return null
        val byName = data.medicines.filter { m -> m.name.lowercase().split(Regex("[^a-z0-9]+")).any { it.length >= 3 && it in tokens } }
        val hint = tokens.any { it in MED_WORDS }
        if (byName.isEmpty() && !hint) return null

        var list = byName
        var heading = "${data.person}'s medicines"
        if (list.isEmpty()) {
            val window = when {
                "morning" in tokens || "breakfast" in tokens -> 4..11 to "in the morning"
                "afternoon" in tokens || "lunch" in tokens -> 12..15 to "in the afternoon"
                "evening" in tokens -> 16..19 to "in the evening"
                "night" in tokens || "bedtime" in tokens || "dinner" in tokens -> 20..23 to "at night"
                else -> null
            }
            list = data.medicines.filter { active(it, data.today) || it.sos }
            if (window != null) {
                list = list.filter { m -> m.times.any { it.hour in window.first } }
                heading = "${data.person} takes, ${window.second}"
            } else if ("today" in tokens || "now" in tokens || "schedule" in tokens) heading = "${data.person}'s medicines for today"
            if (list.isEmpty()) return emptyList<Fact>() to "There are no medicines stored for ${data.person} ${window?.second ?: "at that time"}."
        }
        list = list.take(12)
        val facts = list.map { Fact(medFactText(it, data.today), medShort(it), it.source) }
        val text = heading + ":\n" + facts.joinToString("\n") { "• ${it.short}" }
        return facts to text
    }

    private fun active(m: MedRecord, today: LocalDate) = !today.isBefore(m.startDate) && (m.durationDays == null || today.isBefore(m.startDate.plusDays(m.durationDays.toLong())))

    private fun medName(m: MedRecord) = listOfNotNull(m.name, m.strength).joinToString(" ")

    private fun mealWords(m: MealTiming) = when (m) {
        MealTiming.BEFORE_FOOD -> "before food"
        MealTiming.AFTER_FOOD -> "after food"
        MealTiming.WITH_FOOD -> "with food"
        MealTiming.EMPTY_STOMACH -> "on an empty stomach"
        MealTiming.UNSPECIFIED -> null
    }

    private fun timesText(m: MedRecord) = if (m.sos) "only when needed" else m.times.joinToString(" and ") { it.format(CLOCK) }

    private fun medFactText(m: MedRecord, today: LocalDate): String {
        val end = m.durationDays?.let { m.startDate.plusDays(it.toLong() - 1) }
        return "${medName(m)}: ${timesText(m)}" + (mealWords(m.meal)?.let { ", $it" } ?: "") +
            (m.durationDays?.let { ", for $it days from ${m.startDate.format(LONG)}" } ?: ", from ${m.startDate.format(LONG)} with no end date stated") +
            (if (end != null && end.isBefore(today)) " (course ended ${end.format(LONG)})" else "") + "."
    }

    private fun medShort(m: MedRecord) = medName(m) + ": " + timesText(m) + (mealWords(m.meal)?.let { ", $it" } ?: "") + "."

    // ---- vaccines

    private fun vaccines(tokens: Set<String>, data: LockerData): Pair<List<Fact>, String>? {
        val plan = data.immunization ?: return null
        val wanted = tokens.any { it in VACCINE_WORDS }
        val all = plan.flatMap { ms -> ms.vaccines.map { it to ms } }
        val byName = all.filter { (v, _) -> v.vaccine.label.lowercase().split(Regex("[^a-z0-9]+")).any { it.length >= 3 && it in tokens } }
        if (!wanted && byName.isEmpty()) return null

        val src = Source(SourceKind.VACCINATION, "Vaccination schedule (India UIP)")
        val facts = mutableListOf<Fact>()
        val lines = mutableListOf<String>()
        if (byName.isNotEmpty() && tokens.none { it in setOf("due", "overdue", "vaccines", "vaccinations", "immunization", "immunisation") }) {
            for ((v, ms) in byName) {
                val t = when (v.status) {
                    VaccineStatus.DONE -> "${v.vaccine.label}: given on ${v.givenOn!!.format(LONG)}."
                    VaccineStatus.OVERDUE -> "${v.vaccine.label}: not recorded as given and overdue (was due ${ms.dueDate.format(LONG)})."
                    VaccineStatus.DUE -> "${v.vaccine.label}: due now (from ${ms.dueDate.format(LONG)}), not yet recorded as given."
                    VaccineStatus.UPCOMING -> "${v.vaccine.label}: due on ${ms.dueDate.format(LONG)}."
                }
                facts += Fact(t, t, src); lines += "• $t"
            }
            return facts to (data.person + "'s vaccine records:\n" + lines.joinToString("\n"))
        }
        val given = all.filter { it.first.status == VaccineStatus.DONE }
        val overdue = all.filter { it.first.status == VaccineStatus.OVERDUE }
        val due = all.filter { it.first.status == VaccineStatus.DUE }
        val next = plan.firstOrNull { it.status == VaccineStatus.UPCOMING }
        if (given.isNotEmpty()) facts += Fact("Given: " + given.joinToString(", ") { "${it.first.vaccine.label} (${it.first.givenOn!!.format(SHORT)})" } + ".", "Given: " + given.joinToString(", ") { it.first.vaccine.label } + ".", src)
        if (overdue.isNotEmpty()) facts += Fact("Overdue (not recorded as given): " + overdue.joinToString(", ") { it.first.vaccine.label } + ".", "Overdue: " + overdue.joinToString(", ") { it.first.vaccine.label } + ".", src)
        if (due.isNotEmpty()) facts += Fact("Due now: " + due.joinToString(", ") { it.first.vaccine.label } + ".", "Due now: " + due.joinToString(", ") { it.first.vaccine.label } + ".", src)
        if (next != null) {
            val t = "Next: ${next.vaccines.filter { it.status != VaccineStatus.DONE }.joinToString(", ") { it.vaccine.label }} ${ImmunizationEngine.relativeText(next, data.today)} (from ${next.dueDate.format(LONG)})."
            facts += Fact(t, t, src)
        }
        if (facts.isEmpty()) facts += Fact("Every dose on the schedule is recorded.", "Every dose on the schedule is recorded.", src)
        return facts to (data.person + "'s vaccinations:\n" + facts.joinToString("\n") { "• ${it.short}" })
    }

    // ---- helpers

    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val GENERIC_TEST_WORDS = setOf("total", "serum", "blood", "count", "level", "test")

    fun tokens(question: String, person: String): Set<String> {
        val name = person.lowercase()
        return question.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in STOP && it != name }.toSet()
    }
}
