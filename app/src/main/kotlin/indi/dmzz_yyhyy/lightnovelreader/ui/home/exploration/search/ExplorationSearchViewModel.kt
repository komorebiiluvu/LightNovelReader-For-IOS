package indi.dmzz_yyhyy.lightnovelreader.ui.home.exploration.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import indi.dmzz_yyhyy.lightnovelreader.data.bookshelf.BookshelfRepository
import indi.dmzz_yyhyy.lightnovelreader.data.exploration.ExplorationRepository
import indi.dmzz_yyhyy.lightnovelreader.data.userdata.UserDataPath
import indi.dmzz_yyhyy.lightnovelreader.data.userdata.UserDataRepository
import indi.dmzz_yyhyy.lightnovelreader.data.web.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExplorationSearchViewModel @Inject constructor(
    private val explorationRepository: ExplorationRepository,
    private val bookshelfRepository: BookshelfRepository,
    userDataRepository: UserDataRepository
) : ViewModel() {
    private val _uiState = MutableExplorationSearchUiState()
    private val searchHistoryUserData = userDataRepository.stringListUserData(UserDataPath.Search.History.path)
    private var searchTypeTipMap = emptyMap<String, String>()
    private var searchJob: Job? = null
    val uiState: ExplorationSearchUiState = _uiState

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }

    fun init() {
        viewModelScope.launch(Dispatchers.IO) {
            searchTypeTipMap = explorationRepository.searchTipMap
            _uiState.searchTypeNameMap = explorationRepository.searchTypeMap
            _uiState.searchTypeIdList = explorationRepository.searchTypeIdList.toMutableList()
            _uiState.searchType = explorationRepository.searchTypeIdList.getOrNull(0) ?: return@launch
            _uiState.searchTip = searchTypeTipMap.getOrDefault(_uiState.searchType, "")
            searchHistoryUserData.getFlow().collect {
                it?.let {
                    _uiState.historyList = it.reversed().toMutableList()
                }
            }
        }
        viewModelScope.launch {
            bookshelfRepository.getAllBookshelfBookIdsFlow().collect {
                _uiState.allBookshelfBookIds = it.toMutableList()
            }
        }
    }

    fun changeSearchType(searchTypeId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.searchType = searchTypeId
            _uiState.searchTip = searchTypeTipMap.getOrDefault(_uiState.searchType, "")
        }
    }

    fun deleteHistory(history: String) {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryUserData.update {
                val newList = it.toMutableList()
                newList.remove(history)
                return@update newList
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryUserData.update { emptyList() }
        }
    }

    fun search(
        keyword: String,
        navigateToSingleBook: (bookId: Int) -> Unit
    ) {
        _uiState.isLoading = true
        _uiState.isLoadingComplete = false
        _uiState.errorMessage = ""
        _uiState.searchResult.clear()
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val flow = explorationRepository.search(_uiState.searchType, keyword)
            _uiState.isLoading = false
            flow.collect {
                when(it) {
                    is SearchResult.SingleBook -> launch(Dispatchers.Main) {
                        _uiState.searchBarExpanded = true
                        navigateToSingleBook(it.bookId)
                    }
                    is SearchResult.MultipleBook -> _uiState.searchResult.add(it.bookInformation)
                    is SearchResult.Error -> {
                        _uiState.isLoadingComplete = true
                        _uiState.errorMessage = it.error.message.toString()
                    }
                    is SearchResult.End -> _uiState.isLoadingComplete = true
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryUserData.update {
                val newList = it.toMutableList()
                if (it.contains(keyword))
                    newList.remove(keyword)
                newList.add(keyword)
                return@update newList
            }
        }
    }
}
