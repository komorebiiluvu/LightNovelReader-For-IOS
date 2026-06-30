package indi.dmzz_yyhyy.lightnovelreader.data.statistics

import indi.dmzz_yyhyy.lightnovelreader.data.serializer.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class StatsCache(
    @Serializable(LocalDateSerializer::class)
    val aggregatedUntil: LocalDate,
    @Serializable(LocalDateSerializer::class)
    val firstStatsDate: LocalDate? = null,
    val summary: StatsSummary = StatsSummary(),
    val readBookIds: List<String> = emptyList(),
    val finishedBookIds: List<String> = emptyList(),
    val favoritedBookIds: List<String> = emptyList(),
    val monthlySessions: List<StatsMonthSessions> = emptyList(),
    val readBooks: List<StatsBookDate> = emptyList(),
    val finishedBooks: List<StatsBookDate> = emptyList(),
    val favoritedBooks: List<StatsBookDate> = emptyList(),
    val tailStreak: Int = 0,
)

@Serializable
data class StatsMonthSessions(
    val year: Int,
    val month: Int,
    val sessions: Int,
)

@Serializable
data class StatsBookDate(
    val bookId: String,
    @Serializable(LocalDateSerializer::class)
    val date: LocalDate,
)
