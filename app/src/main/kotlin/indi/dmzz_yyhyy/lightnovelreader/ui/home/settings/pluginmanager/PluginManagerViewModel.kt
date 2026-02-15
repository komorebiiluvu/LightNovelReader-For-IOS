package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.pluginmanager

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import indi.dmzz_yyhyy.lightnovelreader.data.plugin.PluginMetadata
import indi.dmzz_yyhyy.lightnovelreader.data.plugin.PluginManager
import indi.dmzz_yyhyy.lightnovelreader.data.userdata.UserDataRepository
import io.nightfish.lightnovelreader.api.userdata.UserDataPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PluginManagerViewModel @Inject constructor(
    val pluginManager: PluginManager,
    val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val enabledPluginUserData = userDataRepository.stringListUserData(UserDataPath.Plugin.EnabledPlugins.path)
    val enabledPluginFlow = enabledPluginUserData.getFlowWithDefault(emptyList())

    val errorPluginUserData =
        userDataRepository.stringListUserData(UserDataPath.Plugin.ErrorPlugins.path)
    val errorPluginIdsFlow = errorPluginUserData.getFlowWithDefault(emptyList())

    private val _snackbarFlow = MutableSharedFlow<String>()
    val snackbarFlow = _snackbarFlow.asSharedFlow()

    fun onClickEnabledSwitch(pluginInfo: PluginMetadata) {

    }

    fun showSnackbar(message: String) {
        viewModelScope.launch(Dispatchers.Main) { _snackbarFlow.emit(message) }
    }

    suspend fun refreshAppPlugins() = withContext(Dispatchers.IO) {
        //FIXME
    }

    @Composable
    fun PluginContent(id: String, paddingValues: PaddingValues) {
        pluginManager.PluginContent(id, paddingValues)
    }
}
