package com.kutumbam.app.parse

import java.time.LocalTime

/** Indian prescription shorthand: OD/BD/TDS/QID/HS/SOS, 1-0-1 style dosing, q6h, plain-English equivalents. */
object FrequencyParser {

    private val MORNING = LocalTime.of(8, 0)
    private val NOON = LocalTime.of(14, 0)
    private val NIGHT = LocalTime.of(20, 0)
    private val BEDTIME = LocalTime.of(22, 0)

    private const val Q = """(?:[0-3](?:\.5)?|½)"""
    private val DOSE_PATTERN = Regex("""(?<![\d/.\-])($Q)\s*[-–—]\s*($Q)\s*[-–—]\s*($Q)(?:\s*[-–—]\s*($Q))?(?![\d/.\-])""")
    private val EVERY_N_HOURS = Regex("""\b(?:q\s*(\d{1,2})\s*h|every\s+(\d{1,2})\s*(?:hours?|hrs?))\b""", RegexOption.IGNORE_CASE)

    private val WORDS: List<Pair<Regex, FrequencyCode>> = listOf(
        Regex("""\b(?:sos|prn|as\s+needed|if\s+needed|when\s+(?:required|needed)|as\s+required)\b""", RegexOption.IGNORE_CASE) to FrequencyCode.SOS,
        Regex("""\b(?:qid|q\.i\.d\.?|qds|four\s+times(?:\s+a\s+day)?)\b""", RegexOption.IGNORE_CASE) to FrequencyCode.QID,
        Regex("""\b(?:tds|t\.d\.s\.?|tid|t\.i\.d\.?|thrice(?:\s+a\s+day)?|three\s+times(?:\s+a\s+day)?)\b""", RegexOption.IGNORE_CASE) to FrequencyCode.TDS,
        Regex("""\b(?:bd|b\.d\.?|bid|b\.i\.d\.?|twice(?:\s+(?:a\s+)?(?:day|daily))?|two\s+times(?:\s+a\s+day)?)\b""", RegexOption.IGNORE_CASE) to FrequencyCode.BD,
        Regex("""\b(?:hs|h\.s\.?|at\s+bed\s*time|bed\s*time|at\s+night)\b""", RegexOption.IGNORE_CASE) to FrequencyCode.HS,
        Regex("""\b(?:od|o\.d\.?|qd|q\.d\.?|once(?:\s+(?:a\s+)?(?:day|daily))?|daily|every\s+day)\b""", RegexOption.IGNORE_CASE) to FrequencyCode.OD,
    )

    fun defaultTimes(code: FrequencyCode): List<LocalTime> = when (code) {
        FrequencyCode.OD -> listOf(MORNING)
        FrequencyCode.BD -> listOf(MORNING, NIGHT)
        FrequencyCode.TDS -> listOf(MORNING, NOON, NIGHT)
        FrequencyCode.QID -> listOf(MORNING, LocalTime.of(12, 0), LocalTime.of(16, 0), NIGHT)
        FrequencyCode.HS -> listOf(BEDTIME)
        FrequencyCode.SOS, FrequencyCode.CUSTOM -> emptyList()
    }

    fun parse(line: String): Frequency? {
        parseDosePattern(line)?.let { return it }
        parseEveryNHours(line)?.let { return it }
        for ((regex, code) in WORDS) {
            val m = regex.find(line) ?: continue
            return Frequency(code, defaultTimes(code), m.value.trim())
        }
        return null
    }

    /** Range of the frequency token in [line], for callers that need to know where the name/strength stop. */
    fun findSpan(line: String): IntRange? {
        DOSE_PATTERN.find(line)?.let { return it.range }
        EVERY_N_HOURS.find(line)?.let { return it.range }
        return WORDS.firstNotNullOfOrNull { (r, _) -> r.find(line)?.range }
    }

    private fun parseDosePattern(line: String): Frequency? {
        val m = DOSE_PATTERN.find(line) ?: return null
        val parts = m.groupValues.drop(1).filter { it.isNotEmpty() }
        val taken = parts.map { it != "0" }
        val count = taken.count { it }
        if (count == 0) return null
        val raw = m.value.trim()
        val units = parts.mapNotNull { DoseUnits.fromToken(it) }.filterIndexed { i, _ -> taken[i] }
        if (parts.size == 4) {
            return Frequency(if (count == 4) FrequencyCode.QID else FrequencyCode.CUSTOM, defaultTimes(FrequencyCode.QID).filterIndexed { i, _ -> taken[i] }, raw, units)
        }
        val slots = listOf(MORNING, NOON, NIGHT).filterIndexed { i, _ -> taken[i] }
        return when {
            count == 3 -> Frequency(FrequencyCode.TDS, slots, raw, units)
            count == 2 -> Frequency(FrequencyCode.BD, slots, raw, units)
            taken[2] -> Frequency(FrequencyCode.HS, listOf(BEDTIME), raw, units) // 0-0-1
            else -> Frequency(FrequencyCode.OD, slots, raw, units)
        }
    }

    private fun parseEveryNHours(line: String): Frequency? {
        val m = EVERY_N_HOURS.find(line) ?: return null
        val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()?.takeIf { it in 2..24 } ?: return null
        val perDay = 24 / n
        val code = when (perDay) { 1 -> FrequencyCode.OD; 2 -> FrequencyCode.BD; 3 -> FrequencyCode.TDS; 4 -> FrequencyCode.QID; else -> FrequencyCode.CUSTOM }
        // Spread across the waking day using the standard defaults; the user can edit them on the schedule screen.
        val times = if (code == FrequencyCode.CUSTOM) generateSequence(6) { it + n }.takeWhile { it < 24 }.map { LocalTime.of(it, 0) }.toList() else defaultTimes(code)
        return Frequency(code, times, m.value.trim())
    }
}
