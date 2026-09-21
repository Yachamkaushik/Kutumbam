package com.kutumbam.app.vitals

import com.kutumbam.app.parse.formatNumber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class VitalKind(val label: String) { BP("Blood pressure"), SUGAR("Blood sugar"), WEIGHT("Weight") }

val SUGAR_CONTEXTS = listOf("Fasting", "After meal", "Random")

/** One reading the person took at home. [value2] is the diastolic pressure; [context] is fasting / after meal / random for sugar. */
data class Reading(val id: Long, val date: LocalDate, val time: LocalTime, val kind: VitalKind, val value: Double, val value2: Double?, val context: String?) {
    val text: String get() = when (kind) {
        VitalKind.BP -> "${formatNumber(value)}/${formatNumber(value2 ?: 0.0)} mmHg"
        VitalKind.SUGAR -> "${formatNumber(value)} mg/dL${context?.let { " (${it.lowercase()})" }.orEmpty()}"
        VitalKind.WEIGHT -> "${formatNumber(value)} kg"
    }
}

/** A limit the person's own doctor gave. The app never supplies a limit of its own. */
typealias Limits = Map<String, Double>

object VitalRules {
    const val BP_SYSTOLIC = "BP_SYS"
    const val BP_DIASTOLIC = "BP_DIA"
    const val SUGAR_FASTING = "SUGAR_FASTING"
    const val SUGAR_AFTER = "SUGAR_AFTER"

    fun sugarKey(context: String?) = when (context) { "Fasting" -> SUGAR_FASTING; "After meal" -> SUGAR_AFTER; else -> null }

    fun validateBp(sys: Double?, dia: Double?): String? = when {
        sys == null || dia == null -> "Enter both numbers, for example 120 and 80."
        sys !in 60.0..260.0 -> "The top number doesn't look right. Check it and try again."
        dia !in 30.0..160.0 -> "The bottom number doesn't look right. Check it and try again."
        dia >= sys -> "The top number should be larger than the bottom number."
        else -> null
    }

    fun validateSugar(v: Double?): String? = if (v == null || v !in 20.0..600.0) "That sugar reading doesn't look right. Enter it in mg/dL." else null

    fun validateWeight(v: Double?): String? = if (v == null || v !in 2.0..300.0) "That weight doesn't look right. Enter it in kg." else null

    enum class Status { ABOVE, WITHIN, NO_LIMIT }

    /** Compared only with a limit the person entered. With none set there is no judgement at all. */
    fun status(r: Reading, limits: Limits): Status = when (r.kind) {
        VitalKind.BP -> {
            val s = limits[BP_SYSTOLIC]; val d = limits[BP_DIASTOLIC]
            when {
                s == null && d == null -> Status.NO_LIMIT
                (s != null && r.value > s) || (d != null && (r.value2 ?: 0.0) > d) -> Status.ABOVE
                else -> Status.WITHIN
            }
        }
        VitalKind.SUGAR -> {
            val high = sugarKey(r.context)?.let { limits[it] }
            when { high == null -> Status.NO_LIMIT; r.value > high -> Status.ABOVE; else -> Status.WITHIN }
        }
        VitalKind.WEIGHT -> Status.NO_LIMIT
    }

    /** "130/80 mmHg" or "110 mg/dL fasting", the limit as the person entered it. */
    fun limitText(kind: VitalKind, context: String?, limits: Limits): String? = when (kind) {
        VitalKind.BP -> {
            val s = limits[BP_SYSTOLIC]; val d = limits[BP_DIASTOLIC]
            if (s == null && d == null) null else "${s?.let { formatNumber(it) } ?: "-"}/${d?.let { formatNumber(it) } ?: "-"} mmHg"
        }
        VitalKind.SUGAR -> sugarKey(context)?.let { limits[it] }?.let { "${formatNumber(it)} mg/dL ${context?.lowercase()}" }
        VitalKind.WEIGHT -> null
    }
}

private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Plain statements about recent readings, shared by the visit sheet and the summary PDF. All facts; no interpretation. */
object VitalSummary {
    const val WINDOW_DAYS = 14L

    private fun newest(list: List<Reading>) = list.sortedWith(compareBy({ it.date }, { it.time }, { it.id })).lastOrNull()

