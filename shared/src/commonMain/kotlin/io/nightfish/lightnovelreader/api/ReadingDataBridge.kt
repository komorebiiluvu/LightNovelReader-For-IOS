package io.nightfish.lightnovelreader.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * 阅读数据互通门面：iOS 与上游 Android 版之间同步书架/进度。
 *
 * 上游 Android 版的数据（Room 库）未来若通过云端/备份互通，
 * 书架与阅读进度的 JSON 格式以本文件定义的结构为准（字段与上游 :api 对齐）。
 * 当前 iOS 端本地已有完整的书架/进度持久化（AppStore），
 * 本门面预留导入导出入口，供将来对接上游数据格式。
 */
class ReadingDataBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    /** 把 iOS 本地书架序列化成上游兼容的 Bookshelf JSON */
    fun exportBookshelves(shelves: List<BookshelfInput>): String {
        val jsonObj = buildJsonShelves(shelves)
        return jsonObj.toString()
    }

    /** 解析上游格式的书架 JSON 成 iOS 可用结构 */
    fun importBookshelves(jsonString: String): List<BookshelfInput> {
        val obj = json.parseToJsonElement(jsonString) as? JsonObject ?: return emptyList()
        val shelves = obj["bookshelves"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return shelves.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            BookshelfInput(
                id = o["id"]?.toString()?.trim('"').orEmpty(),
                name = o["name"]?.toString()?.trim('"').orEmpty(),
                bookIds = (o["allBookIds"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.toString().trim('"').takeIf { s -> s.isNotEmpty() } }.orEmpty()
            )
        }
    }

    /** 导出阅读进度为上游兼容格式 */
    fun exportReadingData(bookId: String, chapterId: String?, progress: Float): String {
        return buildString {
            append("{\"id\":\"")
            append(bookId)
            append("\",\"lastReadChapterId\":")
            append(chapterId?.let { "\"$it\"" } ?: "null")
            append(",\"readingProgress\":")
            append(progress)
            append("}")
        }
    }

    private fun buildJsonShelves(shelves: List<BookshelfInput>): JsonObject {
        val jsonArray = kotlinx.serialization.json.buildJsonArray {
            shelves.forEach { shelf ->
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(shelf.id))
                        put("name", kotlinx.serialization.json.JsonPrimitive(shelf.name))
                        putJsonArray("allBookIds") {
                            shelf.bookIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        }
                    }
                )
            }
        }
        return kotlinx.serialization.json.buildJsonObject {
            put("bookshelves", jsonArray)
        }
    }

    fun close() {
        scope.cancel()
    }
}

/** iOS 侧书架的最小可互通表示 */
data class BookshelfInput(
    val id: String,
    val name: String,
    val bookIds: List<String>
)
