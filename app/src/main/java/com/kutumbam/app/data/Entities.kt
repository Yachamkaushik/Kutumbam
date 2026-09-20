package com.kutumbam.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dates are stored as ISO strings (yyyy-MM-dd) and times as HH:mm so Room needs no converters. */
@Entity(tableName = "member")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String,
    val dateOfBirth: String?,
    val selfOperatesPhone: Boolean,
    val preferredLanguage: String = "en",
)

@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val type: String,
    val sourceImagePath: String?,
    val captureDate: String,
    val ocrRawText: String,
)

@Entity(tableName = "medicine")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val memberId: Long,
    val name: String,
    val strength: String?,
    val form: String?,
    val frequencyCode: String,
    val timesCsv: String,
    val mealTiming: String,
    val durationDays: Int?,
    val startDate: String,
    val confirmedByUser: Boolean,
)

@Entity(tableName = "lab_value")
data class LabValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val memberId: Long,
    val testName: String,
    val value: Double,
    val unit: String?,
    val printedRangeLow: Double?,
    val printedRangeHigh: Double?,
    val rangeText: String?,
    val flagged: Boolean,
    val date: String,
)

@Entity(tableName = "dose_log", indices = [Index(value = ["medicineId", "date", "time"], unique = true)])
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val date: String,
    val time: String,
    val status: String,
    val loggedAt: Long,
)
