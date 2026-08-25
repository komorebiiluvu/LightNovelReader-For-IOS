/*
 * Copyright (c) dmzz-yyhyy (夜鱼很业余) and contributors of LightNovelReader
 *   (https://github.com/dmzz-yyhyy/LightNovelReader)
 * Copyright (c) 2026 komorebiiluvu (iOS Port / KMP Adapter)
 *
 * Ported from the upstream Android project's wenku8 data layer.
 * Modified by komorebiiluvu 2026 for Kotlin Multiplatform.
 * Licensed under the Apache License, Version 2.0.
 */

package io.nightfish.lightnovelreader.api

import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes
import io.nightfish.lightnovelreader.api.book.ChapterContent
import io.nightfish.lightnovelreader.api.error.WebRequestError
import io.nightfish.lightnovelreader.wenku8.ExploreCategoryInfo
import io.nightfish.lightnovelreader.wenku8.ExplorePageInfo
import io.nightfish.lightnovelreader.wenku8.HomeBlockInfo
import io.nightfish.lightnovelreader.wenku8.Wenku8Client
import io.nightfish.lightnovelreader.wenku8.Wenku8DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 暴露给 Swift 的 KMP 门面（SharedKit.framework）。
 *
 * Swift 侧经 `KmpBookSourceAdapter` 调用这里的方法，实现 `BookSourceService` 协议。
 * suspend 函数全部以 completion-handler 形式暴露，Swift 用 async/await 包装。
 * 书 ID 沿用上游格式：`wk8-<aid>`。
 */
class Wenku8DataSourceApi {
    private val client = Wenku8Client()
    private val dataSource = Wenku8DataSource(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 书源名（与现有 Wenku8Service 保持一致，便于用户数据迁移） */
    val sourceName: String = "文库8(在线)"

    /** 是否已登录（cookie 里含 jieqiUserInfo） */
    val isLoggedIn: Boolean
        get() = client.isLoggedIn

    /** 内置登录账号（与现有 Wenku8Service 一致；后续可改为用户手动登录） */
    val bundledUsername: String = "komorebiiluv"
    val bundledPassword: String = "komorebi041016"

    /** 持久化登录 cookie */
    var savedCookie: String?
        get() = client.savedCookie
        set(value) { client.savedCookie = value }

    /** 当前登录用户名（从 cookie 里解析 jieqiUserName，未登录返回 null） */
    val loggedInUsername: String?
        get() {
            val cookie = client.savedCookie ?: return null
            val decoded = cookie.removeSuffix("%")
            val idx = decoded.indexOf("jieqiUserName=")
            if (idx < 0) return null
            val after = decoded.substring(idx + "jieqiUserName=".length)
            val name = after.takeWhile { it != ',' && it != ';' && !it.isWhitespace() }
            return name.ifEmpty { null }
        }

    /** 退出登录（清除会话 cookie） */
    fun logout() {
        client.savedCookie = null
    }

    // MARK: - 登录

    fun login(
        username: String,
        password: String,
        completionHandler: (String?, Throwable?) -> Unit
    ) {
        scope.launch {
            // 登录加超时保护：无网/站点不可达时快速失败，避免启动流程串行卡死
            val outcome = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                client.login(username, password)
            }
            if (outcome == null) {
                completionHandler("登录超时", null)
            } else {
                completionHandler(
                    outcome.fold(onSuccess = { null }, onFailure = { it.message }),
                    null
                )
            }
        }
    }

    // MARK: - 搜索

    /**
     * 搜索书名，返回匹配的书 ID 列表（仅第 [page] 页；`page` 从 1 开始）。
     * 内部消费 [Wenku8DataSource.search] 的 Flow，收集 SingleBook/MultipleBook 的 ID。
     */
    fun searchBookIds(
        keyword: String,
        page: Int,
        completionHandler: (List<String>?, Throwable?) -> Unit
    ) {
        scope.launch {
            val ids = mutableListOf<String>()
            var currentPage = 0
            try {
                dataSource.search("articlename", keyword).collect { result ->
                    when (result) {
                        is Wenku8DataSource.SearchResult.SingleBook -> ids.add(result.bookId)
                        is Wenku8DataSource.SearchResult.MultipleBook -> ids.add(result.bookId)
                        is Wenku8DataSource.SearchResult.Error -> {
                            throw Exception(result.message)
                        }
                        Wenku8DataSource.SearchResult.End -> {
                            currentPage += 1
                            if (currentPage >= page) return@collect
                        }
                    }
                }
                completionHandler(ids, null)
            } catch (e: Throwable) {
                completionHandler(null, e)
            }
        }
    }

