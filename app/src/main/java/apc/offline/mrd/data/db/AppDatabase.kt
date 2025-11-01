package apc.offline.mrd.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import apc.offline.mrd.data.dao.ManualReadingDao
import apc.offline.mrd.data.dao.MeterReadingDao
import apc.offline.mrd.data.dao.OcrRequestDao
import apc.offline.mrd.data.entities.ManualReadingEntity
import apc.offline.mrd.data.entities.MeterReadingEntity
import apc.offline.mrd.data.entities.OcrRequestEntity
import java.util.concurrent.Executors

@Database(
    entities = [
        OcrRequestEntity::class,
        MeterReadingEntity::class,
        ManualReadingEntity::class
    ],
    version = 5, // ✅ Updated to version 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ocrRequestDao(): OcrRequestDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun manualReadingDao(): ManualReadingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration() // ✅ Important for schema changes
                    .setQueryCallback({ sqlQuery, bindArgs ->
                        Log.d("RoomSQL", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d("RoomSQL", "Query: $sqlQuery")
                        Log.d("RoomSQL", "Args: ${bindArgs.joinToString()}")
                        Log.d("RoomSQL", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    }, Executors.newSingleThreadExecutor())
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
