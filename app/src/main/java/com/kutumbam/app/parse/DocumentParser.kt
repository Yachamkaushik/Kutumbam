package com.kutumbam.app.parse

import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

object DocumentClassifier {
    private val PRESCRIPTION = listOf("rx", "tab.", "tab ", "cap.", "cap ", "syp", "syrup", "inj", "advice", "follow up", "follow-up", "bd", "tds", "od ", "after food", "before food", "diagnosis")
    private val LAB = listOf("reference range", "ref. range", "ref range", "biological ref", "hemoglobin", "haemoglobin", "glucose", "cholesterol", "creatinine", "tsh", "hba1c", "specimen", "pathology", "laboratory", "result", "units", "investigation", "wbc", "platelet")
    private val VACCINE = listOf("bcg", "opv", "hepatitis b", "hep b", "pentavalent", "rotavirus", "ipv", "mmr", "measles", "vaccin", "immuniz", "immunis", "dpt", "pcv", "typhoid", "mother and child", "batch")

    fun classify(text: String): DocumentType {
        val t = text.lowercase()
        val scores = mapOf(
            DocumentType.PRESCRIPTION to PRESCRIPTION.count { it in t },
            DocumentType.LAB_REPORT to LAB.count { it in t },
            DocumentType.VACCINATION_CARD to VACCINE.count { it in t },
        )
        val best = scores.maxByOrNull { it.value }!!
        return if (best.value >= 2) best.key else DocumentType.UNKNOWN
    }
}

/** Finds the most plausible document date: prefers collection/report/visit dates over birth dates. */
object DocumentDates {
    private val NUMERIC = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")
    private val NAMED = Regex("""\b(\d{1,2})[\s\-]([A-Za-z]{3,9})[\s\-,]*(\d{2,4})\b""")
    private val DOB_HINT = Regex("""dob|d\.o\.b|birth|age""", RegexOption.IGNORE_CASE)

    /** The first date on a single line, or null. Used for card rows like "OPV-1  31/08/2026". */
    fun findInLine(line: String): LocalDate? {
        NUMERIC.find(line)?.let { m -> toDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it } }
        NAMED.find(line)?.let { m ->
            val month = monthOf(m.groupValues[2]) ?: return null
            return toDate(m.groupValues[1].toInt(), month, m.groupValues[3].toInt())
        }
        return null
    }

    fun find(text: String): LocalDate? {
        val candidates = mutableListOf<Pair<LocalDate, Boolean>>()
        for (line in text.lines()) {
            val isDob = DOB_HINT.containsMatchIn(line)
            NUMERIC.findAll(line).forEach { m ->
                toDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { candidates += it to isDob }
            }
            NAMED.findAll(line).forEach { m ->
                val month = monthOf(m.groupValues[2]) ?: return@forEach
                toDate(m.groupValues[1].toInt(), month, m.groupValues[3].toInt())?.let { candidates += it to isDob }
            }
        }
        return (candidates.firstOrNull { !it.second } ?: candidates.firstOrNull())?.first
    }

    private fun toDate(d: Int, mo: Int, y: Int): LocalDate? {
        val year = if (y < 100) 2000 + y else y
        return runCatching { LocalDate.of(year, mo, d) }.getOrNull()
    }

    private fun monthOf(s: String): Int? = Month.entries.firstOrNull {
        val full = it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        s.lowercase().let { x -> x.length >= 3 && full.startsWith(x) }
    }?.value
}

object DocumentParser {
    fun parse(text: String, forcedType: DocumentType? = null): ParsedDocument {
        val type = forcedType ?: DocumentClassifier.classify(text)
        val labs = if (type == DocumentType.LAB_REPORT || type == DocumentType.UNKNOWN) LabReportParser.parse(text) else emptyList()
        val meds = if (type == DocumentType.PRESCRIPTION || type == DocumentType.UNKNOWN) PrescriptionParser.parse(text) else emptyList()
        val vaccines = if (type == DocumentType.VACCINATION_CARD || type == DocumentType.UNKNOWN) VaccinationCardParser.parse(text) else emptyList()
        return ParsedDocument(type, DocumentDates.find(text), meds, labs, vaccines)
    }
}
