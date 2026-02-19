package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.bookmanager

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nightfish.lightnovelreader.api.book.MutableBookInformation
import io.nightfish.lightnovelreader.api.book.WordCount
import java.time.LocalDateTime

/* 导入文件的设置表单
   由于选择Epub后应进行解析，以下各项会被识别并填充，未识别的则留空
   标题和作者不允许为空
 */
@Stable
class ImportFormState {
    var selected by mutableStateOf<Uri?>(null)
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var author by mutableStateOf("")
    var description by mutableStateOf("")
    var publishingHouse by mutableStateOf("")
    var tagsText by mutableStateOf("")
    var coverUriText by mutableStateOf("")
    var coverUri by mutableStateOf<Uri?>(null)

    val canImport
        get() = selected != null && title.isNotBlank() && author.isNotBlank()

    fun reset() {
        selected = null
        title = ""
        subtitle = ""
        author = ""
        description = ""
        publishingHouse = ""
        tagsText = ""
        coverUriText = ""
        coverUri = null
    }

    // FIXME
    fun toBookInformation() = MutableBookInformation(
        id = "",
        title = title,
        subtitle = subtitle,
        coverUrl = coverUri ?: Uri.EMPTY,
        author = author,
        description = description,
        tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
        publishingHouse = publishingHouse,
        wordCount = WordCount(0),
        lastUpdated = LocalDateTime.now(),
        isComplete = true
    )
}