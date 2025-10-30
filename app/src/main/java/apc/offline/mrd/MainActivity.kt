package apc.offline.mrd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import apc.offline.mrd.databinding.ActivityMainBinding
import apc.offline.mrd.network.MeterMake
import apc.offline.mrd.network.RetrofitClient
import apc.offline.mrd.ocrlib.OcrLauncher
import apc.offline.mrd.ocrlib.dataClasses.request.OcrRequest
import apc.offline.mrd.data.MeterViewModel
import apc.offline.mrd.data.entities.ManualReadingEntity
import apc.offline.mrd.data.sync.SyncActivity
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import apc.offline.mrd.ocrlib.OcrCallback
import apc.offline.mrd.ocrlib.util.InputDia

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var meterViewModel: MeterViewModel
    private lateinit var pd: InputDia
    private var meterMakes = mutableListOf<String>()
    private var env = 0 // 1 = Production, 0 = Testing

    private val bearerToken =
        "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG5fZG9lIiwic3ViIjoxMCwiaWF0IjoxNzQ2MTI3MjMxLCJleHAiOjE3NDY2NDU2MzF9.Aht12k9e_DvNLBf-kxpCka5SmlxTNvxfxdd_KvLu0aQ"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.syncBtn.setOnClickListener {
            val intent = Intent(this, SyncActivity::class.java)
            startActivity(intent)
        }

        meterViewModel = ViewModelProvider(this)[MeterViewModel::class.java]

        pd = InputDia()
        pd.show(supportFragmentManager, "progress")

        // Fetch meter makes
        fetchMeterReadingMakes(this)

        try {
            val json = intent.getStringExtra("inputs")
            env = intent.getIntExtra("env", 0)

            if (json != null) {
                Log.d("ReceivedJSON", json)
                val inputs = Gson().fromJson(json, OcrRequest::class.java)

                OcrLauncher.launch(this, inputs, object : OcrCallback {
                    override fun onSdkResult(result: String) {
                        Log.d("SDK>>res", result)
                        // ✅ REMOVED: No automatic redirect after OCR
                        // Just show success message
                        Toast.makeText(
                            this@MainActivity,
                            "Data saved locally. Go to Sync to upload.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
            } else {
                pd.dismiss()
                setupManualInput()
            }

        } catch (e: Exception) {
            pd.dismiss()
            Toast.makeText(this, e.message ?: "Unknown error", Toast.LENGTH_LONG).show()
            setupManualInput()
        }
    }

    private fun setupManualInput() {
        binding.button.setOnClickListener {
            val accountId = binding.accIdEt.text.toString().trim()
            val discom = binding.discomEt.text.toString().trim()
            val agencyName = binding.agencyNameEt.text.toString().trim()
            val name = binding.meterReaderNameEt.text.toString().trim()
            val id = binding.meterReaderIdEt.text.toString().trim()
            val mobileNo = binding.mobileNoEt.text.toString().trim()
            val divisionCode = binding.divisionCodeEt.text.toString().trim()
            val divisionName = binding.divisionNameEt.text.toString().trim()
            val meterMake = binding.meterMakeEt.text.toString().trim()
            val reqReadingValuesRaw = binding.readingValuesEt.text.toString().trim()

            if (accountId.isEmpty() || discom.isEmpty() || agencyName.isEmpty() ||
                name.isEmpty() || id.isEmpty() || mobileNo.isEmpty() ||
                divisionCode.isEmpty() || divisionName.isEmpty() || reqReadingValuesRaw.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all fields!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val reqReadingValues = reqReadingValuesRaw.split(",").map { it.trim() }

            val meterReaderData = OcrRequest(
                -1,
                agencyName,
                discom,
                accountId,
                id,
                mobileNo,
                name,
                divisionCode,
                divisionName,
                meterMake,
                reqReadingValues
            )

            Log.d("Req>>", Gson().toJson(meterReaderData))

            OcrLauncher.launch(this, meterReaderData, object : OcrCallback {
                override fun onSdkResult(result: String) {
                    Log.d("SDK>>res", result)

                    showJsonResponseDialog(this@MainActivity, result)
                }
            })
        }
    }

    private fun fetchMeterReadingMakes(context: Context) {
        val call = RetrofitClient.apiService.getMeterMakes(bearerToken)
        call.enqueue(object : Callback<List<MeterMake>> {
            override fun onResponse(
                call: Call<List<MeterMake>>,
                response: Response<List<MeterMake>>
            ) {
                pd.dismiss()
                if (response.isSuccessful) {
                    val list = response.body()
                    if (list != null) {
                        meterMakes.clear()
                        meterMakes.addAll(list.filter { it.org == 3 }.map { it.make })

                        val adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_dropdown_item_1line,
                            meterMakes
                        )
                        binding.meterMakeEt.setAdapter(adapter)
                        binding.meterMakeEt.setOnClickListener { binding.meterMakeEt.showDropDown() }
                    }
                } else {
                    Log.e("Retrofit", "Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<MeterMake>>, t: Throwable) {
                pd.dismiss()
                Log.e("Retrofit", "Network error: ${t.message}")
            }
        })
    }

    private fun showJsonResponseDialog(context: Context, jsonResponse: String) {
        val textView = TextView(context).apply {
            text = jsonResponse
            setPadding(16, 16, 16, 16)
            textSize = 14f
        }

        val scrollView = ScrollView(context).apply {
            addView(textView)
        }

        AlertDialog.Builder(context)
            .setTitle("OCR Result")
            .setView(scrollView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                redirectToBillingApp(jsonResponse)
            }.show()
    }

    private fun redirectToBillingApp(resultJson: String) {
        val intent = Intent().apply {
            setClassName(
                if (env == 1) "com.transvision.gescom" else "com.transvision.gescom_testing",
                "com.transvision.smartbilling.Main_Activity"
            )
            putExtra("result", resultJson)
        }
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("Redirect", "App not found: ${e.message}")
            Toast.makeText(this, "Target app not found", Toast.LENGTH_LONG).show()
        }
    }
}
