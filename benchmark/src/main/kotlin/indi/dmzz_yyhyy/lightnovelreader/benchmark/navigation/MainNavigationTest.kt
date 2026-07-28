package indi.dmzz_yyhyy.lightnovelreader.benchmark.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainNavigationTest : UiAutomatorTest() {
    @Test
    fun bottomNavigationVisitsEveryRootDestination() {
        launchApp()
        assertText("Reading")

        openBottomNavigation("Bookshelf")
        assertText("Benchmark Shelf")

        openBottomNavigation("Explore")
        assertDescription("search")

        openBottomNavigation("Settings")
        assertText("Extensions")
        assertText("Reading")
        assertText("Display")
    }

    @Test
    fun readingStatisticsOverviewAndDetailsOpen() {
        launchApp()
        clickDescription("statistics")
        assertText("Statistics")
        assertTextContains("1")

        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.75).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.30).toInt(),
            20,
        )
        assertForegroundPackage(TARGET_PACKAGE)
        pressBack()
        assertText("Reading")
    }

    @Test
    fun dailyStatisticsDetailSupportsWeeklyMonthlyAndYearlyViews() {
        launchApp()
        clickDescription("statistics")
        assertText("Statistics")
        assertText("Calendar")
        clickText("Detail")

        assertText("Weekly")
        assertText("Monthly")
        assertText("Yearly")
        assertText("Reading Time")
        clickText("Monthly")
        assertText("Reading Time")
        clickText("Yearly")
        assertText("Reading Time")
        clickText("Weekly")
        assertText("Reading Time")
        pressBack()
        assertText("Statistics")
    }
}
