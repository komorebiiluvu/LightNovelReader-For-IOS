package indi.dmzz_yyhyy.lightnovelreader.data.statistics

import kotlinx.serialization.Serializable

@Serializable
data class StatsSummary(
    val totalMinutes: Int = 0,
    val totalReadCount: Int = 0,
    val activeDays: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val readBooks: Int = 0,
    val finishedBooks: Int = 0,
    val favoritedBooks: Int = 0,
)
