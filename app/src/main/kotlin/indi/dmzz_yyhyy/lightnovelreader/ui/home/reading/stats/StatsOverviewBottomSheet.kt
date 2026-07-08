package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import android.icu.text.NumberFormat
import android.icu.util.MeasureUnit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.utils.formatMeasureUnitName
import java.time.YearMonth

private val MonthLabelKey = ExtraStore.Key<List<String>>()
private val MonthLabelFormatter = CartesianValueFormatter { context, x, _ ->
    val labels = context.model.extraStore[MonthLabelKey]
    if (labels.isEmpty()) "" else labels[x.toInt().coerceIn(labels.indices)]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewBottomSheet(
    item: StatsOverviewItem,
    uiState: StatsOverviewUiState,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Expanded,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.Expanded
        )
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        tonalElevation = 2.dp,
        dragHandle = null
    ) {
        StatsOverviewSheetLayout(
            item = item,
            onDismissRequest = onDismissRequest
        ) {
            when (item) {
                StatsOverviewItem.Sessions -> StatsSessionsSheetContent(uiState)
                StatsOverviewItem.CurrentStreak -> StatsStreakSheetContent(uiState)
                StatsOverviewItem.ActiveDays -> StatsActiveDaysSheetContent(uiState)
                StatsOverviewItem.ReadBooks -> StatsBooksSheetContent(
                    uiState = uiState,
                    title = stringResource(R.string.activity_read),
                    count = uiState.totalSummary?.readBooks ?: 0,
                    bookIds = uiState.readBookIds
                )
                StatsOverviewItem.FinishedBooks -> StatsBooksSheetContent(
                    uiState = uiState,
                    title = stringResource(R.string.activity_finished),
                    count = uiState.totalSummary?.finishedBooks ?: 0,
                    bookIds = uiState.finishedBookIds
                )
                StatsOverviewItem.FavoritedBooks -> StatsBooksSheetContent(
                    uiState = uiState,
                    title = stringResource(R.string.activity_collections),
                    count = uiState.totalSummary?.favoritedBooks ?: 0,
                    bookIds = uiState.favoritedBookIds
                )
            }
        }
    }
}

@Composable
private fun StatsOverviewSheetLayout(
    item: StatsOverviewItem,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onDismissRequest) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = "back"
                )
            }
            Text(
                text = stringResource(item.titleRes),
                style = typography.titleLarge,
                fontWeight = FontWeight.W600
            )
        }
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                content()
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(item.descriptionRes),
            style = typography.bodyLarge,
            color = colorScheme.onSurfaceVariant
        )
    }
}

private enum class SessionRange {
    ALL, RECENT
}

@Composable
fun StatsSessionsSheetContent(uiState: StatsOverviewUiState) {
    var range by remember { mutableStateOf(SessionRange.RECENT) }

    val monthlySessions = uiState.monthlySessions
    val sessions = remember(monthlySessions, range) {
        when (range) {
            SessionRange.ALL -> monthlySessions
            SessionRange.RECENT -> monthlySessions.takeLast(6)
        }
    }

    val values = remember(sessions) {
        sessions.map { it.second.toFloat() }
    }

    val options = listOf(
        SessionRange.ALL to R.string.stats_sessions_all,
        SessionRange.RECENT to R.string.stats_sessions_recent
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.total),
                    style = typography.labelMedium,
                    color = colorScheme.secondary
                )
                Text(
                    text = values.sum().toInt().toString(),
                    style = typography.displayMedium
                )
            }

            SingleChoiceSegmentedButtonRow {
                options.forEachIndexed { index, option ->
                    val selected = range == option.first
                    SegmentedButton(
                        selected = selected,
                        onClick = { range = option.first },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        )
                    ) {
                        Text(
                            text = stringResource(option.second),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(220)) +
                        slideInHorizontally(tween(220)) { it * dir / 5 })
                    .togetherWith(
                        fadeOut(tween(160)) +
                                slideOutHorizontally(tween(160)) { -it * dir / 5 }
                    )
            },
            label = "StatsSessionsChart"
        ) { state ->
            val targetSessions = when (state) {
                SessionRange.ALL -> monthlySessions
                SessionRange.RECENT -> monthlySessions.takeLast(6)
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.stats_sessions_monthly_axis_label),
                        style = typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.stats_sessions_cumulative_axis_label),
                        style = typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                StatsSessionsChart(
                    sessions = targetSessions,
                    scrollEnabled = state == SessionRange.ALL,
                    fixedSpacing = state == SessionRange.ALL
                )
            }
        }
    }
}

