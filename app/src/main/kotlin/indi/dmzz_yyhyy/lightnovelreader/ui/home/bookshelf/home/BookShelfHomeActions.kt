package indi.dmzz_yyhyy.lightnovelreader.ui.home.bookshelf.home

import android.net.Uri
import io.nightfish.lightnovelreader.api.bookshelf.BookshelfSortType

data class BookshelfHomeActions(
    val changePage: (Int) -> Unit,
    val changeSortType: (BookshelfSortType) -> Unit,
    val changeSortReversed: (Boolean) -> Unit,
    val changeBookSelectState: (String) -> Unit,
    val enableReorderMode: () -> Unit,
    val disableReorderMode: () -> Unit,
    val moveBook: (Int, Int) -> Unit,
    val enableBookshelfReorderMode: () -> Unit,
    val disableBookshelfReorderMode: () -> Unit,
    val moveBookshelf: (Int, Int) -> Unit,
    val onCreate: () -> Unit,
    val onEdit: (Int) -> Unit,
    val onBookClick: (String) -> Unit,
    val onEnableSelectMode: () -> Unit,
    val onDisableSelectMode: () -> Unit,
    val onSelectAll: () -> Unit,
    val onPin: () -> Unit,
    val onRemove: () -> Unit,
    val onMarkSelectedBooks: () -> Unit,
    val saveAllBookshelfJsonData: (Uri) -> Unit,
    val saveBookshelfJsonData: (Uri) -> Unit,
    val importBookshelf: (Uri) -> Unit,
    val clearToast: () -> Unit,
)