    fun lines(readings: List<Reading>, limits: Limits, today: LocalDate): List<String> = buildList {
        val since = today.minusDays(WINDOW_DAYS)
        val recent = readings.filter { !it.date.isBefore(since) }

        val bp = recent.filter { it.kind == VitalKind.BP }
        newest(bp)?.let { last ->
            val above = bp.count { VitalRules.status(it, limits) == VitalRules.Status.ABOVE }
            val limit = VitalRules.limitText(VitalKind.BP, null, limits)
            add(
                "Blood pressure: latest ${last.text} (${last.date.format(SHORT)}). ${bp.size} ${if (bp.size == 1) "reading" else "readings"} in ${WINDOW_DAYS} days" +
                    (if (bp.size > 1) ", from ${bp.minBy { it.value }.value.let(::formatNumber)}/${bp.minBy { it.value }.value2?.let(::formatNumber)} to ${bp.maxBy { it.value }.value.let(::formatNumber)}/${bp.maxBy { it.value }.value2?.let(::formatNumber)}" else "") +
                    (if (limit != null) "; $above above the limit set ($limit)." else ". No limit set."),
            )
        }
        SUGAR_CONTEXTS.forEach { ctx ->
            val list = recent.filter { it.kind == VitalKind.SUGAR && it.context == ctx }
            newest(list)?.let { last ->
                val limit = VitalRules.limitText(VitalKind.SUGAR, ctx, limits)
                val above = list.count { VitalRules.status(it, limits) == VitalRules.Status.ABOVE }
                add(
                    "Blood sugar (${ctx.lowercase()}): latest ${formatNumber(last.value)} mg/dL (${last.date.format(SHORT)}). ${list.size} ${if (list.size == 1) "reading" else "readings"} in ${WINDOW_DAYS} days" +
                        (if (list.size > 1) ", from ${formatNumber(list.minOf { it.value })} to ${formatNumber(list.maxOf { it.value })}" else "") +
                        (if (limit != null) "; $above above the limit set ($limit)." else ". No limit set."),
                )
            }
        }
        val weights = readings.filter { it.kind == VitalKind.WEIGHT }.sortedWith(compareBy({ it.date }, { it.time }, { it.id }))
        weights.lastOrNull()?.let { last ->
            val before = weights.lastOrNull { it.date.isBefore(last.date.minusDays(20)) } ?: weights.firstOrNull { it.id != last.id }
            val change = before?.let { last.value - it.value }
            add(
                "Weight: ${formatNumber(last.value)} kg (${last.date.format(SHORT)})" +
                    if (before != null && change != null && kotlin.math.abs(change) >= 1.0) ", ${formatNumber(kotlin.math.abs(change))} kg ${if (change < 0) "lower" else "higher"} than ${formatNumber(before.value)} kg on ${before.date.format(SHORT)}." else ".",
            )
        }
    }

    /** The question that goes with a set of readings, or null when there is nothing to ask about. */
    fun questions(readings: List<Reading>, limits: Limits, today: LocalDate, self: Boolean, person: String): List<String> = buildList {
        val me = if (self) "me" else person
        val i = if (self) "I" else person
        val since = today.minusDays(WINDOW_DAYS)
        val recent = readings.filter { !it.date.isBefore(since) }
        val bp = recent.filter { it.kind == VitalKind.BP }
        if (bp.isNotEmpty()) {
            val above = bp.count { VitalRules.status(it, limits) == VitalRules.Status.ABOVE }
            add(
                when {
                    VitalRules.limitText(VitalKind.BP, null, limits) == null -> "What blood pressure range should $i aim for at home?"
                    above > 0 -> "$above of ${bp.size} home blood pressure readings were above the limit set. Does anything need to change for $me?"
                    else -> "Home blood pressure has stayed within the limit set. Is that limit still right for $me?"
                },
            )
        }
        val sugar = recent.filter { it.kind == VitalKind.SUGAR }
        if (sugar.isNotEmpty()) {
            val above = sugar.count { VitalRules.status(it, limits) == VitalRules.Status.ABOVE }
            val anyLimit = SUGAR_CONTEXTS.any { c -> VitalRules.limitText(VitalKind.SUGAR, c, limits) != null }
            add(
                when {
                    !anyLimit -> "What blood sugar range should $i aim for at home, before and after meals?"
                    above > 0 -> "$above of ${sugar.size} home sugar readings were above the limit set. Does anything need to change for $me?"
                    else -> "Home sugar readings have stayed within the limit set. Is that limit still right for $me?"
                },
            )
        }
    }
}
