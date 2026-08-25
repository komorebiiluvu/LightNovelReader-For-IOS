/*
 * Copyright (c) dmzz-yyhyy (夜鱼很业余) and contributors of LightNovelReader
 *   (https://github.com/dmzz-yyhyy/LightNovelReader)
 * Copyright (c) 2026 komorebiiluvu (iOS Port / KMP Adapter)
 *
 * Ported from the upstream Android project's wenku8 data layer.
 * Modified by komorebiiluvu 2026 for Kotlin Multiplatform.
 * Licensed under the Apache License, Version 2.0.
 */

package io.nightfish.lightnovelreader.api.book

import kotlinx.serialization.Serializable

/**
 * 书籍基本信息。字段与上游 LightNovelReader `:api` 模块的 BookInformation 一一对应，
 * 仅把平台类型替换为 KMP 可移植类型（android.net.Uri → String 封面地址）。
 *
 * @since Api 2
 */
@Serializable
data class BookInformation(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String? = null,
    val author: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val publishingHouse: String = "",
    val wordCount: WordCount? = null,
    val lastUpdated: String? = null,
    val isComplete: Boolean = false
)
