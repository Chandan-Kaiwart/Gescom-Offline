package apc.offline.mrd.data.sync

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import apc.offline.mrd.R
import apc.offline.mrd.data.db.AppDatabase
import apc.offline.mrd.data.entities.OcrRequestEntity
import apc.offline.mrd.ocrlib.network.OcrRetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncActivity : AppCompatActivity() {

    private lateinit var syncButton: Button
    private lateinit var backButton: Button
    private lateinit var syncProgress: ProgressBar
    private lateinit var syncStatus: TextView
    private lateinit var recordCountText: TextView
    private lateinit var ocrCountText: TextView
    private lateinit var meterCountText: TextView
    private lateinit var manualCountText: TextView
    private lateinit var database: AppDatabase

    private val TAG = "UNIFIED_SYNC"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)

        database = AppDatabase.getDatabase(this)
        initViews()
        setupListeners()
        updateRecordCounts()
    }

    private fun initViews() {
        syncButton = findViewById(R.id.syncButton)
        backButton = findViewById(R.id.backButton)
        syncProgress = findViewById(R.id.syncProgress)
        syncStatus = findViewById(R.id.syncStatus)
        recordCountText = findViewById(R.id.recordCount)
        ocrCountText = findViewById(R.id.ocrCount)
        meterCountText = findViewById(R.id.meterCount)
        manualCountText = findViewById(R.id.manualCount)
    }

    private fun setupListeners() {
        syncButton.setOnClickListener {
            startUnifiedSync()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    // ✅ MAIN UNIFIED SYNC FUNCTION
    private fun startUnifiedSync() {
        syncButton.isEnabled = false
        syncProgress.visibility = View.VISIBLE
        syncStatus.text = "🔄 Preparing sync..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🚀 Starting unified sync...")

                // ✅ STEP 1: Fetch required data ONCE (not in loop!)
                updateStatus("📥 Fetching metadata...")
                val dataFetched = fetchRequiredMetadata()

                if (!dataFetched) {
                    withContext(Dispatchers.Main) {
                        showError("❌ Failed to fetch metadata")
                    }
                    return@launch
                }

                // ✅ STEP 2: Get all unsynced OCR requests
                updateStatus("📦 Loading local data...")
                val unsyncedRequests = database.ocrRequestDao()
                    .getUnsyncedRequests()
                    .first()

                if (unsyncedRequests.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showSuccess("✅ No data to sync!")
                    }
                    return@launch
                }

                Log.d(TAG, "📦 Found ${unsyncedRequests.size} unsynced requests")

                // ✅ STEP 3: Assign actual server IDs to ALL requests
                updateStatus("🔄 Creating server requests...")
                val idsAssigned = assignServerIdsToAllRequests(unsyncedRequests)

                if (!idsAssigned) {
                    withContext(Dispatchers.Main) {
                        showError("❌ Failed to assign server IDs")
                    }
                    return@launch
                }

                // ✅ STEP 4: Start actual upload sync
                updateStatus("📤 Uploading data...")
                val syncSuccess = startActualUploadSync()

                withContext(Dispatchers.Main) {
                    if (syncSuccess) {
                        observeWorkProgress()
                    } else {
                        showError("❌ Failed to start sync worker")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showError("❌ Error: ${e.message}")
                }
            }
        }
    }


    private suspend fun fetchRequiredMetadata(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📥 Fetching metadata...")

                // 1. Fetch Meter Reading Units
                val unitsResponse = OcrRetrofitClient.getApiService()
                    .getMeterReadingUnits(OcrRetrofitClient.AUTH_TOKEN)
                    .execute()

                if (!unitsResponse.isSuccessful) {
                    Log.e(TAG, "❌ Units fetch failed: ${unitsResponse.code()}")
                    return@withContext false
                }
                Log.d(TAG, "✅ Units: ${unitsResponse.body()?.size}")

                // 2. Fetch Exceptions
                val expsResponse = OcrRetrofitClient.getApiService()
                    .getMeterReadingExceptions(OcrRetrofitClient.AUTH_TOKEN)
                    .execute()

                if (!expsResponse.isSuccessful) {
                    Log.e(TAG, "❌ Exceptions fetch failed: ${expsResponse.code()}")
                    return@withContext false
                }
                Log.d(TAG, "✅ Exceptions: ${expsResponse.body()?.size}")

                // 3. Fetch Meter Makes
                val makesResponse = OcrRetrofitClient.getApiService()
                    .getMeterMakes(OcrRetrofitClient.AUTH_TOKEN)
                    .execute()

                if (!makesResponse.isSuccessful) {
                    Log.e(TAG, "❌ Makes fetch failed: ${makesResponse.code()}")
                    return@withContext false
                }
                Log.d(TAG, "✅ Makes: ${makesResponse.body()?.size}")

                Log.d(TAG, "✅ All metadata fetched successfully!")
                true

            } catch (e: Exception) {
                Log.e(TAG, "❌ Metadata fetch error: ${e.message}", e)
                false
            }
        }
    }


    private suspend fun assignServerIdsToAllRequests(
        requests: List<OcrRequestEntity>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Assigning server IDs to ${requests.size} requests...")

                var successCount = 0
                var failureCount = 0

                requests.forEachIndexed { index, request ->
                    try {
                        updateStatus(
                            "🔄 Creating request ${index + 1}/${requests.size}..."
                        )

                        Log.d(TAG, "🔄 Processing LocalID: ${request.localReqId}")

                        // ✅ Create OCR request on server
                        val serverId = createOcrRequestOnServer(request)

                        if (serverId != null) {
                            // ✅ Update all tables with actual server ID
                            updateAllTablesWithServerId(request.localReqId, serverId)

                            successCount++
                            Log.d(TAG, "✅ LocalID ${request.localReqId} → ServerID $serverId")
                        } else {
                            failureCount++
                            Log.e(TAG, "❌ Failed for LocalID: ${request.localReqId}")
                        }

                    } catch (e: Exception) {
                        failureCount++
                        Log.e(TAG, "❌ Error: ${e.message}", e)
                    }
                }

                Log.d(TAG, "📊 ID Assignment: ✅ $successCount | ❌ $failureCount")
                updateStatus("✅ Server IDs assigned: $successCount/$requests.size")

                // Return true if at least some succeeded
                successCount > 0

            } catch (e: Exception) {
                Log.e(TAG, "❌ Assignment error: ${e.message}", e)
                false
            }
        }
    }

    // ✅ FIXED: Ensure all values are explicitly strings
    private suspend fun createOcrRequestOnServer(
        request: OcrRequestEntity
    ): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val reqReadingValuesList = try {
                    Gson().fromJson(
                        request.reqReadingValues,
                        Array<String>::class.java
                    ).toList()
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Parse error: ${e.message}")
                    emptyList()
                }

                // ✅ CRITICAL FIX: Explicitly convert all values to strings
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
                    "reqReadingValues" to reqReadingValuesList
                )

                Log.d(TAG, "📤 Payload: $payload")
                Log.d(TAG, "📋 AGENCY_ID type: ${payload["AGENCY_ID"]?.javaClass?.simpleName}")

                val response = OcrRetrofitClient.getApiService()
                    .createOcrRequest(payload)
                    .execute()

                if (response.isSuccessful) {
                    val serverId = (response.body()?.get("id") as? Double)?.toInt()
                    Log.d(TAG, "✅ Server ID: $serverId")
                    serverId
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ Error ${response.code()}: $errorBody")
                    null
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception: ${e.message}", e)
                null
            }
        }
    }

    // ✅ Update all tables: LocalID → ServerID
    private suspend fun updateAllTablesWithServerId(localId: Int, serverId: Int) {
        withContext(Dispatchers.IO) {
            try {
                // Update meter_reading table
                database.meterReadingDao().updateOcrReqId(localId, serverId)
                Log.d(TAG, "✅ Updated meter_reading: $localId → $serverId")

                // Update manual_reading table
                database.manualReadingDao().updateOcrReqId(localId, serverId)
                Log.d(TAG, "✅ Updated manual_reading: $localId → $serverId")

                // Update ocr_request table (localReqId → serverId)
                database.ocrRequestDao().updateLocalReqId(localId, serverId)
                Log.d(TAG, "✅ Updated ocr_request: $localId → $serverId")

            } catch (e: Exception) {
                Log.e(TAG, "❌ DB update error: ${e.message}", e)
            }
        }
    }

    // ✅ STEP 3: Start actual upload sync with WorkManager
    private suspend fun startActualUploadSync(): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "📤 Starting upload worker...")

                val syncRequest = OneTimeWorkRequestBuilder<UnifiedSyncWorker>()
                    .build()

                WorkManager.getInstance(this@SyncActivity).enqueueUniqueWork(
                    "unified_meter_sync",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    syncRequest
                )

                Log.d(TAG, "✅ Upload worker queued")
                true

            } catch (e: Exception) {
                Log.e(TAG, "❌ Worker error: ${e.message}", e)
                false
            }
        }
    }

    // ✅ Observe WorkManager progress
    private fun observeWorkProgress() {
        val workManager = WorkManager.getInstance(this)

        workManager.getWorkInfosByTagLiveData("unified_meter_sync")
            .observe(this) { workInfos ->
                workInfos?.firstOrNull()?.let { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            syncStatus.text = "📤 Uploading data..."
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val totalSynced = workInfo.outputData.getInt("synced_count", 0)
                            val totalFailed = workInfo.outputData.getInt("failed_count", 0)

                            showSuccess("✅ Sync complete!\n$totalSynced synced, $totalFailed failed")

                            lifecycleScope.launch {
                                delay(500)
                                updateRecordCounts()
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            showError("❌ Upload failed")
                        }
                        else -> {}
                    }
                }
            }
    }

    // ✅ Update record counts in UI
    private fun updateRecordCounts() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ocrCount = database.ocrRequestDao().getUnsyncedRequests().first().size
                val meterCount = database.meterReadingDao().getUnsyncedReadings().first().size
                val manualCount = database.manualReadingDao().getUnsyncedReadings().first().size
                val totalCount = ocrCount + meterCount + manualCount

                withContext(Dispatchers.Main) {
                    recordCountText.text = "Total Unsynced: $totalCount"
                    ocrCountText.text = "OCR Requests: $ocrCount"
                    meterCountText.text = "Meter Readings: $meterCount"
                    manualCountText.text = "Manual Readings: $manualCount"

                    syncButton.isEnabled = totalCount > 0

                    if (totalCount == 0) {
                        syncStatus.text = "✅ All data synced!"
                    } else {
                        syncStatus.text = "Ready to sync"
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    recordCountText.text = "Error loading counts"
                }
            }
        }
    }

    // ✅ Helper: Update status text
    private suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            syncStatus.text = message
            Log.d(TAG, message)
        }
    }

    // ✅ Helper: Show success
    private fun showSuccess(message: String) {
        syncProgress.visibility = View.GONE
        syncButton.isEnabled = true
        syncStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, message)
    }

    // ✅ Helper: Show error
    private fun showError(message: String) {
        syncProgress.visibility = View.GONE
        syncButton.isEnabled = true
        syncStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    override fun onResume() {
        super.onResume()
        updateRecordCounts()
    }
}