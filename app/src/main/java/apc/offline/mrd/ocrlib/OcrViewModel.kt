package apc.offline.mrd.ocrlib

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity.TELEPHONY_SERVICE
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apc.offline.mrd.ocrlib.dataClasses.UploadMeterReadingImageRes
import apc.offline.mrd.ocrlib.dataClasses.request.OcrRequest
import apc.offline.mrd.ocrlib.dataClasses.response.OcrResponse
import apc.offline.mrd.ocrlib.dataClasses.response.OcrResponseLsm
import apc.offline.mrd.ocrlib.dataClasses.response.OcrResult


//class OcrViewModel : ViewModel() {
//
//    var offlineOcrHelper: OfflineOcrHelper? = null
//
//    val res = MutableLiveData<UploadMeterReadingImageRes>()
//    val inp = MutableLiveData<OcrRequest>()
//    val ocrResults = MutableLiveData<MutableList<OcrResult>>(mutableListOf())
//    val accId = MutableLiveData<String>()
//    var address = MutableLiveData<String>()
//    var lat = MutableLiveData<String>()
//    var lng = MutableLiveData<String>()
//
//    // Device details
//    val deviceModel = Build.MODEL
//    val manufacturer = Build.MANUFACTURER
//    val osVersion = "Android ${Build.VERSION.RELEASE}"
//
//    val registers = MutableLiveData<MutableList<String>>()
//    val curType = MutableLiveData<Int>(0)
//
//    private val _capturedImages = MutableLiveData<MutableMap<Int, Bitmap>>(mutableMapOf())
//    val capturedImages: LiveData<MutableMap<Int, Bitmap>> = _capturedImages
//
//    val ocrRes = MutableLiveData<OcrResponse>(
//        OcrResponse(
//            acc_id = "",
//            address = "",
//            lat = "",
//            lng = "",
//            meter_make = "",
//            meter_no = "",
//            mr_sr_no = "",
//            read_type = "manual",
//            ocr_results = emptyList(),
//            manual_readings = mutableListOf(),
//            ocr_npr = "",
//            probe_npr = "",
//            input = OcrRequest(
//                reqId = -1,
//                AGENCY_ID = "",
//                BOARD_CODE = "",
//                CONSUMER_NO = "",
//                METER_READER_ID = "",
//                METER_READER_MOBILE_NO = "",
//                METER_READER_NAME = "",
//                SUB_DIVISION_CODE = "",
//                SUB_DIVISION_NAME = "",
//                METER_MAKE = "",
//                REQ_READING_VALUES = emptyList()
//            ),
//            field_exp = "",
//            meter_status = "",
//            additional_field = ""
//        )
//    )
//
//    val ocrResLsm = MutableLiveData<OcrResponseLsm>(
//        OcrResponseLsm(
//            acc_id = "",
//            ocr_results = emptyList(),
//            input = OcrRequest(
//                reqId = -1,
//                AGENCY_ID = "",
//                BOARD_CODE = "",
//                CONSUMER_NO = "",
//                METER_READER_ID = "",
//                METER_READER_MOBILE_NO = "",
//                METER_READER_NAME = "",
//                SUB_DIVISION_CODE = "",
//                SUB_DIVISION_NAME = "",
//                METER_MAKE = "",
//                REQ_READING_VALUES = emptyList()
//            ),
//            address = "",
//            lat = "",
//            lng = ""
//        )
//    )
//
//    // Sets the map of captured images
//    fun setCapturedImages(images: MutableMap<Int, Bitmap>) {
//        _capturedImages.value = images
//    }
//
//    // Trigger update of observed OCR results
//    fun triggerOcrResultsUpdate() {
//        ocrResults.value = ocrResults.value
//    }
//
//    // Get count of captured images
//    fun getCapturedImageCount(): Int {
//        return _capturedImages.value?.size ?: 0
//    }
//
//    // Clear all captured images
//    fun clearCapturedImages() {
//        _capturedImages.value?.clear()
//        _capturedImages.value = mutableMapOf()
//    }
//
//    // Add or update a captured image by position
//    fun addCapturedImage(position: Int, bitmap: Bitmap) {
//        val currentImages = _capturedImages.value ?: mutableMapOf()
//        currentImages[position] = bitmap
//        _capturedImages.value = currentImages
//    }
//
//    // Get network signal strength string, requires READ_PHONE_STATE permission and API Q+
//    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
//    @RequiresApi(Build.VERSION_CODES.Q)
//    fun getSignalStrength(context: Context): String {
//        try {
//            val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
//            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo
//
//            cellInfoList?.forEach { cellInfo ->
//                if (cellInfo.isRegistered) {
//                    val signalStrength: CellSignalStrength? = when (cellInfo) {
//                        is CellInfoGsm -> cellInfo.cellSignalStrength
//                        is CellInfoCdma -> cellInfo.cellSignalStrength
//                        is CellInfoLte -> cellInfo.cellSignalStrength
//                        is CellInfoWcdma -> cellInfo.cellSignalStrength
//                        is CellInfoNr -> cellInfo.cellSignalStrength
//                        else -> null
//                    }
//                    signalStrength?.let {
//                        return "${it.dbm} dBm"
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            return "Error"
//        }
//        return "Unknown"
//    }
//}


