package com.kutumbam.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.TestNames
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import com.kutumbam.app.parse.DoseUnits

/** One confirmed medicine as the user approved it on the confirm screen. */
data class ConfirmedMedicine(
    val name: String,
    val strength: String?,
    val form: String?,
    val frequencyCode: String,
    val times: List<java.time.LocalTime>,
    val meal: String,
    val durationDays: Int?,
    val quantity: Int? = null,
    val units: List<Double> = emptyList(),
)

data class ConfirmedLab(
    val testName: String,
    val value: Double,
    val unit: String?,
    val low: Double?,
    val high: Double?,
    val rangeText: String?,
)

data class ConfirmedVaccine(val scheduleId: String, val date: LocalDate)

class Repository(context: Context) {
    private val dao = Room.databaseBuilder(context, AppDb::class.java, "kutumbam.db")
        // Pre-release: the schema is still moving, so a version bump rebuilds the local database.
        .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
        .fallbackToDestructiveMigration(true).build().dao()

    fun members(): Flow<List<FamilyMember>> = dao.members()
    fun medicines(memberId: Long) = dao.medicines(memberId)
    fun doseLogs(date: LocalDate) = dao.doseLogs(date.toString())
    fun latestFlagged(memberId: Long) = dao.latestFlagged(memberId)
    suspend fun allMembers() = dao.allMembers()
    suspend fun medicinesNow(memberId: Long) = dao.medicinesNow(memberId)
    suspend fun labsNow(memberId: Long) = dao.labsNow(memberId)
    suspend fun documentsNow(memberId: Long) = dao.documentsNow(memberId)
    fun immunizations(memberId: Long) = dao.immunizations(memberId)
    suspend fun immunizationsNow(memberId: Long) = dao.immunizationsNow(memberId)

    suspend fun markVaccine(memberId: Long, scheduleId: String, date: LocalDate) =
        dao.upsertImmunizations(listOf(ImmunizationRecord(memberId = memberId, scheduleId = scheduleId, administeredDate = date.toString())))

    suspend fun unmarkVaccine(memberId: Long, scheduleId: String) = dao.deleteImmunization(memberId, scheduleId)

    fun labsForDocument(docId: Long) = dao.labsForDocument(docId)
    fun labHistory(memberId: Long) = dao.labHistory(memberId)
    fun reportSummaries(memberId: Long) = dao.reportSummaries(memberId)

    suspend fun allMedicines() = dao.allMedicines()
    suspend fun medicine(id: Long) = dao.medicine(id)
    suspend fun member(id: Long) = dao.member(id)
    suspend fun isDoseTaken(medicineId: Long, date: LocalDate, time: String) = dao.takenCount(medicineId, date.toString(), time) > 0

    suspend fun addMember(m: FamilyMember) = dao.insertMember(m)
    /** The family counted [count] tablets today; the refill estimate restarts from here. */
    suspend fun setStock(medicineId: Long, count: Int) = dao.setStock(medicineId, count, LocalDateTime.now().withNano(0).toString())
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
        vaccinations: List<ConfirmedVaccine> = emptyList(),
    ): Long {
        val today = LocalDate.now()
        val docId = dao.insertDocument(DocumentEntity(memberId = memberId, type = type, sourceImagePath = imagePath, captureDate = today.toString(), ocrRawText = rawText))
        val saved = medicines.map {
            MedicineEntity(
                documentId = docId, memberId = memberId, name = it.name, strength = it.strength, form = it.form,
                frequencyCode = it.frequencyCode, timesCsv = it.times.joinToString(",") { t -> t.toString() },
                mealTiming = it.meal, durationDays = it.durationDays, startDate = today.toString(), confirmedByUser = true,
                stockCount = it.quantity, stockAsOf = if (it.quantity != null) LocalDateTime.now().withNano(0).toString() else null,
                unitsCsv = it.units.takeIf { u -> u.size == it.times.size && !DoseUnits.allOne(u) }?.let(DoseUnits::toCsv),
            )
        }
        dao.insertMedicines(saved)
        dao.insertLabs(labs.map {
            // The report's own printed range always wins; the small bundled table is used only when none was printed.
            val printed = it.low != null || it.high != null
            val fallback = if (printed) null else TestNames.fallback(it.testName, it.unit)
            val low = if (printed) it.low else fallback?.low
            val high = if (printed) it.high else fallback?.high
            val status = RangeCheck.status(it.value, low, high)
            LabValueEntity(
                documentId = docId, memberId = memberId, testName = it.testName, value = it.value, unit = it.unit,
                printedRangeLow = low, printedRangeHigh = high, rangeText = it.rangeText,
                flagged = status == RangeStatus.ABOVE || status == RangeStatus.BELOW, date = (documentDate ?: today).toString(),
                rangeSource = when { printed -> "printed"; fallback != null -> "standard"; else -> "none" },
            )
        })
        dao.upsertImmunizations(vaccinations.map { ImmunizationRecord(memberId = memberId, scheduleId = it.scheduleId, administeredDate = it.date.toString(), sourceDocumentId = docId) })
        return docId
    }

    private companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicine ADD COLUMN unitsCsv TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicine ADD COLUMN stockCount INTEGER")
                db.execSQL("ALTER TABLE medicine ADD COLUMN stockAsOf TEXT")
            }
        }
    }
}
