package com.kutumbam.app.parse

import java.time.LocalDate
import java.time.LocalTime

enum class DocumentType { PRESCRIPTION, LAB_REPORT, VACCINATION_CARD, UNKNOWN }

enum class FrequencyCode { OD, BD, TDS, QID, HS, SOS, CUSTOM }

enum class MealTiming { BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, EMPTY_STOMACH, UNSPECIFIED }

/** A doctor's shorthand resolved to concrete default reminder times (editable later). */
data class Frequency(
    val code: FrequencyCode,
    val times: List<LocalTime>,
    val raw: String,
)

data class ParsedMedicine(
    val name: String,
    val strength: String?,
    val form: String?,
    val frequency: Frequency?,
    val meal: MealTiming,
    val durationDays: Int?,
    val sourceLine: String,
)

data class ParsedLabValue(
    val testName: String,
    val value: Double,
    val unit: String?,
    val rangeLow: Double?,
    val rangeHigh: Double?,
    /** The range exactly as printed, e.g. "13.0 - 17.0" or "< 200". */
    val rangeText: String?,
    val sourceLine: String,
)

data class ParsedDocument(
    val type: DocumentType,
    val date: LocalDate?,
    val medicines: List<ParsedMedicine>,
    val labValues: List<ParsedLabValue>,
)
