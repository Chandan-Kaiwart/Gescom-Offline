package apc.offline.mrd.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import apc.offline.mrd.data.entities.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: MeterReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<MeterReadingEntity>)

    @Query("SELECT * FROM meter_reading ORDER BY id DESC")
    fun getAllReadings(): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_reading WHERE ocrRequestId = :ocrRequestId")
    fun getReadingsByOcrRequest(ocrRequestId: String): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_reading WHERE isSynced = 0")
    fun getUnsyncedReadings(): Flow<List<MeterReadingEntity>>

    @Query("UPDATE meter_reading SET isSynced = 1 WHERE id = :readingId")
    suspend fun markAsSynced(readingId: Int)

    @Delete
    suspend fun deleteReading(reading: MeterReadingEntity)

    @Query("DELETE FROM meter_reading WHERE id = :readingId")
    suspend fun deleteById(readingId: Int)
}
