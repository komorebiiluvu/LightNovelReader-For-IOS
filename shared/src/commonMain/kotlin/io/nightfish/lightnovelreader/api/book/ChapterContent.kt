package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 章节正文。字段与上游 :api 的 ChapterContent 一致：
 * [content] 是组件化 JSON（与上游 ContentBuilder 输出格式一致）：
 * `{"components":[{"id":"lightnovelreader:simple_text","data":{"text":"..."}},{"id":"lightnovelreader:image","data":{"uri":"..."}}]}`
 */
@Serializable
data class ChapterContent(
    val id: String,
    val title: String,
    val content: JsonObject,
    val prevChapter: String? = null,
    val nextChapter: String? = null
)
