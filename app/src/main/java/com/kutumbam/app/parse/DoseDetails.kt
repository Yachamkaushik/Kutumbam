package com.kutumbam.app.parse

/** Meal timing and course length: the other two pieces of prescription shorthand. */
object MealParser {
    private val BEFORE = Regex(
        """\b(?:before|bef|prior\s+to)\.?\s*(?:food|meals?|breakfast|lunch|dinner|eating)\b|\ba\s*[./]\s*c\b\.?|\ba\.c\b""",
        RegexOption.IGNORE_CASE,
    )
    private val AFTER = Regex(
        """\b(?:after|aft|post)\.?\s*(?:food|meals?|breakfast|lunch|dinner|eating)\b|\bp\s*[./]\s*c\b\.?|\bp\.c\b""",
        RegexOption.IGNORE_CASE,
    )
    private val WITH = Regex("""\bwith\s+(?:food|meals?|milk)\b""", RegexOption.IGNORE_CASE)
    private val EMPTY = Regex("""\bempty\s+stomach\b""", RegexOption.IGNORE_CASE)

    fun parse(line: String): MealTiming = when {
        EMPTY.containsMatchIn(line) -> MealTiming.EMPTY_STOMACH
        BEFORE.containsMatchIn(line) -> MealTiming.BEFORE_FOOD
        AFTER.containsMatchIn(line) -> MealTiming.AFTER_FOOD
        WITH.containsMatchIn(line) -> MealTiming.WITH_FOOD
        else -> MealTiming.UNSPECIFIED
    }

    fun findSpan(line: String): IntRange? =
        listOf(EMPTY, BEFORE, AFTER, WITH).firstNotNullOfOrNull { it.find(line)?.range }
}

object DurationParser {
    private val DAYS_OVER_7 = Regex("""(?<![\d/])(\d{1,2})\s*/\s*7\b(?!\s*/)""")
    private val UNIT = Regex(
        """(?:(?:x|×|for|@)\s*)?\b(\d{1,3})\s*(days?|d|weeks?|wks?|months?|mths?|mos?)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(line: String): Int? {
        UNIT.find(line)?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2].lowercase()
            val hasMarker = m.value.trimStart().let { it.startsWith("x", true) || it.startsWith("×") || it.startsWith("for", true) || it.startsWith("@") }
            // A bare "1 d" / "5 mg-ish" token is too ambiguous; require a marker or a spelled-out unit.
            if (unit == "d" && !hasMarker) return@let
            return when {
                unit.startsWith("d") -> n
                unit.startsWith("w") -> n * 7
                else -> n * 30
            }
        }
        DAYS_OVER_7.find(line)?.let { return it.groupValues[1].toInt() }
        return null
    }

    fun findSpan(line: String): IntRange? = UNIT.find(line)?.range ?: DAYS_OVER_7.find(line)?.range
}
