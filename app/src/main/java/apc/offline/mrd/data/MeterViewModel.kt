package apc.offline.mrd.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import apc.offline.mrd.data.db.AppDatabase
import apc.offline.mrd.data.entities.ManualReadingEntity
import apc.offline.mrd.data.entities.MeterReadingEntity
import apc.offline.mrd.data.entities.OcrRequestEntity
import apc.offline.mrd.data.entities.ReaderTrackingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(AppDatabase.getDatabase(application))

//    // ✅ Use Dispatchers.IO for database operations
//    fun insertReaderTracking(entity: ReaderTrackingEntity) = viewModelScope.launch(Dispatchers.IO) {
//        repository.insertReaderTracking(entity)
//    }  // ✅ Added closing brace

    fun insertOcrRequest(entity: OcrRequestEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertOcrRequest(entity)
    }  // ✅ Added closing brace

    fun insertMeterReading(entity: MeterReadingEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertMeterReading(entity)
    }  // ✅ Added closing brace

    fun insertManualReading(entity: ManualReadingEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertManualReading(entity)
    }  // ✅ Added closing brace
}