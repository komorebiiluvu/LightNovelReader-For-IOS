package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.bookmanager

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookRepository
import javax.inject.Inject

@HiltViewModel
class BookManagerViewModel @Inject constructor (
    val bookRepository: BookRepository
) : ViewModel() {


}