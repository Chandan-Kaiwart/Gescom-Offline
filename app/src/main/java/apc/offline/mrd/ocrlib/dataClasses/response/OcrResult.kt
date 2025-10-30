package apc.offline.mrd.ocrlib.dataClasses.response
data class OcrResult(
    var address: String,
    var img_path: String,
    var lat_long: String,
    var manual_reading: String,
    var ocr_exception_code: Int,
    var ocr_exception_msg: String,
    var ocr_reading: String,
    var meter_reading: String,
    var ocr_unit: String,
    var unit: Int?=1,
    var ocr_mno: String,
    var ocr_meter_make: String,
    var ocr_ref_id: Int,
    val register: String
)