package apc.offline.mrd.ocrlib.dataClasses.request

data class OcrRequest(

    var reqId:Int?=-1,
    var AGENCY_ID: String="1",
    var BOARD_CODE: String="1",
    var CONSUMER_NO: String="1",
    var METER_READER_ID: String="1",
    var METER_READER_MOBILE_NO: String="9999999999",
    var METER_READER_NAME: String="1",
    var SUB_DIVISION_CODE: String="1",
    var SUB_DIVISION_NAME: String="2",
    var METER_MAKE:String?="1",
    val REQ_READING_VALUES: List<String>,

    )