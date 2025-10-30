package apc.offline.mrd.ocrlib.dataClasses.response

data class ManualReading(
    val id:Int,
    val reading: String,
    val unit: String
)