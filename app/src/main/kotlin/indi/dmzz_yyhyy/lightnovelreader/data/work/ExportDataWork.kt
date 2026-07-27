package indi.dmzz_yyhyy.lightnovelreader.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.runCatching
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import indi.dmzz_yyhyy.lightnovelreader.data.local.LocalDataManager
import indi.dmzz_yyhyy.lightnovelreader.utils.writeAppLocalData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import java.io.FileOutputStream

@HiltWorker
class ExportDataWork @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localDataManager: LocalDataManager
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "ExportDataWork"
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun doWork(): Result {
        val fileUri = inputData.getString("uri")?.let(Uri::parse) ?: return Result.failure()
        val exportLocalBookCache = inputData.getBoolean("exportLocalBookCache", true)
        val exportBookshelf = inputData.getBoolean("exportBookshelf", true)
        val exportReadingData = inputData.getBoolean("exportReadingData", true)
        val exportSetting = inputData.getBoolean("exportSetting", true)
        localDataManager.exportAppLocalData(
            localBookCache = exportLocalBookCache,
            bookshelf = exportBookshelf,
            readingRecord = exportReadingData,
            settings = exportSetting
        ).andThen { appLocalData ->
            runCatching {
                applicationContext.contentResolver.openFileDescriptor(fileUri, "w")
                    ?.use { parcelFileDescriptor ->
                        FileOutputStream(parcelFileDescriptor.fileDescriptor).use {
                            it.writeAppLocalData(Cbor.encodeToByteArray(appLocalData))
                        }
                    }
            }
        }.onErr {
            Log.e(TAG, "Failed to get AppLocalData")
            it.printStackTrace()
            return Result.failure()
        }
        return Result.success()
    }
}