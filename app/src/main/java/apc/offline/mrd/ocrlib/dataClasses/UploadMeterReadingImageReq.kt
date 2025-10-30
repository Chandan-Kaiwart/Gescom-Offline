package apc.offline.mrd.ocrlib.dataClasses

data class UploadMeterReadingImageReq(
    val address: String,
    val agency: Int,
    val ca_no: String,
    val consumer: Int,
    val exception: Int,
    val image_path: String,
    val lat_long: String,
    val location: Int,
    val location_type: Int,
    val meter_no: String,
    val meter_reader: Int,
    val meter_reading: String,
    val mru: Int,
    val reading_date_time: String,
    val site_location: String,
     val unit: Int,
    val load: Int
)