package indi.dmzz_yyhyy.lightnovelreader.benchmark.book

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BookAndReaderTest : UiAutomatorTest() {
    private fun openBookDetails() {
        launchApp()
        openBottomNavigation("Bookshelf")
        clickText("Benchmark Sample Novel")
        assertText("Benchmark Sample Novel")
    }

    @Test
    fun detailActionsOpenExportFormattingAndMoreMenus() {
        openBookDetails()

        clickDescription("export")
        assertText("Export as Epub")
        pressBack()

        clickDescription("formatting")
        assertText("Book Rules")
        pressBack()

        clickDescription("more")
        assertText("Mark as read…")
        pressBack()
    }

    @Test
    fun chapterSelectionAndReaderControlsWork() {
        openBookDetails()
        clickText("Benchmark Chapter One")
        assertTextContains("Benchmark paragraph")

        device.click(device.displayWidth / 2, device.displayHeight / 2)
        assertDescription("menu")
        assertDescription("setting")
        assertDescription("mark")

        clickDescription("menu")
        assertText("Select Chapter")
        assertText("Benchmark Chapter One")
        clickCenter(scrollToText("Benchmark Bonus Volume"))
        clickCenter(scrollToText("Benchmark Chapter Two"))
        assertForegroundPackage(TARGET_PACKAGE)
    }

    @Test
    fun readerSettingsExposeAllGroupsAndPageModes() {
        openBookDetails()
        clickText("Benchmark Chapter One")
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        clickDescription("setting")

        assertText("Reader Settings")
        assertText("Appearance")
        assertText("Controls")
        assertText("Margins")
        assertText("Keep Screen On")
        assertText("Hide Status Bar")
        assertText("Theme Settings…")

        clickText("Controls")
        assertText("Page Turn Mode")
        assertText("Back Prevention")
    }

    @Test
    fun scrollingAndVolumeNavigationKeepReaderResponsive() {
        openBookDetails()
        clickText("Benchmark Chapter One")
        assertTextContains("Benchmark paragraph")

        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.75).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.25).toInt(),
            30,
        )
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
        assertTextContains("Benchmark paragraph")
    }

    @Test
    fun bookInformationSheetShowsEveryMetadataGroup() {
        openBookDetails()
        clickScrolledText("Info")

        assertText("Title")
        assertText("Benchmark Sample Novel")
        assertText("ID")
        assertText("9999999")
        assertText("Author")
        assertText("Benchmark Author")
        scrollToText("Stats")
        assertTextContains("12")
        assertTextContains("2 chapters")
    }

    @Test
    fun epubExportOptionsSupportVolumeAndSelectionBranches() {
        openBookDetails()
        clickDescription("export")

        assertText("Export as Epub")
        assertText("Include images")
        assertText("Export by volumes")
        clickText("Export by volumes")
        assertText("Benchmark Volume")
        assertText("Benchmark Bonus Volume")
        clickText("Benchmark Volume")
        assertText("Select All")
        clickText("Select All")
        assertTextNotVisible("Select All")
    }

    @Test
    fun markReadDialogSupportsRangeSelectionAndConfirmation() {
        openBookDetails()
        clickDescription("more")
        clickText("Mark as read…")

        assertText("全部章节")
        assertText("选择范围")
        clickText("选择范围")
        assertText("Benchmark Chapter One")
        assertText("Benchmark Chapter Two")
        clickText("Benchmark Chapter One")
        clickText("Benchmark Chapter Two")
        assertText("标记已选 2 章为已读")
        clickText("标记已选 2 章为已读")

        assertText("Benchmark Sample Novel")
        device.waitForIdle(2_000)
        restartApp()
        openBottomNavigation("Bookshelf")
        clickText("Benchmark Sample Novel")
        scrollToText("Finished Reading")
    }

    @Test
    fun markReadDialogCanBeCancelledWithoutChangingProgress() {
        openBookDetails()
        clickDescription("more")
        clickText("Mark as read…")
        clickText("取消")

        assertText("Benchmark Sample Novel")
        clickDescription("more")
        clickText("Mark as read…")
        assertText("标记全部为已读")
    }

    @Test
    fun readerModeSwitchesExposeConditionalControlsAndMargins() {
        openBookDetails()
        clickText("Benchmark Chapter One")
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        clickDescription("setting")

        clickText("Controls")
        assertText("Page Turn Mode")
        assertText("Continuous Scrolling")
        clickText("Page Turn Mode")
        assertText("Volume Key Navigation")
        scrollToText("Tap to Turn Pages")
        scrollToText("Quick Chapter Switch")
        assertText("Page Turn Animation")

        clickText("Margins")
        assertText("Auto Margin Adjustment")
        clickText("Auto Margin Adjustment")
        assertText("Top Margin")
        assertText("Bottom Margin")
        scrollToText("Right Margin")
        assertText("Left Margin")
    }

    @Test
    fun readerAppearanceTogglesPersistAcrossReaderReentry() {
        openBookDetails()
        clickText("Benchmark Chapter One")
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        clickDescription("setting")
        assertText("Keep Screen On")
        clickText("Keep Screen On")
        assertFirstSwitchChecked(true)

        pressBack()
        pressBack()
        clickText("Benchmark Chapter One")
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        clickDescription("setting")
        assertFirstSwitchChecked(true)
    }
}
