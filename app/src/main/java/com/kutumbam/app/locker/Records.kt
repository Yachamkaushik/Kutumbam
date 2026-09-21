package com.kutumbam.app.locker

import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.MilestoneState
import java.time.LocalDate
import java.time.LocalTime

enum class SourceKind { PRESCRIPTION, LAB_REPORT, VACCINATION }

/** Where an answer came from, so the person can go and check it. */
data class Source(val kind: SourceKind, val label: String, val documentId: Long? = null)

/** The refill estimate for a medicine that has a tablet count; already worded by the predictor. */
data class SupplyInfo(val phrase: String, val left: String?, val countedOn: LocalDate, val needsRefill: Boolean)

data class MedRecord(
    val name: String,
    val strength: String?,
    val times: List<LocalTime>,
    val sos: Boolean,
    val meal: MealTiming,
    val durationDays: Int?,
    val startDate: LocalDate,
    val source: Source,
    val supply: SupplyInfo? = null,
    /** Tablets per dose, same order as [times]; empty means one each. */
    val units: List<Double> = emptyList(),
)

data class LabRecord(
    val testName: String,
    val key: String,
    val value: Double,
    val unit: String?,
    val low: Double?,
    val high: Double?,
    val standardReference: Boolean,
    val date: LocalDate,
    val source: Source,
)

/** Everything stored for one family member. Retrieval works only over this. */
data class LockerData(
    val person: String,
    val today: LocalDate,
    val medicines: List<MedRecord>,
    val labs: List<LabRecord>,
    val immunization: List<MilestoneState>?,
)

/** One retrievable statement. [text] is complete (for the model and grounding); [short] is what the person reads without the model. */
data class Fact(val text: String, val short: String, val source: Source)

enum class Topic { MEDICINE, LAB, VACCINE }

data class Retrieved(
    val facts: List<Fact>,
    val topics: Set<Topic>,
    /** A complete plain-language answer built directly from the records, used when the model is absent or fails a check. */
    val directAnswer: String,
    val advice: Boolean,
) {
    val sources: List<Source> get() = facts.map { it.source }.distinct()
    val factsText: String get() = facts.joinToString("\n") { it.text }
}
