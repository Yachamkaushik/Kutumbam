package com.kutumbam.app.data

import android.content.Context
import androidx.room.Room
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** One confirmed medicine as the user approved it on the confirm screen. */
data class ConfirmedMedicine(
    val name: String,
    val strength: String?,
    val form: String?,
    val frequencyCode: String,
    val times: List<java.time.LocalTime>,
    val meal: String,
    val durationDays: Int?,
)

data class ConfirmedLab(
    val testName: String,
    val value: Double,
    val unit: String?,
    val low: Double?,
    val high: Double?,
    val rangeText: String?,
)

class Repository(context: Context) {
    private val dao = Room.databaseBuilder(context, AppDb::class.java, "kutumbam.db").build().dao()

    fun members(): Flow<List<FamilyMember>> = dao.members()
    fun medicines(memberId: Long) = dao.medicines(memberId)
    fun doseLogs(date: LocalDate) = dao.doseLogs(date.toString())
    fun latestFlagged(memberId: Long) = dao.latestFlagged(memberId)

    suspend fun addMember(m: FamilyMember) = dao.insertMember(m)
    suspend fun setLanguage(id: Long, code: String) = dao.setLanguage(id, code)

    suspend fun setDose(medicineId: Long, date: LocalDate, time: String, taken: Boolean) {
        if (taken) dao.logDose(DoseLog(medicineId = medicineId, date = date.toString(), time = time, status = "taken", loggedAt = System.currentTimeMillis()))
        else dao.unlogDose(medicineId, date.toString(), time)
    }

    /** Nothing reaches the schedule except through here, and only after the user pressed Confirm. */
    suspend fun saveConfirmed(
        memberId: Long,
        type: String,
        imagePath: String?,
        rawText: String,
        documentDate: LocalDate?,
        medicines: List<ConfirmedMedicine>,
        labs: List<ConfirmedLab>,
    ) {
        val today = LocalDate.now()
        val docId = dao.insertDocument(DocumentEntity(memberId = memberId, type = type, sourceImagePath = imagePath, captureDate = today.toString(), ocrRawText = rawText))
        dao.insertMedicines(medicines.map {
            MedicineEntity(
                documentId = docId, memberId = memberId, name = it.name, strength = it.strength, form = it.form,
                frequencyCode = it.frequencyCode, timesCsv = it.times.joinToString(",") { t -> t.toString() },
                mealTiming = it.meal, durationDays = it.durationDays, startDate = today.toString(), confirmedByUser = true,
            )
        })
        dao.insertLabs(labs.map {
            val status = RangeCheck.status(it.value, it.low, it.high)
            LabValueEntity(
                documentId = docId, memberId = memberId, testName = it.testName, value = it.value, unit = it.unit,
                printedRangeLow = it.low, printedRangeHigh = it.high, rangeText = it.rangeText,
                flagged = status == RangeStatus.ABOVE || status == RangeStatus.BELOW, date = (documentDate ?: today).toString(),
            )
        })
    }
}
