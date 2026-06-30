package indi.dmzz_yyhyy.lightnovelreader.data.statistics

import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.BookRecordDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.dao.DailyCountDao
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.BookRecordEntity
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.entity.DailyCountEntity
import indi.dmzz_yyhyy.lightnovelreader.data.userdata.UserDataRepository
import io.nightfish.lightnovelreader.api.userdata.UserDataPath
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val bookRecordDao: BookRecordDao,
    private val dailyCountDao: DailyCountDao,
    private val userDataRepository: UserDataRepository
) {
    private companion object {
        const val LEGACY_TOTAL_BOOK_ID = "-721"
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val userData = userDataRepository.stringUserData(UserDataPath.Statistics.SummaryCache.path)

    fun getStatsStore(): StatsCache? = userData.get()?.let {
        runCatching { json.decodeFromString<StatsCache>(it) }.getOrNull()
    }

    fun setStatsStore(cache: StatsCache) {
        userData.set(json.encodeToString(cache))
    }

    fun clearStatsStore() {
        userDataRepository.remove(UserDataPath.Statistics.SummaryCache.path)
    }

    private val bookReadTimeBuffer = mutableMapOf<String, Pair<LocalTime, Int>>()

    suspend fun accumulateBookReadTime(bookId: String, seconds: Int) {
        if (seconds < 0) {
            bookReadTimeBuffer.keys.toList().forEach { _ ->
                clearBookReadTimeBuffer(bookId)
                bookReadTimeBuffer.remove(bookId)
            }
            return
        }
        val current = bookReadTimeBuffer[bookId] ?: Pair(LocalTime.now(), 0)
        val newTotal = current.second + seconds
        bookReadTimeBuffer[bookId] = current.copy(second = newTotal)

        if (newTotal >= 60 || Duration.between(current.first, LocalTime.now()).seconds >= 60) {
            clearBookReadTimeBuffer(bookId)
        }
    }

    private suspend fun clearBookReadTimeBuffer(bookId: String) {
        val (startTime, totalSeconds) = bookReadTimeBuffer[bookId] ?: return

        updateReadingStatistics(
            ReadingStatsUpdate(
                bookId = bookId,
                secondDelta = totalSeconds,
                localTime = startTime,
                readEventDelta = 0
            )
        )

        bookReadTimeBuffer.clear()
    }

    suspend fun getBookRecords(
        start: LocalDate,
        end: LocalDate? = null
    ): Map<LocalDate, List<BookRecord>> {
        return if (end == null) {
            bookRecordDao.getBookRecordsForDate(start)
                .filterRealBooks()
                .map { it.toData() }
                .takeIf { it.isNotEmpty() }
                ?.let { mapOf(start to it) }
                ?: emptyMap()
        } else {
            bookRecordDao
                .getBookRecordsBetweenDates(start, end)
                .filterRealBooks()
                .map { it.toData() }
                .groupBy { it.date }
                .filterValues { it.isNotEmpty() }
        }
    }

    suspend fun getDailyCounts(start: LocalDate, end: LocalDate): Map<LocalDate, Count> {
        return dailyCountDao.getBetween(start, end)
            .associate { it.date to it.timeCount }
    }

    suspend fun getFirstDate(): LocalDate? {
        val bookDate = bookRecordDao.getFirstDateExcept(LEGACY_TOTAL_BOOK_ID)
        val dailyDate = dailyCountDao.getFirstDate()

        return listOfNotNull(bookDate, dailyDate).minOrNull()
    }

    suspend fun getOverviewCache(): StatsCache {
        val today = LocalDate.now()
        val cache = syncCache(today)
        val todayCount = dailyCountDao.getByDate(today)
        val todayRecords = bookRecordDao.getBookRecordsForDate(today).filterRealBooks()

        if (todayCount == null && todayRecords.isEmpty()) {
            return cache
        }
        val firstStatsDate = cache.firstStatsDate ?: today

        val readBooks = cache.readBooks.toBookDateMap()
        val finishedBooks = cache.finishedBooks.toBookDateMap()
        val favoritedBooks = cache.favoritedBooks.toBookDateMap()
        val monthlySessions = cache.monthlySessions.toMonthSessionsMap()

        todayRecords.forEach { record ->
            if (record.reads > 0 || record.seconds > 0) readBooks.putIfAbsent(record.bookId, today)
            if (record.isFinished) finishedBooks.putIfAbsent(record.bookId, today)
            if (record.isFavorited) favoritedBooks.putIfAbsent(record.bookId, today)
        }

        val todayMinutes = todayCount?.timeCount?.getTotalMinutes() ?: 0
        val todayReads = todayRecords.sumOf { it.reads }
        if (todayReads > 0) {
            val month = YearMonth.from(today)
            monthlySessions[month] = (monthlySessions[month] ?: 0) + todayReads
        }
        val hasActivityToday = todayMinutes > 0
        val todayStreak = if (hasActivityToday) cache.tailStreak + 1 else 0

        return cache.copy(
            firstStatsDate = firstStatsDate,
            summary = cache.summary.copy(
                totalMinutes = cache.summary.totalMinutes + todayMinutes,
                totalReadCount = cache.summary.totalReadCount + todayReads,
                activeDays = cache.summary.activeDays + if (hasActivityToday) 1 else 0,
                currentStreak = todayStreak,
                longestStreak = maxOf(cache.summary.longestStreak, todayStreak),
                readBooks = readBooks.size,
                finishedBooks = finishedBooks.size,
                favoritedBooks = favoritedBooks.size,
            ),
            readBookIds = readBooks.keys.sorted(),
            finishedBookIds = finishedBooks.keys.sorted(),
            favoritedBookIds = favoritedBooks.keys.sorted(),
            monthlySessions = monthlySessions.toStatsMonthSessions(),
            readBooks = readBooks.toStatsBookDate(),
            finishedBooks = finishedBooks.toStatsBookDate(),
            favoritedBooks = favoritedBooks.toStatsBookDate(),
        )
    }

    suspend fun getSummary(): StatsSummary = getOverviewCache().summary

    suspend fun syncCache(today: LocalDate = LocalDate.now()): StatsCache {
        val targetDate = today.minusDays(1)
        val cache = getStatsStore()?.takeIf {
            it.firstStatsDate != null || it.summary == StatsSummary()
        }

        if (cache != null && !cache.aggregatedUntil.isBefore(targetDate)) {
            val nextCache = cache.copy(
                summary = cache.summary.copy(currentStreak = cache.tailStreak)
            )
            if (nextCache != cache) setStatsStore(nextCache)
            return nextCache
        }

        val firstDate = getFirstDate()
        if (firstDate == null || firstDate.isAfter(targetDate)) {
            return StatsCache(
                aggregatedUntil = targetDate,
                firstStatsDate = firstDate
            ).also {
                setStatsStore(it)
            }
        }

        val startDate = cache?.aggregatedUntil?.plusDays(1) ?: firstDate
        if (startDate.isAfter(targetDate)) {
            val nextCache = cache?.copy(
                aggregatedUntil = targetDate,
                firstStatsDate = cache.firstStatsDate ?: firstDate
            ) ?: StatsCache(
                aggregatedUntil = targetDate,
                firstStatsDate = firstDate
            )
            setStatsStore(nextCache)
            return nextCache
        }

        val nextCache = accumulateCache(
            cache = cache,
            firstStatsDate = firstDate,
            startDate = startDate,
            endDate = targetDate
        )
        setStatsStore(nextCache)
        return nextCache
    }

    fun clearSummaryCache() {
        clearStatsStore()
    }

    suspend fun updateReadingStatistics(update: ReadingStatsUpdate) {
        val today = LocalDate.now()

        val existingDailyCount = dailyCountDao.getByDate(today)
            ?: DailyCountEntity(today, Count())
        val updatedDailyCount = existingDailyCount.copy(
            timeCount = updateCount(existingDailyCount.timeCount, update)
        )
        dailyCountDao.insert(updatedDailyCount)

        val existingRecord = bookRecordDao.getBookRecordByIdAndDate(update.bookId, today)
            ?: createRecordEntity(update.bookId, today)

        val updatedRecord = existingRecord.copy(
            reads = existingRecord.reads + update.readEventDelta,
            seconds = existingRecord.seconds + update.secondDelta,
            lastSeen = update.localTime,
        )
        bookRecordDao.insertBookRecord(updatedRecord)
        bookReadTimeBuffer.clear()
    }

    suspend fun markBookFinished(bookId: String) {
        val today = LocalDate.now()
        val existingRecord = bookRecordDao.getBookRecordByIdAndDate(bookId, today)
            ?: createRecordEntity(bookId, today)

        if (!existingRecord.isFinished) {
            bookRecordDao.insertBookRecord(existingRecord.copy(isFinished = true))
        }
    }

    suspend fun markBookFavorited(bookId: String) {
        val today = LocalDate.now()
        val existingRecord = bookRecordDao.getBookRecordByIdAndDate(bookId, today)
            ?: createRecordEntity(bookId, today)

        if (!existingRecord.isFavorited) {
            bookRecordDao.insertBookRecord(existingRecord.copy(isFavorited = true))
        }
    }

    /*suspend fun getBookFirstReadDate(bookId: String): LocalDate? =
        if (bookId == LEGACY_TOTAL_BOOK_ID) null else bookRecordDao.getFirstReadDate(bookId)

    suspend fun getBookFinishedDate(bookId: String): LocalDate? =
        if (bookId == LEGACY_TOTAL_BOOK_ID) null else bookRecordDao.getFirstFinishedDate(bookId)

    suspend fun getBookFirstReadDateMap(): Map<String, LocalDate> =
        bookRecordDao.getFirstReadDates()
            .filterNot { it.bookId == LEGACY_TOTAL_BOOK_ID }
            .associate { it.bookId to it.date }

    suspend fun getBookFirstFinishedDateMap(): Map<String, LocalDate> =
        bookRecordDao.getFirstFinishedDates()
            .filterNot { it.bookId == LEGACY_TOTAL_BOOK_ID }
            .associate { it.bookId to it.date }

    suspend fun getBookFavoriteDateMap(): Map<String, LocalDate> =
        bookRecordDao.getFirstFavoritedDates()
            .filterNot { it.bookId == LEGACY_TOTAL_BOOK_ID }
            .associate { it.bookId to it.date }*/

    private suspend fun accumulateCache(
        cache: StatsCache?,
        firstStatsDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate
    ): StatsCache {
        val counts = dailyCountDao.getBetween(startDate, endDate).associateBy { it.date }
        val records = bookRecordDao
            .getBookRecordsBetweenDates(startDate, endDate)
            .filterRealBooks()
            .groupBy { it.date }

        var totalMinutes = cache?.summary?.totalMinutes ?: 0
        var totalReadCount = cache?.summary?.totalReadCount ?: 0
        var activeDays = cache?.summary?.activeDays ?: 0
        var longestStreak = cache?.summary?.longestStreak ?: 0
        var tailStreak = cache?.tailStreak ?: 0

        val readBooks = cache?.readBooks?.toBookDateMap() ?: mutableMapOf()
        val finishedBooks = cache?.finishedBooks?.toBookDateMap() ?: mutableMapOf()
        val favoritedBooks = cache?.favoritedBooks?.toBookDateMap() ?: mutableMapOf()
        val monthlySessions = cache?.monthlySessions?.toMonthSessionsMap() ?: mutableMapOf()

        var date = startDate
        while (!date.isAfter(endDate)) {
            val dayCount = counts[date]
            val dayRecords = records[date].orEmpty()
            val dayMinutes = dayCount?.timeCount?.getTotalMinutes() ?: 0

            totalMinutes += dayMinutes
            val dayReads = dayRecords.sumOf { it.reads }
            totalReadCount += dayReads
            if (dayReads > 0) {
                val month = YearMonth.from(date)
                monthlySessions[month] = (monthlySessions[month] ?: 0) + dayReads
            }

            if (dayMinutes > 0) {
                activeDays++
                tailStreak++
                longestStreak = maxOf(longestStreak, tailStreak)
            } else {
                tailStreak = 0
            }

            dayRecords.forEach { record ->
                if (record.reads > 0 || record.seconds > 0) readBooks.putIfAbsent(record.bookId, date)
                if (record.isFinished) finishedBooks.putIfAbsent(record.bookId, date)
                if (record.isFavorited) favoritedBooks.putIfAbsent(record.bookId, date)
            }

            date = date.plusDays(1)
        }

        return StatsCache(
            aggregatedUntil = endDate,
            firstStatsDate = firstStatsDate,
            summary = StatsSummary(
                totalMinutes = totalMinutes,
                totalReadCount = totalReadCount,
                activeDays = activeDays,
                currentStreak = tailStreak,
                longestStreak = longestStreak,
                readBooks = readBooks.size,
                finishedBooks = finishedBooks.size,
                favoritedBooks = favoritedBooks.size,
            ),
            readBookIds = readBooks.keys.sorted(),
            finishedBookIds = finishedBooks.keys.sorted(),
            favoritedBookIds = favoritedBooks.keys.sorted(),
            monthlySessions = monthlySessions.toStatsMonthSessions(),
            readBooks = readBooks.toStatsBookDate(),
            finishedBooks = finishedBooks.toStatsBookDate(),
            favoritedBooks = favoritedBooks.toStatsBookDate(),
            tailStreak = tailStreak,
        )
    }

    private fun List<StatsMonthSessions>.toMonthSessionsMap(): MutableMap<YearMonth, Int> =
        associate { YearMonth.of(it.year, it.month) to it.sessions }.toMutableMap()

    private fun Map<YearMonth, Int>.toStatsMonthSessions(): List<StatsMonthSessions> =
        toList()
            .sortedBy { it.first }
            .map { (month, sessions) ->
                StatsMonthSessions(
                    year = month.year,
                    month = month.monthValue,
                    sessions = sessions
                )
            }

    private fun List<StatsBookDate>.toBookDateMap(): MutableMap<String, LocalDate> =
        filterNot { it.bookId == LEGACY_TOTAL_BOOK_ID }
            .associate { it.bookId to it.date }
            .toMutableMap()

    private fun Map<String, LocalDate>.toStatsBookDate(): List<StatsBookDate> =
        toList()
            .sortedByDescending { it.second }
            .map { (bookId, date) -> StatsBookDate(bookId, date) }

    private fun List<BookRecordEntity>.filterRealBooks(): List<BookRecordEntity> =
        filterNot { it.bookId == LEGACY_TOTAL_BOOK_ID }

    private fun createRecordEntity(bookId: String, date: LocalDate): BookRecordEntity =
        BookRecordEntity(
            bookId = bookId,
            date = date,
            reads = 0,
            seconds = 0,
            isFinished = false,
            isFavorited = false,
            firstSeen = LocalTime.now(),
            lastSeen = LocalTime.now(),
        )

    private fun updateCount(count: Count, update: ReadingStatsUpdate): Count {
        val minutesDelta = update.secondDelta / 60
        if (minutesDelta > 0) {
            val hour = update.localTime.hour
            val totalMinutes = count.getMinute(hour) + minutesDelta
            count.setMinute(hour, totalMinutes.coerceAtMost(60))
        }
        return count
    }

    fun clear() {
        bookRecordDao.clear()
        dailyCountDao.clear()
        clearSummaryCache()
    }
}
