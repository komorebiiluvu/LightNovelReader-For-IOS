package indi.dmzz_yyhyy.lightnovelreader.benchmark.settings

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SettingsTest : UiAutomatorTest() {
    private fun openSettings() {
        launchApp()
        openBottomNavigation("Settings")
    }

    @Test
    fun extensionSourceAndPluginManagerOpen() {
        openSettings()
        clickText("Data Source")
        assertText("Data Source")
        assertForegroundPackage(TARGET_PACKAGE)
        pressBack()

        clickText("Plugins")
        assertText("Plugins")
        assertDescription("install")
        pressBack()
        assertText("Settings")
    }

    @Test
    fun readingThemeFormattingAndConversionControlsOpen() {
        openSettings()
        clickText("Theme & Paper")
        assertText("Theme & Paper")
        assertText("Dynamic Colors")
        assertText("Light Theme")
        assertText("Dark Theme")
        pressBack()

        SystemClock.sleep(500)
        clickText("Text Formatting")
        assertText("Text Formatting")
        assertText("Global Rules")
        SystemClock.sleep(500)
        clickText("Global Rules")
        SystemClock.sleep(500)
        assertDescription("add")
        pressBack()
        pressBack()

        clickText("Traditional Chinese Conversion")
        assertText("Traditional Chinese Conversion")
    }

    @Test
    fun languageAndFormatsOpenAndReturn() {
        openSettings()
        clickText("Language")
        device.waitForIdle()
        assertForegroundPackage("com.android.settings")
        pressBack()
        assertForegroundPackage(TARGET_PACKAGE)

        openBottomNavigation("Settings")
        clickScrolledText("Formats")
        assertText("Formats")
        assertText("Date Format")
        assertText("Use Relative Time")
        assertText("Chinese Characters Variant")
        pressBack()
    }

    @Test
    fun updatesControlsAndManualCheckWork() {
        openSettings()
        clickScrolledText("Auto Check for Updates")
        assertText("Auto Check for Updates")
        clickScrolledText("Test Update Channel")
        assertText("Beta Version")
        pressBack()
        clickScrolledText("Distribution Platform")
        pressBack()
        clickScrolledText("Check for Updates")
        assertForegroundPackage(TARGET_PACKAGE)
    }

    @Test
    fun dataSnapshotImportStorageAndProxyControlsOpen() {
        openSettings()
        clickScrolledText("Snapshot User Data")
        assertText("Select the data to export")
        assertText("Local Book Cache")
        assertText("Bookshelf")
        assertText("Reading Data")
        pressBack()

        clickScrolledText("Import User Data")
        device.waitForIdle()
        assertForegroundPackage("com.android.documentsui")
        pressBack()

        launchApp()
        openBottomNavigation("Settings")
        clickScrolledText("Storage Usage")
        assertText("Storage Manager")
        assertTextContains("Database")
        pressBack()

        clickScrolledText("Auto Proxy")
        assertText("Auto Proxy")
    }

    @Test
    fun logsLogLevelAboutStatisticsAndLicensesOpen() {
        openSettings()
        clickScrolledText("App Logs")
        assertText("Logs")
        assertDescription("more")
        pressBack()

        clickScrolledText("Log Level")
        assertText("Debug")
        pressBack()

        clickScrolledText("LightNovelReader")
        assertTextContains("indi.dmzz_yyhyy.lightnovelreader")
        pressBack()

        clickScrolledText("Statistics")
        assertText("Hold on…")
        pressBack()

        clickScrolledText("Open-source licenses")
        assertText("Open-source licenses")
        assertDescription("back")
    }

    @Test
    fun traditionalConversionPersistsAcrossProcessRestart() {
        openSettings()
        scrollToText("Traditional Chinese Conversion")
        assertFirstSwitchChecked(false)
        clickText("Traditional Chinese Conversion")
        assertFirstSwitchChecked(true)

        restartApp()
        openBottomNavigation("Settings")
        scrollToText("Traditional Chinese Conversion")
        assertFirstSwitchChecked(true)
    }

    @Test
    fun formatsMenusAndRelativeTimeSettingPersist() {
        openSettings()
        clickScrolledText("Formats")

        clickText("Date Format")
        assertText("Numeric")
        assertText("Written")
        clickText("Written")
        assertText("Date Format")

        clickText("Use Relative Time")
        restartApp()
        openBottomNavigation("Settings")
        clickScrolledText("Formats")
        assertText("Written")
        assertText("Use Relative Time")
    }

    @Test
    fun themeSelectionAndReaderTypographyControlsAreReachable() {
        openSettings()
        clickText("Theme & Paper")

        clickText("Light Theme")
        assertText("Default")
        assertText("Designer")
        clickText("Designer")

        scrollToText("Paper")
        scrollToText("Background Image")
        scrollToText("Text Color")
        scrollToText("Text Font")
        scrollToText("Font Weight")
        scrollToText("Font Size")
        scrollToText("Line Spacing")
    }

    @Test
    fun globalFormattingRuleCanBeCreatedToggledEditedAndDeleted() {
        openSettings()
        clickText("Text Formatting")
        clickText("Global Rules")
        SystemClock.sleep(500)
        clickDescription("add")

        assertText("Edit Rule")
        setTextField(0, "Automation Rule")
        setTextField(1, "Benchmark")
        setTextField(2, "Verified")
        clickText("Save rule")
        pressBack()
        clickText("Global Rules")
        assertText("Automation Rule")
        assertText("Benchmark")
        assertText("Verified")
        assertFirstSwitchChecked(true)

        SystemClock.sleep(500)
        clickText("Benchmark")
        assertText("Delete rule")
        clickText("Use regex matching")
        setTextField(1, "[")
        assertText("Edit Rule")
        setTextField(1, "Benchmark.*")
        clickText("Save rule")
        device.waitForIdle(1_000)
        restartApp()
        openBottomNavigation("Settings")
        clickText("Text Formatting")
        clickText("Global Rules")
        assertText("Automation Rule")
        assertText("Benchmark.*")

        SystemClock.sleep(500)
        clickText("Benchmark.*")
        assertText("Edit Rule")
        clickText("Delete rule")
        device.waitForIdle(1_000)
        restartApp()
        openBottomNavigation("Settings")
        clickText("Text Formatting")
        clickText("Global Rules")
        assertTextNotVisible("Automation Rule")
    }

    @Test
    fun snapshotDialogExposesAllCategoriesAndFileDestination() {
        openSettings()
        scrollToText("Snapshot User Data")
        SystemClock.sleep(500)
        clickClickableText("Snapshot User Data")

        assertText("Local Book Cache")
        assertText("Bookshelf")
        assertText("Reading Data")
        assertText("Settings")
        scrollToText("Share")
        clickScrolledText("Export to File")
        assertForegroundPackage("com.android.documentsui")
    }

    @Test
    fun updatePlatformChannelAndAutoCheckPersist() {
        openSettings()
        clickScrolledText("Distribution Platform")
        assertText("GitHub")
        assertText("LightNovelReader API")
        clickText("GitHub")

        clickScrolledText("Test Update Channel")
        assertText("None (Stable)")
        assertText("Beta Version")
        assertText("Alpha Version (Unstable)")
        clickText("Alpha Version (Unstable)")

        clickScrolledText("Auto Check for Updates")
        restartApp()
        openBottomNavigation("Settings")
        scrollToText("Alpha Version (Unstable)")
        assertText("GitHub")
    }

    @Test
    fun logViewerMenusAndStatisticsDisableConfirmationWork() {
        openSettings()
        clickScrolledText("App Logs")
        clickDescription("more")
        assertText("Clear Temporary Logs")
        assertText("Auto-scroll")
        assertText("Word Wrap")
        pressBack()
        pressBack()

        clickScrolledText("Statistics")
        assertText("Hold on…")
        assertText("Turn off")
        clickText("Turn off")
        assertText("Settings")
    }
}
