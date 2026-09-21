package com.kutumbam.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface KutumbamDao {
    @Insert suspend fun insertMember(m: FamilyMember): Long
    @Query("SELECT * FROM member ORDER BY id") fun members(): Flow<List<FamilyMember>>

    @Query("UPDATE member SET preferredLanguage = :lang WHERE id = :id") suspend fun setLanguage(id: Long, lang: String)

    @Insert suspend fun insertDocument(d: DocumentEntity): Long
    @Insert suspend fun insertMedicines(m: List<MedicineEntity>)
    @Insert suspend fun insertLabs(l: List<LabValueEntity>)

    @Query("SELECT * FROM medicine WHERE memberId = :memberId AND confirmedByUser = 1")
    fun medicines(memberId: Long): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM dose_log WHERE date = :date") fun doseLogs(date: String): Flow<List<DoseLog>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun logDose(l: DoseLog)
    @Query("DELETE FROM dose_log WHERE medicineId = :medicineId AND date = :date AND time = :time")
    suspend fun unlogDose(medicineId: Long, date: String, time: String)

    @Query("SELECT * FROM lab_value WHERE memberId = :memberId AND flagged = 1 ORDER BY date DESC, id DESC LIMIT 1")
    fun latestFlagged(memberId: Long): Flow<LabValueEntity?>
}

@Database(
    entities = [FamilyMember::class, DocumentEntity::class, MedicineEntity::class, LabValueEntity::class, DoseLog::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): KutumbamDao
}
