package apc.offline.mrd.ocrlib

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import apc.offline.mrd.ocrlib.dataClasses.request.OcrRequest
import apc.offline.mrd.ocrlib.OcrActivity
import com.google.gson.Gson


object OcrLauncher {
    private var callback: OcrCallback? = null

    fun launch(context: Context, input: OcrRequest, cb: OcrCallback) {
        callback = cb
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val intent = Intent(context, OcrActivity::class.java)
                intent.putExtra("input", Gson().toJson(input))

                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()           }
        }, 500) // 1000 milliseconds = 1 second

    }

    fun sendResultBack(result: String) {
        callback?.onSdkResult(result)
        callback = null
    }
}