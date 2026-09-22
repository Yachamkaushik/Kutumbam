package com.kutumbam.app.data

import androidx.room.ColumnInfo
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
    /** The person using the phone. At most one member has this set. */
    @ColumnInfo(defaultValue = "0") val isSelf: Boolean = false,
    val bloodGroup: String? = null,
    val allergies: String? = null,
    val conditions: String? = null,
    val emergencyName: String? = null,
    val emergencyPhone: String? = null,
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
    /** Tablets counted on [stockAsOf] (null when unknown), the basis of the refill estimate. */
    val stockCount: Int? = null,
    val stockAsOf: String? = null,
    /** Tablets per dose in the same order as [timesCsv]; null means one each. */
    val unitsCsv: String? = null,
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
    /** "printed" (range from the report itself), "standard" (bundled fallback), or "none". */
    val rangeSource: String = "printed",
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

/** One dose that has actually been given. Whether a dose is due, upcoming or overdue is computed from the date of birth. */
@Entity(tableName = "immunization", indices = [Index(value = ["memberId", "scheduleId"], unique = true)])
data class ImmunizationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val scheduleId: String,
    val administeredDate: String,
    val sourceDocumentId: Long? = null,
)

/** One weighing/measuring of a child. Any of the three values may be missing. */
@Entity(tableName = "measurement")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val date: String,
    val weightKg: Double?,
    val heightCm: Double?,
    val headCm: Double?,
)

/** A home reading: blood pressure (value = systolic, value2 = diastolic), blood sugar (context says fasting / after meal / random) or weight. */
@Entity(tableName = "vital")
data class Vital(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val date: String,
    val time: String,
    val kind: String,
    val value: Double,
    val value2: Double?,
    val context: String?,
)

/** A limit the person's own doctor gave (for example "BP below 130/80"). The app never invents one. */
@Entity(tableName = "vital_target", indices = [Index(value = ["memberId", "key"], unique = true)])
data class VitalTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val key: String,
    val low: Double?,
    val high: Double?,
)

/** Something the person noticed, in their own words, to mention at the next visit. */
@Entity(tableName = "health_note")
data class HealthNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val date: String,
    val text: String,
)

/** An upcoming clinic review or doctor follow-up visit. */
@Entity(tableName = "follow_up_visit")
data class FollowUpVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val doctorOrClinic: String,
    val date: String,
    val time: String? = null,
    val reason: String? = null,
    val completed: Boolean = false,
)

