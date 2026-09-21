package com.kutumbam.app.parse

import java.time.LocalDate

/** One dose read from a vaccination card. The date is null when the card row had none. */
data class ParsedVaccination(val scheduleId: String, val label: String, val date: LocalDate?, val sourceLine: String)

/** Reads vaccine names (with their dose numbers) and the date on the same card row. Rule-based, like the rest of the parsers. */
object VaccinationCardParser {

    private val I = RegexOption.IGNORE_CASE
    private const val SEP = """[\s\-_./]*"""

    private val PATTERNS: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""\bBCG\b""", I) to { _ -> "BCG" },
        Regex("""\bOPV${SEP}(0|1|2|3)\b""", I) to { m -> "OPV-${m.groupValues[1]}" },
        Regex("""\bOPV${SEP}(booster|b)\b""", I) to { _ -> "OPV-B" },
        Regex("""(?<![+\w])hep(?:atitis)?${SEP}b\b(?![\s\-]*\+)(?:${SEP}(?:birth|0|zero))?""", I) to { _ -> "HEPB-0" },
        Regex("""\bpenta(?:valent)?${SEP}(1|2|3)\b""", I) to { m -> "PENTA-${m.groupValues[1]}" },
        Regex("""\bDPT\s*\+\s*hep\s*b\s*\+\s*hib${SEP}(1|2|3)\b""", I) to { m -> "PENTA-${m.groupValues[1]}" },
        Regex("""\brota(?:virus)?${SEP}(1|2|3)\b""", I) to { m -> "ROTA-${m.groupValues[1]}" },
        Regex("""\bf?IPV${SEP}(1|2|3)\b""", I) to { m -> "FIPV-${m.groupValues[1]}" },
        Regex("""\bPCV${SEP}(1|2)\b""", I) to { m -> "PCV-${m.groupValues[1]}" },
        Regex("""\bPCV${SEP}(booster|b)\b""", I) to { _ -> "PCV-B" },
        // UIP uses Measles-Rubella; many cards and private clinics write MMR, so both map to the same dose.
        Regex("""\b(?:MR|MMR|measles(?:${SEP}rubella)?)${SEP}(1|2)\b""", I) to { m -> "MR-${m.groupValues[1]}" },
        Regex("""\bDPT${SEP}(?:booster|b)(?:${SEP}(1|2))?\b""", I) to { m -> if (m.groupValues[1] == "2") "DPT-B2" else "DPT-B1" },
    )

    fun parse(text: String): List<ParsedVaccination> {
        val found = LinkedHashMap<String, ParsedVaccination>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.length < 3) continue
            val ids = PATTERNS.flatMap { (regex, toId) -> regex.findAll(line).map(toId).toList() }.distinct()
            if (ids.isEmpty()) continue
            val date = DocumentDates.findInLine(line)
            for (id in ids) {
                val vaccine = UipSchedule.byId(id) ?: continue
                val existing = found[id]
                // Keep the first row for a dose, unless a later row is the one that carries a date.
                if (existing == null || (existing.date == null && date != null)) found[id] = ParsedVaccination(id, vaccine.label, date, line)
            }
        }
        return found.values.toList()
    }
}
