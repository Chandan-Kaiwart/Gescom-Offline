package apc.offline.mrd.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manual_reading")
data class ManualReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reading: String,
    val image_path: String,
    val unit: String,
    val ocrRequestId: Int,
    val isSynced: Boolean= false

)
