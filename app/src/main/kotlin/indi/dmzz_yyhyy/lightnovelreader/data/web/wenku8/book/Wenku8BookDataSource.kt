package indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.book

import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookVolumes
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.web.SearchResult
import kotlinx.coroutines.flow.Flow

interface Wenku8BookDataSource {
    suspend fun getBookInformation(id: Int): BookInformation
    suspend fun getBookVolumes(id: Int): BookVolumes
    suspend fun getChapterContent(chapterId: Int, bookId: Int): ChapterContent
    fun search(searchType: String, keyword: String): Flow<SearchResult>
}