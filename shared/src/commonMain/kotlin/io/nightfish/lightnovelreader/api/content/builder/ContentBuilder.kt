/*
 * Copyright (c) dmzz-yyhyy (夜鱼很业余) and contributors of LightNovelReader
 *   (https://github.com/dmzz-yyhyy/LightNovelReader)
 * Copyright (c) 2026 komorebiiluvu (iOS Port / KMP Adapter)
 *
 * Ported from the upstream Android project's wenku8 data layer.
 * Modified by komorebiiluvu 2026 for Kotlin Multiplatform.
 * Licensed under the Apache License, Version 2.0.
 */

package io.nightfish.lightnovelreader.api.content.builder

import io.nightfish.lightnovelreader.api.content.component.ImageComponentData
import io.nightfish.lightnovelreader.api.content.component.SimpleTextComponentData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 章节内容构建器：把文本/插图组件组装成与上游 ContentBuilder 完全一致的 JSON 格式。
 * iOS 阅读器按此格式解析正文（文本段落列表 + 插图地址列表）。
 */
class ContentBuilder {
    private val components = mutableListOf<JsonObject>()

    fun simpleText(text: String): ContentBuilder {
        if (text.isBlank()) return this
        components.add(
            buildJsonObject {
                put("id", "lightnovelreader:simple_text")
                put("data", buildJsonObject { put("text", text) })
            }
        )
        return this
    }

    fun image(uri: String): ContentBuilder {
        components.add(
            buildJsonObject {
                put("id", "lightnovelreader:image")
                put("data", buildJsonObject { put("uri", uri) })
            }
        )
        return this
    }

    fun build(): JsonObject =
        buildJsonObject {
            putJsonArray("components") {
                components.forEach { add(it) }
            }
        }

    companion object {
        /** 从上游格式的 content JSON 里提取纯文本段落（iOS 正文渲染用） */
        fun extractParagraphs(content: JsonObject): List<String> {
            val paragraphs = mutableListOf<String>()
            val components = content["components"] as? kotlinx.serialization.json.JsonArray ?: return paragraphs
            for (element in components) {
                val obj = element as? JsonObject ?: continue
                val id = obj["id"]?.toString()?.trim('"') ?: continue
                if (id == "lightnovelreader:simple_text") {
                    val data = obj["data"] as? JsonObject ?: continue
                    val text = data["text"]?.toString()?.trim('"') ?: continue
                    paragraphs.add(text)
                }
            }
            return paragraphs
        }

        /** 从上游格式的 content JSON 里提取插图地址列表 */
        fun extractImages(content: JsonObject): List<String> {
            val images = mutableListOf<String>()
            val components = content["components"] as? kotlinx.serialization.json.JsonArray ?: return images
            for (element in components) {
                val obj = element as? JsonObject ?: continue
                val id = obj["id"]?.toString()?.trim('"') ?: continue
                if (id == "lightnovelreader:image") {
                    val data = obj["data"] as? JsonObject ?: continue
                    val uri = data["uri"]?.toString()?.trim('"') ?: continue
                    images.add(uri)
                }
            }
            return images
        }
    }
}
