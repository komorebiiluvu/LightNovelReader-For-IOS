package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.sourcechange

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.nightfish.lightnovelreader.api.Route
import indi.dmzz_yyhyy.lightnovelreader.utils.LocalSnackbarHost
import indi.dmzz_yyhyy.lightnovelreader.utils.isResumed
import indi.dmzz_yyhyy.lightnovelreader.utils.popBackStackIfResumed
import io.nightfish.lightnovelreader.api.ui.LocalNavController

fun NavGraphBuilder.settingsSourceChangeDestination() {
    composable<Route.Main.Settings.SourceChange> {
        val navController = LocalNavController.current
        val viewModel = hiltViewModel<SourceChangeViewModel>()
        val snackbarHostState = LocalSnackbarHost.current

        LaunchedEffect(viewModel.snackbarFlow) {
            viewModel.snackbarFlow.collect { message ->
                snackbarHostState.showSnackbar(message, withDismissAction = true)
            }
        }

        SourceChangeScreen(
            uiState = viewModel.uiState,
            onClickBack = navController::popBackStackIfResumed,
            onApplyClick = { selectedId ->
                viewModel.changeWebSource(selectedId)
            },
        )
    }
}

fun NavController.navigateToSettingsSourceChangeDestination() {
    if (!this.isResumed()) return
    navigate(Route.Main.Settings.SourceChange)
}
