package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable

/** 一本书的完整目录：卷列表。字段与上游 :api 的 BookVolumes 一致。 */
@Serializable
data class BookVolumes(
    val bookId: String,
    val volumes: List<Volume>
)
