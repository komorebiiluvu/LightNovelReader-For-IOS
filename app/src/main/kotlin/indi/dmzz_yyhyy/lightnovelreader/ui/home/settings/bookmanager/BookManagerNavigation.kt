package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.bookmanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import indi.dmzz_yyhyy.lightnovelreader.ui.navigation.Route
import indi.dmzz_yyhyy.lightnovelreader.utils.popBackStackIfResumed
import io.nightfish.lightnovelreader.api.ui.LocalNavController

fun NavGraphBuilder.settingsBookManagerDestination() {
    composable<Route.Main.Settings.BookManager> {
        val navController = LocalNavController.current
        val viewModel = hiltViewModel<BookManagerViewModel>()

        var showTxtImportSettings by remember { mutableStateOf(false) }
        var showEpubImportSettings by remember { mutableStateOf(false) }

        BookManagerScreen(
            onClickBack = navController::popBackStackIfResumed,
            onImportLocalFile = { type, uri ->
                when(type) {
                    ImportType.TXT -> {
                        showTxtImportSettings = true
                    }
                    ImportType.EPUB -> {
                        showEpubImportSettings = true
                    }
                }
            }
        )

        if (showTxtImportSettings) {
            TxtImportSettingsBottomSheet(
                onClickImport = { a, b -> },
                onDismissRequest = { showTxtImportSettings = false }
            )
        }

        if (showEpubImportSettings) {
            EpubImportSettingsBottomSheet(
                onClickImport =  { a, b -> },
                onDismissRequest = { showEpubImportSettings = false}
            )
        }

    }
}

fun NavController.navigateToSettingsBookManagerDestination() {
    navigate(Route.Main.Settings.BookManager)
}
