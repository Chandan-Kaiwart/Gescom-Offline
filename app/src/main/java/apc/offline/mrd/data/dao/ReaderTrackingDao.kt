package apc.offline.mrd.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import apc.offline.mrd.data.entities.ReaderTrackingEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface ReaderTrackingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracking(readerTracking: ReaderTrackingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trackings: List<ReaderTrackingEntity>)

    @Query("SELECT * FROM reader_tracking ORDER BY created_at DESC")
    fun getAllTrackings(): Flow<List<ReaderTrackingEntity>>

    @Query("SELECT * FROM reader_tracking WHERE id = :id LIMIT 1")
    suspend fun getTrackingById(id: Int): ReaderTrackingEntity?

    @Delete
    suspend fun deleteTracking(readerTracking: ReaderTrackingEntity)

    @Query("DELETE FROM reader_tracking")
    suspend fun clearAll()
}