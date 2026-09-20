package com.kutumbam.app.parse

/**
 * Reads lab rows laid out as test - value - unit - printed reference range, either on one OCR line
 * or as the space-separated columns of a reconstructed table row.
 */
object LabReportParser {

    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""
    private const val UNIT =
        """(?:%|(?:[a-zµμ]{1,8}\^?\d?/[a-zµμ0-9^*.]{1,10})|(?:10\^?\d\s*/\s*[a-zµμ]{1,6})|fl|pg|g|mg|gm|iu|u|ratio|mmhg|seconds?|sec|mm)"""
    private const val RANGE_SEP = """\s*(?:-|–|—|to)\s*"""

    private val ROW = Regex(
        """^(?<name>[A-Za-z][A-Za-z0-9 ,()/%.'\-]*?)[\s:]+(?<lt>[<>]=?\s*)?(?<value>$NUM)(?![\w.])(?<flag>\s*(?:\(?\b(?:H|L|HH|LL|HIGH|LOW|High|Low)\b\)?|\*))?(?<unit>\s*$UNIT(?![a-z]))?(?<rest>(?!\s*(?!desirable|normal|ref|range|optimal|adult|males?|females?|men|women|child|m\b|f\b|upto|up\s+to|less|below|above|more|greater)[a-z]).*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val RANGE_BETWEEN = Regex("""(?<![\d.])($NUM)$RANGE_SEP($NUM)(?![\d.])""")
    private val RANGE_UPPER = Regex("""(?:<|≤|up\s*to|upto|less\s+than|below)\s*=?\s*($NUM)""", RegexOption.IGNORE_CASE)
    private val RANGE_LOWER = Regex("""(?:>|≥|more\s+than|above|greater\s+than)\s*=?\s*($NUM)""", RegexOption.IGNORE_CASE)

    private val SKIP_NAME = Regex(
        """^(?:age|sex|gender|date|dob|page|phone|ph|mobile|ref|referred|sample|specimen|reg|lab|patient|name|collected|received|reported|report|barcode|sid|uhid|pid|invoice|bill|doctor|dr|address|time|units?|result|test|investigation|normal|method|remarks?|note|interpretation|tel|fax|www|email|id)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): List<ParsedLabValue> = text.lines().mapNotNull { parseLine(it) }

    fun parseLine(rawLine: String): ParsedLabValue? {
        val line = rawLine.trim().replace(Regex("""\s{2,}"""), "  ")
        if (line.length < 5) return null
        val m = ROW.matchEntire(line) ?: return null

        val name = m.groups["name"]!!.value.trim(' ', ':', '-', '.', ',')
        if (name.count { it.isLetter() } < 2 || SKIP_NAME.containsMatchIn(name)) return null

        val unit = m.groups["unit"]?.value?.trim()?.takeIf { it.isNotEmpty() }
        val rest = m.groups["rest"]?.value.orEmpty()

        var low: Double? = null
        var high: Double? = null
        var rangeText: String? = null
        RANGE_BETWEEN.find(rest)?.let {
            low = it.groupValues[1].toNum(); high = it.groupValues[2].toNum(); rangeText = it.value.trim()
        } ?: RANGE_UPPER.find(rest)?.let {
            high = it.groupValues[1].toNum(); rangeText = it.value.trim()
        } ?: RANGE_LOWER.find(rest)?.let {
            low = it.groupValues[1].toNum(); rangeText = it.value.trim()
        }

        // A bare number with neither a unit nor a printed range is far more likely an address or ID than a result.
        if (unit == null && rangeText == null) return null
        // Reject rows where the "range" is really the same number repeated (e.g. page 1-1).
        if (low != null && high != null && low!! >= high!!) return null

        return ParsedLabValue(
            testName = name,
            value = m.groups["value"]!!.value.toNum(),
            unit = unit,
            rangeLow = low,
            rangeHigh = high,
            rangeText = rangeText,
            sourceLine = rawLine.trim(),
        )
    }

    private fun String.toNum() = replace(",", "").toDouble()
}
