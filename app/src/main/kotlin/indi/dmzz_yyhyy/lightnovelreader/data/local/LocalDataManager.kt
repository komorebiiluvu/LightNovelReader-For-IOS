package indi.dmzz_yyhyy.lightnovelreader.data.local

import android.content.Context
import android.util.Log
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrapError
import dagger.hilt.android.qualifiers.ApplicationContext
import indi.dmzz_yyhyy.lightnovelreader.BuildConfig
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark.BackupCompression
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark.BackupFileHeader
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark.BackupTableHeader
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark.BackupTable
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.FrameType
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.readExactly
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.readFrame
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.writeCborFrame
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.writeFrame
import indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.writeTable
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.BookInformationDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.BookRecordDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.BookVolumesDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.BookshelfDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.ChapterContentDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.DailyCountDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.FormattingRuleDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.UserDataDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.UserReadingDataDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookshelfBookMetadataEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookshelfEntity
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSourceProvider
import io.nightfish.lightnovelreader.api.userdata.UserDataPath
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("OPT_IN_USAGE")
@Singleton
class LocalDataManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val webDataSourceProvider: WebBookDataSourceProvider,
    private val bookInformationDao: BookInformationDao,
    private val bookRecordDao: BookRecordDao,
    private val dailyCountDao: DailyCountDao,
    private val bookshelfDao: BookshelfDao,
    private val chapterContentDao: ChapterContentDao,
    private val bookVolumesDao: BookVolumesDao,
    private val formattingRuleDao: FormattingRuleDao,
    private val userReadingDataDao: UserReadingDataDao,
    private val userDataDao: UserDataDao
) {
    companion object {
        const val TAG = "LocalDataManager"
    }

    private val backupDataFileVersion = 1
    private val backupDataFileHead =
        "?lnrdata%04d".format(backupDataFileVersion).toByteArray(Charsets.US_ASCII)
    private val backupDataFileMagic = backupDataFileHead + ByteArray(4)
    val localDataDir = context.dataDir.resolve("local_data").also {
        if (!it.exists()) it.mkdirs()
    }
    val webDataSourceUserDataPathSet = mutableSetOf<String>()

    fun registerWebDataSourceUserData(path: String) {
        webDataSourceUserDataPathSet.add(path)
    }

    private fun writeBackupFileHeader(
        output: OutputStream,
        compression: BackupCompression
    ) {
        output.write(backupDataFileMagic)
        writeCborFrame(
            output = output,
            type = FrameType.FILE_HEADER,
            payload = BackupFileHeader(
                createdAt = System.currentTimeMillis(),
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                compression = compression
            )
        )
    }

    fun exportCurrentDataSourceLocalData(
        outputStream: OutputStream,
        localBookCache: Boolean,
        bookshelf: Boolean,
        readingRecord: Boolean,
        settings: Boolean
    ): Result<Unit, Throwable> {
        return exportData(
            outputStream = outputStream,
            localBookCache = localBookCache,
            bookshelf = bookshelf,
            readingRecord = readingRecord,
            settings = settings
        ).fold(
            success = { Ok(Unit) },
            failure = { Err(it) }
        )
    }

    private fun exportData(
        outputStream: OutputStream,
        localBookCache: Boolean = true,
        bookshelf: Boolean = true,
        readingRecord: Boolean = true,
        settings: Boolean = true,
        sourceId: Int = webDataSourceProvider.default.id
    ): Result<Unit, Throwable> {
        return runCatching {
            outputStream.buffered().use { output ->
                writeBackupFileHeader(output, BackupCompression.GZIP)
                output.flush()
                GZIPOutputStream(output).use { stream ->
                    if (localBookCache) stream.writeBookContents(sourceId)
                    if (bookshelf) stream.writeBookshelves(sourceId)
                    if (readingRecord) stream.writeBookRecords(sourceId)
                    if (settings) stream.writeSettings(sourceId)
                    writeFrame(stream, FrameType.FILE_END)
                }
            }
        }
    }

    private fun OutputStream.writeBookContents(sourceId: Int) {
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.BOOK_INFORMATION.id,
            items = bookInformationDao.getAllEntities()
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.CHAPTER_INFORMATION.id,
            items = bookVolumesDao.getAllChapterInformationEntities()
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.CHAPTER_CONTENT.id,
            items = chapterContentDao.getAllEntities(),
            batchSize = 20
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.VOLUME.id,
            items = bookVolumesDao.getAllVolumeEntities()
        )
    }

    private fun OutputStream.writeBookshelves(sourceId: Int) {
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.BOOKSHELF.id,
            items = bookshelfDao.getAllBookshelves()
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.BOOKSHELF_BOOK_METADATA.id,
            items = bookshelfDao.getAllBookshelfBookMetadata()
        )
    }

    private fun OutputStream.writeBookRecords(sourceId: Int) {
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.BOOK_RECORD.id,
            items = bookRecordDao.getAllBookRecords()
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.DAILY_COUNT.id,
            items = dailyCountDao.getAll()
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.USER_READING_DATA.id,
            items = userReadingDataDao.getAllEntities()
        )
    }

    private fun OutputStream.writeSettings(sourceId: Int) {
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.FORMATTING_RULE.id,
            items = formattingRuleDao.getAllBookRuleEntities()
        )
        writeTable(
            output = this,
            sourceId = sourceId,
            tableId = BackupTable.USER_DATA.id,
            items = userDataDao.getAllEntities().filter {
                webDataSourceUserDataPathSet.contains(it.path)
            }
        )
    }

    suspend fun importAppLocalData(
        inputStream: InputStream,
        targetSourceId: Int = webDataSourceProvider.default.id
    ): Result<Unit, Throwable> {
        return runCatching {
            inputStream.buffered().use { stream ->
                val magic = readExactly(stream, 16)
                if (!magic.contentEquals(backupDataFileMagic)) error("Invalid backup file format")

                val headerFrame = readFrame(stream)
                if (headerFrame.type != FrameType.FILE_HEADER) {
                    error("Missing backup file header")
                }
                val fileHeader = Cbor{ ignoreUnknownKeys = true }.decodeFromByteArray<BackupFileHeader>(headerFrame.payload)

                Log.i(TAG, "Starting import, current datasource is ${webDataSourceProvider.default.id}")
                val bodyInput = when (fileHeader.compression) {
                    BackupCompression.NONE -> stream
                    BackupCompression.GZIP -> GZIPInputStream(stream)
                }

                bodyInput.use { body ->
                    while (true) {
                        val frame = readFrame(body)

                        when (frame.type) {
                            FrameType.TABLE_HEADER -> {
                                val tableHeader = Cbor.decodeFromByteArray<BackupTableHeader>(
                                    frame.payload
                                )
                                importBackupTable(tableHeader, body, targetSourceId)
                            }

                            FrameType.FILE_END -> break

                            else -> error("Unexpected backup frame type: ${frame.type}")
                        }
                    }
                }
            }
        }.fold(
            success = { Ok(Unit) },
            failure = { Err(it) }
        )
    }

    private suspend fun importBackupTable(
        tableHeader: BackupTableHeader,
        input: InputStream,
        targetSourceId: Int
    ) {
        if (tableHeader.webBookDataSourceId != targetSourceId) {
            error("Backup source mismatch: expected=$targetSourceId, actual=${tableHeader.webBookDataSourceId}")
        }
        Log.i(TAG, "Processing: ${BackupTable.fromId(tableHeader.tableId)} for webBookDataSource=${tableHeader.webBookDataSourceId}")
        when (tableHeader.tableId) {
            BackupTable.BOOK_INFORMATION.id -> importTableEntities(
                input = input,
                decodeKey = { it.id },
                getOldEntities = bookInformationDao::getEntitiesByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = bookInformationDao::insertAll
            )
            BackupTable.BOOK_RECORD.id -> importTableEntities(
                input = input,
                decodeKey = { it.bookId },
                getOldEntities = bookRecordDao::getBookRecordsByBookIds,
                mergeEntity = {old, new -> old.merge(new) },
                insertEntities = bookRecordDao::insertBookRecords
            )
            BackupTable.DAILY_COUNT.id -> importTableEntities(
                input = input,
                decodeKey = { it.date },
                getOldEntities = dailyCountDao::getEntitiesByDates,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = dailyCountDao::insertAll
            )
            BackupTable.BOOKSHELF.id -> importTableEntities(
                input = input,
                decodeKey = { it.id },
                getOldEntities = bookshelfDao::getBookshelvesByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = bookshelfDao::insertBookshelves
            )
            BackupTable.BOOKSHELF_BOOK_METADATA.id -> importTableEntities(
                input = input,
                decodeKey = { it.id },
                getOldEntities = bookshelfDao::getBookshelfBookMetadataEntitiesByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = bookshelfDao::insertBookshelfBookMetadataEntities
            )
            BackupTable.CHAPTER_CONTENT.id -> importTableEntities(
                input = input,
                decodeKey = { it.id },
                getOldEntities = chapterContentDao::getByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = chapterContentDao::insertAll
            )
            BackupTable.CHAPTER_INFORMATION.id -> importTableEntities(
                input = input,
                decodeKey = { it.id },
                getOldEntities = bookVolumesDao::getChapterInformationEntitiesByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = bookVolumesDao::insertChapterInformationEntities
            )
            BackupTable.FORMATTING_RULE.id -> importTableEntities(
                input = input,
                decodeKey = { Triple(it.bookId, it.match, it.replacement) },
                getOldEntities = { keys ->
                    formattingRuleDao.getBookRuleEntitiesByBookIds(
                        keys.map { it.first }.distinct()
                    )},
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = formattingRuleDao::insertAll
            )
            BackupTable.USER_DATA.id -> importTableEntities(
                input = input,
                decodeKey = { it.path },
                getOldEntities = userDataDao::getEntitiesByPaths,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = userDataDao::insertAll
            )
            BackupTable.USER_READING_DATA.id -> importTableEntities(
                input = input,
                decodeKey = { it.id },
                getOldEntities = userReadingDataDao::getEntitiesByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = userReadingDataDao::insertAll
            )
            BackupTable.VOLUME.id -> importTableEntities(
                input = input,
                decodeKey = { it.volumeId },
                getOldEntities = bookVolumesDao::getVolumeEntitiesByIds,
                mergeEntity = { old, new -> old.merge(new) },
                insertEntities = bookVolumesDao::insertVolumes
            )
            else -> {
                Log.w(TAG, "Skipped table ${tableHeader.tableId} for webBookDataSource=${tableHeader.webBookDataSourceId}")
                while (true) {
                    when (val type = readFrame(input).type) {
                        FrameType.ENTITY_BATCH -> Unit
                        FrameType.TABLE_END -> return
                        else -> error("Unexpected frame type in skipped table: $type")
                    }
                }
            }
        }
    }

    private suspend inline fun <reified T, K> importTableEntities(
        input: InputStream,
        crossinline decodeKey: (T) -> K?,
        crossinline getOldEntities: suspend (List<K>) -> List<T>,
        crossinline mergeEntity: (old: T, new: T) -> T,
        crossinline insertEntities: suspend (List<T>) -> Unit
    ) {
        while (true) {
            val frame = readFrame(input)

            when (frame.type) {
                FrameType.ENTITY_BATCH -> {
                    val entities = Cbor.decodeFromByteArray<List<T>>(frame.payload)
                    val keys = entities.mapNotNull(decodeKey).distinct()
                    val oldMap = getOldEntities(keys).associateBy(decodeKey)

                    val merged = entities.map { entity ->
                        oldMap[decodeKey(entity)]?.let { mergeEntity(it, entity) } ?: entity
                    }

                    Log.i(TAG, " - ${entities.size} entity(s) imported")
                    Log.i(TAG, " - ${oldMap.size} entity(s) merged")
                    insertEntities(merged)
                }
                FrameType.TABLE_END -> return
                else -> error("Unexpected frame type in table: ${frame.type}")
            }
        }
    }

    fun saveCurrentDataSourceToFile(): Result<Unit, Throwable> {
        return runCatching {
            val sourceId = webDataSourceProvider.default.id
            val file = localDataDir.resolve(sourceId.toString())
            file.outputStream().use { output ->
                exportData(output, sourceId = sourceId).let {
                    it.component1() ?: throw it.unwrapError()
                }
            }
        }
    }

    suspend fun restoreDataSourceFromFile(
        webBookDataSourceId: Int
    ): Result<Unit, Throwable> {
        val file = localDataDir.resolve(webBookDataSourceId.toString())
        if (!file.exists()) return Ok(Unit)

        return runCatching {
            file.inputStream().buffered().use { input ->
                importAppLocalData(input, webBookDataSourceId).let {
                    it.component1() ?: throw it.unwrapError()
                }
            }
        }
    }

    fun exportBookshelfLocalData(
        outputStream: OutputStream,
        webBookDataSourceId: Int,
        bookshelfEntities: List<BookshelfEntity>,
        bookshelfBookMetadataEntities: List<BookshelfBookMetadataEntity>
    ): Result<Unit, Throwable> {
        return runCatching {
            writeBackupFileHeader(outputStream, BackupCompression.GZIP)
            outputStream.flush()

            GZIPOutputStream(outputStream).use { output ->
                writeTable(
                    output = output,
                    sourceId = webBookDataSourceId,
                    tableId = BackupTable.BOOKSHELF.id,
                    items = bookshelfEntities
                )
                writeTable(
                    output = output,
                    sourceId = webBookDataSourceId,
                    tableId = BackupTable.BOOKSHELF_BOOK_METADATA.id,
                    items = bookshelfBookMetadataEntities
                )
                writeFrame(
                    output = output,
                    type = FrameType.FILE_END
                )
            }
        }
    }

    fun cleanDatabaseWithoutGlobalUserData() {
        bookInformationDao.clear()
        bookRecordDao.clear()
        dailyCountDao.clear()
        bookshelfDao.clear()
        bookVolumesDao.clear()
        chapterContentDao.clear()
        formattingRuleDao.clear()
        userReadingDataDao.clear()

        for (entity in userDataDao.getAllEntities()) {
            if (!webDataSourceUserDataPathSet.contains(entity.path)) continue
            userDataDao.remove(entity.path)
        }
    }

    init {
        registerWebDataSourceUserData(UserDataPath.Settings.Data.WebDataSourceId.path)
        registerWebDataSourceUserData(UserDataPath.ReadingBooks.path)
        registerWebDataSourceUserData(UserDataPath.CompletedDownloadBookList.path)
        registerWebDataSourceUserData(UserDataPath.Search.History.path)
    }
}
