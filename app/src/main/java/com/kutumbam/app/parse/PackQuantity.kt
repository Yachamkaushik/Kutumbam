package com.kutumbam.app.parse

/** The number of tablets in the pack, as printed on the strip or noted on the prescription ("strip of 10", "Qty: 30", "15 tabs"). */
object PackQuantityParser {
    private val LABELLED = Regex(
        """(?:\b(?:strip|pack|packet|bottle|box|blister)\s+of|\b(?:qty|quantity|disp(?:ense|ensed)?|no\.?\s+of\s+(?:tab(?:let)?s?|caps?(?:ules?)?))\s*[:.=\-]?|#)\s*(\d{1,3})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val COUNTED = Regex("""(?<![\d/.\-x×])\b(\d{1,3})\s*(?:tab(?:let)?s?|caps?(?:ules?)?)\b""", RegexOption.IGNORE_CASE)

    /** A bare "2 tablets" is a dose, not a pack, so unlabelled counts need to be at least this large. */
    private const val MIN_UNLABELLED = 5

    fun parse(line: String): Int? = span(line)?.second

    /** Where the quantity phrase starts, and its value. */
    fun span(line: String): Pair<IntRange, Int>? {
        LABELLED.find(line)?.let { m -> m.groupValues[1].toInt().takeIf { it > 0 }?.let { return m.range to it } }
        COUNTED.find(line)?.let { m -> m.groupValues[1].toInt().takeIf { it >= MIN_UNLABELLED }?.let { return m.range to it } }
        return null
    }
}
