package apc.offline.mrd.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import apc.offline.mrd.data.db.AppDatabase
import apc.offline.mrd.ocrlib.network.OcrRetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.util.concurrent.TimeUnit

class OcrRequestSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = AppDatabase.getDatabase(context)
    private val TAG = "OcrRequestSync"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting OCR Request sync...")

            // Get all unsynced requests
            val unsyncedRequests = database.ocrRequestDao()
                .getAllRequests()
                .first()
                .filter { !it.isSynced }

            if (unsyncedRequests.isEmpty()) {
                Log.d(TAG, "✅ No pending requests to sync")
                return@withContext Result.success()
            }

            Log.d(TAG, "📤 Found ${unsyncedRequests.size} unsynced requests")

            var successCount = 0
            var failCount = 0

            unsyncedRequests.forEach { request ->
                try {
                    // Convert string to list
                    val reqReadingValues = Gson().fromJson(
                        request.reqReadingValues,
                        Array<String>::class.java
                    ).toList()

                    val payload = mapOf(
                        "agencyId" to request.agencyId,
                        "boardCode" to request.boardCode,
                        "consumerNo" to request.consumerNo,
                        "meterReaderId" to request.meterReaderId,
                        "meterReaderMobileNo" to request.meterReaderMobile,
                        "meterReaderName" to request.meterReaderName,
                        "subDivisionCode" to request.subDivisionCode,
                        "subDivisionName" to request.subDivisionName,
                        "meterMake" to request.meterMake,
                        "reqReadingValues" to reqReadingValues
                    )

                    Log.d(TAG, "📡 Syncing request: Consumer=${request.consumerNo}")

                    val response = OcrRetrofitClient.getApiService().createOcrRequest(payload)
                        .awaitResponse()

                    if (response.isSuccessful) {
                        // Mark as synced
                        database.ocrRequestDao().insertRequest(
                            request.copy(isSynced = true)
                        )
                        successCount++
                        Log.d(TAG, "✅ Synced: ${request.consumerNo}")

                        // Show notification
                        showSyncNotification(
                            applicationContext,
                            "OCR Request Synced",
                            "Consumer ${request.consumerNo} uploaded successfully"
                        )
                    } else {
                        failCount++
                        Log.e(TAG, "❌ Failed: ${response.code()}")
                    }

                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "❌ Error syncing request: ${e.message}", e)
                }
            }

            Log.d(TAG, "🎯 Sync complete: ✅ $successCount | ❌ $failCount")

            return@withContext if (failCount == 0) {
                Result.success()
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Sync failed: ${e.message}", e)
            return@withContext Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "ocr_request_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<OcrRequestSyncWorker>(
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )

            Log.d("WorkManager", "✅ OCR Request sync scheduled (every 5 minutes)")
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

// Notification Helper
private fun showSyncNotification(context: Context, title: String, message: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

    // Create channel for Android 8+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            "sync_channel",
            "Data Sync",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    val notification = androidx.core.app.NotificationCompat.Builder(context, "sync_channel")
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}