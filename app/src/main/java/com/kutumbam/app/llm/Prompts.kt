package com.kutumbam.app.llm

/**
 * Every prompt restates already-structured, already-confirmed data. The model never extracts, diagnoses,
 * or advises on doses; the safety framing lives here so it is applied the same way everywhere.
 */
object Prompts {

    const val SAFETY = "You only restate the information given. Never diagnose, never suggest starting, " +
        "stopping or changing any medicine or dose, and never add facts that are not in the data. " +
        "If something is unclear, say to ask the doctor or pharmacist."

    fun explainMedicine(language: String, medicineLines: String): Pair<String, String> {
        val system = "You explain a family member's medicine schedule in simple, warm $language for an elderly " +
            "person. Keep medicine names in English exactly as given. Use short sentences, at most 4. $SAFETY"
        val user = "Medicines from the confirmed prescription:\n$medicineLines\n\n" +
            "Explain what to take, when, and whether before or after food."
        return system to user
    }

    fun trendSummary(facts: String): Pair<String, String> {
        val system = "You write a short, plain-language note for a family member about how one lab value changed across " +
            "reports. Use only the facts given and quote the numbers exactly as given. At most 2 short sentences. $SAFETY"
        val user = "Facts:\n$facts\nWrite the note."
        return system to user
    }
}
