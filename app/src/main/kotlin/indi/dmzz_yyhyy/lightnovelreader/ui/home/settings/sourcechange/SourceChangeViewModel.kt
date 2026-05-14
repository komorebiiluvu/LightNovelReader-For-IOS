package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.sourcechange

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrapError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import indi.dmzz_yyhyy.lightnovelreader.data.local.LocalDataManager
import indi.dmzz_yyhyy.lightnovelreader.data.local.room.LightNovelReaderDatabase
import indi.dmzz_yyhyy.lightnovelreader.data.userdata.UserDataRepository
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSourceManager
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSourceProvider
import io.nightfish.lightnovelreader.api.userdata.UserDataPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class SourceChangeViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val webBookDataSourceProvider: WebBookDataSourceProvider,
    private val localDataManager: LocalDataManager,
    private val database: LightNovelReaderDatabase,
    private val userDataRepository: UserDataRepository,
    webBookDataSourceManager: WebBookDataSourceManager
) : ViewModel() {

    private val _uiState = MutableSourceChangeUiState().apply {
        currentSourceId = webBookDataSourceProvider.default.id
        webDataSourceItems = webBookDataSourceManager.webDataSourceItems
    }
    private val _snackbarFlow = MutableSharedFlow<String>()
    val snackbarFlow = _snackbarFlow.asSharedFlow()
    val uiState: SourceChangeUiState = _uiState
    fun changeWebSource(newWebDataSourceId: Int) {
        if (newWebDataSourceId == _uiState.currentSourceId) return
        if (_uiState.isProcessing) return

        _uiState.isProcessing = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = localDataManager.saveCurrentDataSourceToFile()
                    .andThen {
                        runCatching {
                            database.withTransaction {
                                localDataManager.cleanDatabaseWithoutGlobalUserData()
                                localDataManager.restoreDataSourceFromFile(newWebDataSourceId).let {
                                    it.component1() ?: throw it.unwrapError()
                                }
                                userDataRepository
                                    .intUserData(UserDataPath.Settings.Data.WebDataSourceId.path)
                                    .set(newWebDataSourceId)
                            }
                        }
                    }

                if (result.isErr) {
                    Log.e("SourceChangeViewModel", "Failed to change data source.")
                    result.unwrapError().printStackTrace()
                    viewModelScope.launch(Dispatchers.Main) {
                        _snackbarFlow.emit("Failed to change data source. Please check the log for more information")
                    }

                    return@launch
                }

                _uiState.currentSourceId = newWebDataSourceId
                restartApp(appContext)
            } finally {
                _uiState.isProcessing = false
            }
        }
    }

    private fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}
