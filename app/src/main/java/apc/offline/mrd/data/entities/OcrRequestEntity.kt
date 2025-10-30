package apc.offline.mrd.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_request")
data class OcrRequestEntity(
    @PrimaryKey val id: Int = 0,
    val agencyId: String,
    val boardCode: String,
    val consumerNo: String,
    val meterReaderId: String,
    val meterReaderMobile: String,
    val meterReaderName: String,
    val subDivisionCode: String,
    val subDivisionName: String,
    val meterMake: String,
    val isSynced: Boolean= false,
    val reqReadingValues: String, // Must be String!
    val readingDate: Long = System.currentTimeMillis()
)
