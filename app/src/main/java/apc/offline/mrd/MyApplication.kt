package apc.offline.mrd

import android.app.Application
import android.util.Log
import apc.offline.mrd.data.sync.OcrRequestSyncWorker


class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
//        OcrRequestSyncWorker.schedule(this)
//        Log.d("MyApplication", "🚀 Periodic sync initialized - Every 5 minutes")
    }
}