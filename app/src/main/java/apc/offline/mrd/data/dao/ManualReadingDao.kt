package apc.offline.mrd.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import apc.offline.mrd.data.entities.ManualReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManualReading(reading: ManualReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<ManualReadingEntity>)

    @Query("SELECT * FROM manual_reading ORDER BY id DESC")
    fun getAllManualReadings(): Flow<List<ManualReadingEntity>>

    @Query("SELECT * FROM manual_reading WHERE ocrRequestId = :ocrRequestId")
    fun getManualReadingsByOcrRequest(ocrRequestId: Int): Flow<List<ManualReadingEntity>>

    @Query("SELECT * FROM manual_reading WHERE isSynced = 0")
    fun getUnsyncedReadings(): Flow<List<ManualReadingEntity>>

    @Query("UPDATE manual_reading SET isSynced = 1 WHERE id = :readingId")
    suspend fun markAsSynced(readingId: Int)

    @Delete
    suspend fun deleteManualReading(reading: ManualReadingEntity)

    @Query("DELETE FROM manual_reading WHERE id = :readingId")
    suspend fun deleteById(readingId: Int)
}
