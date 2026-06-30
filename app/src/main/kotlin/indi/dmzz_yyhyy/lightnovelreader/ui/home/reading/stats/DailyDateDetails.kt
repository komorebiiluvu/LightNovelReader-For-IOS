package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import io.nightfish.lightnovelreader.api.book.BookInformation

data class DailyDateDetails(
    val formattedTotalTime: String,
    val timeDetails: List<Pair<BookInformation, Int>>
)
