package indi.dmzz_yyhyy.lightnovelreader.data.plugin

import androidx.annotation.StringRes
import com.github.michaelbull.result.Result

sealed interface InstallState {
    sealed class Start(
        @StringRes strId: Int
    ): InstallState {
        object PrasePackageInfo: Start()
        object PrasePluginMetadata: Start()
        object Clean: Start()
        object CheckPluginInstallLegality: Start()
        object WritePluginMetadataToFile: Start()
        object CopyPlugin: Start()
    }

    class Completed(
        val pluginPackage: String
    ): InstallState

    class Error(
        val result: Throwable
    ): InstallState
}