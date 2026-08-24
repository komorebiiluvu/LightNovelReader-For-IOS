package io.nightfish.lightnovelreader.api.content.component

import kotlinx.serialization.Serializable

/**
 * 纯文本内容组件。id 为 `lightnovelreader:simple_text`，与上游 :api 一致。
 */
@Serializable
data class SimpleTextComponentData(
    val text: String
)
