package indi.dmzz_yyhyy.lightnovelreader.data.book

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import indi.dmzz_yyhyy.lightnovelreader.utils.CanBeEmpty
import kotlinx.serialization.json.JsonObject

@Stable
interface ChapterContent: CanBeEmpty {
    val id: Int
    val title: String
    val content: String
    val lastChapter: Int
    val nextChapter: Int

    fun hasPrevChapter(): Boolean = lastChapter > -1
    fun hasNextChapter(): Boolean = nextChapter > -1
    override fun isEmpty() = this.id == -1 || this.content.isBlank()

    companion object {
        fun empty(): ChapterContent = MutableChapterContent(-1, "", "")
        fun empty(chapterId: Int): ChapterContent = MutableChapterContent(
            chapterId,
            "",
            ""
        )
    }

    fun toMutable(): MutableChapterContent {
        if (this is MutableChapterContent)
            return this
        return MutableChapterContent(id, title, content, lastChapter, nextChapter)
    }
}

class MutableChapterContent(
    id: Int,
    title: String,
    content: String,
    lastChapter: Int = -1,
    nextChapter: Int = -1
) : ChapterContent {
    override var id by mutableIntStateOf(id)
    override var title by mutableStateOf(title)
    override var content by mutableStateOf(content)
    override var lastChapter by mutableIntStateOf(lastChapter)
    override var nextChapter by mutableIntStateOf(nextChapter)

    companion object {
        fun empty(): MutableChapterContent = MutableChapterContent(-1, "", "")
    }

    fun update(chapterContent: ChapterContent) {
        this.id = chapterContent.id
        this.title = chapterContent.title
        this.content = chapterContent.content
        this.lastChapter = chapterContent.lastChapter
        this.nextChapter = chapterContent.nextChapter
    }

    override fun equals(other: Any?): Boolean {
        if (other is ChapterContent) {
            return this.id == other.id &&
                    this.content == other.content &&
                    this.lastChapter == other.lastChapter &&
                    this.nextChapter == other.nextChapter
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + lastChapter
        result = 31 * result + nextChapter
        return result
    }
}