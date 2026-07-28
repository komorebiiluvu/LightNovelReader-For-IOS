package indi.dmzz_yyhyy.lightnovelreader.benchmark.reading

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReadingAndManagerTest : UiAutomatorTest() {
    @Test
    fun continueReadingAndRecentBookOpenTheirExpectedDestinations() {
        launchApp()
        assertText("Continue Reading")
        assertText("Resume Last Reading")
        assertTextContains("Recent Reads")

        clickText("Resume Last Reading")
        assertTextContains("Benchmark paragraph")
        pressBack()
        pressBack()
        assertText("Reading")

        clickLastText("Benchmark Sample Novel")
        assertText("Benchmark Author")
        assertText("Benchmark Volume")
    }

    @Test
    fun downloadAndLocalBookManagerExposeTabsMenusAndCacheDetails() {
        launchApp()
        val statistics = assertDescription("statistics")
        val bounds = statistics.visibleBounds
        device.click(
            statistics.visibleBounds.centerX() - (device.displayWidth * 0.10f).toInt(),
            bounds.centerY(),
        )
        device.waitForIdle()

        assertText("Book Manager")
        assertText("Downloads")
        assertText("Local books")
        assertText("Nothing Here")

        clickText("Local books")
        assertText("Benchmark Sample Novel")
        assertDescription("sort")
        assertDescription("select")
        assertDescription("more")

        clickDescription("sort")
        assertText("Sort by size")
        assertText("Sort by last read")
        assertText("Sort by chapter count")
        clickText("Sort by chapter count")

        clickDescription("info")
        assertText("Book Cache Details")
        assertText("Book information")
        assertText("Volume index")
        assertText("Chapter information")
        assertText("Chapter content")
        assertText("Reading record")
    }
}
