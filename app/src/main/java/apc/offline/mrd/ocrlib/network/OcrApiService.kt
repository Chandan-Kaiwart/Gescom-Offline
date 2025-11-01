package apc.offline.mrd.ocrlib.network

import apc.offline.mrd.ocrlib.dataClasses.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface OcrApiService {

    // ═══════════════════════════════════════════════════════════
    // GET ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    @GET("meter-reading-exceptions")
    fun getMeterReadingExceptions(
        @Header("Authorization") token: String,
        @Header("accept") accept: String = "*/*"
    ): Call<List<MeterReadingExceptionsResItem>>

    @GET("meter-reading-unit")
    fun getMeterReadingUnits(
        @Header("Authorization") token: String,
        @Header("accept") accept: String = "*/*"
    ): Call<List<MeterReadingUnitsResItem>>

    @GET("uppcl-meter-makes")
    fun getMeterMakes(
        @Header("Authorization") token: String,
        @Header("accept") accept: String = "*/*"
    ): Call<List<MeterMakesResItem>>

    // ═══════════════════════════════════════════════════════════
    // POST ENDPOINTS - FIXED WITH @JvmSuppressWildcards
    // ═══════════════════════════════════════════════════════════

    /**
     * Create OCR Request
     * POST https://test.vidyut-suvidha.in/ocr-requests
     */
    @POST("ocr-requests")
    @Headers("Content-Type: application/json")
    fun createOcrRequest(
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Call<Map<String, @JvmSuppressWildcards Any>>

    /**
     * Create Meter Reading
     * POST https://test.vidyut-suvidha.in/meter-reading
     */
    @POST("meter-reading")
    @Headers("Content-Type: application/json")
    fun createMeterReading(
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Call<Map<String, @JvmSuppressWildcards Any>>

    /**
     * Create Manual Reading
     * POST https://test.vidyut-suvidha.in/manual-reading
     */
    @POST("manual-reading")
    @Headers("Content-Type: application/json")
    fun createManualReading(
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Call<Map<String, @JvmSuppressWildcards Any>>

    /**
     * Reader Tracking
     * POST https://test.vidyut-suvidha.in/reader-tracking
     */
    @POST("reader-tracking")
    @Headers("Content-Type: application/json")
    fun postReaderTracking(
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Call<Map<String, @JvmSuppressWildcards Any>>

    // ═══════════════════════════════════════════════════════════
    // MULTIPART UPLOAD
    // ═══════════════════════════════════════════════════════════

    /**
     * Upload Meter Image with multipart form data
     * POST https://test.vidyut-suvidha.in/meter-reading/upload
     */
    @Multipart
    @POST("meter-reading")
    fun uploadMeterImage(
        @Header("Authorization") token: String,
        @Header("accept") accept: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("ocrRequestId") ocrRequestId: RequestBody, // ✅ CHANGED: "localReqId" → "ocrRequestId"
        @Part("reading_date_time") readingDateTime: RequestBody,
        @Part("site_location") siteLocation: RequestBody,
        @Part("ca_no") caNo: RequestBody,
        @Part("image_path") imagePath: RequestBody,
        @Part("meter_no") meterNo: RequestBody,
        @Part("meter_reading") meterReading: RequestBody,
        @Part("lat_long") latLong: RequestBody,
        @Part("address") address: RequestBody,
        @Part("unit") unit: RequestBody,
        @Part("meter_reader") meterReader: RequestBody,
        @Part("consumer") consumer: RequestBody,
        @Part("mru") mru: RequestBody,
        @Part("exception") exception: RequestBody,
        @Part("meter_model") meterModel: RequestBody,
        @Part("location_type") locationType: RequestBody,
        @Part("location") location: RequestBody,
        @Part("agency") agency: RequestBody
    ): Call<UploadMeterReadingImageRes>
}
