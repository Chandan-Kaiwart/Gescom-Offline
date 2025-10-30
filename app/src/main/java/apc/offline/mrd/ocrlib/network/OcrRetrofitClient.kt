package apc.offline.mrd.ocrlib.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object OcrRetrofitClient {
    private const val BASE_URL = "https://test.vidyut-suvidha.in/"

    const val AUTH_TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6ImpvaG5fZG9lIiwic3ViIjoxMCwiaWF0IjoxNzQ2MTI3MjMxLCJleHAiOjE3NDY2NDU2MzF9.Aht12k9e_DvNLBf-kxpCka5SmlxTNvxfxdd_KvLu0aQ"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // ✅ FIX: Use a function instead of properties to avoid naming conflicts
    fun getApiService(): OcrApiService {
        return retrofit.create(OcrApiService::class.java)
    }
}