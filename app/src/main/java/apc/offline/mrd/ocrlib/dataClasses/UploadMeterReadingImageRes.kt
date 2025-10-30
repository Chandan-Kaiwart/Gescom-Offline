package apc.offline.mrd.ocrlib.dataClasses

data class UploadMeterReadingImageRes(
    val address: String,
    val agency: String,
    val ca_no: String,
    val meter_model: String,
    val consumer: String,
    val created_at: String,
    val exception: String,
    val id: Int,
    val image_path: String,
    val lat_long: String,
    val location: String,
    val location_type: String,
    val meter_no: String,
    val meter_reader: String,
    val meter_reading: String,
    val mru: String,
    val reading_date_time: String,
    val ocr_unit: String?,
    val site_location: String,
    val unit: String,
    val updated_at: String
)