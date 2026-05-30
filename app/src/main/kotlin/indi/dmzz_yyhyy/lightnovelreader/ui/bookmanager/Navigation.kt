package indi.dmzz_yyhyy.lightnovelreader.ui.bookmanager

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.nightfish.lightnovelreader.api.ui.LocalNavController
import io.nightfish.lightnovelreader.api.Route
import indi.dmzz_yyhyy.lightnovelreader.utils.LocalSnackbarHost
import indi.dmzz_yyhyy.lightnovelreader.utils.isResumed
import indi.dmzz_yyhyy.lightnovelreader.utils.popBackStackIfResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavGraphBuilder.bookManager() {
    composable<Route.BookManager> {
        val navController = LocalNavController.current
        val snackbarHostState = LocalSnackbarHost.current
        val coroutineScope = rememberCoroutineScope()
        val viewModel = hiltViewModel<BookManagerViewModel>()
        val uiState = viewModel.localBookManagerUiState
        LaunchedEffect(navController, snackbarHostState, coroutineScope) {
            uiState.deleteSelected = {
                coroutineScope.launch {
                    val count = withContext(Dispatchers.IO) {
                        viewModel.deleteSelectedLocalBooks()
                    }
                    if (count > 0) {
                        snackbarHostState.showSnackbar("Cleared $count items", withDismissAction = true)
                    }
                }
            }
            uiState.clearOrphanedData = {
                coroutineScope.launch {
                    val count = withContext(Dispatchers.IO) {
                        viewModel.clearOrphanedData()
                    }
                    snackbarHostState.showSnackbar("Cleared $count items", withDismissAction = true)
                }
            }
            uiState.openStorageOverview = {
                navController.navigate(Route.StorageManager)
            }
            uiState.openBookDetailScreen = { id ->
                navController.navigate(Route.Book.Detail(id))
            }
            uiState.clearBookData = { _, _ -> }
        }
        BookManagerScreen(
            onClickBack = navController::popBackStackIfResumed,
            downloadItemIdList = viewModel.downloadItemIdList,
            bookInformationMap = viewModel.bookInformationMap,
            uiState = uiState,
            loadBookInfo = viewModel::loadBookInfo,
            onClickCancel = viewModel::onClickCancel,
            onClickClearCompleted = viewModel::onClickClearCompleted
        )
    }
}

fun NavController.navigateToDownloadManager() {
    if (!this.isResumed()) return
    navigate(Route.BookManager)
}
