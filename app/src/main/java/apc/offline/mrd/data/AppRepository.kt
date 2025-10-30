package apc.offline.mrd.data

import apc.offline.mrd.data.db.AppDatabase
import apc.offline.mrd.data.entities.ManualReadingEntity
import apc.offline.mrd.data.entities.MeterReadingEntity
import apc.offline.mrd.data.entities.OcrRequestEntity
import apc.offline.mrd.data.entities.ReaderTrackingEntity


class AppRepository(private val db: AppDatabase) {


    val ocrRequestDao = db.ocrRequestDao()
    val meterReadingDao = db.meterReadingDao()
    val manualReadingDao = db.manualReadingDao()

    // ✅ Add 'suspend' keyword to all these methods

    suspend fun insertOcrRequest(entity: OcrRequestEntity) = ocrRequestDao.insertRequest(entity)
    suspend fun insertMeterReading(entity: MeterReadingEntity) = meterReadingDao.insertReading(entity)
    suspend fun insertManualReading(entity: ManualReadingEntity) = manualReadingDao.insertManualReading(entity)
}