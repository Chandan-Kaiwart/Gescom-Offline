package apc.offline.mrd

import android.app.Application
import android.util.Log



class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
//        OcrRequestSyncWorker.schedule(this)
//        Log.d("MyApplication", "🚀 Periodic sync initialized - Every 5 minutes")
    }
}