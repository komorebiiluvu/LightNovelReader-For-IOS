package indi.dmzz_yyhyy.lightnovelreader.benchmark.plugin

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
class PluginSystemTest : UiAutomatorTest() {
    @Test
    fun examplePluginInstallsAndAppearsInManager() {
        launchApp()
        installExamplePlugin()

        openBottomNavigation("Settings")
        clickText("Plugins")
        assertText("PotatoLib")
        assertTextContains("1.0")

        clickText("PotatoLib")
        assertText("Enable plugin")
        assertText("About this plugin")
        assertFirstSwitchChecked(false)
        clickText("Enable plugin")
        assertFirstSwitchChecked(true)

        clickScrolledText("Signature")
        assertText("Signature details")
        clickText("OK")

        pressBack()
        clickText("PotatoLib")
        clickText("Enable plugin")
        assertFirstSwitchChecked(false)
    }

    @Test
    fun examplePluginInstallCanBeCancelledAfterInspection() {
        launchApp()
        openInstaller()

        assertText("plugin")
        assertText("io.nightfish.potatolib")
        assertTextContains("1.0")
        clickText("Abort")
        assertForegroundPackage(TARGET_PACKAGE)
    }

    @Test
    fun installedPluginDeletionSupportsCancelAndConfirmation() {
        launchApp()
        installExamplePlugin()
        openBottomNavigation("Settings")
        clickText("Plugins")

        longClickText("PotatoLib")
        assertText("Signature details")
        clickMenuItemAfter("Signature details")
        assertText("Delete PotatoLib")
        assertTextContains("cannot be undone")
        clickText("Cancel")
        assertText("PotatoLib")

        longClickText("PotatoLib")
        clickMenuItemAfter("Signature details")
        clickText("Delete")
        assertText("Deletion complete")
        clickText("OK")
        assertTextNotVisible("PotatoLib")
    }

    private fun installExamplePlugin() {
        openInstaller()
        assertText("plugin")
        assertText("io.nightfish.potatolib")
        assertTextContains("1.0")
        clickText("Install plugin")
        assertText("Plugin installed")
        clickText("OK")
    }

    private fun openInstaller() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val fixtureDirectory = File(testContext.cacheDir, "plugin-fixtures").apply { mkdirs() }
        val fixture = File(fixtureDirectory, "PotatoLib.lnrp")
        testContext.assets.open("PotatoLib.lnrp").use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        val uri = FileProvider.getUriForFile(testContext, FILE_PROVIDER_AUTHORITY, fixture)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            setPackage(TARGET_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        testContext.startActivity(intent)
        assertForegroundPackage(TARGET_PACKAGE)
    }

    companion object {
        private const val FILE_PROVIDER_AUTHORITY =
            "indi.dmzz_yyhyy.lightnovelreader.benchmark.files"
    }
}
