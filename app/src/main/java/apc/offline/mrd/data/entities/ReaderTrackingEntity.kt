package apc.offline.mrd.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reader_tracking")
data class ReaderTrackingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uidNo: String,
    val mobileNo: String,
    val lat: String,
    val lon: String,
    val division: String,
    val mrRemark: String,
    val ocrRequestId: Int,
    val billBasis: String,
    val readingType: String,
    val timestamp: Long = System.currentTimeMillis(), // Use Long
    val signalStrength: String,
    val deviceData: String,
    val sim: String,
    val boardCode: String,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)