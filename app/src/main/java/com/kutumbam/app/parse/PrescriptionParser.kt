package com.kutumbam.app.parse

/** Turns OCR text of a prescription into medicines. Pure regex + dictionary; no model involved. */
object PrescriptionParser {

    private val FORM_PREFIX = Regex(
        """^\s*(?:\d{1,2}\s*[.)\]]\s*)?(tab(?:let)?s?|cap(?:sule)?s?|syp|syrup|inj(?:ection)?|oint(?:ment)?|cream|drops?|susp(?:ension)?|gel|lotion|sachet|neb|powder|spray|inhaler)\b\.?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING_INDEX = Regex("""^\s*\d{1,2}\s*[.)\]]\s+""")
    private val STRENGTH = Regex(
        """(?<![\w.])(\d+(?:\.\d+)?(?:\s*/\s*\d+(?:\.\d+)?)?)\s*(mg|mcg|µg|μg|ug|g|gm|ml|iu|%)(?![a-z])""",
        RegexOption.IGNORE_CASE,
    )
    private val DURATION_STOP = Regex("""\b(?:x|×|for)\s*\d""", RegexOption.IGNORE_CASE)
    private val NON_MEDICINE_LINE = Regex(
        """^\s*(?:dr\b|date|name|age|sex|gender|weight|wt\b|bp\b|temp|pulse|spo2|address|phone|ph\b|reg|clinic|hospital|diagnosis|dx\b|c/o|advice|follow|review|next|signature|patient|mr\b|mrs\b|ms\b|uhid|opd|id\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val FORM_CANON = mapOf(
        "tab" to "Tablet", "tablet" to "Tablet", "cap" to "Capsule", "capsule" to "Capsule", "syp" to "Syrup",
        "syrup" to "Syrup", "inj" to "Injection", "injection" to "Injection", "oint" to "Ointment",
        "ointment" to "Ointment", "cream" to "Cream", "drop" to "Drops", "drops" to "Drops", "susp" to "Suspension",
        "suspension" to "Suspension", "gel" to "Gel", "lotion" to "Lotion", "sachet" to "Sachet", "neb" to "Nebuliser",
        "powder" to "Powder", "spray" to "Spray", "inhaler" to "Inhaler",
    )

    fun parse(text: String): List<ParsedMedicine> {
        val result = mutableListOf<ParsedMedicine>()
        var pending: ParsedMedicine? = null
        var pendingIndex = -1

        for (raw in text.lines()) {
            val line = raw.trim().replace(Regex("""\s+"""), " ")
            if (line.length < 3) continue
            val startsMedicine = looksLikeMedicineStart(line)
            if (startsMedicine != null) {
                pending = startsMedicine
                result += startsMedicine
                pendingIndex = result.lastIndex
                continue
            }
            // A dosing-only continuation line ("1-0-1 after food x 30 days") completes the previous medicine.
            val prev = pending
            if (prev != null && !NON_MEDICINE_LINE.containsMatchIn(line)) {
                val freq = prev.frequency ?: FrequencyParser.parse(line)
                val meal = if (prev.meal == MealTiming.UNSPECIFIED) MealParser.parse(line) else prev.meal
                val days = prev.durationDays ?: DurationParser.parse(line)
                val qty = prev.quantity ?: PackQuantityParser.parse(line)
                if (freq != prev.frequency || meal != prev.meal || days != prev.durationDays || qty != prev.quantity) {
                    val merged = prev.copy(frequency = freq, meal = meal, durationDays = days, quantity = qty, sourceLine = prev.sourceLine + " | " + line)
                    result[pendingIndex] = merged
                    pending = merged
                }
            }
        }
        return result
    }

    private fun looksLikeMedicineStart(line: String): ParsedMedicine? {
        if (NON_MEDICINE_LINE.containsMatchIn(line)) return null

        val prefix = FORM_PREFIX.find(line)
        val body = if (prefix != null) line.substring(prefix.range.last + 1) else line.replace(LEADING_INDEX, "")
        val form = prefix?.groupValues?.get(1)?.lowercase()?.let { FORM_CANON[it.trimEnd('s')] ?: FORM_CANON[it] }

        val strength = STRENGTH.find(body)
        val freq = FrequencyParser.parse(body)
        val days = DurationParser.parse(body)
        val meal = MealParser.parse(body)
        val qty = PackQuantityParser.span(body)

        val hasDosing = freq != null || days != null
        val accepted = prefix != null || (strength != null && hasDosing) || (freq != null && FrequencyParser.findSpan(body)?.first?.let { it > 2 } == true && body.firstOrNull()?.isLetter() == true)
        if (!accepted) return null

        val stopAt = listOfNotNull(
            strength?.range?.first,
            FrequencyParser.findSpan(body)?.first,
            MealParser.findSpan(body)?.first,
            DurationParser.findSpan(body)?.first,
            DURATION_STOP.find(body)?.range?.first,
            qty?.first?.first,
            body.indexOf(" - ").takeIf { it > 0 },
            body.indexOf(" — ").takeIf { it > 0 },
        ).minOrNull() ?: body.length
        val name = body.substring(0, stopAt).trim(' ', '-', '–', ',', ':', '.', '(', ')')
        if (name.count { it.isLetter() } < 3) return null

        return ParsedMedicine(
            name = name,
            strength = strength?.let { it.groupValues[1].replace(" ", "") + " " + it.groupValues[2].lowercase().let(::canonUnit) },
            form = form,
            frequency = freq,
            meal = meal,
            durationDays = days,
            sourceLine = line,
            quantity = qty?.second,
        )
    }

    private fun canonUnit(u: String) = when (u) { "gm" -> "g"; "μg", "ug" -> "mcg"; "µg" -> "mcg"; else -> u }
}
