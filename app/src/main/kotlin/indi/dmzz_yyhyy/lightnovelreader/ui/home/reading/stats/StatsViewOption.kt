package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

sealed class StatsViewOption(val viewIndex: Int) {
    abstract fun rangeFor(date: LocalDate): ClosedRange<LocalDate>

    object Daily : StatsViewOption(viewIndex = 0) {
        override fun rangeFor(date: LocalDate): ClosedRange<LocalDate> {
            return date.minusDays(6)..date
        }
    }

    object Weekly : StatsViewOption(viewIndex = 1) {
        override fun rangeFor(date: LocalDate): ClosedRange<LocalDate> {
            val startOfMonth = date.withDayOfMonth(1)
            val endOfMonth = date.with(TemporalAdjusters.lastDayOfMonth())
            return startOfMonth..endOfMonth
        }
    }

    object Monthly : StatsViewOption(viewIndex = 2) {
        override fun rangeFor(date: LocalDate): ClosedRange<LocalDate> {
            val startOfYear = LocalDate.of(date.year, 1, 1)
            val endOfYear = LocalDate.of(date.year, 12, 31)
            return startOfYear..endOfYear
        }
    }

    companion object {
        fun fromIndex(index: Int): StatsViewOption = when (index) {
            Daily.viewIndex -> Daily
            Weekly.viewIndex -> Weekly
            Monthly.viewIndex -> Monthly
            else -> throw IllegalArgumentException("invalid viewIndex $index")
        }
    }
}

private operator fun LocalDate.rangeTo(other: LocalDate): ClosedRange<LocalDate> = object : ClosedRange<LocalDate> {
    override val start: LocalDate = this@rangeTo
    override val endInclusive: LocalDate = other
}