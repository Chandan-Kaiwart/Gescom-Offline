package apc.offline.mrd.ocrlib

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import apc.offline.mrd.databinding.FragOutputBinding
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import androidx.fragment.app.activityViewModels




class OutputFrag : Fragment() {
    private lateinit var mContext: Context
    private lateinit var binding: FragOutputBinding
    private lateinit var navController: NavController
    private val vm: OcrViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragOutputBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        val formatted = current.format(formatter)

        val args = vm.res.value

        binding.readingTv.text = "Meter Reading : ${args?.meter_reading ?: ""} \n Meter Number: ${args?.meter_no ?: ""}"

        val info = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
        val versionCode = info.versionName
        binding.verTv.text = "v$versionCode"

        val imageUrl = args?.image_path?.replace(
            "/home/gescom/be/gescom_backend/src/ocr/images_power/",
            "https://api.vidyut-suvidha.in/images/"
        ) ?: ""

        Glide.with(this).load(imageUrl).into(binding.imgIv)

        binding.numberTv.text = "ACC ID: ${args?.ca_no ?: ""}"
        binding.locTv.text = "Lat.: ,Long.: "
        binding.latLongTv.text = "Date & Time : $formatted \nLocation: "

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.rejBt.setOnClickListener {
            // Implement rejection logic if needed
        }

        binding.conBt.setOnClickListener {
            val res = Gson().toJson(vm.res.value)
            Log.d("SDK>>>", res.toString())
            OcrLauncher.sendResultBack(res)
            requireActivity().finish()
        }
    }
}
