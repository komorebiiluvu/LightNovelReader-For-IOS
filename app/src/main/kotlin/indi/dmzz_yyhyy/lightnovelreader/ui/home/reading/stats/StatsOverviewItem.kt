package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.data.statistics.StatsSummary

enum class StatsOverviewItem(
    val titleRes: Int,
    val iconRes: Int,
    val descriptionRes: Int,
    val value: (StatsSummary?) -> Int
) {
    Sessions(
        R.string.reading_sessions,
        R.drawable.schedule_90dp,
        R.string.stats_sessions_desc,
        { it?.totalReadCount ?: 0 }
    ),
    CurrentStreak(
        R.string.stats_streak,
        R.drawable.calendar_today_24px,
        R.string.stats_streak_desc,
        { it?.currentStreak ?: 0 }
    ),
    ActiveDays(
        R.string.stats_active_days,
        R.drawable.calendar_today_24px,
        R.string.stats_active_days_desc,
        { it?.activeDays ?: 0 }
    ),
    ReadBooks(
        R.string.activity_read,
        R.drawable.menu_book_24px,
        R.string.stats_read_books_desc,
        { it?.readBooks ?: 0 }
    ),
    FinishedBooks(
        R.string.activity_finished,
        R.drawable.done_all_24px,
        R.string.stats_finished_books_desc,
        { it?.finishedBooks ?: 0 }
    ),
    FavoritedBooks(
        R.string.activity_collections,
        R.drawable.filled_bookmark_24px,
        R.string.stats_favorited_books_desc,
        { it?.favoritedBooks ?: 0 }
    )
}
