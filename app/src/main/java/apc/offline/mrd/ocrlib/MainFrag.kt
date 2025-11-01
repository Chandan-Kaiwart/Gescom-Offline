package apc.offline.mrd.ocrlib

import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import apc.offline.mrd.ocrlib.network.OcrRetrofitClient
import android.view.ViewGroup
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import apc.offline.mrd.databinding.MainFragBinding
import apc.offline.mrd.ocrlib.dataClasses.MeterReadingExceptionsResItem
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apc.offline.mrd.R
import apc.offline.mrd.ocrlib.dataClasses.MeterMakesRes
import apc.offline.mrd.ocrlib.dataClasses.response.ManualReading
import apc.offline.mrd.ocrlib.dataClasses.response.OcrResult
import apc.offline.mrd.ocrlib.util.ProgressDia
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import android.Manifest
import androidx.fragment.app.activityViewModels
import apc.offline.mrd.ocrlib.dataClasses.UploadMeterReadingImageRes
import com.bumptech.glide.Glide
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import apc.offline.mrd.ocrlib.dataClasses.MeterMakesResItem
import apc.offline.mrd.ocrlib.dataClasses.MeterReadingUnitsResItem
import java.io.ByteArrayOutputStream
import apc.offline.mrd.data.db.AppDatabase
import apc.offline.mrd.data.entities.ManualReadingEntity
import apc.offline.mrd.data.entities.MeterReadingEntity
import apc.offline.mrd.data.entities.OcrRequestEntity
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException


