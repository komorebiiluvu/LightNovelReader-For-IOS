package indi.dmzz_yyhyy.lightnovelreader.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrapError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import indi.dmzz_yyhyy.lightnovelreader.data.local.LocalDataManager
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.LightNovelReaderDatabase
import java.io.FileInputStream

@HiltWorker
class ImportDataWork @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localDataManager: LocalDataManager,
    private val database: LightNovelReaderDatabase
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "ImportDataWork"
    }

    override suspend fun doWork(): Result {
        val fileUri = inputData.getString("uri")?.let(Uri::parse) ?: return Result.failure()
        val overwrite = inputData.getBoolean("overwrite", false)

        val result = runCatching {
            applicationContext.contentResolver.openFileDescriptor(fileUri, "r")
                ?: error("Failed to open import file descriptor")
        }.andThen { parcelFileDescriptor ->
            parcelFileDescriptor.use {
                FileInputStream(it.fileDescriptor).use { inputStream ->
                    database.withTransaction {
                        if (overwrite) {
                            localDataManager.cleanDatabaseWithoutGlobalUserData()
                        }
                        localDataManager.importAppLocalData(inputStream).also { importResult ->
                            if (importResult.isErr) {
                                throw importResult.unwrapError()
                            }
                        }
                    }
                }
            }
        }

        if (result.isOk) {
            return Result.success()
        }

        Log.e(TAG, "Failed to import app local data")
        result.unwrapError().printStackTrace()
        return Result.failure()
    }
}