    // MARK: - 探索（对齐上游 Wenku8ExplorePageProvider）

    /** 探索「全部」板块的栏目列表（对齐上游 6 个 expanded page） */
    val exploreCategories: List<ExploreCategoryInfo>
        get() = dataSource.exploreCategories()

    /** 全站标签列表（对齐上游硬编码 48 标签） */
    val tagList: List<String>
        get() = dataSource.tagList()

    /** 标签 → 书库浏览栏目（对齐上游 tags.php?t= 展开页） */
    fun tagCategory(tag: String): ExploreCategoryInfo = dataSource.tagCategory(tag)

    /** 探索首页推荐块（对齐上游 Wenku8HomeExploreTapPage） */
    fun getHomeBlocks(completionHandler: (List<HomeBlockInfo>?, Throwable?) -> Unit) {
        scope.launch {
            try {
                val result = dataSource.getHomeBlocks()
                result.fold(
                    onSuccess = { completionHandler(it, null) },
                    onFailure = { completionHandler(null, errorFrom(it)) }
                )
            } catch (e: Throwable) {
                completionHandler(null, NSErrorCompat(2, "解析首页推荐失败", e.message ?: "未知错误"))
            }
        }
    }

    /** 探索栏目/标签展开页分页（对齐上游 HomeBookExpandPageDataSource） */
    fun fetchExplorePage(
        path: String,
        extraParams: String,
        page: Int,
        completionHandler: (ExplorePageInfo?, Throwable?) -> Unit
    ) {
        scope.launch {
            try {
                val category = ExploreCategoryInfo("", "", path.trimStart('/'), extraParams)
                val result = dataSource.getExplorePage(category, page)
                result.fold(
                    onSuccess = { completionHandler(it, null) },
                    onFailure = { completionHandler(null, errorFrom(it)) }
                )
            } catch (e: Throwable) {
                completionHandler(null, NSErrorCompat(2, "解析探索页失败", e.message ?: "未知错误"))
            }
        }
    }

    // MARK: - 详情

    fun getBookInformation(
        bookId: String,
        completionHandler: (BookInformation?, Throwable?) -> Unit
    ) {
        scope.launch {
            try {
                val result = dataSource.getBookInformation(bookId)
                result.fold(
                    onSuccess = { completionHandler(it, null) },
                    onFailure = { completionHandler(null, errorFrom(it)) }
                )
            } catch (e: Throwable) {
                completionHandler(null, NSErrorCompat(2, "解析书籍信息失败", e.message ?: "未知错误"))
            }
        }
    }

    // MARK: - 目录

    fun getBookVolumes(
        bookId: String,
        completionHandler: (BookVolumes?, Throwable?) -> Unit
    ) {
        scope.launch {
            try {
                val result = dataSource.getBookVolumes(bookId)
                result.fold(
                    onSuccess = { completionHandler(it, null) },
                    onFailure = { completionHandler(null, errorFrom(it)) }
                )
            } catch (e: Throwable) {
                completionHandler(null, NSErrorCompat(2, "解析目录失败", e.message ?: "未知错误"))
            }
        }
    }

    // MARK: - 正文

    fun getChapterContent(
        chapterId: String,
        bookId: String,
        completionHandler: (ChapterContent?, Throwable?) -> Unit
    ) {
        scope.launch {
            try {
                val result = dataSource.getChapterContent(chapterId, bookId)
                result.fold(
                    onSuccess = { completionHandler(it, null) },
                    onFailure = { completionHandler(null, errorFrom(it)) }
                )
            } catch (e: Throwable) {
                // 防御：Ksoup 解析/正文构建的意外异常转成错误回调，避免协程异常导致 app 崩溃
                completionHandler(null, NSErrorCompat(2, "解析正文失败", e.message ?: "未知错误"))
            }
        }
    }

