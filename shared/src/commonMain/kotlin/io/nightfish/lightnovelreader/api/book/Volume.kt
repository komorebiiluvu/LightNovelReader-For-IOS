package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable

/** 一卷：卷 id、卷标题、卷内章节。字段与上游 :api 的 Volume 一致。 */
@Serializable
data class Volume(
    val volumeId: String,
    val volumeTitle: String,
    val chapters: List<ChapterInformation>
)
