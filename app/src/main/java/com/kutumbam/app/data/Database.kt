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
}

@Database(
    entities = [FamilyMember::class, DocumentEntity::class, MedicineEntity::class, LabValueEntity::class, DoseLog::class, ImmunizationRecord::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): KutumbamDao
}
