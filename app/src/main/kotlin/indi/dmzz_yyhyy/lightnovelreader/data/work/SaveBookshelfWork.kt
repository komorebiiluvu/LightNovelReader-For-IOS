package indi.dmzz_yyhyy.lightnovelreader.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.michaelbull.result.unwrapError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import indi.dmzz_yyhyy.lightnovelreader.data.local.LocalDataManager
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.BookshelfDao
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSourceProvider
import java.io.FileOutputStream

@HiltWorker
class SaveBookshelfWork @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val webBookDataSourceProvider: WebBookDataSourceProvider,
    private val localDataManager: LocalDataManager,
    private val bookshelfDao: BookshelfDao
) : Worker(appContext, workerParams) {
    companion object {
        const val TAG = "SaveBookshelfWork"
    }

    override fun doWork(): Result {
        val id = inputData.getInt("bookshelfId", -1)
        val uri = inputData.getString("uri")?.let(Uri::parse)
            ?: return Result.failure()

        val bookshelfEntityList =
            if (id != -1) {
                bookshelfDao.getBookshelf(id)?.let(::listOf) ?: emptyList()
            } else {
                bookshelfDao.getAllBookshelves()
            }

        if (bookshelfEntityList.isEmpty() && id != -1) {
            Log.e(TAG, "Bookshelf doesn't exist (id=$id)")
            return Result.failure()
        }

        val bookshelfIds = bookshelfEntityList.map { it.id }.toSet()

        val bookIds = bookshelfEntityList
            .flatMap { it.allBookIds }
            .distinct()

        val bookshelfBookMetadataEntities = bookIds
            .mapNotNull(bookshelfDao::getBookshelfBookMetadataEntity)
            .map { entity ->
                entity.copy(
                    bookShelfIds = entity.bookShelfIds.filter { it in bookshelfIds }
                )
            }

        return try {
            applicationContext.contentResolver.openFileDescriptor(uri, "w")
                ?.use { parcelFileDescriptor ->
                    FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                        val result = localDataManager.exportBookshelfLocalData(
                            outputStream = outputStream,
                            webBookDataSourceId = webBookDataSourceProvider.default.id,
                            bookshelfEntities = bookshelfEntityList,
                            bookshelfBookMetadataEntities = bookshelfBookMetadataEntities
                        )

                        if (result.isErr) {
                            Log.e(TAG, "Failed to save bookshelf data")
                            result.unwrapError().printStackTrace()
                            return Result.failure()
                        }
                    }
                } ?: return Result.failure()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file")
            e.printStackTrace()
            Result.failure()
        }
    }
}