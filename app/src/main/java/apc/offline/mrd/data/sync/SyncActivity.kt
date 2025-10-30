package apc.offline.mrd.data.sync

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)

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
            startManualSync()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun startManualSync() {
        syncButton.isEnabled = false
        syncProgress.visibility = View.VISIBLE
        syncStatus.text = "Syncing all data..."

        // ✅ Use UnifiedSyncWorker
        val syncWorkRequest = OneTimeWorkRequestBuilder<UnifiedSyncWorker>()
            .build()

        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(syncWorkRequest)

        workManager.getWorkInfoByIdLiveData(syncWorkRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        syncStatus.text = "Syncing..."
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        syncProgress.visibility = View.GONE
                        syncButton.isEnabled = true

                        val totalSynced = workInfo.outputData.getInt("synced_count", 0)
                        val totalFailed = workInfo.outputData.getInt("failed_count", 0)
                        val ocrSynced = workInfo.outputData.getInt("ocr_synced", 0)
                        val meterSynced = workInfo.outputData.getInt("meter_synced", 0)
                        val manualSynced = workInfo.outputData.getInt("manual_synced", 0)

                        syncStatus.text = buildString {
                            append("✅ Total Synced: $totalSynced\n")
                            append("❌ Total Failed: $totalFailed\n\n")
                            append("OCR: $ocrSynced | Meter: $meterSynced | Manual: $manualSynced")
                        }

                        Toast.makeText(
                            this,
                            "Sync complete! $totalSynced records synced",
                            Toast.LENGTH_LONG
                        ).show()

                        lifecycleScope.launch {
                            delay(500)
                            updateRecordCounts()
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        syncProgress.visibility = View.GONE
                        syncButton.isEnabled = true

                        syncStatus.text = "❌ Sync failed"
                        Toast.makeText(
                            this,
                            "Sync failed. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {}
                }
            }
    }

    private fun updateRecordCounts() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(this@SyncActivity)

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
                        syncStatus.text = "All data synced! ✅"
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

    override fun onResume() {
        super.onResume()
        updateRecordCounts()
    }
}
