package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable

/** 章节信息：章节 id 与标题。字段与上游 :api 的 ChapterInformation 一致。 */
@Serializable
data class ChapterInformation(
    val id: String,
    val title: String
)
