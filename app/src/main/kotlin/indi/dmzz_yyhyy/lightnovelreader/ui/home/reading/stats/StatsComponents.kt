package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.ui.components.Cover
import io.nightfish.lightnovelreader.api.book.BookInformation
import java.time.LocalDate
import kotlin.random.Random

@Composable
fun StatsCard(
    modifier: Modifier = Modifier,
    title: String,
    subTitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = typography.titleMedium,
                fontWeight = FontWeight.W600
            )
            if (subTitle != null) {
                Text(
                    text = subTitle,
                    style = typography.titleSmall,
                    color = colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun BookStack(
    modifier: Modifier = Modifier,
    uiState: StatsDetailedUiState,
    books: List<String>,
    count: Int,
    scaleEnabled: Boolean = false,
    compact: Boolean = true,
    rotate: Float? = null,
) {
    val displayBooks = books.distinct().take(count)

    BoxWithConstraints(
        modifier = modifier.then(
            if (compact) {
                Modifier
                    .wrapContentWidth()
                    .padding(end = (displayBooks.size * 20).dp)
            } else {
                Modifier.fillMaxWidth()
            }
        )
    ) {
        val baseWidth = 63.dp
        val baseOffset = 20.dp

        val offsetStep = if (compact) {
            baseOffset
        } else {
            if (displayBooks.size <= 1) {
                0.dp
            } else {
                val availableWidth = maxWidth - baseWidth
                (availableWidth / (displayBooks.size - 1)).coerceAtMost(baseWidth)
            }
        }

        displayBooks.fastForEachIndexed { index, bookId ->
            val scale = if (scaleEnabled) {
                1f - (index * 0.01f).coerceAtMost(0.3f)
            } else 1f

            val offsetY = remember(bookId) {
                Random.nextInt(-3, 4).dp
            }

            Box(
                modifier = Modifier
                    .wrapContentHeight()
                    .zIndex((displayBooks.size - index).toFloat())
                    .align(Alignment.CenterStart)
                    .offset(
                        x = offsetStep * index,
                        y = offsetY
                    )
                    .graphicsLayer {
                        rotationZ = rotate ?: 0f
                    }
            ) {
                uiState.bookInformationMap[bookId]?.let {
                    Cover(
                        width = 63.dp * scale,
                        height = 90.dp * scale,
                        uri = it.coverUri,
                        rounded = 6.dp
                    )
                }
            }
        }
    }
}

val predefinedColors = listOf(
    Color(0xFF2196F3),
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFFF44336),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFF3F51B5),
    Color(0xFFFF5722),
)

/**
 * @return startedBooks/finishedBooks 在日期范围内的 BookId 列表
 */
private fun getBooksInRange(
    bookDateMap: Map<String, LocalDate>,
    dateRange: ClosedRange<LocalDate>
): List<String> {
    return bookDateMap
        .filterValues { it in dateRange }
        .toList()
        .sortedBy { it.second }
        .map { it.first }
}

/**
 * 统计详情: 活动卡片的行
 */
@Composable
private fun BookActivitySection(
    titleResId: Int,
    bookIds: List<String>,
    bookInfoMap: Map<String, BookInformation>,
    uiState: StatsDetailedUiState,
    modifier: Modifier = Modifier
) {
    if (bookIds.isEmpty()) return

    val displayedTitles = bookIds.distinct().mapNotNull { id ->
        bookInfoMap[id]?.title
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(titleResId),
                style = typography.titleMedium
            )
            val titleList = displayedTitles.take(2)
            titleList.forEach {
                Text(
                    text = it,
                    style = typography.bodyMedium,
                    maxLines = 1,
                    color = colorScheme.secondary,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (displayedTitles.size > titleList.size)
                Text(
                    text = stringResource(R.string.activity_etc, displayedTitles.size),
                    style = typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
        Spacer(Modifier.width(12.dp))
        Box {
            BookStack(
                modifier = Modifier,
                uiState = uiState,
                books = bookIds,
                count = 5,
                rotate = 4.5f,
                scaleEnabled = false
            )
        }
    }
}

/**
 * 活动卡片（适用各种时间范围）
 */
@Composable
fun ActivityStatsCard(
    uiState: StatsDetailedUiState,
    modifier: Modifier = Modifier
) {
    val dateRange = uiState.currentDateRange
    val startedBooks = getBooksInRange(uiState.bookFirstReadDateMap, dateRange)
    val finishedBooks = getBooksInRange(uiState.bookFirstFinishedDateMap, dateRange)
    val favoriteBooks = getBooksInRange(uiState.bookFavoriteDateMap, dateRange)

    val hasActivity = startedBooks.isNotEmpty() || finishedBooks.isNotEmpty() || favoriteBooks.isNotEmpty()
    if (!hasActivity) return

    StatsCard(
        modifier = modifier,
        title = stringResource(R.string.activity)
    ) {
        Column {
            val sections = listOf(
                R.string.activity_first_read to startedBooks,
                R.string.activity_collections to favoriteBooks,
                R.string.activity_finished to finishedBooks
            ).filter { it.second.isNotEmpty() }

            sections.forEachIndexed { index, (title, books) ->
                BookActivitySection(
                    titleResId = title,
                    bookIds = books,
                    bookInfoMap = uiState.bookInformationMap,
                    uiState = uiState
                )

                if (index != sections.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
