package indi.dmzz_yyhyy.lightnovelreader.benchmark.bookshelf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import indi.dmzz_yyhyy.lightnovelreader.benchmark.ui.UiAutomatorTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BookshelfTest : UiAutomatorTest() {
    private fun openBookshelf() {
        launchApp()
        openBottomNavigation("Bookshelf")
    }

    private fun openBookshelfMenu() {
        device.click(
            (device.displayWidth * 0.94).toInt(),
            (device.displayHeight * 0.075).toInt(),
        )
        device.waitForIdle()
    }

    @Test
    fun seededShelfAndBookDetailsOpen() {
        launchApp()
        openBottomNavigation("Bookshelf")
        assertText("Benchmark Shelf")
        clickText("Benchmark Sample Novel")

        assertText("Benchmark Sample Novel")
        assertText("Benchmark Author")
        assertText("Benchmark Volume")
        assertDescription("export")
        assertDescription("formatting")
        assertDescription("more")
    }

    @Test
    fun createBookshelfFlowPersistsNewShelf() {
        openBookshelf()
        clickDescription("create")
        assertText("New Bookshelf")
        setFirstTextField("Automation Shelf")
        clickDescription("save")
        assertText("Automation Shelf")

        restartApp()
        openBottomNavigation("Bookshelf")
        assertText("Automation Shelf")
    }

    @Test
    fun sortAndReorderControlsOpen() {
        openBookshelf()
        clickDescription("sort")
        assertText("Sort Type")
        assertText("Default")
        assertText("Recently Updated")
        assertText("Name")
        assertText("Word Count")
        assertText("Reverse")

        pressBack()
        openBookshelfMenu()
        assertText("Adjust Order")
        assertText("Bookshelf Settings")
        assertText("Share Bookshelf")
    }

    @Test
    fun bookshelfRenameAndSettingsPersistAfterRestart() {
        openBookshelf()
        openBookshelfMenu()
        clickText("Bookshelf Settings")

        assertText("Edit Bookshelf")
        setFirstTextField("Renamed Benchmark Shelf")
        clickText("Auto Cache")
        clickText("Update Notification")
        clickDescription("save")
        assertText("Renamed Benchmark Shelf")

        restartApp()
        openBottomNavigation("Bookshelf")
        assertText("Renamed Benchmark Shelf")
        openBookshelfMenu()
        clickText("Bookshelf Settings")
        assertText("Renamed Benchmark Shelf")
    }

    @Test
    fun emptyBookshelfNameShowsValidationAndDoesNotSave() {
        openBookshelf()
        openBookshelfMenu()
        clickText("Bookshelf Settings")
        clickDescription("cancel")
        clickDescription("save")

        assertText("Enter the bookshelf name.")
        assertText("Edit Bookshelf")
    }

    @Test
    fun selectionModeSupportsPinMoveAndCancel() {
        openBookshelf()
        longClickText("Benchmark Sample Novel")
        assertDescription("select all")
        assertDescription("pin")
        assertDescription("remove")
        assertDescription("bookmark")

        clickDescription("cancel")
        assertText("Bookshelf")

        longClickText("Benchmark Sample Novel")
        clickDescription("bookmark")
        assertText("Add this book to the following bookshelves")
        assertText("Benchmark Shelf")
        clickText("Cancel")
        assertText("Bookshelf")
    }

    @Test
    fun selectedBookCanBeRemovedFromShelf() {
        openBookshelf()
        longClickText("Benchmark Sample Novel")
        clickDescription("remove")

        assertText("Nothing Here")
        assertTextNotVisible("Benchmark Sample Novel")
    }

    @Test
    fun deleteBookshelfSupportsCancelAndConfirmation() {
        openBookshelf()
        openBookshelfMenu()
        clickText("Bookshelf Settings")
        clickText("Delete Bookshelf")
        assertTextContains("lost forever")
        clickText("Cancel")
        assertText("Edit Bookshelf")

        clickText("Delete Bookshelf")
        clickText("OK")
        assertText("Bookshelf")
        assertTextNotVisible("Benchmark Shelf")
    }
}
