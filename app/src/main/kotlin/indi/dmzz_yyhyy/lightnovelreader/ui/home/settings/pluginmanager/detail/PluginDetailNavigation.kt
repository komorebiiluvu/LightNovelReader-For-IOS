package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.pluginmanager.detail

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import indi.dmzz_yyhyy.lightnovelreader.data.plugin.PluginSource
import io.nightfish.lightnovelreader.api.ui.LocalNavController
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.pluginmanager.PluginManagerViewModel
import indi.dmzz_yyhyy.lightnovelreader.ui.navigation.Route
import indi.dmzz_yyhyy.lightnovelreader.utils.popBackStackIfResumed

fun NavGraphBuilder.settingsPluginManagerDetailDestination() {
    composable<Route.Main.Settings.PluginManager.Detail> { navBackStackEntry ->
        val navController = LocalNavController.current

        val viewModel = hiltViewModel<PluginManagerViewModel>()
        val pluginId = navBackStackEntry.toRoute<Route.Main.Settings.PluginManager.Detail>().id
        val plugin = viewModel.pluginList.find { it.id == pluginId }
        val enabledPluginList by viewModel.enabledPluginFlow.collectAsState(emptyList())
        val enabledPluginPackageList by viewModel.enabledPluginPackagesFlow.collectAsState(emptyList())
        val enabled = when (plugin?.source) {
            PluginSource.InstalledApp -> {
                val pkg = plugin.packageName
                pkg != null && enabledPluginPackageList.contains(pkg)
            }
            else -> enabledPluginList.contains(pluginId)
        }
        PluginDetailScreen(
            enabled = enabled,
            pluginInfo = plugin,
            onClickBack = navController::popBackStackIfResumed,
            onClickSwitch = viewModel::onClickEnabledSwitch,
            pluginContent = { viewModel.PluginContent(pluginId, it) }
        )
    }
}

fun NavController.navigateToSettingsPluginManagerDetailDestination(id: String) {
    navigate(Route.Main.Settings.PluginManager.Detail(id))
}
