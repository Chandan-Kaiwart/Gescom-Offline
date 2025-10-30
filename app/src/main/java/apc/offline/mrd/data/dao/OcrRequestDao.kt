package apc.offline.mrd.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import apc.offline.mrd.data.entities.OcrRequestEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface OcrRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: OcrRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(requests: List<OcrRequestEntity>)

    @Query("SELECT * FROM ocr_request ORDER BY readingDate DESC")
    fun getAllRequests(): Flow<List<OcrRequestEntity>>

    @Query("SELECT * FROM ocr_request WHERE id = :id LIMIT 1")
    suspend fun getRequestById(id: Int): OcrRequestEntity

    @Query("SELECT * FROM ocr_request WHERE consumerNo = :consumerNo")
    fun getRequestsByConsumer(consumerNo: String): Flow<List<OcrRequestEntity>>

    @Delete
    suspend fun deleteRequest(request: OcrRequestEntity)

    @Query("DELETE FROM ocr_request")
    suspend fun clearAll()

    @Query("SELECT * FROM ocr_request WHERE isSynced = 0 ORDER BY readingDate DESC")
    fun getUnsyncedRequests(): Flow<List<OcrRequestEntity>>

    @Query("UPDATE ocr_request SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)
}