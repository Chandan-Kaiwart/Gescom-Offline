package apc.offline.mrd.ocrlib.dataClasses.response
data class OcrResultLsm(
    var address: String,
    var img_path: String,
    var lat_long: String,
    var ocr_reading: String,
    var meter_reading: String,
    var ocr_unit: String,
    var ocr_mno: String,
    val register: String
)