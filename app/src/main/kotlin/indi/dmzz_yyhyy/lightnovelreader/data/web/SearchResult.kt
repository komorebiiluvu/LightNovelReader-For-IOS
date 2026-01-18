package indi.dmzz_yyhyy.lightnovelreader.data.web

import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation

sealed class SearchResult {
    class SingleBook(
        val bookId: Int
    ): SearchResult()

    class MultipleBook(
        val bookInformation: BookInformation
    ): SearchResult()

    class Error(
        val error: Throwable
    ): SearchResult() {
        constructor(message: String): this(kotlin.Error(message))
    }

    class End: SearchResult()
}