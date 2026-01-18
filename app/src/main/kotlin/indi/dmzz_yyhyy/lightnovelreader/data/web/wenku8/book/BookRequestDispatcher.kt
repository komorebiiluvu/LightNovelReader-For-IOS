package indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.book

import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookVolumes
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.web.SearchResult
import indi.dmzz_yyhyy.lightnovelreader.utils.CanBeEmpty
import indi.dmzz_yyhyy.lightnovelreader.utils.UserAgentGenerator
import indi.dmzz_yyhyy.lightnovelreader.utils.update
import kotlinx.coroutines.flow.Flow

class BookRequestDispatcher: Wenku8BookDataSource {
    val source = listOf(
        Wenku8WebsiteDataSource(),
        Wenku8AppDataSource(update("eNpb85aBtYRBMaOkpMBKXz-xoECvPDUvu9RCLzk_Vz8xL6UoPzNFryCjAAAfiA5Q").toString()) { "wenku8" },
        Wenku8AppDataSource(update("eNpb85aBtYRBNqOkpKDYSl-_PDUvu9RCtyg1J7FSLze1vEIvvygdAO0UDQw").toString()) { UserAgentGenerator.generate() },
    )

    private suspend fun <T: CanBeEmpty>rotation(default: T, block: suspend Wenku8BookDataSource.() -> T): T {
        for (dataSource in source) {
            val result = block.invoke(dataSource)
            if (result.isEmpty()) continue
            return result
        }
        return default
    }

    override suspend fun getBookInformation(id: Int): BookInformation = rotation(BookInformation.empty(id)) {
        getBookInformation(id)
    }

    override suspend fun getBookVolumes(id: Int): BookVolumes = rotation(BookVolumes.empty(id)) {
        getBookVolumes(id)
    }

    override suspend fun getChapterContent(
        chapterId: Int,
        bookId: Int
    ): ChapterContent = rotation(ChapterContent.empty(chapterId)) {
        getChapterContent(chapterId, bookId)
    }

    override fun search(searchType: String, keyword: String): Flow<SearchResult> {
        return source.first().search(searchType, keyword)
    }
}