package indi.dmzz_yyhyy.lightnovelreader.data.exploration

import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.text.TextProcessingRepository
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorationRepository @Inject constructor(
    private val webBookDataSource: WebBookDataSource,
    private val processingRepository: TextProcessingRepository
) {
    val searchTypeIdList get() = webBookDataSource.searchTypeIdList
    val searchTypeMap get() = processingRepository.processSearchTypeNameMap { webBookDataSource.searchTypeMap }
    val searchTipMap get() = processingRepository.processSearchTipMap { webBookDataSource.searchTipMap }
    val explorationPageIdList get() = webBookDataSource.explorationPageIdList
    val explorationPageDataSourceMap get() = webBookDataSource.explorationPageDataSourceMap
    val explorationExpandedPageDataSourceMap get() = webBookDataSource.explorationExpandedPageDataSourceMap

    fun search(searchType: String, keyword: String): Flow<BookInformation> =
        webBookDataSource.search(searchType, keyword)

    fun stopAllSearch() = webBookDataSource.stopAllSearch()
}