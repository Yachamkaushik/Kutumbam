package com.kutumbam.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class ReportSummary(val documentId: Long, val date: String, val total: Int, val flagged: Int)

@Dao
interface KutumbamDao {
    @Insert suspend fun insertMember(m: FamilyMember): Long
    @Query("SELECT * FROM member ORDER BY id") fun members(): Flow<List<FamilyMember>>

    @Query("UPDATE member SET preferredLanguage = :lang WHERE id = :id") suspend fun setLanguage(id: Long, lang: String)

    @Query("SELECT * FROM member ORDER BY id") suspend fun allMembers(): List<FamilyMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertImmunizations(r: List<ImmunizationRecord>)
    @Query("SELECT * FROM immunization WHERE memberId = :memberId") fun immunizations(memberId: Long): Flow<List<ImmunizationRecord>>
    @Query("SELECT * FROM immunization WHERE memberId = :memberId") suspend fun immunizationsNow(memberId: Long): List<ImmunizationRecord>
    @Query("DELETE FROM immunization WHERE memberId = :memberId AND scheduleId = :scheduleId") suspend fun deleteImmunization(memberId: Long, scheduleId: String)

    @Query("SELECT * FROM medicine WHERE memberId = :memberId AND confirmedByUser = 1") suspend fun medicinesNow(memberId: Long): List<MedicineEntity>
    @Query("SELECT * FROM lab_value WHERE memberId = :memberId ORDER BY date, id") suspend fun labsNow(memberId: Long): List<LabValueEntity>
    @Query("SELECT * FROM document WHERE memberId = :memberId") suspend fun documentsNow(memberId: Long): List<DocumentEntity>

    @Query("UPDATE medicine SET stockCount = :count, stockAsOf = :asOf WHERE id = :id") suspend fun setStock(id: Long, count: Int, asOf: String)

    @Insert suspend fun insertMeasurement(m: Measurement)
    @Query("DELETE FROM measurement WHERE id = :id") suspend fun deleteMeasurement(id: Long)
    @Query("SELECT * FROM measurement WHERE memberId = :memberId ORDER BY date, id") fun measurements(memberId: Long): Flow<List<Measurement>>
    @Query("SELECT * FROM measurement WHERE memberId = :memberId ORDER BY date, id") suspend fun measurementsNow(memberId: Long): List<Measurement>

    @androidx.room.Update suspend fun updateMember(m: FamilyMember)

    @Insert suspend fun insertVital(v: Vital)
    @Query("DELETE FROM vital WHERE id = :id") suspend fun deleteVital(id: Long)
    @Query("SELECT * FROM vital WHERE memberId = :memberId ORDER BY date DESC, time DESC, id DESC") fun vitals(memberId: Long): Flow<List<Vital>>
    @Query("SELECT * FROM vital WHERE memberId = :memberId ORDER BY date DESC, time DESC, id DESC") suspend fun vitalsNow(memberId: Long): List<Vital>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTarget(t: VitalTarget)
    @Query("DELETE FROM vital_target WHERE memberId = :memberId AND `key` = :key") suspend fun deleteTarget(memberId: Long, key: String)
    @Query("SELECT * FROM vital_target WHERE memberId = :memberId") fun targets(memberId: Long): Flow<List<VitalTarget>>
    @Query("SELECT * FROM vital_target WHERE memberId = :memberId") suspend fun targetsNow(memberId: Long): List<VitalTarget>

    @Insert suspend fun insertNote(n: HealthNote)
    @Query("DELETE FROM health_note WHERE id = :id") suspend fun deleteNote(id: Long)
    @Query("SELECT * FROM health_note WHERE memberId = :memberId ORDER BY date DESC, id DESC") fun notes(memberId: Long): Flow<List<HealthNote>>
    @Query("SELECT * FROM health_note WHERE memberId = :memberId ORDER BY date DESC, id DESC") suspend fun notesNow(memberId: Long): List<HealthNote>

    @Query("SELECT * FROM dose_log WHERE date >= :fromDate AND status = 'taken'") suspend fun takenSince(fromDate: String): List<DoseLog>

    @Insert suspend fun insertDocument(d: DocumentEntity): Long
    @Insert suspend fun insertMedicines(m: List<MedicineEntity>)
    @Insert suspend fun insertLabs(l: List<LabValueEntity>)

    @Query("SELECT * FROM medicine WHERE memberId = :memberId AND confirmedByUser = 1")
    fun medicines(memberId: Long): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicine WHERE confirmedByUser = 1") suspend fun allMedicines(): List<MedicineEntity>
    @Query("SELECT * FROM medicine WHERE id = :id") suspend fun medicine(id: Long): MedicineEntity?
    @Query("SELECT * FROM member WHERE id = :id") suspend fun member(id: Long): FamilyMember?
    @Query("SELECT COUNT(*) FROM dose_log WHERE medicineId = :medicineId AND date = :date AND time = :time AND status = 'taken'")
    suspend fun takenCount(medicineId: Long, date: String, time: String): Int

    @Query("SELECT * FROM dose_log WHERE date = :date") fun doseLogs(date: String): Flow<List<DoseLog>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun logDose(l: DoseLog)
    @Query("DELETE FROM dose_log WHERE medicineId = :medicineId AND date = :date AND time = :time")
    suspend fun unlogDose(medicineId: Long, date: String, time: String)

    @Query("SELECT * FROM lab_value WHERE documentId = :docId ORDER BY id") fun labsForDocument(docId: Long): Flow<List<LabValueEntity>>
    @Query("SELECT * FROM lab_value WHERE memberId = :memberId ORDER BY date, id") fun labHistory(memberId: Long): Flow<List<LabValueEntity>>
    @Query("SELECT documentId, MAX(date) AS date, COUNT(*) AS total, SUM(flagged) AS flagged FROM lab_value WHERE memberId = :memberId GROUP BY documentId ORDER BY date DESC, documentId DESC")
    fun reportSummaries(memberId: Long): Flow<List<ReportSummary>>

    @Query("SELECT * FROM lab_value WHERE memberId = :memberId AND flagged = 1 ORDER BY date DESC, id DESC LIMIT 1")
    fun latestFlagged(memberId: Long): Flow<LabValueEntity?>

    @Insert suspend fun insertVisit(v: FollowUpVisit): Long
    @Query("DELETE FROM follow_up_visit WHERE id = :id") suspend fun deleteVisit(id: Long)
    @Query("SELECT * FROM follow_up_visit ORDER BY date, time") fun allVisits(): Flow<List<FollowUpVisit>>
    @Query("SELECT * FROM follow_up_visit WHERE memberId = :memberId ORDER BY date, time") fun visitsForMember(memberId: Long): Flow<List<FollowUpVisit>>
    @Query("SELECT * FROM follow_up_visit ORDER BY date, time") suspend fun allVisitsNow(): List<FollowUpVisit>
}

@Database(
    entities = [FamilyMember::class, DocumentEntity::class, MedicineEntity::class, LabValueEntity::class, DoseLog::class, ImmunizationRecord::class, Measurement::class, Vital::class, VitalTarget::class, HealthNote::class, FollowUpVisit::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): KutumbamDao
}
