package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.logcat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import indi.dmzz_yyhyy.lightnovelreader.data.logging.LogEntry

@Stable
interface LogcatUiState {
    val isFileMode: Boolean
    val isLoggingEnabled: Boolean
    val selectedLogFile: String
    val displayedLogEntries: List<LogEntry>
}

class MutableLogcatUiState : LogcatUiState {
    override var isFileMode: Boolean by mutableStateOf(false)
    override var isLoggingEnabled: Boolean by mutableStateOf(false)
    override var selectedLogFile: String by mutableStateOf("")
    override var displayedLogEntries: List<LogEntry> by mutableStateOf(emptyList())
}
