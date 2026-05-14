package indi.dmzz_yyhyy.lightnovelreader.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrapError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import indi.dmzz_yyhyy.lightnovelreader.data.local.LocalDataManager
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

    override suspend fun doWork(): Result {
        val fileUri = inputData.getString("uri")?.let(Uri::parse) ?: return Result.failure()
        val exportLocalBookCache = inputData.getBoolean("exportLocalBookCache", true)
        val exportBookshelf = inputData.getBoolean("exportBookshelf", true)
        val exportReadingData = inputData.getBoolean("exportReadingData", true)
        val exportSetting = inputData.getBoolean("exportSetting", true)

        val result = runCatching {
            applicationContext.contentResolver.openFileDescriptor(fileUri, "w")
                ?: error("Failed to open export file descriptor")
        }.andThen { parcelFileDescriptor ->
            parcelFileDescriptor.use {
                FileOutputStream(it.fileDescriptor).use { outputStream ->
                    localDataManager.exportCurrentDataSourceLocalData(
                        outputStream = outputStream,
                        localBookCache = exportLocalBookCache,
                        bookshelf = exportBookshelf,
                        readingRecord = exportReadingData,
                        settings = exportSetting
                    )
                }
            }
        }

        if (result.isErr) {
            Log.e(TAG, "Failed to export app local data")
            result.unwrapError().printStackTrace()
            return Result.failure()
        }

        return Result.success()
    }
}