@Composable
private fun StatsSessionsChart(
    sessions: List<Pair<YearMonth, Int>>,
    scrollEnabled: Boolean,
    fixedSpacing: Boolean,
) {
    val values = sessions.map { it.second.toFloat() }
    val accumulatedValues = sessions
        .runningFold(0f) { total, (_, count) -> total + count }
        .drop(1)
    val labels = sessions.map { (month, _) -> month.axisLabel() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(values, accumulatedValues, labels) {
        modelProducer.runTransaction {
            columnSeries { series(values) }
            lineSeries { series(accumulatedValues) }
            extras { it[MonthLabelKey] = labels }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        fill = Fill(colorScheme.primary),
                        thickness = 18.dp,
                        shape = RoundedCornerShape(topStartPercent = 30, topEndPercent = 30)
                    )
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(colorScheme.secondary))
                    )
                ),
                verticalAxisPosition = Axis.Position.Vertical.End
            ),
            startAxis = VerticalAxis.rememberStart(
                label = rememberAxisLabelComponent(),
                itemPlacer = VerticalAxis.ItemPlacer.count({ 7 }),
                guideline = null,
                valueFormatter = CartesianValueFormatter { _, value, _ -> value.toInt().toString() }
            ),
            endAxis = VerticalAxis.rememberEnd(
                label = rememberAxisLabelComponent(),
                itemPlacer = VerticalAxis.ItemPlacer.count({ 7 }),
                guideline = rememberAxisGuidelineComponent(),
                valueFormatter = CartesianValueFormatter { _, value, _ -> value.toInt().toString() }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = rememberAxisLabelComponent(),
                valueFormatter = MonthLabelFormatter
            )
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        scrollState = rememberVicoScrollState(
            scrollEnabled = scrollEnabled,
            initialScroll = Scroll.Absolute.End,
            autoScroll = Scroll.Absolute.End,
            autoScrollCondition = AutoScrollCondition.OnModelGrowth
        ),
        zoomState = rememberVicoZoomState(
            zoomEnabled = false,
            initialZoom = if (fixedSpacing) Zoom.fixed() else Zoom.Content,
            minZoom = if (fixedSpacing) Zoom.fixed() else Zoom.Content,
            maxZoom = if (fixedSpacing) Zoom.fixed() else Zoom.Content
        )
    )
}

@Composable
fun StatsStreakSheetContent(uiState: StatsOverviewUiState) {
    val summary = uiState.totalSummary
    val current = summary?.currentStreak ?: 0
    val longest = summary?.longestStreak ?: 0
    val locale = LocalLocale.current.platformLocale
    val day = remember(current, locale) { formatMeasureUnitName(current, MeasureUnit.DAY, locale) }
    val longestDay = remember(longest, locale) { formatMeasureUnitName(longest, MeasureUnit.DAY, locale) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MetricTextBlock(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.stats_current_streak),
            value = current,
            unit = day
        )
        if (current < longest) {
            MetricTextBlock(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.stats_longest_streak),
                value = longest,
                unit = longestDay
            )
        }
    }
}

@Composable
fun StatsActiveDaysSheetContent(uiState: StatsOverviewUiState) {
    val activeDays = uiState.totalSummary?.activeDays ?: 0
    val locale = LocalLocale.current.platformLocale
    MetricTextBlock(
        label = stringResource(R.string.stats_active_days),
        value = activeDays,
        unit = remember(activeDays, locale) { formatMeasureUnitName(activeDays, MeasureUnit.DAY, locale) }
    )
}

@Composable
fun StatsBooksSheetContent(
    uiState: StatsOverviewUiState,
    title: String,
    count: Int,
    bookIds: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BookStack(
            modifier = Modifier.fillMaxWidth(),
            uiState = uiState,
            books = bookIds,
            count = minOf(bookIds.size, 10),
            compact = false,
            rotate = 4.5f
        )
        Column {
            Text(
                text = title,
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.n_books, count),
                style = typography.titleLarge,
            )
        }
    }
}

@Composable
private fun MetricTextBlock(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    unit: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = NumberFormat.getInstance(LocalLocale.current.platformLocale).format(value),
                style = typography.displayLarge.copy(
                    fontSize = 36.sp,
                    lineHeight = 38.sp
                ),
                fontWeight = FontWeight.W600,
                color = colorScheme.onSurface
            )
            Text(
                text = unit,
                modifier = Modifier
                    .padding(start = 6.dp, bottom = 8.dp),
                style = typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun YearMonth.axisLabel(): String =
    if (monthValue % 6 == 0) {
        "$year/$monthValue"
    } else {
        monthValue.toString()
    }