    /**
     * 正文获取（Swift 友好版）：Kotlin 侧直接解析段落/图片/标题/上下章，
     * 一次性返回纯 String 类型，避免 JsonObject 跨 Swift 边界桥接崩溃。
     */
    fun getChapterContentFlat(
        chapterId: String,
        bookId: String,
        completionHandler: (ChapterContentFlat?, Throwable?) -> Unit
    ) {
        scope.launch {
            try {
                val result = dataSource.getChapterContent(chapterId, bookId)
                result.fold(
                    onSuccess = { content ->
                        val paragraphs = ContentBuilderExtractor.extractParagraphs(content.content)
                        val images = ContentBuilderExtractor.extractImages(content.content)
                        completionHandler(
                            ChapterContentFlat(
                                title = content.title,
                                paragraphs = paragraphs,
                                images = images,
                                prevChapter = content.prevChapter,
                                nextChapter = content.nextChapter
                            ),
                            null
                        )
                    },
                    onFailure = { completionHandler(null, errorFrom(it)) }
                )
            } catch (e: Throwable) {
                completionHandler(null, NSErrorCompat(2, "解析正文失败", e.message ?: "未知错误"))
            }
        }
    }

    /** 把组件化 content JSON 转成纯文本段落（Swift 正文渲染用） */
    fun extractParagraphs(contentJson: JsonObject): List<String> =
        ContentBuilderExtractor.extractParagraphs(contentJson)

    /** 把组件化 content JSON 转成插图地址列表 */
    fun extractImages(contentJson: JsonObject): List<String> =
        ContentBuilderExtractor.extractImages(contentJson)

    // MARK: - 错误映射

    private fun errorFrom(error: Throwable): NSErrorCompat {
        val title = (error as? WebRequestError)?.title ?: "请求失败"
        return NSErrorCompat(
            code = 1,
            title = title,
            message = error.message ?: "网络错误"
        )
    }

    /** 关闭底层资源（App 退出时调用） */
    fun close() {
        scope.cancel()
    }
}

/** 供 Swift 桥接使用的简单错误类型 */
class NSErrorCompat(
    val code: Int,
    val title: String,
    override val message: String
) : Exception("$title: $message")

/** ContentBuilder 提取器的别名封装（Swift 友好） */
object ContentBuilderExtractor {
    fun extractParagraphs(contentJson: JsonObject): List<String> =
        io.nightfish.lightnovelreader.api.content.builder.ContentBuilder.extractParagraphs(contentJson)

    fun extractImages(contentJson: JsonObject): List<String> =
        io.nightfish.lightnovelreader.api.content.builder.ContentBuilder.extractImages(contentJson)
}

/** 给 Swift 看的简单 JSON 构造/解析辅助（与 kotlinx.serialization 桥接） */
object JsonBridge {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseObject(jsonString: String): JsonObject? =
        runCatching { json.parseToJsonElement(jsonString) as? JsonObject }.getOrNull()
}

/** 给 Swift 测试/调试用的 GBK 解码桥（验证完整解码表） */
object GbkBridge {
    /** 把 GBK/GB18030 十六进制字节串（如 "B2BBCAAC"）解码成字符串 */
    fun decodeHex(hex: String): String {
        val cleaned = hex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        val bytes = ArrayList<Byte>(cleaned.length / 2)
        var i = 0
        while (i + 1 < cleaned.length) {
            val hi = cleaned[i].digitToIntOrNull(16)
            val lo = cleaned[i + 1].digitToIntOrNull(16)
            if (hi == null || lo == null) { i += 1; continue }
            bytes.add(((hi shl 4) or lo).toByte())
            i += 2
        }
        return io.nightfish.lightnovelreader.api.util.Gbk.decodeToString(bytes.toByteArray())
    }
}

/** 正文数据（Swift 友好）：纯 String 字段，避免 JsonObject 跨 Swift 边界 */
class ChapterContentFlat(
    val title: String,
    val paragraphs: List<String>,
    val images: List<String>,
    val prevChapter: String?,
    val nextChapter: String?
)
