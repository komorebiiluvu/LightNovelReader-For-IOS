package indi.dmzz_yyhyy.lightnovelreader.ui.home.bookshelf.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.bottomsheet.BottomSheetBehavior.State
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.bookshelf.Bookshelf
import io.nightfish.lightnovelreader.api.bookshelf.MutableBookshelf

@State
interface BookshelfHomeUiState {
    val bookshelfList: List<Bookshelf>
    val selectedBookshelfId: Int
    val bookInformationMap: Map<String, BookInformation>
    val bookLastChapterTitleMap: Map<String, String>
    val selectedTabIndex get() = bookshelfList.indexOfFirst { it.id == selectedBookshelfId }
    val selectedBookshelf: Bookshelf get() = if (selectedTabIndex != -1) bookshelfList[selectedTabIndex] else MutableBookshelf()
    val selectMode: Boolean
    val reorderMode: Boolean
    val reorderBookshelfMode: Boolean
    var updatedExpanded: Boolean
    var pinnedExpanded: Boolean
    var allExpanded: Boolean
    val selectedBookIds: List<String>
    val reorderBookIds: List<String>
    val reorderBookshelfIds: List<Int>
    val toast: String
}

class MutableBookshelfHomeUiState : BookshelfHomeUiState {
    override var bookshelfList by mutableStateOf(emptyList<MutableBookshelf>())
    override var selectedBookshelfId by mutableIntStateOf(-1)
    override var bookInformationMap = mutableStateMapOf<String, BookInformation>()
    override var bookLastChapterTitleMap = mutableStateMapOf<String, String>()
    override var selectMode by mutableStateOf(false)
    override var reorderMode by mutableStateOf(false)
    override var reorderBookshelfMode by mutableStateOf(false)
    override var updatedExpanded by mutableStateOf(true)
    override var pinnedExpanded by mutableStateOf(true)
    override var allExpanded by mutableStateOf(true)
    override val selectedBookIds: MutableList<String> = mutableStateListOf()
    override val reorderBookIds: MutableList<String> = mutableStateListOf()
    override val reorderBookshelfIds: MutableList<Int> = mutableStateListOf()
    override var toast by mutableStateOf("")
}
