package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import android.util.Log
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nightfish.lightnovelreader.api.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookRepository
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.Count
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.StatsMonthSessions
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.StatsRepository
import indi.dmzz_yyhyy.lightnovelreader.utils.DurationFormat
import indi.dmzz_yyhyy.lightnovelreader.utils.quickSelect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.collections.set
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class StatsOverviewViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    private var _uiState = MutableStatisticsOverviewUiState()
    val uiState: StatsOverviewUiState = _uiState
    private var allDailyCounts: Map<LocalDate, Count> = emptyMap()

    init {
        reloadData()
    }

    private fun reloadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.isLoading = true

            val time = System.currentTimeMillis()
            Log.d("AppReadingStats", "Refresh started")
            val endDate = LocalDate.now()
            val overviewCache = statsRepository.getOverviewCache()
            val firstStatsDate = overviewCache.firstStatsDate
            val firstDate = firstStatsDate ?: endDate

            _uiState.startDate = firstDate
            _uiState.firstStatsDate = firstStatsDate
            _uiState.totalSummary = overviewCache.summary

            val selectedDate = when {
                _uiState.selectedDate.isBefore(firstDate) -> endDate
                _uiState.selectedDate.isAfter(endDate) -> endDate
                else -> _uiState.selectedDate
            }
            _uiState.selectedDate = selectedDate

            val dailyCounts = statsRepository.getDailyCounts(firstDate, endDate)
            allDailyCounts = dailyCounts
            generateLevelMap(dailyCounts)

            val bookRecordsMap = statsRepository.getBookRecords(firstDate, endDate)

            _uiState.bookRecordsByDate = bookRecordsMap
            _uiState.monthlySessions = getMonthlySessions(firstStatsDate, endDate, overviewCache.monthlySessions)
            _uiState.readBookIds = overviewCache.readBooks.map { it.bookId }
            _uiState.finishedBookIds = overviewCache.finishedBooks.map { it.bookId }
            _uiState.favoritedBookIds = overviewCache.favoritedBooks.map { it.bookId }
            _uiState.bookFirstReadDateMap = overviewCache.readBooks.associate { it.bookId to it.date }
            _uiState.bookFirstFinishedDateMap = overviewCache.finishedBooks.associate { it.bookId to it.date }
            _uiState.bookFavoriteDateMap = overviewCache.favoritedBooks.associate { it.bookId to it.date }
            _uiState.bookInformationMap.clear()
            val allBookIds = (
                bookRecordsMap
                .flatMap { it.value.map { record -> record.bookId } }
                    + _uiState.readBookIds.take(10)
                    + _uiState.finishedBookIds.take(10)
                    + _uiState.favoritedBookIds.take(10)
                )
                .distinct()

            allBookIds.fastForEach { id ->
                _uiState.bookInformationMap[id] = bookRepository.getStateBookInformation(id, viewModelScope)
            }
            getDateDetails(selectedDate)
            loadSelectedRange(selectedDate)

            _uiState.isLoading = false
            val elapsed = (System.currentTimeMillis() - time) / 1000.0
            Log.d("AppReadingStats", "Refresh completed in $elapsed seconds")
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.selectedDate = date
        getDateDetails(date)
        viewModelScope.launch(Dispatchers.IO) {
            loadSelectedRange(date)
        }
    }

    fun setSelectedView(index: Int) {
        if (_uiState.selectedViewIndex == index) return
        _uiState.selectedViewIndex = index
        viewModelScope.launch(Dispatchers.IO) {
            loadSelectedRange(_uiState.selectedDate)
        }
    }

    private fun getDateDetails(selectedDate: LocalDate) {
        val records = _uiState.bookRecordsByDate[selectedDate] ?: emptyList()
        if (records.isEmpty()) {
            _uiState.selectedDateDetails = null
            return
        }
        var totalSeconds = 0L
        val detailsList = mutableListOf<Pair<BookInformation, Int>>()

        for (rec in records) {
            val seconds = rec.seconds
            totalSeconds += seconds

            val bookInfo = _uiState.bookInformationMap[rec.bookId] ?: BookInformation.empty()
            detailsList += bookInfo to seconds
        }

        val sortedDetails = detailsList
            .sortedByDescending { it.second }
            .toMutableList()

        val formattedTotal = DurationFormat()
            .format(totalSeconds.toDuration(DurationUnit.SECONDS), DurationFormat.Unit.MINUTE)

        _uiState.selectedDateDetails = DailyDateDetails(
            formattedTotalTime = formattedTotal,
            timeDetails = sortedDetails
        )
    }

    private fun generateLevelMap(
        dailyCounts: Map<LocalDate, Count>
    ) {
        val dateTotalTimeMap = dailyCounts.mapValues { (_, count) -> count.getTotalMinutes() }
        val localDateList = dateTotalTimeMap.keys.sorted()
        val readingTimes = dateTotalTimeMap.values.toList()
        val thresholds = readingTimes.filter { it > 0 }.run {
            if (isEmpty()) listOf(0, 0, 0) else listOf(
                quickSelect(this, 0.25),
                quickSelect(this, 0.5),
                quickSelect(this, 0.75)
            )
        }
        _uiState.thresholds = thresholds[2]

        val dateLevelMap = localDateList.associateWith { date ->
            val readingTime = dateTotalTimeMap[date] ?: 0
            when {
                thresholds.all { it == 0 } -> Level.Zero
                readingTime >= thresholds[2] -> Level.Four
                readingTime >= thresholds[1] -> Level.Three
                readingTime >= thresholds[0] -> Level.Two
                readingTime > 0 -> Level.One
                else -> Level.Zero
            }
        }
        _uiState.dateLevelMap = dateLevelMap
    }

    private fun getMonthlySessions(
        firstStatsDate: LocalDate?,
        endDate: LocalDate,
        cachedSessions: List<StatsMonthSessions>
    ): List<Pair<YearMonth, Int>> {
        val firstMonth = YearMonth.from(firstStatsDate ?: endDate)
        val lastMonth = YearMonth.from(endDate)
        val months = generateSequence(firstMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(lastMonth) }
            .toList()
        val sessions = cachedSessions
            .associate { YearMonth.of(it.year, it.month) to it.sessions }

        return months.map { it to (sessions[it] ?: 0) }
    }

    private fun loadSelectedRange(selectedDate: LocalDate) {
        val viewOption = StatsViewOption.fromIndex(_uiState.selectedViewIndex)
        val range = viewOption.rangeFor(selectedDate)
        val startDate = maxOf(range.start, _uiState.startDate)
        val endDate = minOf(range.endInclusive, LocalDate.now())
        val dates = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .toList()

        _uiState.targetDateRange = startDate to endDate
        _uiState.targetDateRangeRecordsMap = dates.associateWith { _uiState.bookRecordsByDate[it].orEmpty() }
        _uiState.targetDateRangeCountMap = dates.associateWith { allDailyCounts[it] ?: Count() }

        val ids = _uiState.targetDateRangeRecordsMap.values
            .flatten()
            .map { it.bookId }
            .toSet()
        ids.forEach { id ->
            if (!_uiState.bookInformationMap.containsKey(id)) {
                _uiState.bookInformationMap[id] = bookRepository.getStateBookInformation(id, viewModelScope)
            }
        }
    }
}
