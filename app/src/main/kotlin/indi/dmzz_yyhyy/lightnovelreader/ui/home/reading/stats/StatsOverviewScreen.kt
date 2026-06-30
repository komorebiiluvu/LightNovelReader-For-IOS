package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.StatsSummary
import indi.dmzz_yyhyy.lightnovelreader.ui.components.AnimatedText
import indi.dmzz_yyhyy.lightnovelreader.ui.components.Cover
import indi.dmzz_yyhyy.lightnovelreader.ui.components.HeatMapCalendar
import indi.dmzz_yyhyy.lightnovelreader.ui.components.calendar.core.CalendarDay
import indi.dmzz_yyhyy.lightnovelreader.ui.components.calendar.core.CalendarMonth
import indi.dmzz_yyhyy.lightnovelreader.ui.components.calendar.core.CalendarWeek
import indi.dmzz_yyhyy.lightnovelreader.ui.components.calendar.core.displayText
import indi.dmzz_yyhyy.lightnovelreader.ui.components.calendar.core.yearMonth
import indi.dmzz_yyhyy.lightnovelreader.ui.components.calendar.rememberHeatMapCalendarState
import indi.dmzz_yyhyy.lightnovelreader.utils.DurationFormat
import indi.dmzz_yyhyy.lightnovelreader.utils.navigationBarSpacer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewScreen(
    onClickBack: () -> Unit,
    viewModel: StatsOverviewViewModel,
) {
    val uiState = viewModel.uiState
    val pinnedScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haptic = LocalHapticFeedback.current
    var selectedStatsItem by remember { mutableStateOf<StatsOverviewItem?>(null) }

    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = pinnedScrollBehavior,
                onClickBack = onClickBack,
            )
        }
    ) { paddingValues ->
        Crossfade(
            targetState = uiState.isLoading
        ) { isLoading ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CalendarBlock(
                            viewModel,
                            onSelectedDate = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectDate(it)
                            }
                        )
                    }
                    item { DailyStatsBlock(uiState) }
                    stickyHeader {
                        RangeSelectorBlock(
                            uiState = uiState,
                            onViewSelected = viewModel::setSelectedView
                        )
                    }
                    item {
                        RangeStatsBlock(uiState)
                    }
                    stickyHeader { Modifier.fillMaxWidth() }
                    item { OverallReadingBlock(uiState) }
                    item {
                        SummaryBlock(
                            summary = uiState.totalSummary,
                            onClickItem = { selectedStatsItem = it }
                        )
                    }
                    item { SinceStatsText(uiState.firstStatsDate) }
                    navigationBarSpacer()
                }
            }
        }
    }

    selectedStatsItem?.let { item ->
        StatsOverviewBottomSheet(
            item = item,
            uiState = uiState,
            onDismissRequest = { selectedStatsItem = null }
        )
    }
}

@Composable
private fun RangeSelectorBlock(
    uiState: StatsOverviewUiState,
    onViewSelected: (Int) -> Unit
) {
    val labels = listOf(
        stringResource(R.string.view_weekly),
        stringResource(R.string.view_monthly),
        stringResource(R.string.view_yearly)
    )

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp)
            .padding(bottom = 6.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    modifier = Modifier.weight(1f),
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    selected = uiState.selectedViewIndex == index,
                    onClick = { onViewSelected(index) }
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun RangeStatsBlock(
    uiState: StatsOverviewUiState
) {
    val locale = LocalLocale.current.platformLocale
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy/MM", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("MM/dd", locale) }
    val rangeText = when (uiState.selectedViewIndex) {
        0 -> stringResource(
            R.string.stats_selected_range,
            uiState.targetDateRange.first.format(dayFormatter),
            uiState.targetDateRange.second.format(dayFormatter)
        )
        1 -> YearMonth.from(uiState.selectedDate).format(monthFormatter)
        else -> uiState.selectedDate.year.toString()
    }

    Spacer(Modifier.height(8.dp))
    ActivityStatsCard(uiState)
    StatsCard(title = stringResource(R.string.stats_reading_statistics_of, rangeText)) {
        AnimatedContent(
            targetState = uiState.selectedViewIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it * direction / 5 })
                    .togetherWith(
                        fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it * direction / 5 }
                    )
            },
            label = "StatsReadingChart"
        ) { index ->
            when (index) {
                0 -> WeeklyStatsChart(
                    statsMap = uiState.targetDateRangeCountMap,
                    selectedDate = uiState.selectedDate
                )
                1 -> MonthlyStatsChart(
                    statsMap = uiState.targetDateRangeCountMap,
                    selectedDate = uiState.selectedDate
                )
                else -> YearlyStatsChart(
                    statsMap = uiState.targetDateRangeCountMap,
                    selectedDate = uiState.selectedDate
                )
            }
        }
    }
}

