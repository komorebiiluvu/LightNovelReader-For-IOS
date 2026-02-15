package indi.dmzz_yyhyy.lightnovelreader.ui.dialog

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import indi.dmzz_yyhyy.lightnovelreader.data.plugin.PluginManager
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.pluginmanager.MutablePluginInstallerDialogUiState
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.pluginmanager.PluginInstallerDialogUiState
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class PluginInstallerDialogViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginManager: PluginManager
) : ViewModel() {
    val uiState: PluginInstallerDialogUiState = MutablePluginInstallerDialogUiState()

    private val _snackbarFlow = MutableSharedFlow<String>()
    val snackbarFlow = _snackbarFlow.asSharedFlow()


}