class OcrViewModel : ViewModel() {

    var offlineOcrHelper: OfflineOcrHelper? = null
    // ✅ FIXED - Single declaration of each variable
    val res = MutableLiveData<UploadMeterReadingImageRes>()
    val inp = MutableLiveData<OcrRequest>()
    val ocrResults = MutableLiveData<MutableList<OcrResult>>(mutableListOf())
    val accId = MutableLiveData<String>()
    var address = MutableLiveData<String>()
    var lat = MutableLiveData<String>()
    var lng = MutableLiveData<String>()

    // Device info
    val deviceModel = Build.MODEL            // e.g. "Redmi Note 12"
    val manufacturer = Build.MANUFACTURER    // e.g. "Xiaomi"
    val osVersion = "Android ${Build.VERSION.RELEASE}"

    val registers = MutableLiveData<MutableList<String>>()
    val curType = MutableLiveData<Int>(0)

    // ✅ FIXED - Captured images storage
    private val _capturedImages = MutableLiveData<MutableMap<Int, Bitmap>>(mutableMapOf())
    val capturedImages: LiveData<MutableMap<Int, Bitmap>> = _capturedImages

    // Main OCR response objects
    val ocrRes = MutableLiveData<OcrResponse>(
        OcrResponse(
            acc_id = "",
            agency_req_id = "",  // ← Add this!
            field_exp = "",
            meter_status = "",
            meter_make = "",
            meter_no = "",
            ocr_npr = "",
            read_type = "manual",
            ocr_results = emptyList(),
            manual_readings = mutableListOf(),
            probe_npr = "",
            mr_sr_no = "",
            input = OcrRequest(
                reqId = -1,
                AGENCY_ID = "",
                BOARD_CODE = "",
                CONSUMER_NO = "",
                METER_READER_ID = "",
                METER_READER_MOBILE_NO = "",
                METER_READER_NAME = "",
                SUB_DIVISION_CODE = "",
                SUB_DIVISION_NAME = "",
                METER_MAKE = "",
                REQ_READING_VALUES = emptyList()
            ),
            address = "",
            lat = "",
            lng = ""
        )
    )

    val ocrResLsm = MutableLiveData<OcrResponseLsm>(
        OcrResponseLsm(
            "",     // acc_id
            emptyList(), // ocr_results
            OcrRequest(-1,"","","","","","","","","", emptyList()), // input
            "",     // address
            "",     // lat
            ""      // lng
        )
    )

    // ✅ FIXED - Captured images management functions
    fun setCapturedImages(images: MutableMap<Int, Bitmap>) {
        _capturedImages.value = images
    }

    fun triggerOcrResultsUpdate() {
        ocrResults.value = ocrResults.value // Trigger observer
    }

    fun getCapturedImageCount(): Int {
        return _capturedImages.value?.size ?: 0
    }

    // Clear captured images
    fun clearCapturedImages() {
        _capturedImages.value?.clear()
        _capturedImages.value = mutableMapOf()
    }

    // Add single captured image
    fun addCapturedImage(position: Int, bitmap: Bitmap) {
        val currentImages = _capturedImages.value ?: mutableMapOf()
        currentImages[position] = bitmap
        _capturedImages.value = currentImages
    }

    // Get signal strength function
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @RequiresApi(Build.VERSION_CODES.Q)
    fun getSignalStrength(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

            cellInfoList?.let { cellList ->
                for (cellInfo in cellList) {
                    if (cellInfo.isRegistered) {
                        val signalStrength: CellSignalStrength? = when (cellInfo) {
                            is CellInfoGsm -> cellInfo.cellSignalStrength
                            is CellInfoCdma -> cellInfo.cellSignalStrength
                            is CellInfoLte -> cellInfo.cellSignalStrength
                            is CellInfoWcdma -> cellInfo.cellSignalStrength
                            is CellInfoNr -> cellInfo.cellSignalStrength
                            else -> null
                        }
                        signalStrength?.let {
                            return "${it.dbm} dBm"
                        }
                    }
                }
            }
            "Unknown"
        } catch (e: Exception) {
            "Error"
        }
    }
}