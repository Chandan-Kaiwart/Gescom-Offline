package apc.offline.mrd.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_request")
data class OcrRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localReqId: Int, // ✅ ADD THIS FIELD
    val agencyId: String,
    val boardCode: String,
    val consumerNo: String,
    val meterReaderId: String,
    val meterReaderMobile: String,
    val meterReaderName: String,
    val subDivisionCode: String,
    val subDivisionName: String,
    val meterMake: String,
    val reqReadingValues: String,
    val isSynced: Boolean = false,
    val readingDate: Long = System.currentTimeMillis()
)