package apc.offline.mrd.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meter_reading")
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reading_data_time: String,
    val site_location: String,
    val ca_no: String,
    val meter_model: String,
    val image_path: String,
    val meter_no: String,
    val meter_reading: String,
    val mrn_text: String,
    val lat_long: String,
    val address: String,
    val unit: String,
    val meter_reader: String,
    val consumer: String,
    val mru: String,
    val isSynced: Boolean= false,
    val agency: String,
    val exception: String,
    val location_type: String,
    val ocr_unit: String,
    val location: String,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val ocrReqId: Int,
    val usedUrl: String,
    val responseTime: String,
    val ocrRequestId: String
)
