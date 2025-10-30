package apc.offline.mrd.ocrlib.dataClasses.response

import apc.offline.mrd.ocrlib.dataClasses.request.OcrRequest

data class OcrResponseLsm(
    var acc_id: String,
    var ocr_results: List<OcrResultLsm>,
    var input: OcrRequest,
    var address:String,
    var lat:String,
    var lng:String

)