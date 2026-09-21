package com.kutumbam.app.locker

/** Wording and safety checks around the model, kept as plain testable rules. */
object AnswerRules {

    fun refusal(person: String, self: Boolean = false) =
        "I can only tell you what's written in ${if (self) "your" else "$person's"} stored records. For advice about starting, stopping or changing a medicine, " +
            "or about what a result means, please ask the doctor or pharmacist."

    fun systemPrompt(person: String, language: String, today: String, self: Boolean = false) =
        "You answer questions about ${if (self) "$person's own (the user's)" else "$person's"} health records${if (self) ", speaking to them as \"you\"," else ""} using ONLY the numbered records provided. Today is $today. Rules: " +
            "1) If the answer is not in the records, say you can't find it in the stored records. " +
            "2) Never give medical advice, never explain what a result means medically, never suggest starting, stopping or changing a medicine or dose. " +
            "3) Quote every number, date and time exactly as written in the records. " +
            "4) The records do not describe how a medicine looks (colour or shape). " +
            "5) Answer in at most 3 short sentences in $language, keeping medicine names in English."

    fun userPrompt(retrieved: Retrieved, question: String) =
        "Records:\n" + retrieved.facts.mapIndexed { i, f -> "${i + 1}. ${f.text}" }.joinToString("\n") + "\n\nQuestion: $question"

    private val NUMBER = Regex("""\d+(?:\.\d+)?""")

    /**
     * A model answer is used only if every multi-digit number or decimal in it appears in the records or the question.
     * A number the records never contained means the model made something up, so the direct answer is shown instead.
     */
    fun isGrounded(answer: String, factsText: String, question: String, today: String): Boolean {
        val text = answer.trim()
        if (text.length !in 8..700) return false
        val allowed = NUMBER.findAll("$factsText $question $today").map { it.value }.toSet()
        return NUMBER.findAll(text).map { it.value }.filter { it.length >= 2 || it.contains('.') }.all { it in allowed }
    }
}