@Composable
private fun SinceStatsText(firstStatsDate: LocalDate?) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        text = stringResource(
            R.string.stats_since_date,
            firstStatsDate?.toString() ?: "--"
        ),
        style = typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SummaryBlock(
    summary: StatsSummary?,
    onClickItem: (StatsOverviewItem) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Text(
            text = stringResource(R.string.total_record),
            style = typography.titleMedium,
            fontWeight = FontWeight.W600
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatsOverviewItem.entries.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        SummaryItem(
                            modifier = Modifier.weight(1f),
                            item = item,
                            value = item.value(summary).toString(),
                            onClick = { onClickItem(item) }
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarBlock(
    viewModel: StatsOverviewViewModel,
    onSelectedDate: (LocalDate) -> Unit,
) {
    val uiState = viewModel.uiState
    val now = LocalDate.now()
    val startDate = uiState.startDate
    val state = rememberHeatMapCalendarState(
        startMonth = startDate.yearMonth,
        endMonth = now.yearMonth,
        firstVisibleMonth = LocalDate.now().yearMonth,
        firstDayOfWeek = DayOfWeek.MONDAY
    )

    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Text(
            text = stringResource(R.string.calendar),
            style = typography.titleMedium,
            fontWeight = FontWeight.W600
        )
        Spacer(Modifier.height(8.dp))
        HeatMapCalendar(
            state = state,
            contentPadding = PaddingValues(end = 6.dp),
            dayContent = { day, week ->
                val isClicked = uiState.selectedDate == day.date
                val level = uiState.dateLevelMap[day.date] ?: Level.Zero
                Day(
                    selected = isClicked,
                    day = day,
                    startDate = startDate,
                    endDate = now,
                    week = week,
                    level = level,
                ) { date ->
                    onSelectedDate(date)
                }
            },
            weekHeader = { WeekHeader(it) },
            monthHeader = { MonthHeader(it, LocalDate.now()) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.heatmap_indicator_less),
                style = typography.bodySmall
            )
            Spacer(Modifier.width(6.dp))
            Level.entries.forEach { level ->
                LevelBox(level.color)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.heatmap_indicator_more, uiState.thresholds),
                style = typography.bodySmall
            )
        }
    }
}

