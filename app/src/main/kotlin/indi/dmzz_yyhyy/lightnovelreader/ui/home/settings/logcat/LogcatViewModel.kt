package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.logcat

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import indi.dmzz_yyhyy.lightnovelreader.data.logging.LogEntry
import indi.dmzz_yyhyy.lightnovelreader.data.logging.LogLevel
import indi.dmzz_yyhyy.lightnovelreader.data.logging.LoggerRepository
import javax.inject.Inject

@HiltViewModel
class LogcatViewModel @Inject constructor (
    private val loggerRepository: LoggerRepository
): ViewModel() {

    private val _uiState = MutableLogcatUiState()
    val uiState: LogcatUiState = _uiState

    fun syncState() {
        _uiState.isLoggingEnabled = loggerRepository.logLevel != LogLevel.NONE
        if (_uiState.selectedLogFile.isBlank()) {
            _uiState.selectedLogFile = LoggerRepository.REALTIME_LOG
        }
        if (!_uiState.isLoggingEnabled && !_uiState.isFileMode) {
            _uiState.displayedLogEntries = emptyList()
        }
    }

    fun startLogging() {
        syncState()
        if (!_uiState.isLoggingEnabled) return
        loggerRepository.startLogging()
        _uiState.isFileMode = false
        _uiState.selectedLogFile = LoggerRepository.REALTIME_LOG
        _uiState.displayedLogEntries = loggerRepository.realTimeLogEntries
        Log.i("Logger", "----- history")
    }

    fun clearLogs() = loggerRepository.refreshLogs()

    fun shareLogs() {
        if (_uiState.isFileMode) {
            loggerRepository.shareLogs(_uiState.selectedLogFile)
        } else {
            loggerRepository.shareLogs()
        }
    }

    val displayedLogEntries: List<LogEntry>
        get() = if (_uiState.isFileMode) {
            loggerRepository.fileLogEntries
        } else {
            loggerRepository.realTimeLogEntries
        }

    fun deleteLogFile(fileName: String) {
        loggerRepository.deleteLogFile(fileName)
        onSelectLogFile(LoggerRepository.REALTIME_LOG)
    }

    fun onSelectLogFile(fileName: String) {
        _uiState.isFileMode = fileName != LoggerRepository.REALTIME_LOG
        _uiState.selectedLogFile = fileName
        if (_uiState.isFileMode) {
            loggerRepository.loadLogFile(fileName)
        } else {
            syncState()
            _uiState.displayedLogEntries = loggerRepository.realTimeLogEntries
        }
    }

    val logFilenameList: List<String>
        get() = loggerRepository.getAvailableLogFiles() + LoggerRepository.REALTIME_LOG
}
