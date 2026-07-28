package indi.dmzz_yyhyy.lightnovelreader.benchmark.explore

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ExploreTest : UiAutomatorTest() {
    @Test
    fun liveHomepageLoadsNetworkBooksAndOpensDetail() {
        launchApp()
        openBottomNavigation("Explore")

        val covers = waitForDescriptions("cover")
        clickCenter(covers.first())
        device.waitForIdle()

        assertDescription("export")
        assertDescription("formatting")
        assertDescription("more")
    }

    @Test
    fun exploreSourceTabsAndContentWork() {
        launchApp()
        openBottomNavigation("Explore")

        // Source-provided tab labels are intentionally not localized by the
        // app and can change remotely. Exercise each stable tab position.
        val tabY = (device.displayHeight * 0.21).toInt()
        listOf(0.17f, 0.50f, 0.83f).forEach { horizontalPosition ->
            device.click((device.displayWidth * horizontalPosition).toInt(), tabY)
            device.waitForIdle()
            assertForegroundPackage(TARGET_PACKAGE)
            assertDescription("search")
        }
    }

    @Test
    fun searchInputFilterClearAndBackWork() {
        launchApp()
        openBottomNavigation("Explore")
        clickDescription("search")
        assertDescription("back")

        setFirstTextField("benchmark")
        assertDescription("filter")
        clickDescription("filter")
        assertForegroundPackage(TARGET_PACKAGE)
        pressBack()
        clickDescription("clear")
        pressBack()
        assertText("Explore")
    }

    @Test
    fun searchHistoryCanBeCreatedDeletedAndCleared() {
        launchApp()
        openBottomNavigation("Explore")
        clickDescription("search")
        setFirstTextField("automation-history")
        device.pressEnter()
        device.waitForIdle()

        val field = device.findObject(
            By.clazz("android.widget.EditText")
        )
        clickCenter(field)
        assertText("Search History")
        assertText("automation-history")
        clickDescription("delete")
        clickDescription("clear")
        assertTextNotVisible("automation-history")

        setFirstTextField("clear-all-history")
        device.pressEnter()
        device.waitForIdle()
        clickCenter(device.findObject(By.clazz("android.widget.EditText")))
        assertText("Clear All")
        clickText("Clear All")
        clickDescription("clear")
        assertTrue(
            "Cleared history entry remained visible",
            device.wait(Until.gone(By.text("clear-all-history")), TIMEOUT),
        )
    }

    @Test
    fun liveExactSearchLoadsNetworkBookAndOpensDetail() {
        launchApp()
        openBottomNavigation("Explore")
        clickDescription("search")

        val query = "奇招百出的维多利亚"
        setFirstTextField(query)
        // Wenku8 rejects searches less than five seconds apart for the same
        // public IP. Other live-search tests may have just used that endpoint.
        SystemClock.sleep(6_000)
        shell("logcat -c")
        device.pressEnter()
        device.waitForIdle()

        val queryNodes = device.wait(Until.findObjects(By.text(query)), TIMEOUT)
        assertTrue("Submitted query is not visible", queryNodes.isNotEmpty())

        waitForDescriptions("export")
        val networkLog = shell("logcat -d")
        assertTrue(
            "Search endpoint was not requested",
            "modules/article/search.php?searchtype=articlename" in networkLog,
        )
        assertTrue("Search response was not received", "Ktor Client: FROM:" in networkLog)

        assertDescription("formatting")
    }

    @Test
    fun liveExpandedPageLoadsFiltersResultsAndPaging() {
        launchApp()
        openBottomNavigation("Explore")

        // The second source tab is the built-in "All" page. Its labels are
        // supplied by the source, so select it by stable tab position.
        device.click(device.displayWidth / 2, (device.displayHeight * 0.21).toInt())
        device.waitForIdle()

        val expandButtons = waitForDescriptions("expand")
        clickCenter(expandButtons.first())
        device.waitForIdle()

        assertDescription("back")
        val filters = device.wait(Until.findObjects(By.checkable(true)), NETWORK_TIMEOUT)
        assertTrue("Expanded page filters did not load", filters.isNotEmpty())
        waitForDescriptions("cover")

        // Exercise a real filter refresh and the near-end paging callback.
        clickCenter(filters.first())
        device.waitForIdle()
        waitForDescriptions("cover")
        repeat(5) {
            val scroller = device.findObjects(By.scrollable(true))
                .maxByOrNull { it.visibleBounds.height() }
            if (scroller != null) {
                scroller.scroll(Direction.DOWN, 0.9f)
            } else {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.8).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.2).toInt(),
                    30,
                )
            }
            device.waitForIdle()
        }
        assertForegroundPackage(TARGET_PACKAGE)
        assertDescription("back")
    }

    private fun waitForDescriptions(
        description: String,
        timeout: Long = NETWORK_TIMEOUT,
    ): List<UiObject2> {
        val objects = device.wait(Until.findObjects(By.desc(description)), timeout).orEmpty()
        assertTrue(
            "Network content with description '$description' did not load within ${timeout}ms",
            objects.isNotEmpty(),
        )
        return objects
    }

    companion object {
        private const val NETWORK_TIMEOUT = 45_000L
    }
}
