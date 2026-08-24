package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable

/** 字数统计。字段与上游 :api 的 WordCount 一致（unit 可空，unitResId 为 Android 专属，已去掉）。 */
@Serializable
data class WordCount(
    val count: Int,
    val unit: String? = null
)
