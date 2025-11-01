package apc.offline.mrd.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import apc.offline.mrd.data.db.AppDatabase
import apc.offline.mrd.data.entities.MeterReadingEntity
import apc.offline.mrd.data.entities.ManualReadingEntity
import apc.offline.mrd.data.entities.OcrRequestEntity
import apc.offline.mrd.ocrlib.network.OcrRetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class UnifiedSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "UnifiedSyncWorker"
    private val database = AppDatabase.getDatabase(applicationContext)
    private val apiService = OcrRetrofitClient.getApiService()
    private val gson = Gson()

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🔄 Starting unified sync...")

            var totalSynced = 0
            var totalFailed = 0

            val (ocrSynced, ocrFailed) = syncOcrRequests()
            totalSynced += ocrSynced
            totalFailed += ocrFailed

            val (meterSynced, meterFailed) = syncMeterReadings()
            totalSynced += meterSynced
            totalFailed += meterFailed

            val (manualSynced, manualFailed) = syncManualReadings()
            totalSynced += manualSynced
            totalFailed += manualFailed

            Log.d(TAG, "✅ Sync complete: $totalSynced synced, $totalFailed failed")

            val outputData = workDataOf(
                "synced_count" to totalSynced,
                "failed_count" to totalFailed,
                "ocr_synced" to ocrSynced,
                "meter_synced" to meterSynced,
                "manual_synced" to manualSynced
            )

            Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun syncOcrRequests(): Pair<Int, Int> {
        var synced = 0
        var failed = 0

        try {
            val unsyncedRequests = database.ocrRequestDao()
                .getUnsyncedRequests()
                .first()

            Log.d(TAG, "📦 Found ${unsyncedRequests.size} unsynced OCR requests")

            unsyncedRequests.forEach { request ->
                try {
                    val success = uploadOcrRequest(request)

                    if (success) {
                        database.ocrRequestDao().markAsSynced(request.id)
                        synced++
                        Log.d(TAG, "✅ OCR Request localReqId=${request.localReqId} synced")
                    } else {
                        failed++
                        Log.e(TAG, "❌ OCR Request localReqId=${request.localReqId} failed")
                    }
                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "❌ Error syncing OCR request: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching OCR requests: ${e.message}")
        }

        return Pair(synced, failed)
    }

    private suspend fun uploadOcrRequest(request: OcrRequestEntity): Boolean {
        return try {
            val reqReadingValuesList = gson.fromJson(
                request.reqReadingValues,
                Array<String>::class.java
            ).toList()

            val payload = mapOf(
                "id" to request.localReqId,
                "agencyId" to request.agencyId,
                "boardCode" to request.boardCode,
                "consumerNo" to request.consumerNo,
                "meterReaderId" to request.meterReaderId,
                "meterReaderMobileNo" to request.meterReaderMobile,
                "meterReaderName" to request.meterReaderName,
                "subDivisionCode" to request.subDivisionCode,
                "subDivisionName" to request.subDivisionName,
                "meterMake" to request.meterMake,
                "reqReadingValues" to reqReadingValuesList
            )

            Log.d(TAG, "📤 Uploading OCR Request: $payload")

            val response = apiService.createOcrRequest(payload).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "✅ OCR Request uploaded (localReqId: ${request.localReqId})")
                true
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Upload failed: ${response.code()} - $errorBody")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed: ${e.message}", e)
            false
        }
    }

    private suspend fun syncMeterReadings(): Pair<Int, Int> {
        var synced = 0
        var failed = 0

        try {
            val unsyncedReadings = database.meterReadingDao()
                .getUnsyncedReadings()
                .first()

            Log.d(TAG, "📊 Found ${unsyncedReadings.size} unsynced meter readings")

            unsyncedReadings.forEach { reading ->
                try {
                    val success = uploadMeterReadingWithImage(reading)

                    if (success) {
                        database.meterReadingDao().markAsSynced(reading.id)
                        synced++
                        Log.d(TAG, "✅ Meter Reading ID ${reading.id} synced")
                    } else {
                        failed++
                        Log.e(TAG, "❌ Meter Reading ID ${reading.id} failed")
                    }
                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "❌ Error syncing meter reading: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching meter readings: ${e.message}")
        }

        return Pair(synced, failed)
    }

    private suspend fun uploadMeterReadingWithImage(reading: MeterReadingEntity): Boolean {
        return try {
            val imageFile = File(reading.image_path)

            if (!imageFile.exists()) {
                Log.e(TAG, "❌ Image file not found: ${reading.image_path}")
                return false
            }

            val requestFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)

            // ✅ CRITICAL FIX: Create RequestBody for EACH field
            val accept = "*/*".toRequestBody("text/plain".toMediaTypeOrNull())
            val ocrRequestId = reading.ocrReqId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val readingDateTime = reading.reading_data_time.toRequestBody("text/plain".toMediaTypeOrNull())
            val siteLocation = reading.site_location.toRequestBody("text/plain".toMediaTypeOrNull())
            val caNo = reading.ca_no.toRequestBody("text/plain".toMediaTypeOrNull())
            val imagePath = "uploaded".toRequestBody("text/plain".toMediaTypeOrNull()) // ✅ Don't send local path
            val meterNo = reading.meter_no.toRequestBody("text/plain".toMediaTypeOrNull())
            val meterReading = reading.meter_reading.toRequestBody("text/plain".toMediaTypeOrNull())
            val latLong = reading.lat_long.toRequestBody("text/plain".toMediaTypeOrNull())
            val address = reading.address.toRequestBody("text/plain".toMediaTypeOrNull())
            val unit = reading.unit.toRequestBody("text/plain".toMediaTypeOrNull())
            val meterReader = reading.meter_reader.toRequestBody("text/plain".toMediaTypeOrNull())
            val consumer = reading.consumer.toRequestBody("text/plain".toMediaTypeOrNull())
            val mru = reading.mru.toRequestBody("text/plain".toMediaTypeOrNull())
            val exception = reading.exception.toRequestBody("text/plain".toMediaTypeOrNull())
            val meterModel = reading.meter_model.toRequestBody("text/plain".toMediaTypeOrNull())
            val locationType = reading.location_type.toRequestBody("text/plain".toMediaTypeOrNull())
            val location = reading.location.toRequestBody("text/plain".toMediaTypeOrNull())
            val agency = reading.agency.toRequestBody("text/plain".toMediaTypeOrNull())

            Log.d(TAG, "📤 Uploading Meter Reading (ocrReqId: ${reading.ocrReqId})")
            Log.d("DEBUG_SYNC", "🔹 Reading Data Time: ${reading.reading_data_time}")
            Log.d("DEBUG_SYNC", "🔹 Meter Reading: ${reading.meter_reading}")
            Log.d("DEBUG_SYNC", "🔹 CA No: ${reading.ca_no}")
            Log.d("DEBUG_SYNC", "🔹 Unit: ${reading.unit}")

            val response = apiService.uploadMeterImage(
                token = OcrRetrofitClient.AUTH_TOKEN,
                file = filePart,
                ocrRequestId = ocrRequestId,
                readingDateTime = readingDateTime,
                siteLocation = siteLocation,
                caNo = caNo,
                imagePath = imagePath,
                meterNo = meterNo,
                meterReading = meterReading,
                latLong = latLong,
                address = address,
                unit = unit,
                meterReader = meterReader,
                consumer = consumer,
                mru = mru,
                exception = exception,
                meterModel = meterModel,
                locationType = locationType,
                location = location,
                agency = agency,
                accept = accept
            ).execute()

            if (response.isSuccessful) {
                val responseBody = response.body()
                Log.d(TAG, "✅ Meter Reading uploaded: ${responseBody.toString()}")
                true
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Upload failed: ${response.code()} - $errorBody")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed: ${e.message}", e)
            false
        }
    }

    private suspend fun syncManualReadings(): Pair<Int, Int> {
        var synced = 0
        var failed = 0

        try {
            val unsyncedReadings = database.manualReadingDao()
                .getUnsyncedReadings()
                .first()

            Log.d(TAG, "📝 Found ${unsyncedReadings.size} unsynced manual readings")

            unsyncedReadings.forEach { reading ->
                try {
                    val success = uploadManualReading(reading)

                    if (success) {
                        database.manualReadingDao().markAsSynced(reading.id)
                        synced++
                        Log.d(TAG, "✅ Manual Reading ID ${reading.id} synced")
                    } else {
                        failed++
                        Log.e(TAG, "❌ Manual Reading ID ${reading.id} failed")
                    }
                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "❌ Error syncing manual reading: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching manual readings: ${e.message}")
        }

        return Pair(synced, failed)
    }

    private suspend fun uploadManualReading(reading: ManualReadingEntity): Boolean {
        return try {
            val payload = mapOf(
                "reading" to reading.reading,
                "image_path" to reading.image_path,
                "unit" to reading.unit,
                "ocrRequestId" to reading.ocrRequestId
            )

            Log.d(TAG, "📤 Uploading Manual Reading: $payload")

            val response = apiService.createManualReading(payload).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "✅ Manual Reading uploaded successfully")
                true
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Upload failed: ${response.code()} - $errorBody")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed: ${e.message}", e)
            false
        }
    }
}