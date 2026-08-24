package io.nightfish.lightnovelreader.api.content.component

import kotlinx.serialization.Serializable

/**
 * 插图内容组件。id 为 `lightnovelreader:image`，[uri] 为图片地址。
 * 与上游 :api 一致（上游用 android.net.Uri，这里用 String 表示地址）。
 */
@Serializable
data class ImageComponentData(
    val uri: String
)
