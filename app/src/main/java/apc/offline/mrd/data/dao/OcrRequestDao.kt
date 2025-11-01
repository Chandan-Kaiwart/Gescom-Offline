package apc.offline.mrd.data.dao

import androidx.room.*
import apc.offline.mrd.data.entities.OcrRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: OcrRequestEntity)

    @Query("SELECT * FROM ocr_request WHERE isSynced = 0")
    fun getUnsyncedRequests(): Flow<List<OcrRequestEntity>>

    @Query("UPDATE ocr_request SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("SELECT * FROM ocr_request WHERE consumerNo = :consumerNo")
    fun getRequestsByConsumer(consumerNo: String): Flow<List<OcrRequestEntity>>

    @Query("SELECT * FROM ocr_request WHERE localReqId = :localReqId")
    fun getRequestsByReqId(localReqId: Int): Flow<List<OcrRequestEntity>>
}