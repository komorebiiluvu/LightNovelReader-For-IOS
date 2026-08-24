package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable

/**
 * 用户阅读数据（进度）。字段与上游 :api 的 UserReadingData 一致，
 * 用于 iOS 与上游 Android 版之间互通阅读进度。
 */
@Serializable
data class UserReadingData(
    val id: String,
    val lastReadTime: Long? = null,
    val totalReadTime: Int = 0,
    val readingProgress: Float = 0f,
    val lastReadChapterId: String? = null,
    val lastReadChapterTitle: String? = null,
    val currentChapterReadingProgressMap: Map<String, Float> = emptyMap(),
    val maxChapterReadingProgressMap: Map<String, Float> = emptyMap()
)