@Composable
private fun OverallReadingBlock(
    uiState: StatsOverviewUiState
) {
    val bookTimes = uiState.bookRecordsByDate.values
        .flatten()
        .groupBy { it.bookId }
        .mapValues { (_, records) -> records.sumOf { it.seconds } }
    val totalSeconds = bookTimes.values.sum()
    val topBooks = bookTimes
        .toList()
        .sortedByDescending { it.second }
        .take(10)

    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Text(
            text = stringResource(R.string.reading_duration),
            style = typography.titleMedium,
            fontWeight = FontWeight.W600
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (totalSeconds > 0) {
                        DurationFormat().format(
                            totalSeconds.toDuration(DurationUnit.SECONDS),
                            DurationFormat.Unit.MINUTE,
                            DurationFormat.Unit.HOUR
                        )
                    } else {
                        "--"
                    },
                    style = typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 2.dp)
                )
                Text(
                    text = stringResource(R.string.stats_top_books),
                    style = typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topBooks.isEmpty()) {
                    NoRecords()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topBooks.forEachIndexed { index, (bookId, seconds) ->
                            val book = uiState.bookInformationMap[bookId]
                            TopBookItem(
                                rank = index + 1,
                                title = book?.title
                                    ?.takeIf(String::isNotBlank)
                                    ?: bookId,
                                coverUri = book?.coverUri ?: Uri.EMPTY,
                                seconds = seconds,
                                totalSeconds = totalSeconds
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyStatsBlock(
    uiState: StatsOverviewUiState,
) {
    val selectedDate = uiState.selectedDate
    val details = uiState.selectedDateDetails

    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        Row(
            modifier = Modifier.height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedText(
                text = selectedDate.toString(),
                style = typography.titleMedium,
                fontWeight = FontWeight.W600
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatSection(
                icon = painterResource(R.drawable.schedule_90dp),
                title = stringResource(R.string.activity_reading_time),
                value = details?.formattedTotalTime ?: "--"
            ) {
                Crossfade(targetState = details?.timeDetails.isNullOrEmpty(), label = "") { isEmpty ->
                    if (isEmpty) {
                        NoRecords()
                    } else {
                        Column {
                            details?.timeDetails?.forEach {
                                val duration = it.second.toDuration(DurationUnit.SECONDS)
                                val formattedTime = DurationFormat().format(duration, DurationFormat.Unit.MINUTE)
                                DataItem(it.first.title, formattedTime)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun NoRecords() {
    Text(
        text = stringResource(R.string.no_records),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        style = typography.bodyMedium
    )
}

@Composable
private fun StatSection(
    icon: Painter,
    title: String,
    value: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                style = typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        content()
    }
}

@Composable
private fun DataItem(leftText: String, rightText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = leftText,
            style = typography.bodyMedium,
            maxLines = 1,
            overflow = Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = rightText,
            style = typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
    }
}

@Composable
private fun SummaryItem(
    modifier: Modifier = Modifier,
    item: StatsOverviewItem,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(item.titleRes),
                style = typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = typography.titleLarge,
                fontWeight = FontWeight.W600
            )
        }
    }
}

@Composable
private fun TopBookItem(
    rank: Int,
    title: String,
    coverUri: Uri,
    seconds: Int,
    totalSeconds: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(
            width = 30.dp,
            height = 42.dp,
            uri = coverUri,
            rounded = 4.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rank.toString().padStart(2, '0'),
                    style = typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = typography.bodyMedium,
                    maxLines = 1,
                    overflow = Ellipsis
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = DurationFormat().format(
                        seconds.toDuration(DurationUnit.SECONDS),
                        DurationFormat.Unit.MINUTE,
                        DurationFormat.Unit.HOUR
                    ),
                    style = typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            (seconds.toFloat() / totalSeconds.coerceAtLeast(1).toFloat())
                                .coerceIn(0f, 1f)
                        )
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onClickBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.nav_statistics),
                style = typography.displayLarge,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onClickBack) {
                Icon(
                    painter = painterResource(id = R.drawable.arrow_back_24px),
                    contentDescription = "back"
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun Day(
    selected: Boolean? = false,
    day: CalendarDay,
    startDate: LocalDate,
    endDate: LocalDate,
    week: CalendarWeek,
    level: Level,
    onClick: (LocalDate) -> Unit,
) {
    val weekDates = week.days.map { it.date }
    val isWeekday = (day.date.dayOfWeek.value in 1..5)


    if (day.date in startDate..endDate) {
        LevelBox(
            color = if (isWeekday) level.color else level.colorWeekends,
            selected = selected,
        ) {
            onClick(day.date)
        }
    } else if (weekDates.contains(startDate)) {
        LevelBox(
            color = Color.Transparent,
        )
    }
}

@Composable
private fun LevelBox(
    color: Color,
    selected: Boolean? = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .let { modifier ->
                if (selected == true) {
                    modifier.border(1.dp, MaterialTheme.colorScheme.onSurface)
                } else {
                    modifier
                }
            }
            .background(color = color)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
    )
}

@Composable
private fun WeekHeader(dayOfWeek: DayOfWeek) {
    val text = if (dayOfWeek in listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SUNDAY
        )
    ) dayOfWeek.displayText() else ""

    Box(
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            text = text,
            style = typography.headlineMedium,
        )
    }
}


@Composable
private fun MonthHeader(
    calendarMonth: CalendarMonth,
    endDate: LocalDate,
) {
    if (calendarMonth.weekDays.first().first().date <= endDate) {
        val month = calendarMonth.yearMonth
        val title = if (month.month == Month.JANUARY) {
            month.displayText(short = true)
        } else {
            month.month.displayText()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            Text(text = title, style = typography.headlineMedium)
        }
    }
}
