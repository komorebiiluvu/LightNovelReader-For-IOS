package indi.dmzz_yyhyy.lightnovelreader.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.LightNovelReaderDatabase
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookInformationEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookRecordEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookshelfBookMetadataEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookshelfEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.ChapterContentEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.ChapterInformationEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.DailyCountEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.UserReadingDataEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.UserDataEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.VolumeEntity
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.Count
import io.nightfish.lightnovelreader.api.book.WordCount
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.content.builder.simpleText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking

/**
 * Seeds deterministic, local-only UI data into the benchmark build.
 *
 * This receiver is compiled only into the `benchmark` variant and is never
 * present in debug, snapshot, or release artifacts.
 */
class BenchmarkFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val result = when (intent.action) {
                    ACTION_SEED -> {
                        runBlocking {
                            seed(LightNovelReaderDatabase.getInstance(context))
                        }
                        "seed=SUCCEEDED"
                    }
                    else -> "unsupported-action=${intent.action}"
                }
                pending.resultCode = Activity.RESULT_OK
                pending.resultData = result
            } catch (throwable: Throwable) {
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData =
                    "exception=${throwable::class.java.simpleName}:${throwable.message}"
            } finally {
                pending.finish()
            }
        }.start()
    }

    private suspend fun seed(database: LightNovelReaderDatabase) {
        val now = LocalDateTime.now()
        val book = BookInformationEntity(
            id = BOOK_ID,
            title = "Benchmark Sample Novel",
            subtitle = "A deterministic local test fixture",
            coverUri = Uri.EMPTY,
            author = "Benchmark Author",
            description = "Local content used to exercise every book and reader workflow without network access.",
            tags = listOf("Benchmark", "Local", "Automation"),
            publishingHouse = "Benchmark Press",
            wordCount = WordCount(12_345),
            lastUpdated = now.minusDays(1),
            isComplete = false,
        )
        database.bookInformationDao().insert(book)

        database.bookVolumesDao().insertVolume(
            VolumeEntity(
                bookId = BOOK_ID,
                volumeId = VOLUME_ID,
                volumeTitle = "Benchmark Volume",
                chapterIds = listOf(CHAPTER_ONE_ID),
                index = 0,
            )
        )
        database.bookVolumesDao().insertVolume(
            VolumeEntity(
                bookId = BOOK_ID,
                volumeId = SECOND_VOLUME_ID,
                volumeTitle = "Benchmark Bonus Volume",
                chapterIds = listOf(CHAPTER_TWO_ID),
                index = 1,
            )
        )
        database.bookVolumesDao().insertChapterInformationEntities(
            ChapterInformationEntity(CHAPTER_ONE_ID, "Benchmark Chapter One"),
            ChapterInformationEntity(CHAPTER_TWO_ID, "Benchmark Chapter Two"),
        )

        val firstContent = ContentBuilder()
            .simpleText(LONG_TEXT)
            .build()
        val secondContent = ContentBuilder()
            .simpleText("Benchmark chapter two.\n\n$LONG_TEXT")
            .build()
        database.chapterContentDao().update(
            ChapterContentEntity(
                id = CHAPTER_ONE_ID,
                title = "Benchmark Chapter One",
                content = firstContent,
                prevChapter = "",
                nextChapter = CHAPTER_TWO_ID,
            )
        )
        database.chapterContentDao().update(
            ChapterContentEntity(
                id = CHAPTER_TWO_ID,
                title = "Benchmark Chapter Two",
                content = secondContent,
                prevChapter = CHAPTER_ONE_ID,
                nextChapter = "",
            )
        )

        database.userReadingDataDao().insert(
            UserReadingDataEntity(
                id = BOOK_ID,
                lastReadTime = now,
                totalReadTime = 3_600,
                readingProgress = 0.25f,
                lastReadChapterId = CHAPTER_ONE_ID,
                lastReadChapterTitle = "Benchmark Chapter One",
                currentChapterReadingProgressMap = mapOf(CHAPTER_ONE_ID to 0.25f),
                maxChapterReadingProgressMap = mapOf(CHAPTER_ONE_ID to 0.5f),
            )
        )
        database.userDataDao().insert(
            UserDataEntity(
                path = "reading_books",
                group = "",
                type = "StringList",
                value = BOOK_ID,
            )
        )
        database.userDataDao().insert(
            UserDataEntity(
                path = "bookshelf_order",
                group = "",
                type = "StringList",
                value = BOOKSHELF_ID.toString(),
            )
        )
        database.bookshelfDao().insertBookshelf(
            BookshelfEntity(
                id = BOOKSHELF_ID,
                name = "Benchmark Shelf",
                sortType = "default",
                sortReversed = false,
                autoCache = false,
                systemUpdateReminder = false,
                allBookIds = listOf(BOOK_ID),
                pinnedBookIds = emptyList(),
                updatedBookIds = listOf(BOOK_ID),
            )
        )
        database.bookshelfDao().insertBookshelfBookMetadata(
            BookshelfBookMetadataEntity(
                id = BOOK_ID,
                lastUpdate = now,
                bookShelfIds = listOf(BOOKSHELF_ID),
            )
        )

        val today = LocalDate.now()
        database.bookRecordDao().insertBookRecord(
            BookRecordEntity(
                bookId = BOOK_ID,
                date = today,
                reads = 2,
                seconds = 3_600,
                isFavorited = true,
                firstSeen = LocalTime.of(9, 0),
                lastSeen = LocalTime.of(10, 0),
            )
        )
        val count = Count().apply {
            setMinute(9, 30)
            setMinute(10, 30)
        }
        database.dailyCountDao().insert(DailyCountEntity(today, count))
    }

    companion object {
        const val ACTION_SEED = "indi.dmzz_yyhyy.lightnovelreader.benchmark.SEED"
        // The built-in Wenku8 source parses book IDs as integers when it
        // performs its background refresh, so the fixture ID must be numeric.
        const val BOOK_ID = "9999999"
        const val VOLUME_ID = "benchmark-volume"
        const val SECOND_VOLUME_ID = "benchmark-volume-2"
        const val CHAPTER_ONE_ID = "benchmark-chapter-1"
        const val CHAPTER_TWO_ID = "benchmark-chapter-2"
        const val BOOKSHELF_ID = 1_000_001

        private val LONG_TEXT = buildString {
            repeat(30) { paragraph ->
                append("Benchmark paragraph ")
                append(paragraph + 1)
                append(". This deterministic text exercises layout, scrolling, pagination, progress, and formatting. ")
                append("It contains enough content to span multiple reader pages.\n\n")
            }
        }
    }
}