class MainFrag : Fragment() {
    private lateinit var mContext: Context
    private lateinit var navController: NavController
    private lateinit var binding: MainFragBinding
    private val vm by activityViewModels<OcrViewModel>()
    private var versionCode: String? = ""  // ✅ FIX 3: Make nullable
    private lateinit var ocrUnits: List<MeterReadingUnitsResItem>
    private var isManualDataSubmitted = false
    private lateinit var ocrExps: List<MeterReadingExceptionsResItem>
    private lateinit var meterMakes: MeterMakesRes

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    private fun generateLocalReqId(): Int {
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MainFragBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = view.findNavController()
        meterMakes = MeterMakesRes()
        ocrUnits = emptyList()
        ocrExps = emptyList()
        binding.vm = vm
        binding.lifecycleOwner = requireActivity()

        val info = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
        versionCode = info.versionName

        vm.capturedImages.observe(requireActivity()) { capturedImages ->
            // ✅ FIXED - Don't create new adapter, just notify existing one
            val currentAdapter = binding.readingsRecyclerView.adapter as? OcrResultsAdapter

            if (currentAdapter != null) {
                // Just notify adapter about data change - images will persist
                currentAdapter.notifyDataSetChanged()
                Log.d("CapturedImages", "Adapter notified - Images: ${capturedImages.size}")
            }
            // ✅ Don't create new adapter here - it clears images!
        }
        vm.ocrRes.value?.acc_id = vm.inp.value?.CONSUMER_NO.toString()
        try {
            binding.verTv.setText("Req"+vm.ocrRes.value?.input?.reqId!!.toString())

        }
        catch (e:Exception){

        }
        binding.readingsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.readingsRecyclerView.setHasFixedSize(true)
        binding.readingsRecyclerView.isNestedScrollingEnabled = false
        vm.lat.observe(requireActivity()) {
            vm.lat.value.let {
                if (it != null) {
                    vm.ocrRes.value?.lat=it
                }
            }
            vm.lng.value.let {
                if (it != null) {
                    vm.ocrRes.value?.lng=it
                }
            }


            binding.locTv.setText(vm.ocrRes.value?.lat+"\n"+vm.ocrRes.value?.lng)

        }
        val istTime: String = Instant.now()
            .atZone(ZoneId.of("Asia/Kolkata"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        binding.timeTv.setText(versionCode+"\n"+istTime)
//
//        fetchMeterReadingMakes(mContext)
//        fetchMeterReadingExceptions(mContext)
//        fetchMeterReadingUnits(mContext)

        if (vm.inp.value?.reqId==-1 || vm.inp.value?.reqId==null){
            Log.d("REQ>>1",vm.inp.value?.reqId.toString())
            createOcrRequest()
        }
        else{
            Log.d("REQ>>2",vm.inp.value?.reqId.toString())
        }

        binding.remRg.setOnCheckedChangeListener { group, checkedId ->

            when (checkedId) {

                R.id.OKRb -> {
                    vm.ocrRes.value!!.meter_status = "OK"

                    binding.statusLL.visibility = View.VISIBLE
                    /*   binding.OKRb.isEnabled = false
                    binding.IDFRb.isEnabled = false
                    binding.TDMRRb.isEnabled = false*/

//                    val adapter1 =
//                        OcrResultsAdapter(
//                            vm.ocrResults.value?.toList()!!,
//                            vm,
//                            navController,
//                            binding
//                        )
//                    binding.readingsRecyclerView.layoutManager = LinearLayoutManager(context)
//                    binding.readingsRecyclerView.adapter = adapter1
                    //       binding.ocrExp.visibility = View.VISIBLE


                }

                R.id.IDFRb -> {
                    vm.ocrRes.value!!.meter_status = "IDF"

                    binding.statusLL.visibility = View.VISIBLE

                    binding.ocrExp.visibility = View.VISIBLE
//                    val adapter1 =
//                        OcrResultsAdapter(
//                            vm.ocrResults.value?.toList()!!,
//                            vm,
//                            navController,
//                            binding
//                        )
//                    binding.readingsRecyclerView.layoutManager = LinearLayoutManager(context)
//                    binding.readingsRecyclerView.adapter = adapter1


                }

                R.id.TDMRRb -> {
                    vm.ocrRes.value!!.meter_status = "TDMR"
                    binding.statusLL.visibility = View.VISIBLE

                    binding.ocrExp.visibility = View.VISIBLE
//                    val adapter1 =
//                        OcrResultsAdapter(
//                            vm.ocrResults.value?.toList()!!,
//                            vm,
//                            navController,
//                            binding
//                        )
//                    binding.readingsRecyclerView.layoutManager = LinearLayoutManager(context)
//                    binding.readingsRecyclerView.adapter = adapter1


                }
            }
        }
        //  binding.ocrExp.visibility = View.VISIBLE

        if (vm.ocrResults.value?.size == 0 || (vm.registers.value?.size != vm.ocrResults.value?.size)) {
            vm.ocrResults.value?.clear()
            vm.registers.value?.forEach { reg ->
                val ocrResult = OcrResult("", "", "", "", 0, "", "", "", "",1, "", "", 0, reg)
                vm.ocrResults.value?.add(ocrResult)
            }
        } else {
            binding.OKRb.isEnabled = false
            binding.IDFRb.isEnabled = false
            binding.TDMRRb.isEnabled = false
            vm.ocrResults.value?.forEachIndexed { index, ocrResult ->
                if (ocrResult.ocr_mno.isNotEmpty()) {
                    Log.d("HereMno>>>1", ocrResult.ocr_mno)

                    vm.ocrRes.value?.meter_no = ocrResult.ocr_mno
                }
                if (ocrResult.ocr_meter_make.isNotEmpty()) {
                    vm.ocrRes.value?.meter_make = ocrResult.ocr_meter_make
                }
            }
            if (vm.ocrResults.value?.size == 2) {
                val res1 = vm.ocrResults.value?.get(0)!!
                val res2 = vm.ocrResults.value?.get(1)!!
                if (res1.ocr_mno.isNotEmpty() || res2.ocr_mno.isNotEmpty()) {
                    vm.ocrRes.value?.meter_no =
                        if (res1.ocr_mno.length > res2.ocr_mno.length) res1.ocr_mno else res2.ocr_mno


                }
                if (res1.ocr_meter_make.isNotEmpty() || res2.ocr_meter_make.isNotEmpty()) {
                    vm.ocrRes.value?.meter_make =
                        if (res1.ocr_meter_make.length > res2.ocr_meter_make.length) res1.ocr_meter_make else res2.ocr_meter_make
                    //  binding.meterMakes.isFocusable = false

                }


            }
        }

        vm.ocrRes.value?.ocr_results = vm.ocrResults.value!!
        vm.ocrRes.value?.input = vm.inp.value!!
        if (vm.ocrRes.value?.meter_status == "OK") {
            binding.OKRb.isChecked = true
            Log.d("HERE>>", "HERE>>1")


        } else if (vm.ocrRes.value?.meter_status == "IDF") {
            binding.IDFRb.isChecked = true
            Log.d("HERE>>", "HERE>>2")


        } else if (vm.ocrRes.value?.meter_status == "TDMR") {
            binding.TDMRRb.isChecked = true
            Log.d("HERE>>", "HERE>>3")

        } else {
            Log.d("HERE>>", "HERE>>")
        }
        binding.meterMakes.setText(vm.ocrRes.value?.meter_make)
        // binding.mNoEt.setText(vm.ocrRes.value?.meter_no)
        binding.probeExps.setText(vm.ocrRes.value?.probe_npr)
        binding.ocrExps.setText(vm.ocrRes.value?.ocr_npr)
        binding.fieldExps.setText(vm.ocrRes.value?.field_exp)
        if (vm.ocrRes.value!!.meter_status == "IDF") {
            binding.IDFRb.isChecked = true

        } else if (vm.ocrRes.value!!.meter_status == "TDMR") {
            binding.TDMRRb.isChecked = true

        } else {
            binding.OKRb.isChecked = true

        }



        Log.d("List>>>", vm.registers.value.toString())


        val adapter1 =
            OcrResultsAdapter(vm.ocrResults.value?.toList()!!, vm, navController, binding)
        binding.readingsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.readingsRecyclerView.adapter = adapter1

        binding.getDataBt.setOnClickListener {
            val capturedImages = vm.capturedImages.value

            if (capturedImages.isNullOrEmpty()) {
                Toast.makeText(mContext, "Please capture images first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // ✅ Check manual mode
            val isManualMode = binding.ocrExps.text.isNotEmpty()

            if (isManualMode) {
                // ✅ MANUAL MODE: Skip OCR completely
                Log.d("MANUAL_MODE", "⚠️ Manual mode - skipping OCR")

                Toast.makeText(
                    mContext,
                    "Manual mode enabled. Enter readings manually.",
                    Toast.LENGTH_SHORT
                ).show()

                binding.subBt.visibility = View.VISIBLE
                return@setOnClickListener
            }

            // ✅ OCR MODE: Run OCR
            Log.d("OCR_MODE", "🔍 Running OCR...")

            lifecycleScope.launch(Dispatchers.Default) {
                withContext(Dispatchers.Main) {
                    val pd = ProgressDia()
                    pd.show(childFragmentManager, "Processing OCR...")

                    lifecycleScope.launch(Dispatchers.Default) {
                        try {
                            // ✅ CHANGE 1: Use OfflineOcrHelperV2 instead of OfflineOcrHelper
                            if (vm.offlineOcrHelper == null) {
                                withContext(Dispatchers.Main) {
                                    vm.offlineOcrHelper = OfflineOcrHelperV2(mContext)
                                }
                            }

                            var successCount = 0

                            capturedImages.forEach { (position, bitmap) ->
                                val unit = ocrUnits.find {
                                    it.name.equals(vm.registers.value?.get(position), ignoreCase = true)
                                }?.id ?: 1

                                // ✅ CHANGE 2: Call processImage with unit parameter
                                val result = vm.offlineOcrHelper!!.processImage(bitmap, unit)

                                withContext(Dispatchers.Main) {
                                    // ✅ CHANGE 3: Remove result.success check - new version returns OcrResult directly
                                    // Check if reading is not empty instead
                                    if (result.reading.isNotEmpty()) {
                                        successCount++
                                        vm.ocrResults.value?.get(position)?.apply {
                                            // ✅ CHANGE 4: Use result.reading directly
                                            ocr_reading = result.reading
                                            meter_reading = result.reading

                                            // ✅ CHANGE 5: Set exception code to 1 (success) if reading found
                                            ocr_exception_code = 1
                                            ocr_exception_msg = ""

                                            // ✅ CHANGE 6: Use result.meterNumber (only for KWH/unit=1)
                                            ocr_mno = result.meterNumber

                                            ocr_meter_make = ""

                                            // ✅ CHANGE 7: Set ocr_unit based on unit parameter
                                            ocr_unit = unit.toString()

                                            this.unit = unit
                                            ocr_ref_id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                                        }

                                        Log.d("OCR_SUCCESS", "Position $position: reading=${result.reading}, mno=${result.meterNumber}")
                                    } else {
                                        // ✅ CHANGE 8: Handle failed detection
                                        Log.w("OCR_FAILED", "Position $position: No reading detected")
                                        vm.ocrResults.value?.get(position)?.apply {
                                            ocr_exception_code = 22 // "Not detected" exception
                                            ocr_exception_msg = ocrExps.find { it.id == 22 }?.name ?: "Not detected"
                                        }
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                pd.dismiss()

                                if (successCount > 0) {
                                    // ✅ CHANGE 9: Get longest meter number from all results
                                    val mno = vm.ocrResults.value
                                        ?.mapNotNull { it.ocr_mno }
                                        ?.filter { it.isNotEmpty() }
                                        ?.maxByOrNull { it.length }

                                    if (!mno.isNullOrEmpty()) {
                                        vm.ocrRes.value?.meter_no = mno
                                        binding.mNoEt.setText(mno)
                                        Log.d("OCR_MNO", "Meter Number detected: $mno")
                                    }

                                    binding.readingsRecyclerView.adapter?.notifyDataSetChanged()
                                    binding.subBt.visibility = View.VISIBLE

                                    Toast.makeText(
                                        mContext,
                                        "OCR Complete! $successCount readings detected.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        mContext,
                                        "No readings detected. Try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                pd.dismiss()
                                Log.e("OFFLINE_OCR", "Error: ${e.message}", e)
                                Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        binding.subBt.setOnClickListener {
            // Update ViewModel fields from UI
            vm.ocrRes.value?.ocr_npr = binding.ocrExps.text.toString()
            vm.ocrRes.value?.meter_make = binding.meterMakes.text.toString()
            vm.ocrRes.value?.probe_npr = binding.probeExps.text.toString()
            vm.ocrRes.value?.field_exp = binding.fieldExps.text.toString()
            vm.ocrRes.value?.acc_id = vm.inp.value?.CONSUMER_NO.toString()
            vm.ocrRes.value?.meter_no = binding.mNoEt.text.toString()
            vm.ocrRes.value?.mr_sr_no = binding.mNoEt.text.toString()
            vm.ocrRes.value?.ocr_results = vm.ocrResults.value!!

            val results = vm.ocrRes.value?.ocr_results

            // Map updatedItems
            val updatedItems = results?.map {
                it.copy(
                    meter_reading = it.manual_reading.ifBlank { it.ocr_reading }
                )
            }
            val updatedItems2 = results?.map {
                it.copy(
                    meter_reading = it.manual_reading.ifBlank { it.ocr_reading },
                    ocr_reading = it.manual_reading.ifBlank { it.ocr_reading },
                    manual_reading = it.manual_reading.ifBlank { it.ocr_reading }
                )
            }

            // Prepare manual readings list
            val manReadings = mutableListOf<Triple<String,String,String>>()
            updatedItems?.filter { it.manual_reading.isNotEmpty() }?.forEach { res4 ->
                manReadings.add(Triple(res4.unit.toString(),res4.img_path, res4.manual_reading))
            }
            manReadings.add(Triple("14","", binding.meterMakes.text.toString()))
            manReadings.add(Triple("9","", binding.mNoEt.text.toString()))

            // Set read_type
            vm.ocrRes.value?.read_type =
                if (updatedItems?.filter { it.manual_reading.isNotEmpty() }!!.isNotEmpty())
                    "corrected_ocr" else "ocr"

            // Update ocr_results
            if (updatedItems2 != null) {
                vm.ocrRes.value?.ocr_results = updatedItems2
            } else {
                vm.ocrRes.value?.ocr_results = updatedItems!!
            }

            val isManualMode = binding.ocrExps.text.isNotEmpty()
            val haveCapturedImages = vm.capturedImages.value?.isNotEmpty() == true
            val manualCount = vm.ocrResults.value!!.filter { it.manual_reading.isNotEmpty() }.size
            val ocrCount = vm.ocrRes.value!!.ocr_results.filter { it.ocr_exception_code == 1 }.size
            val allReadingsTaken = (manualCount + ocrCount) >= vm.registers.value?.size!!

            when {
                // ✅ Check 1: Manual mode requires at least ONE captured image
                isManualMode && !haveCapturedImages -> {
                    Toast.makeText(mContext, "Capture at least one image!", Toast.LENGTH_LONG).show()
                }

                // ✅ Check 2: Manual mode requires at least ONE manual reading entered
                isManualMode && manualCount == 0 -> {
                    Toast.makeText(mContext, "Enter at least one manual reading!", Toast.LENGTH_LONG).show()
                }

                // ✅ Check 4: OCR mode requires ALL readings (OCR or manual)
                !isManualMode && !allReadingsTaken -> {
                    val emp = vm.ocrRes.value!!.ocr_results.find {
                        it.ocr_reading.isEmpty() && it.manual_reading.isEmpty()
                    }
                    Toast.makeText(mContext, "Enter ${emp?.register} value!", Toast.LENGTH_LONG).show()
                }

                // ✅ Check 5: OCR mode requires valid OCR readings (no exceptions > 1)
                !isManualMode && vm.ocrRes.value!!.ocr_results.any { it.ocr_exception_code > 1 } -> {
                    val emp = vm.ocrRes.value!!.ocr_results.find { it.ocr_exception_code > 1 }
                    Toast.makeText(mContext, "${emp?.ocr_exception_msg} in ${emp?.register} register", Toast.LENGTH_LONG).show()
                }

                // ✅ Check 6: Meter number required for OCR mode only
                !isManualMode && binding.mNoEt.text.isNullOrEmpty() -> {
                    Toast.makeText(mContext, "Enter Meter No", Toast.LENGTH_LONG).show()
                }

                else -> {
                    // ✅ FULLY OFFLINE SUBMISSION
                    saveCompleteDataOffline(manReadings)
                }
            }
        }
    }


    private fun saveCompleteDataOffline(manReadings: List<Triple<String, String, String>>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(mContext)

                // ✅ STEP 1: Generate or get local request ID
                val localReqId = vm.inp.value?.reqId ?: generateLocalReqId()
                vm.inp.value?.reqId = localReqId
                vm.ocrRes.value?.input?.reqId = localReqId

                Log.d("OFFLINE_SAVE", "🔹 Using Local Request ID: $localReqId")

                // ✅ STEP 2: Save OCR Request FIRST
                try {
                    val inp = vm.inp.value!!
                    val reqReadingValuesJson = Gson().toJson(inp.REQ_READING_VALUES)

                    val ocrRequestEntity = OcrRequestEntity(
                        id = 0,
                        localReqId = localReqId,
                        agencyId = inp.AGENCY_ID,
                        boardCode = inp.BOARD_CODE,
                        consumerNo = inp.CONSUMER_NO,
                        meterReaderId = inp.METER_READER_ID,
                        meterReaderMobile = inp.METER_READER_MOBILE_NO,
                        meterReaderName = inp.METER_READER_NAME,
                        subDivisionCode = inp.SUB_DIVISION_CODE,
                        subDivisionName = inp.SUB_DIVISION_NAME,
                        meterMake = vm.ocrRes.value?.meter_make ?: "",
                        reqReadingValues = reqReadingValuesJson,
                        isSynced = false,
                        readingDate = System.currentTimeMillis()
                    )

                    database.ocrRequestDao().insertRequest(ocrRequestEntity)
                    Log.d("OFFLINE_SAVE", "✅ OCR Request saved")
                } catch (e: Exception) {
                    Log.e("OFFLINE_SAVE", "❌ OCR Request failed: ${e.message}")
                }

                // ✅ STEP 3: Save ALL images with meter readings
                val capturedImages = vm.capturedImages.value
                var savedCount = 0

                if (!capturedImages.isNullOrEmpty()) {
                    capturedImages.forEach { (position, bitmap) ->
                        try {
                            val ocrResult = vm.ocrResults.value?.get(position)
                            val unit = ocrResult?.unit ?: 1

                            // ✅ Save bitmap to file
                            val imagePath = saveBitmapToFile(
                                bitmap,
                                "img_${localReqId}_${position}_${System.currentTimeMillis()}.jpg"
                            )

                            val meterReadingEntity = MeterReadingEntity(
                                id = 0,
                                reading_data_time = getCurrentTimestamp(),
                                site_location = "${vm.ocrRes.value?.address} .",
                                ca_no = vm.inp.value?.CONSUMER_NO?.ifEmpty { "1" } ?: "1",
                                meter_model = ocrResult?.ocr_meter_make?.ifEmpty {
                                    vm.ocrRes.value?.meter_make
                                } ?: "",
                                image_path = imagePath,
                                meter_no = binding.mNoEt.text.toString().ifEmpty {
                                    ocrResult?.ocr_mno ?: ""
                                },
                                meter_reading = ocrResult?.manual_reading?.ifEmpty {
                                    ocrResult.ocr_reading
                                } ?: "",
                                mrn_text = ocrResult?.register ?: "",
                                lat_long = "${vm.ocrRes.value?.lat},${vm.ocrRes.value?.lng}",
                                address = "${vm.ocrRes.value?.address} .",
                                unit = unit.toString(),
                                meter_reader = vm.inp.value?.METER_READER_ID?.ifEmpty { "1" } ?: "1",
                                consumer = vm.inp.value?.CONSUMER_NO?.ifEmpty { "1" } ?: "1",
                                mru = "1",
                                isSynced = false,
                                agency = "1",
                                exception = "48",
                                location_type = "1",
                                ocr_unit = ocrResult?.ocr_unit ?: "",
                                location = "1",
                                created_at = System.currentTimeMillis(),
                                updated_at = System.currentTimeMillis(),
                                ocrReqId = localReqId,
                                usedUrl = "OFFLINE_OCR",
                                responseTime = System.currentTimeMillis().toString(),
                                ocrRequestId = localReqId.toString()
                            )
                            Log.d("DEBUG_SAVE", "Saving meter_no: '${meterReadingEntity.meter_no}'")
                            database.meterReadingDao().insertReading(meterReadingEntity)
                            savedCount++
                            Log.d("DEBUG_SAVE", "🔹 Image Path: $imagePath")
                            Log.d("DEBUG_SAVE", "🔹 Meter Reading: ${ocrResult?.manual_reading?.ifEmpty { ocrResult.ocr_reading }}")
                            Log.d("DEBUG_SAVE", "🔹 Unit: $unit")
                            Log.d("DEBUG_SAVE", "🔹 CA No: ${vm.inp.value?.CONSUMER_NO}")
                            Log.d("OFFLINE_SAVE", "✅ Meter Reading $position saved: ${ocrResult?.register}")

                        } catch (e: Exception) {
                            Log.e("OFFLINE_SAVE", "❌ Failed position $position: ${e.message}")
                        }
                    }
                }

                // ✅ STEP 4: Save Manual Readings (if manual mode)
                val isManualMode = binding.ocrExps.text.isNotEmpty()

                if (isManualMode) {
                    manReadings.forEach { (unit, path, reading) ->
                        try {
                            val unitName = when (unit) {
                                "1" -> "KWH"
                                "3" -> "KW"
                                "9" -> "MeterNo"
                                "13" -> "PF"
                                "14" -> "MeterMake"
                                else -> unit
                            }

                            // ✅ Check if already saved with image
                            val alreadySaved = capturedImages?.values?.any { bitmap ->
                                val pos = capturedImages.keys.first { capturedImages[it] == bitmap }
                                vm.ocrResults.value?.get(pos)?.unit.toString() == unit
                            } ?: false

                            if (!alreadySaved) {
                                val manualEntity = ManualReadingEntity(
                                    id = 0,
                                    reading = reading,
                                    image_path = path,
                                    unit = unitName,
                                    ocrRequestId = localReqId,
                                    isSynced = false
                                )
                                database.manualReadingDao().insertManualReading(manualEntity)
                                Log.d("OFFLINE_SAVE", "✅ Extra manual: $unitName = $reading")
                            }
                        } catch (e: Exception) {
                            Log.e("OFFLINE_SAVE", "❌ Extra manual failed: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mContext,
                        "✅ Saved offline! ($savedCount readings)\nSync when online.",
                        Toast.LENGTH_LONG
                    ).show()

                    // ✅ Return result
                    val resultData = mapOf(
                        "status" to "success",
                        "message" to "Data saved locally",
                        "local_req_id" to localReqId,
                        "saved_count" to savedCount,
                        "consumer_no" to vm.inp.value?.CONSUMER_NO,
                        "meter_no" to vm.ocrRes.value?.meter_no,
                        "meter_make" to vm.ocrRes.value?.meter_make,
                        "meter_status" to vm.ocrRes.value?.meter_status,
                        "readings" to vm.ocrResults.value?.map { result ->
                            mapOf(
                                "register" to result.register,
                                "reading" to (result.manual_reading.ifEmpty {
                                    result.ocr_reading
                                }),
                                "unit" to result.unit
                            )
                        }
                    )

                    val resultJson = Gson().toJson(resultData)
                    OcrLauncher.sendResultBack(resultJson)
                    navController.navigateUp()
                }

            } catch (e: Exception) {
                Log.e("OFFLINE_SAVE", "❌ Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mContext,
                        "Error saving: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): String {
        val file = File(mContext.filesDir, fileName)
        file.createNewFile()

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        val bitmapData = bos.toByteArray()

        FileOutputStream(file).use { fos ->
            fos.write(bitmapData)
            fos.flush()
        }

        return file.absolutePath
    }

    private fun proceedWithSubmission(manReadings: List<Triple<String, String, String>>) {
        val isManualMode = binding.ocrExps.text.isNotEmpty()

        if (isManualMode) {
            // ✅ Manual mode: save locally only
            saveManualReadingLocally(manReadings)
            Toast.makeText(mContext, "Manual readings saved locally ✓", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ OCR mode: save OCR data to local DB (no OCR re-run)
        saveOcrDataToLocal()
        Toast.makeText(mContext, "OCR readings saved locally ✓", Toast.LENGTH_SHORT).show()

        // ✅ Optionally, later you can sync this data to server if needed
        // e.g. by calling a separate upload coroutine
    }


    private fun saveManualReadingLocally(manReadings: List<Triple<String, String, String>>) {
        manReadings.forEach { (unit, path, reading) ->
            saveManualReadingToLocal(0, reading, unit, path)
        }
        saveOcrDataToLocal()
    }

    class OcrResultsAdapter(
        private val readings: List<OcrResult>,
        private val vm: OcrViewModel,
        private val navController: NavController,
        private val binding: MainFragBinding


    ) : RecyclerView.Adapter<OcrResultsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val unitText: TextView = view.findViewById(R.id.unit)
            val valueText: TextView = view.findViewById(R.id.unitValueTv)
            val mnoText: TextView = view.findViewById(R.id.mnTv)
            val makeTv: TextView = view.findViewById(R.id.meterMakeTv)
            val readingLL: LinearLayout = view.findViewById(R.id.ocrReadingLL)
            val manReadingEt: EditText = view.findViewById(R.id.manValueEt)
            val expText: TextView = view.findViewById(R.id.unitExpTv)
            val readingImage: ImageView = view.findViewById(R.id.unitIv)
            val unitCard: CardView = view.findViewById(R.id.unitCard)

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ocr_result, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = readings.size
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = readings[position]
            holder.unitText.text = if(item.register.contains("#")) "Meter No." else item.register
            if (binding.TDMRRb.isChecked || binding.IDFRb.isChecked) {
                Log.d("OCR>>", "HERE>>2" + vm.ocrRes.value!!.ocr_npr)

                holder.manReadingEt.visibility = View.VISIBLE

            } else if (vm.ocrRes.value!!.ocr_npr.isNotEmpty()) {
                holder.manReadingEt.visibility = View.VISIBLE

            } else {
                holder.manReadingEt.visibility = View.GONE

            }
            holder.manReadingEt.setText(vm.ocrResults.value?.get(holder.adapterPosition)?.manual_reading)

            holder.valueText.text = if(item.ocr_unit=="9") item.ocr_mno else item.ocr_reading
            holder.expText.text =
                if (item.ocr_exception_code == 22) item.ocr_exception_msg + " (" + item.ocr_unit + ")" else if (item.ocr_exception_code==48) "" else item.ocr_exception_msg
            holder.makeTv.text = item.ocr_meter_make
            holder.mnoText.text = if (item.ocr_mno.isEmpty()) "" else "# ${item.ocr_mno}"
            if (item.ocr_exception_code >= 1) {
                holder.readingLL.visibility = View.VISIBLE
                if (item.ocr_exception_code > 1) {
                    holder.expText.visibility = View.VISIBLE
                }
            }
            holder.manReadingEt.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    // Called *before* the text changes
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Called *while* the text is changing
                }

                override fun afterTextChanged(s: Editable?) {
                    // Called *after* the text has changed
                    vm.ocrResults.value?.get(holder.adapterPosition)?.manual_reading = s.toString()
                }
            })
            if (binding.OKRb.isChecked) {
                binding.ocrExps.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                        // Called *before* the text changes
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        // Called *while* the text is changing
                    }

                    override fun afterTextChanged(s: Editable?) {
                        // Called *after* the text has changed
                        if (s.toString().isEmpty()) {
                            holder.manReadingEt.visibility = View.GONE

                        } else {
                            Log.d("OCR>>", "HERE>>3" + vm.ocrRes.value!!.ocr_npr)
                            holder.manReadingEt.visibility = View.VISIBLE
                        }
                    }
                })

            } else {
                Log.d("OCR>>", "HERE>>4" + vm.ocrRes.value!!.ocr_npr)

                holder.manReadingEt.visibility = View.VISIBLE

            }




            if (item.img_path.isNotEmpty()) {
                val url = item.img_path.replace(
                    "/home/gescom/be/gescom_backend/src/ocr/images_power/",
                    "https://api.vidyut-suvidha.in/images/"
                )
                Glide.with(holder.itemView.context).load(url).into(holder.readingImage)
            }
            holder.unitCard.setOnClickListener {

                vm.curType.value = position
                vm.ocrRes.value?.meter_make = binding.meterMakes.text.toString()
                vm.ocrRes.value?.meter_no = binding.mNoEt.text.toString()
                vm.ocrRes.value?.probe_npr = binding.probeExps.text.toString()
                vm.ocrRes.value?.ocr_npr = binding.ocrExps.text.toString()
                vm.ocrRes.value?.field_exp = binding.fieldExps.text.toString()
                Log.d("SENT>>",binding.ocrExps.text.toString())
                try {
                    navController.navigate(R.id.action_mainFrag_to_captureFrag)

                }
                catch (e:Exception){

                }
            }

            // ✅ CONFIRMED CORRECT - This logic is already perfect:
            val capturedBitmap = vm.capturedImages.value?.get(position)
            if (capturedBitmap != null) {
                // Show captured image (highest priority)
                holder.readingImage.setImageBitmap(capturedBitmap)
                holder.readingImage.visibility = View.VISIBLE
            } else if (item.img_path.isNotEmpty()) {
                // Show API image exactly like old code
                val url = item.img_path.replace(
                    "home/gescom/be/gescom_backend/src/ocr/images_power/",
                    "https://api.vidyut-suvidha.in/images/"
                )
                Glide.with(holder.itemView.context).load(url).into(holder.readingImage)
                holder.readingImage.visibility = View.VISIBLE
            }


        }
    }

    private fun saveManualReadingToLocal(
        apiId: Int,
        reading: String,
        unit: String,
        imgPath: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("LOCAL_DB", "💾 Saving manual reading to local database...")

                val database = AppDatabase.getDatabase(mContext)

                // ✅ CRITICAL FIX: Convert unit ID to NAME before saving
                val unitName = when (unit) {
                    "1" -> "KWH"
                    "3" -> "KW"
                    "9" -> "MeterNo"
                    "13" -> "PF"
                    "14" -> "MeterMake"
                    else -> unit // Keep original if unknown
                }

                // Create ManualReadingEntity with unit NAME
                val manualReadingEntity = ManualReadingEntity(
                    id = 0,
                    reading = reading,
                    image_path = imgPath,
                    unit = unitName, // ✅ Save NAME not ID
                    ocrRequestId = vm.inp.value?.reqId ?: 0,
                    isSynced = false
                )

                database.manualReadingDao().insertManualReading(manualReadingEntity)

                Log.d("LOCAL_DB", "✅ Manual reading saved: unit=$unitName, reading=$reading")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mContext,
                        "✔ Manual reading saved locally",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("LOCAL_DB", "❌ Failed to save manual reading: ${e.message}", e)
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mContext,
                        "Failed to save manual: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ✅ Also update the createManualReading function
    fun createManualReading(
        context: Context,
        unit: String,
        imgPath: String,
        reading: String,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        // ✅ Convert unit ID to NAME
        val unitName = when (unit) {
            "1" -> "KWH"
            "3" -> "KW"
            "9" -> "MeterNo"
            "13" -> "PF"
            "14" -> "MeterMake"
            else -> unit
        }

        val payload = mapOf(
            "reading" to reading,
            "image_path" to imgPath,
            "unit" to unitName // ✅ Send NAME
        )

        Log.d("REQ>>", payload.toString())

        OcrRetrofitClient.getApiService().createManualReading(payload)
            .enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(
                    call: Call<Map<String, Any>>,
                    response: Response<Map<String, Any>>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let { result ->
                            val manualReadingId = (result["id"] as Double).toInt()
                            vm.ocrRes.value?.manual_readings?.add(
                                ManualReading(manualReadingId, reading, unitName)
                            )
                            Log.d("RESP>>", result.toString())

                            // Save to local with NAME
                            saveManualReadingToLocal(manualReadingId, reading, unitName, imgPath)

                            onSuccess(manualReadingId)
                        }
                    } else {
                        val err = response.errorBody()?.string() ?: "Unknown error"
                        Log.d("ERR>>", err)
                        onError(err)
                    }
                }

                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    Log.e("ManualReading>>", "Failure: ${t.message}", t)
                    onError(t.message ?: "Network error")
                }
            })
    }


    private fun saveOcrDataToLocal() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("LOCAL_DB", "📄 Saving OCR data to local database...")

                val database = AppDatabase.getDatabase(mContext)
                val ocrResults = vm.ocrResults.value ?: return@launch

                var savedCount = 0

                ocrResults.forEachIndexed { index, ocrResult ->
                    // Skip if no data
                    if (ocrResult.ocr_reading.isEmpty() && ocrResult.manual_reading.isEmpty()) {
                        Log.d("LOCAL_DB", "⏭️ Skipping ${ocrResult.register} - no data")
                        return@forEachIndexed
                    }

                    // Create MeterReadingEntity
                    val meterReadingEntity = MeterReadingEntity(
                        id = 0,
                        reading_data_time = getCurrentTimestamp(),
                        site_location = "${vm.ocrRes.value?.address} .",
                        ca_no = vm.inp.value?.CONSUMER_NO?.ifEmpty { "1" } ?: "1",
                        meter_model = ocrResult.ocr_meter_make.ifEmpty { vm.ocrRes.value?.meter_make ?: "" },
                        image_path = ocrResult.img_path,
                        meter_no = ocrResult.ocr_mno,
                        meter_reading = ocrResult.manual_reading.ifEmpty { ocrResult.ocr_reading },
                        mrn_text = ocrResult.register,
                        lat_long = "${vm.ocrRes.value?.lat},${vm.ocrRes.value?.lng}",
                        address = "${vm.ocrRes.value?.address} .",
                        unit = ocrResult.unit.toString(),
                        meter_reader = vm.inp.value?.METER_READER_ID?.ifEmpty { "1" } ?: "1",
                        consumer = vm.inp.value?.CONSUMER_NO?.ifEmpty { "1" } ?: "1",
                        mru = "1",
                        isSynced = false,
                        agency = "1",
                        exception = "48", // ✅ FIX: Always "1"
                        location_type = "1",
                        ocr_unit = ocrResult.ocr_unit,
                        location = "1",
                        created_at = System.currentTimeMillis(),
                        updated_at = System.currentTimeMillis(),
                        ocrReqId = vm.inp.value?.reqId ?: 0, // ✅ FIX: Server reqId
                        usedUrl = "OFFLINE_OCR",
                        responseTime = System.currentTimeMillis().toString(),
                        ocrRequestId = vm.inp.value?.reqId?.toString() ?: "0" // ✅ FIX: Server reqId as string
                    )
                    Log.d("DEBUG_SAVE", "Saving meter_no: '${meterReadingEntity.meter_no}'")
                    // Insert into database
                    database.meterReadingDao().insertReading(meterReadingEntity)
                    savedCount++
                    Log.d("LOCAL_DB", "✅ Saved: ${ocrResult.register} = ${ocrResult.ocr_reading}")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mContext,
                        "✅ Saved $savedCount readings locally",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("LOCAL_DB", "❌ Failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mContext,
                        "Failed to save: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun fetchMeterReadingExceptions(context: Context) {
        OcrRetrofitClient.getApiService().getMeterReadingExceptions(OcrRetrofitClient.AUTH_TOKEN)
            .enqueue(object : Callback<List<MeterReadingExceptionsResItem>> {
                override fun onResponse(
                    call: Call<List<MeterReadingExceptionsResItem>>,
                    response: Response<List<MeterReadingExceptionsResItem>>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let { exceptions ->
                            ocrExps = exceptions
                            Log.e("Exps>>", "Success: ${exceptions.size} exceptions")

                            val probeExps = exceptions.filter { it.level == 3 }.map { it.name }
                            val fieldExps = exceptions.filter { it.level == 4 }.map { it.name }
                            val ocrExpsList = exceptions.filter { it.level == 5 }.map { it.name }

                            // ✅ FIXED: Use explicit String type for ArrayAdapter
                            val probeAdapter = ArrayAdapter<String>(
                                mContext,
                                android.R.layout.simple_dropdown_item_1line,
                                probeExps
                            )
                            val fieldAdapter = ArrayAdapter<String>(
                                mContext,
                                android.R.layout.simple_dropdown_item_1line,
                                fieldExps
                            )
                            val ocrAdapter = ArrayAdapter<String>(
                                mContext,
                                android.R.layout.simple_dropdown_item_1line,
                                ocrExpsList
                            )

                            binding.probeExps.setAdapter(probeAdapter)
                            binding.fieldExps.setAdapter(fieldAdapter)
                            binding.ocrExps.setAdapter(ocrAdapter)

                            binding.probeExps.setOnClickListener { binding.probeExps.showDropDown() }
                            binding.ocrExps.setOnClickListener { binding.ocrExps.showDropDown() }
                            binding.fieldExps.setOnClickListener { binding.fieldExps.showDropDown() }

                            binding.probeExps.threshold = 1
                            binding.fieldExps.threshold = 1
                            binding.ocrExps.threshold = 1

                            setupExceptionListeners(ocrExpsList)
                        }
                    } else {
                        Log.e("Exps>>", "Error: ${response.code()}")
                        Toast.makeText(mContext, "Failed to load exceptions", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<MeterReadingExceptionsResItem>>, t: Throwable) {
                    Log.e("Exps>>", "Failure: ${t.message}", t)
                    Toast.makeText(mContext, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // Helper function for exception listeners
    private fun setupExceptionListeners(ocrExpsList: List<String>) {
        binding.ocrExps.setOnItemClickListener { _, _, _, _ ->
            vm.ocrResults.value?.clear()
            vm.registers.value?.forEach { reg ->
                val unit = ocrUnits.find {
                    it.name.equals(reg, ignoreCase = true)
                }?.id
                val ocrResult = OcrResult("", "", "", "", 0, "", "", "", "", unit, "", "", 0, reg)
                vm.ocrResults.value?.add(ocrResult)
            }
            vm.ocrRes.value?.ocr_results = vm.ocrResults.value!!
        }

        if (binding.ocrExps.text.isEmpty()) {
            binding.manBt.text = "Make OCR correction"
            binding.getDataBt.text = "Get OCR Data"
        } else {
            binding.manBt.visibility = View.GONE
            binding.subBt.visibility = View.GONE
            binding.getDataBt.text = "Submit"
        }

        setupManualButton(ocrExpsList)
    }

    private fun setupManualButton(ocrExpsList: List<String>) {
        binding.manBt.setOnClickListener {
            if (binding.ocrExps.text.isEmpty()) {
                binding.manBt.visibility = View.GONE
                binding.subBt.visibility = View.GONE
                binding.ocrExps.setText(ocrExpsList[0])
                binding.getDataBt.text = "Submit"

                val tmp = vm.ocrResults.value.orEmpty()

                val newList = vm.registers.value.orEmpty().map { reg ->
                    val unit = ocrUnits.find { it.name.equals(reg, ignoreCase = true) }?.id
                    val matching = tmp.find { it.register.equals(reg, ignoreCase = true) }

                    OcrResult(
                        address = matching?.address ?: "",
                        img_path = matching?.img_path ?: "",
                        lat_long = matching?.lat_long ?: "",
                        manual_reading = if (matching?.ocr_exception_code == 1)
                            matching.ocr_reading else "",
                        ocr_exception_code = 1,
                        ocr_exception_msg = "",
                        ocr_reading = matching?.ocr_reading ?: "",
                        meter_reading = "",
                        ocr_unit = matching?.ocr_unit ?: "",
                        unit = unit,
                        ocr_mno = matching?.ocr_mno ?: "",
                        ocr_meter_make = matching?.ocr_meter_make ?: "",
                        ocr_ref_id = matching?.ocr_ref_id ?: 0,
                        register = reg
                    )
                }

                vm.ocrResults.value = newList.toMutableList()
                Log.d("MANRES>>final", vm.ocrResults.value.toString())
            } else {
                binding.manBt.text = "Take Manual reading"
                binding.getDataBt.text = "Get OCR Data"
                binding.ocrExps.setText("")

                vm.ocrResults.value?.clear()
                vm.registers.value?.forEach { reg ->
                    val unit = ocrUnits.find {
                        it.name.equals(reg, ignoreCase = true)
                    }?.id
                    val ocrResult = OcrResult("", "", "", "", 0, "", "", "", "", unit, "", "", 0, reg)
                    vm.ocrResults.value?.add(ocrResult)
                }
            }

            vm.ocrRes.value?.ocr_results = vm.ocrResults.value!!

            val adapter1 = OcrResultsAdapter(
                vm.ocrResults.value?.toList()!!,
                vm,
                navController,
                binding
            )
            binding.readingsRecyclerView.layoutManager = LinearLayoutManager(context)
            binding.readingsRecyclerView.adapter = adapter1
            binding.scrollView.scrollTo(0, 0)
        }
    }

    fun fetchMeterReadingMakes(context: Context) {
        OcrRetrofitClient.getApiService().getMeterMakes(OcrRetrofitClient.AUTH_TOKEN)
            .enqueue(object : Callback<List<MeterMakesResItem>> {
                override fun onResponse(
                    call: Call<List<MeterMakesResItem>>,
                    response: Response<List<MeterMakesResItem>>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let { makes ->
                            meterMakes = MeterMakesRes().apply { addAll(makes) }
                            Log.e("Makes>>", "Success: ${makes.size} makes")

                            val meterMakesStrs = makes.filter { it.org == 3 }.map { it.name }

                            // ✅ FIXED: Use explicit String type for ArrayAdapter
                            val mmAdapter = ArrayAdapter<String>(
                                mContext,
                                android.R.layout.simple_dropdown_item_1line,
                                meterMakesStrs
                            )

                            binding.meterMakes.setAdapter(mmAdapter)
                            binding.meterMakes.setOnClickListener {
                                binding.meterMakes.showDropDown()
                            }

                            val index = meterMakesStrs.indexOfFirst {
                                it == vm.inp.value?.METER_MAKE.toString()
                            }
                            if (index >= 0) {
                                binding.meterMakes.setText(meterMakesStrs[index], false)
                            }

                            binding.meterMakes.threshold = 1
                        }
                    } else {
                        Log.e("Makes>>", "Error: ${response.code()}")
                        Toast.makeText(mContext, "Failed to load meter makes", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<MeterMakesResItem>>, t: Throwable) {
                    Log.e("Makes>>", "Failure: ${t.message}", t)
                    Toast.makeText(mContext, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }



    fun createManualReadingSequential(
        context: Context,
        readings: List<Triple<String, String, String>>,
        onComplete: () -> Unit
    ) {
        var index = 0

        fun createNext() {
            if (index >= readings.size) {
                onComplete()
                return
            }

            val (unit, path, reading) = readings[index]
            createManualReading(context, unit, path, reading,
                onSuccess = {
                    index++
                    createNext()
                },
                onError = {
                    index++
                    createNext() // Continue even on error
                }
            )
        }

        createNext()
    }


    fun postReaderTracking(context: Context, payload: JSONObject?, res: String) {
        Log.d("TRACK>>req", payload.toString())

        // Convert JSONObject to Map
        val payloadMap = mutableMapOf<String, Any>()
        payload?.keys()?.forEach { key ->
            payloadMap[key] = payload.get(key)
        }

        OcrRetrofitClient.getApiService().postReaderTracking(payloadMap)
            .enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(
                    call: Call<Map<String, Any>>,
                    response: Response<Map<String, Any>>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Submitted successfully", Toast.LENGTH_SHORT).show()
                        OcrLauncher.sendResultBack(res)
                        requireActivity().finish()
                    } else {
                        val err = response.errorBody()?.string() ?: "Unknown error"
                        Log.d("ERR>>", err)
                        Toast.makeText(
                            context,
                            "Failed to submit: ${response.message()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    Log.e("Tracking>>", "Failure: ${t.message}", t)
                    Toast.makeText(
                        context,
                        "Failed to submit: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }
    fun createOcrRequest() {
        val inp = vm.inp.value!!

        // ✅ Generate local reqId instead of API call
        val localReqId = generateLocalReqId()

        vm.ocrRes.value?.input?.reqId = localReqId
        vm.inp.value?.reqId = localReqId

        // ✅ Update TextView immediately with LOCAL ID
        binding.verTv.text = "Req$localReqId"

        Log.d("REQ>>", "Local Request ID created: $localReqId")

        // ✅ Don't save to DB here - will save on submit button
        Toast.makeText(mContext, "Request ID: $localReqId", Toast.LENGTH_SHORT).show()
    }

    fun fetchMeterReadingUnits(context: Context) {
        Log.e("Units>>", "Fetching...")

        OcrRetrofitClient.getApiService().getMeterReadingUnits(OcrRetrofitClient.AUTH_TOKEN)
            .enqueue(object : Callback<List<MeterReadingUnitsResItem>> {
                override fun onResponse(
                    call: Call<List<MeterReadingUnitsResItem>>,
                    response: Response<List<MeterReadingUnitsResItem>>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let { units ->
                            ocrUnits = units // ✅ FIXED: Direct assignment
                            Log.e("Units>>", "Success: ${units.size} units")
                        }
                    } else {
                        Log.e("Units>>", "Error: ${response.code()}")
                        Toast.makeText(mContext, "Failed to load units", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<MeterReadingUnitsResItem>>, t: Throwable) {
                    Log.e("Units>>", "Failure: ${t.message}", t)
                    Toast.makeText(mContext, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getSignalStrength(context: Context): Int? {
        val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo
        if (cellInfoList != null) {
            for (cellInfo in cellInfoList) {
                if (cellInfo.isRegistered) { // currently connected cell
                    val signalStrength: CellSignalStrength = when (cellInfo) {
                        is CellInfoLte -> cellInfo.cellSignalStrength
                        is CellInfoGsm -> cellInfo.cellSignalStrength
                        is CellInfoCdma -> cellInfo.cellSignalStrength
                        is CellInfoWcdma -> cellInfo.cellSignalStrength
                        is CellInfoNr -> cellInfo.cellSignalStrength // 5G
                        else -> continue
                    }
                    return signalStrength.dbm // e.g., -85 dBm
                }
            }
        }
        return null
    }

    fun uploadAllImagesSequentially(
        context: Context,
        images: List<Pair<Int, Bitmap>>,
        onComplete: () -> Unit
    ) {
        var index = 0

        fun uploadNext() {
            if (index >= images.size) {
                onComplete()
                return
            }

            val (position, bitmap) = images[index]
            val unit = ocrUnits.find {
                it.name.equals(
                    vm.registers.value?.get(position),
                    ignoreCase = true
                )
            }?.id

            uploadMeterImageForPosition(context, bitmap, position, unit ?: 1,
                onSuccess = {
                    Log.e("UploadImage", "Success for position: $position")
                    index++
                    uploadNext()
                },
                onError = { error ->
                    Log.e("UploadImage", "Error for position $position: $error")
                    index++
                    uploadNext()
                }
            )
        }

        uploadNext()
    }

    fun uploadMeterImageForPosition(
        context: Context,
        bp: Bitmap,
        position: Int,
        unit: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Convert bitmap to file
        val file = bitmapToFile(bp, "meter_image_${position}_${System.currentTimeMillis()}.jpg")
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val params = mapOf(
            "reading_date_time" to getCurrentTimestamp(),
            "site_location" to "${vm.ocrRes.value?.address} .",
            "ca_no" to (vm.inp.value?.CONSUMER_NO?.ifEmpty { "1" } ?: "1"),
            "image_path" to "DT 1",
            "meter_no" to "DT 1",
            "meter_reading" to "DT 1",
            "lat_long" to "${vm.ocrRes.value?.lat},${vm.ocrRes.value?.lng}",
            "address" to "${vm.ocrRes.value?.address} .",
            "unit" to unit.toString(),
            "meter_reader" to (vm.inp.value?.METER_READER_ID?.ifEmpty { "1" } ?: "1"),
            "consumer" to (vm.inp.value?.CONSUMER_NO?.ifEmpty { "1" } ?: "1"),
            "mru" to "1",
            "exception" to "48",
            "meter_model" to vm.ocrRes.value!!.meter_make,
            "location_type" to "1",
            "location" to "1",
            "agency" to "1"
        )

        val requestBodies = params.mapValues {
            it.value.toRequestBody("text/plain".toMediaTypeOrNull())
        }

        OcrRetrofitClient.getApiService().uploadMeterImage(
            token = OcrRetrofitClient.AUTH_TOKEN,
            file = filePart,
            readingDateTime = requestBodies["reading_date_time"]!!,
            siteLocation = requestBodies["site_location"]!!,
            caNo = requestBodies["ca_no"]!!,
            imagePath = requestBodies["image_path"]!!,
            meterNo = requestBodies["meter_no"]!!,
            meterReading = requestBodies["meter_reading"]!!,
            latLong = requestBodies["lat_long"]!!,
            address = requestBodies["address"]!!,
            unit = requestBodies["unit"]!!,
            meterReader = requestBodies["meter_reader"]!!,
            consumer = requestBodies["consumer"]!!,
            mru = requestBodies["mru"]!!,
            exception = requestBodies["exception"]!!,
            meterModel = requestBodies["meter_model"]!!,
            locationType = requestBodies["location_type"]!!,
            location = requestBodies["location"]!!,
            agency = requestBodies["agency"]!!,
            accept = requestBodies["accept"]!!,
            ocrRequestId = requestBodies["ocrRequestId"]!!,
        ).enqueue(object : Callback<UploadMeterReadingImageRes> {
            override fun onResponse(
                call: Call<UploadMeterReadingImageRes>,
                response: Response<UploadMeterReadingImageRes>
            ) {
                if (response.isSuccessful) {
                    response.body()?.let { uploadRes ->
                        Log.d("RESP>>", "Success: $uploadRes")
                        Log.d("MNO>>$position", uploadRes.meter_no)

                        // Update the specific ocrResult for this position
                        vm.ocrResults.value?.get(position)?.apply {
                            ocr_reading = uploadRes.meter_reading
                            img_path = uploadRes.image_path.replace(
                                "/home/gescom/be/gescom_backend/src/ocr/images_power/",
                                "https://api.vidyut-suvidha.in/images/"
                            )
                            ocr_exception_code = uploadRes.exception.toInt()
                            // ✅ FIXED: Use the list properly
                            ocr_exception_msg = ocrExps.find { expItem ->
                                expItem.id == uploadRes.exception.toInt()
                            }?.name ?: ""
                            ocr_mno = uploadRes.meter_no.toString()
                            ocr_meter_make = if (uploadRes.meter_model.isNullOrEmpty()) ""
                            else uploadRes.meter_model
                            ocr_unit = uploadRes.ocr_unit.toString()
                            this.unit = unit
                            ocr_ref_id = uploadRes.id
                            lat_long = "${vm.ocrRes.value?.lat},${vm.ocrRes.value?.lng}"
                            address = vm.ocrRes.value?.address.toString()
                        }

                        onSuccess()
                    }
                } else {
                    val error = "Error: ${response.code()} - ${response.errorBody()?.string()}"
                    Log.e("Upload>>", error)
                    onError(error)
                }

                // Clean up temp file
                file.delete()
            }
            override fun onFailure(call: Call<UploadMeterReadingImageRes>, t: Throwable) {
                Log.e("Upload>>", "Failure: ${t.message}", t)

                val errorMessage = when (t) {
                    is SocketTimeoutException -> "Request timed out"
                    is UnknownHostException -> "No internet connection"
                    else -> t.message ?: "Unknown error"
                }

                onError(errorMessage)

                // Clean up temp file
                file.delete()
            }
        })
    }

    private fun bitmapToFile(bitmap: Bitmap, fileName: String): File {
        val file = File(mContext.cacheDir, fileName)
        file.createNewFile()

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        val bitmapData = bos.toByteArray()

        FileOutputStream(file).use { fos ->
            fos.write(bitmapData)
            fos.flush()
        }

        return file
    }

    fun getCurrentTimestamp(): String {
        val date = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }
    private fun uploadAllCapturedImages(
        images: Map<Int, Bitmap>,
        onComplete: (Boolean) -> Unit
    ) {
        var processedCount = 0
        val totalImages = images.size
        var hasError = false

        images.forEach { (position, bitmap) ->
            val unit = ocrUnits.find {
                it.name.equals(vm.registers.value?.get(position), ignoreCase = true)
            }?.id ?: 1

            uploadMeterImageForPosition(mContext, bitmap, position, unit,
                onSuccess = {
                    processedCount++
                    if (processedCount == totalImages) {
                        onComplete(!hasError)
                    }
                },
                onError = { error ->
                    hasError = true
                    processedCount++
                    Log.e("UploadError", "Position $position: $error")
                    if (processedCount == totalImages) {
                        onComplete(!hasError)
                    }
                }
            )
        }
    }

    fun removeManualData(jsonString: String, mContext: Context): String {
        var cleanedJson = jsonString
        try {
            val root = JSONObject(jsonString)

            // Remove manual_readings from root
            root.remove("manual_readings")

            // Remove manual_reading from each object in ocr_results
            if (root.has("ocr_results")) {
                val ocrResults = root.getJSONArray("ocr_results")
                for (i in 0 until ocrResults.length()) {
                    val item = ocrResults.getJSONObject(i)
                    if(item.get("ocr_unit")=="1") item.put("ocr_unit","KWH")
                    if(item.get("ocr_unit")=="3") item.put("ocr_unit","KW")
                    if(item.get("ocr_unit")=="13") item.put("ocr_unit","PF")

                    item.remove("manual_reading")
                }
            }

            cleanedJson = root.toString()
        } catch (e: Exception) {
            Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        return cleanedJson
    }

}
