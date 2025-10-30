package apc.offline.mrd.network


import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @GET("uppcl-meter-makes")
    fun getMeterMakes(
        @Header("Authorization") token: String,
        @Header("Content-Type") contentType: String = "application/json"
    ): Call<List<MeterMake>>

    @POST("upload-meter-reading")
    suspend fun uploadMeterReading(
        @Header("Authorization") token: String,
        @Body payload: Map<String, Any>
    ): Response<Map<String, Any>>
}

data class MeterMake(
    val make: String,
    val org: Int
)
