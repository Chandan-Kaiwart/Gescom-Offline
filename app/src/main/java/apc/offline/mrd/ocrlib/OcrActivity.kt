package apc.offline.mrd.ocrlib

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import apc.offline.mrd.R
import apc.offline.mrd.databinding.ActivityOcrBinding
import apc.offline.mrd.ocrlib.dataClasses.request.OcrRequest
import apc.offline.mrd.ocrlib.util.PermissionHelper
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.gson.Gson
import java.util.Locale

class OcrActivity : AppCompatActivity() {

    private lateinit var perms: Array<String>
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var permissionHelper: PermissionHelper
    private var latitude: String = ""
    private var longitude: String = ""
    private lateinit var navController: NavController
    private lateinit var binding: ActivityOcrBinding
    private val vm: OcrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_ocr)

        // Fix: Use NavHostFragment to get NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.setGraph(R.navigation.navigation)

        val inputs = intent.getStringExtra("input")
        vm.inp.value = Gson().fromJson(inputs, OcrRequest::class.java)
        vm.accId.value = vm.inp.value?.CONSUMER_NO.toString()
        vm.registers.value = vm.inp.value?.REQ_READING_VALUES?.toMutableList()

        Log.d("LIST>>", vm.registers.value.toString())
        Log.d("SDK>>", inputs.toString())

        permissionHelper = PermissionHelper(this)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        perms = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
            )
        }

        if (!permissionHelper.checkPermissions(perms)) {
            permissionHelper.requestPermissions(perms, 100)
        } else {
            getLastLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation()
        } else {
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        Log.d("LOC>>", "lastLocation: ")
        if (isLocationEnabled) {
            try {
                mFusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    val location = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        latitude = location.latitude.toString()
                        longitude = location.longitude.toString()
                        vm.lat.value = latitude
                        vm.lng.value = longitude
                        Log.d("LAT>>>1", latitude)
                        Log.d("LONG>>>", longitude)

                        try {
                            val geocoder = Geocoder(this, Locale.getDefault())
                            val addresses: List<Address> = geocoder.getFromLocation(
                                location.latitude,
                                location.longitude,
                                1
                            ) ?: emptyList()

                            if (addresses.isNotEmpty()) {
                                val address = addresses[0].getAddressLine(0)
                                vm.address.value = address
                                Log.d("ADDRESS>>", address ?: "")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            showLocationSettingsRequest()
        }
    }

    private fun showLocationSettingsRequest() {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val locationSettingsRequest = builder.build()
        val settingsClient: SettingsClient = LocationServices.getSettingsClient(this)
        val task = settingsClient.checkLocationSettings(locationSettingsRequest)

        task.addOnSuccessListener(
            this,
            OnSuccessListener { _ ->
                Log.d("LOC>>", "All location settings are satisfied.")
                getLastLocation()
            })

        task.addOnFailureListener(this, OnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, 1001)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e("LOC>>", "Error starting location settings", sendEx)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 0
            numUpdates = 1
        }
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation = locationResult.lastLocation
            latitude = mLastLocation?.latitude.toString()
            longitude = mLastLocation?.longitude.toString()
            vm.lat.value = latitude
            vm.lng.value = longitude
        }
    }

    private val isLocationEnabled: Boolean
        get() {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    // ✅ DELETED createOcrRequest() - not needed, already in MainFrag
}