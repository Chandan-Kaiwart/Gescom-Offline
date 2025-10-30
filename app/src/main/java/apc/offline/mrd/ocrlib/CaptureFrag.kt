package apc.offline.mrd.ocrlib

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.SeekBar
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import apc.offline.mrd.R
import apc.offline.mrd.databinding.FragCaptureBinding
import apc.offline.mrd.ocrlib.network.OcrRetrofitClient
import apc.offline.mrd.ocrlib.network.OcrApiService
import apc.offline.mrd.ocrlib.dataClasses.MeterReadingExceptionsResItem
import apc.offline.mrd.ocrlib.dataClasses.MeterReadingUnitsRes
import apc.offline.mrd.ocrlib.dataClasses.MeterReadingUnitsResItem
import apc.offline.mrd.ocrlib.dataClasses.UploadMeterReadingImageRes
import apc.offline.mrd.ocrlib.dataClasses.response.OcrResult
import kotlin.collections.find
import apc.offline.mrd.ocrlib.util.ProgressDia
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import com.google.android.gms.location.LocationRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit


class CaptureFrag : Fragment() {

    private lateinit var mContext: Context
    private lateinit var navController: NavController
    private lateinit var binding: FragCaptureBinding
    private var ocrExps: List<MeterReadingExceptionsResItem> = emptyList()
    private var ocrUnits: List<MeterReadingUnitsResItem> = emptyList()
    private lateinit var imageCapture: ImageCapture
    private lateinit var pd: ProgressDia
    private var isTorchOn = false
    private lateinit var cameraProvider: ProcessCameraProvider
    private var latitude: String? = ""
    private var longitude: String? = ""
    private var location: String? = ""
    private var isManual = false
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    var rcpt: Bitmap? = null
    private val vm: OcrViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragCaptureBinding.inflate(inflater)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        // Fetch data using Retrofit
        fetchMeterReadingExceptions(mContext)
        fetchMeterReadingUnits(mContext)

        binding.caNumber.visibility = View.GONE
        val ca = System.currentTimeMillis().toString().substring(7)
        binding.caNumber.setText(ca)

        pd = ProgressDia()

        vm.ocrRes.observe(viewLifecycleOwner) { res ->
            isManual = res?.ocr_npr!!.isNotEmpty()
            binding.spinner.setText("Take ${vm.registers.value?.get(vm.curType.value!!)} value")
        }

        binding.btnBack.setOnClickListener {
            navController.navigateUp()
        }

