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
                    // ✅ STEP 1: Create OCR Request on server and get actual ID
                    val actualServerId = createOcrRequestOnServer(request)

                    if (actualServerId != null) {
                        Log.d(TAG, "✅ Got server ID: $actualServerId for local ID: ${request.localReqId}")

                        // ✅ STEP 2: Update ALL related records with actual server ID
                        val updateSuccess = updateLocalIdToServerId(request.localReqId, actualServerId)

                        if (updateSuccess) {
                            // ✅ STEP 3: Mark OCR request as synced
                            database.ocrRequestDao().markAsSynced(request.id)
                            synced++
                            Log.d(TAG, "✅ OCR Request synced: Local ${request.localReqId} → Server $actualServerId")
                        } else {
                            failed++
                            Log.e(TAG, "❌ Failed to update IDs for request ${request.localReqId}")
                        }
                    } else {
                        failed++
                        Log.e(TAG, "❌ Failed to get server ID for request ${request.localReqId}")
                    }

                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "❌ Error syncing OCR request: ${e.message}", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching OCR requests: ${e.message}")
        }

        return Pair(synced, failed)
    }
    // ✅ FINAL FIX: Use lowercase camelCase keys (not UPPERCASE)
    private suspend fun createOcrRequestOnServer(request: OcrRequestEntity): Int? {
        return try {
            val reqReadingValuesList = gson.fromJson(
                request.reqReadingValues,
                Array<String>::class.java
            ).toList()

            // ✅ CRITICAL FIX: Use lowercase camelCase keys
            val payload = mapOf(
                "agencyId" to request.agencyId.toString(),                    // ✅ lowercase
                "boardCode" to request.boardCode.toString(),                  // ✅ lowercase
                "consumerNo" to request.consumerNo.toString(),                // ✅ lowercase
                "meterReaderId" to request.meterReaderId.toString(),          // ✅ lowercase
                "meterReaderMobileNo" to request.meterReaderMobile.toString(), // ✅ lowercase
                "meterReaderName" to request.meterReaderName.toString(),      // ✅ lowercase
                "subDivisionCode" to request.subDivisionCode.toString(),      // ✅ lowercase
                "subDivisionName" to request.subDivisionName.toString(),      // ✅ lowercase
                "meterMake" to (request.meterMake.takeIf { it.isNotEmpty() } ?: ""), // ✅ lowercase
                "reqReadingValues" to reqReadingValuesList                    // ✅ lowercase
            )

            Log.d(TAG, "📤 Payload: $payload")

            val response = apiService.createOcrRequest(payload).execute()

            if (response.isSuccessful) {
                val responseBody = response.body()
                val serverId = (responseBody?.get("id") as? Double)?.toInt()

                Log.d(TAG, "✅ Server response: $responseBody")
                Log.d(TAG, "✅ Server ID: $serverId")

                serverId
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ API Error: ${response.code()} - $errorBody")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception: ${e.message}", e)
            null
        }
    }
    // ✅ NEW FUNCTION: Update local IDs to server IDs in all tables
    private suspend fun updateLocalIdToServerId(localId: Int, serverId: Int): Boolean {
        return try {
            Log.d(TAG, "🔄 Updating IDs: Local $localId → Server $serverId")

            // Update in meter_reading table
            val meterReadingsUpdated = database.meterReadingDao()
                .updateOcrReqId(oldId = localId, newId = serverId)
            Log.d(TAG, "📊 Updated $meterReadingsUpdated meter readings")

            // Update in manual_reading table
            val manualReadingsUpdated = database.manualReadingDao()
                .updateOcrReqId(oldId = localId, newId = serverId)
            Log.d(TAG, "📝 Updated $manualReadingsUpdated manual readings")

            // Update in ocr_request table
            database.ocrRequestDao().updateLocalReqId(oldId = localId, newId = serverId)
            Log.d(TAG, "✅ Updated OCR request ID")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating IDs: ${e.message}", e)
            false
        }
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


// ✅ DEBUG LOG ADD KARO
            Log.d("DEBUG_METER", "🔍 Meter No from DB: '${reading.meter_no}'")
            Log.d("DEBUG_METER", "🔍 Meter No length: ${reading.meter_no.length}")
            Log.d("DEBUG_METER", "🔍 Meter No is blank: ${reading.meter_no.isBlank()}")

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