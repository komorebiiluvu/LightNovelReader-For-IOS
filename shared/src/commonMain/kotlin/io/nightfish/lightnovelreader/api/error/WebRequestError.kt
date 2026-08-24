package io.nightfish.lightnovelreader.api.error

import kotlinx.serialization.Serializable

/**
 * 书源请求错误。字段与上游 :api 的 WebRequestError 一致。
 *
 * 继承 [Exception] 以作为 Kotlin 内置 `Result<T>` 的失败值（Kotlin/Native 导出到
 * Swift 时统一走 NSError，这里保留结构化 title/message 便于门面映射）。
 */
@Serializable
data class WebRequestError(
    val title: String,
    override val message: String
) : Exception(message)