        binding.capBt.setOnClickListener {
            lastLocation
            val ca = binding.caNumber.text.toString()

            if (ca.isEmpty()) {
                Toast.makeText(mContext, "Enter CA Number!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            try {
                var locationAddress = "Unknown Address"
                try {
                    val geocoder = Geocoder(mContext, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(
                        latitude!!.toDouble(),
                        longitude!!.toDouble(),
                        1
                    )

                    if (addresses != null && addresses.isNotEmpty()) {
                        locationAddress = addresses[0].getAddressLine(0)
                        location = addresses[0].locality
                    }
                } catch (e: Exception) {
                    Log.e("CaptureFrag", "Geocoding failed: ${e.message}")
                }

                val unit = ocrUnits.find {
                    it.name.equals(
                        vm.registers.value?.get(vm.curType.value!!),
                        ignoreCase = true
                    )
                }?.id ?: 1

                val croppedBitmap = cropImage(
                    binding.previewView.bitmap!!,
                    requireActivity().window.decorView.rootView,
                    if (unit == 9) binding.drawLL2 else binding.drawLL
                )

                val currentImages = vm.capturedImages.value ?: mutableMapOf()
                currentImages[vm.curType.value!!] = croppedBitmap
                vm.setCapturedImages(currentImages)

                val ocrResult = OcrResult(
                    address = locationAddress,
                    img_path = "",
                    lat_long = "$latitude,$longitude",
                    manual_reading = "",
                    meter_reading = "",
                    ocr_unit = unit.toString(),
                    ocr_mno = "",
                    register = vm.registers.value?.get(vm.curType.value!!) ?: "",
                    ocr_reading = "",
                    unit = unit,
                    ocr_meter_make = "",
                    ocr_exception_msg = "",
                    ocr_exception_code = 0,
                    ocr_ref_id = 0
                )

                vm.ocrResults.value?.set(vm.curType.value!!, ocrResult)
                vm.triggerOcrResultsUpdate()

                Toast.makeText(
                    mContext,
                    "✅ Image captured for ${vm.registers.value?.get(vm.curType.value!!)} (${currentImages.size}/${vm.registers.value?.size})",
                    Toast.LENGTH_SHORT
                ).show()

                navController.navigateUp()

            } catch (e: Exception) {
                Log.e("CaptureFrag", "Capture error: ${e.message}")
                Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.pb.visibility = View.GONE
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext)
        cameraProviderFuture = ProcessCameraProvider.getInstance(mContext)
        lastLocation

        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(mContext))

        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
                            Log.e("Exps>>", "Success: ${exceptions.size} exceptions loaded")
                        }
                    } else {
                        Log.e("Exps>>", "Error: ${response.code()} - ${response.message()}")
                        Toast.makeText(mContext, "Failed to load exceptions", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<MeterReadingExceptionsResItem>>, t: Throwable) {
                    Log.e("Exps>>", "Failure: ${t.message}", t)
                    Toast.makeText(mContext, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    //  Updated with Retrofit
    fun fetchMeterReadingUnits(context: Context) {
        Log.e("Units>>", "Fetching units...")

        OcrRetrofitClient.getApiService().getMeterReadingUnits(OcrRetrofitClient.AUTH_TOKEN)
            .enqueue(object : Callback<List<MeterReadingUnitsResItem>> {
                override fun onResponse(
                    call: Call<List<MeterReadingUnitsResItem>>,
                    response: Response<List<MeterReadingUnitsResItem>>
                ) {
                    if (response.isSuccessful) {
                        response.body()?.let { units ->
                            ocrUnits = units
                            Log.e("Units>>", "Success: ${units.size} units loaded")

                            val unit = ocrUnits.find {
                                it.name.equals(
                                    vm.registers.value?.get(vm.curType.value!!),
                                    ignoreCase = true
                                )
                            }?.id

                            if (unit == 9) {
                                binding.drawLL2.visibility = View.GONE
                                binding.drawLL.visibility = View.GONE
                            } else {
                                binding.drawLL2.visibility = View.GONE
                                binding.drawLL.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        Log.e("Units>>", "Error: ${response.code()} - ${response.message()}")
                        Toast.makeText(mContext, "Failed to load units", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<MeterReadingUnitsResItem>>, t: Throwable) {
                    Log.e("Units>>", "Failure: ${t.message}", t)
                    Toast.makeText(mContext, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // Helper function to convert Bitmap to File
    private fun bitmapToFile(bitmap: Bitmap, fileName: String): java.io.File {
        val file = java.io.File(mContext.cacheDir, fileName)
        file.createNewFile()

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        val bitmapData = bos.toByteArray()

        java.io.FileOutputStream(file).use { fos ->
            fos.write(bitmapData)
            fos.flush()
        }

        return file
    }
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview1: Preview = Preview.Builder().build()
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()

        val cameraSelector1: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview1.setSurfaceProvider(binding.previewView.surfaceProvider)
        val camera = cameraProvider.bindToLifecycle(
            this, cameraSelector1, preview1, imageCapture
        )

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                camera.cameraControl.setLinearZoom(progress / 100.toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBar.afterMeasured {
            val autoFocusPoint = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(.5f, .5f)
            try {
                val autoFocusAction = FocusMeteringAction.Builder(
                    autoFocusPoint, FocusMeteringAction.FLAG_AF
                ).apply {
                    Log.d("FOCUS>>>>", "bindPreview: ")
                    setAutoCancelDuration(5, TimeUnit.SECONDS)
                }.build()
                camera.cameraControl.startFocusAndMetering(autoFocusAction)
            } catch (e: CameraInfoUnavailableException) {
                Log.d("ERROR", "cannot access camera", e)
            }
        }

        binding.torchFab.setOnClickListener {
            if (camera.cameraInfo.hasFlashUnit()) {
                if (!isTorchOn) {
                    camera.cameraControl.enableTorch(true)
                    isTorchOn = true
                    binding.torchFab.setImageResource(R.drawable.ic_baseline_flashlight_off_24)
                } else {
                    camera.cameraControl.enableTorch(false)
                    isTorchOn = false
                    binding.torchFab.setImageResource(R.drawable.ic_baseline_flashlight_on_24)
                }
            }
        }
    }

    @get:SuppressLint("MissingPermission")
    private val lastLocation: Unit
        get() {
            if (isLocationEnabled) {
                mFusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    val location = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        latitude = location.latitude.toString()
                        longitude = location.longitude.toString()
                        Log.d("LAT>>>2", location.latitude.toString())
                        Log.d("LONG>>>", location.longitude.toString())
                    }
                }
            } else {
                Toast.makeText(mContext, "Please turn on your location...", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 5
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.myLooper()
        )
    }

    private val mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation = locationResult.lastLocation
        }
    }

    private val isLocationEnabled: Boolean
        get() {
            val locationManager = mContext.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    private fun cropImage(bitmap: Bitmap, rootView: View, smallFrame: View): Bitmap {
        val heightOriginal = rootView.height
        val widthOriginal = rootView.width
        val heightFrame = smallFrame.height
        val widthFrame = smallFrame.width
        val leftFrame = smallFrame.left
        val topFrame = smallFrame.top
        val heightReal = bitmap.height
        val widthReal = bitmap.width
        val widthFinal = widthFrame * widthReal / widthOriginal
        val heightFinal = heightFrame * heightReal / heightOriginal
        val leftFinal = leftFrame * widthReal / widthOriginal
        val topFinal = topFrame * heightReal / heightOriginal

        return Bitmap.createBitmap(
            bitmap, leftFinal, topFinal - 100, widthFinal, heightFinal + 100
        )
    }

    private inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    f()
                }
            }
        })
    }

    private fun getExecutor(): Executor {
        return ContextCompat.getMainExecutor(mContext)
    }

    fun getCurrentTimestamp(): String {
        val date = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }
}