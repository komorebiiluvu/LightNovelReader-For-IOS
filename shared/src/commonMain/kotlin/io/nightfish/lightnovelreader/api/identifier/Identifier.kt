package io.nightfish.lightnovelreader.api.identifier

import kotlinx.serialization.Serializable

/**
 * 书源标识符。与上游 :api 的 Identifier 一致（去掉 Parcelable）。
 * 格式 `namespace:id`，如 `lightnovelreader:Wenku8`。
 */
@Serializable
data class Identifier(
    val namespace: String,
    val id: String
) {
    override fun toString(): String = "$namespace:$id"

    companion object {
        val WENKU8_NAMESPACE = "lightnovelreader"
        val WENKU8_ID = "Wenku8"
    }
}

/** `"Wenku8".ofId()` 等价物：生成 `lightnovelreader:Wenku8` */
fun wenku8Identifier(): Identifier = Identifier(Identifier.WENKU8_NAMESPACE, Identifier.WENKU8_ID)
