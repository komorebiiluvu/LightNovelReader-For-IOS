package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.BookRecord
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.Count
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.StatsSummary
import io.nightfish.lightnovelreader.api.book.BookInformation
import java.time.LocalDate
import java.time.YearMonth

interface StatsDetailedUiState {
    val targetDateRangeCountMap: Map<LocalDate, Count>
    val targetDateRangeRecordsMap: Map<LocalDate, List<BookRecord>>
    val targetDateRange: Pair<LocalDate, LocalDate>
    var selectedChartDateRange: Pair<LocalDate, LocalDate>?
    var selectedDate: LocalDate
    var selectedViewIndex: Int
    val isLoading: Boolean
    val bookInformationMap: Map<String, BookInformation>
    val bookFirstReadDateMap: Map<String, LocalDate>
    val bookFirstFinishedDateMap: Map<String, LocalDate>
    val bookFavoriteDateMap: Map<String, LocalDate>
}

val StatsDetailedUiState.currentViewOption: StatsViewOption
    get() = StatsViewOption.fromIndex(selectedViewIndex)

val StatsDetailedUiState.currentDateRange: ClosedRange<LocalDate>
    get() = StatsViewOption.fromIndex(selectedViewIndex).rangeFor(selectedDate)

interface StatsOverviewUiState : StatsDetailedUiState {
    override var isLoading: Boolean
    var selected: Boolean
    override var selectedDate: LocalDate
    var thresholds: Int
    var startDate: LocalDate
    var dateLevelMap: Map<LocalDate, Level>
    var firstStatsDate: LocalDate?
    var monthlySessions: List<Pair<YearMonth, Int>>
    var readBookIds: List<String>
    var finishedBookIds: List<String>
    var favoritedBookIds: List<String>
    var totalSummary: StatsSummary?
    var bookRecordsByDate: Map<LocalDate, List<BookRecord>>
    override val bookInformationMap: Map<String, BookInformation>
    var selectedDateDetails: DailyDateDetails?
}

class MutableStatisticsOverviewUiState : StatsOverviewUiState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var selected: Boolean by mutableStateOf(false)
    override var selectedDate: LocalDate by mutableStateOf(LocalDate.now())
    override var thresholds: Int by mutableIntStateOf(0)
    override var startDate: LocalDate by mutableStateOf(LocalDate.now().minusMonths(6))
    override var dateLevelMap: Map<LocalDate, Level> by mutableStateOf(emptyMap())
    override var firstStatsDate: LocalDate? by mutableStateOf(null)
    override var monthlySessions: List<Pair<YearMonth, Int>> by mutableStateOf(emptyList())
    override var readBookIds: List<String> by mutableStateOf(emptyList())
    override var finishedBookIds: List<String> by mutableStateOf(emptyList())
    override var favoritedBookIds: List<String> by mutableStateOf(emptyList())
    override var totalSummary: StatsSummary? by mutableStateOf(null)
    override var bookRecordsByDate: Map<LocalDate, List<BookRecord>> by mutableStateOf(emptyMap())
    override var bookInformationMap = mutableStateMapOf<String, BookInformation>()
    override var selectedDateDetails: DailyDateDetails? by mutableStateOf(null)
    override var targetDateRangeCountMap: Map<LocalDate, Count> by mutableStateOf(emptyMap())
    override var targetDateRangeRecordsMap: Map<LocalDate, List<BookRecord>> by mutableStateOf(emptyMap())
    override var targetDateRange: Pair<LocalDate, LocalDate> by mutableStateOf(LocalDate.now() to LocalDate.now())
    override var selectedChartDateRange: Pair<LocalDate, LocalDate>? by mutableStateOf(null)
    override var selectedViewIndex: Int by mutableIntStateOf(0)
    override var bookFirstReadDateMap: Map<String, LocalDate> by mutableStateOf(emptyMap())
    override var bookFirstFinishedDateMap: Map<String, LocalDate> by mutableStateOf(emptyMap())
    override var bookFavoriteDateMap: Map<String, LocalDate> by mutableStateOf(emptyMap())
}
