package io.nightfish.lightnovelreader.api.bookshelf

import kotlinx.serialization.Serializable

/** 书架。字段与上游 :api 的 Bookshelf 一致（sortType 简化为字符串枚举）。 */
@Serializable
data class Bookshelf(
    val id: String = "",
    val name: String = "",
    val sortType: String = "Default",
    val sortReversed: Boolean = false,
    val autoCache: Boolean = false,
    val systemUpdateReminder: Boolean = false,
    val allBookIds: List<String> = emptyList(),
    val pinnedBookIds: List<String> = emptyList(),
    val updatedBookIds: List<String> = emptyList()
)
