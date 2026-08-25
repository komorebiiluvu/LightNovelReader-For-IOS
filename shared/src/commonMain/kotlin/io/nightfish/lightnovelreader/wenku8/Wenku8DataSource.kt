/*
 * Copyright (c) dmzz-yyhyy (夜鱼很业余) and contributors of LightNovelReader
 *   (https://github.com/dmzz-yyhyy/LightNovelReader)
 * Copyright (c) 2026 komorebiiluvu (iOS Port / KMP Adapter)
 *
 * Ported from the upstream Android project's wenku8 data layer.
 * Modified by komorebiiluvu 2026 for Kotlin Multiplatform.
 * Licensed under the Apache License, Version 2.0.
 */

package io.nightfish.lightnovelreader.wenku8

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes
import io.nightfish.lightnovelreader.api.book.ChapterContent
import io.nightfish.lightnovelreader.api.book.ChapterInformation
import io.nightfish.lightnovelreader.api.book.Volume
import io.nightfish.lightnovelreader.api.book.WordCount
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.error.WebRequestError
import io.nightfish.lightnovelreader.api.util.Gbk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * wenku8 抓取器：从上游 `Wenku8WebsiteDataSource` 移植，把 JVM 依赖换成 KMP 等价物：
 * - jsoup → Ksoup（KMP 版 HTML 解析器，API 与 jsoup 高度兼容）
 * - URLEncoder(gb2312) → [Gbk.percentEncode]
 * - GB18030 解码 → [Gbk.decodeToString]
 */
class Wenku8DataSource(
    private val client: Wenku8Client,
    private val hosts: List<String> = listOf(
        "https://www.wenku8.cc",
        "https://www.wenku8.net",
        "https://www.wenku8.com"
    )
) {
    private val titleRegex = Regex("(.*) ?[(（](.*)[)）] ?$")
    private val json = Json { ignoreUnknownKeys = true }

    private fun host(): String = hosts.first()

    private fun url(string: String): String = "${host()}/$string"

    private fun aidOf(bookId: String): Int = bookId.toIntOrNull() ?: 0

    // MARK: - 详情

    suspend fun getBookInformation(id: String): Result<BookInformation> {
        val html: String = client.getHtml(url("book/$id.htm")).getOrElse {
            return Result.failure(WebRequestError("网络请求失败", it.message ?: "网络错误"))
        }
        val soup = Ksoup.parse(html)
        // 标题：详情页嵌套 table 里的第一个 <b>（真实页面无固定层级，用宽松选择器）
        val titleEl = soup.selectFirst("#content b")
        val titleText = titleEl?.text() ?: return Result.failure(WebRequestError("解析错误", "无法解析该书本的信息(id=$id)"))
        if (soup.text().contains("因版权问题")) {
            return Result.failure(WebRequestError("版权错误", "由于「$titleText」为Wenku8上具有版权文件的书籍的章节, 我们无法提供其数据"))
        }
        val titleGroup = titleRegex.find(titleText)?.groups
        val title = titleGroup?.get(1)?.value ?: titleText
        val subtitle = titleGroup?.get(2)?.value ?: ""

        // 封面：content 区域里的图片（img.wenku8.com 或 /image/ 路径）
        val coverUrl = soup.selectFirst("#content img[src*='wenku8.com'], #content img[src*='/image/']")?.attr("src")

        // 信息区 DOM 定位：找含「key：」的 td，取 value（与简介 DOM 方案一致，不依赖 text() 拼接）
        fun fieldValue(label: String): String {
            val td = soup.selectFirst("#content td:contains($label)") ?: return ""
            val text = td.text()
            val idx = text.indexOf(label)
            if (idx < 0) return ""
            return text.substring(idx + label.length).trim().removeSuffix("字")
        }
        val author = fieldValue("小说作者：").ifEmpty { fieldValue("小说作者:") }
        val publishingHouse = fieldValue("文库分类：").ifEmpty { fieldValue("文库分类:") }
        val isComplete = fieldValue("文章状态：").contains("已完结")
        val wordCount = fieldValue("全文长度：").toIntOrNull()?.let { WordCount(it) }
        val lastUpdated = fieldValue("最后更新：").trim().ifEmpty { null }
        val description = extractDescription(soup)

        return Result.success(
            BookInformation(
                id = id,
                title = title,
                subtitle = subtitle,
                coverUrl = coverUrl,
                author = author,
                description = description,
                tags = soup.selectFirst("#content span:contains(作品Tags)")?.text()
                    ?.replace("作品Tags：", "")?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                publishingHouse = publishingHouse,
                wordCount = wordCount,
                lastUpdated = lastUpdated,
                isComplete = isComplete
            )
        )
    }

    /** 提取简介：定位「内容简介：」标签，取其后的简介正文 span（DOM 结构定位，避免吞入按钮/最近章节） */
    private fun extractDescription(soup: Document): String {
        val label = soup.selectFirst("#content span:contains(内容简介)") ?: return ""
        var node = label.nextElementSibling()
        while (node != null) {
            if (node.tagName() == "span") {
                // 简介正文 span：不含链接（<a>），且非「最近章节」那种带链接的
                if (node.selectFirst("a") == null) {
                    val t = node.text().trim()
                    if (t.isNotEmpty()) return t
                }
            }
            node = node.nextElementSibling()
        }
        return ""
    }

    // MARK: - 目录（卷 + 章）

    suspend fun getBookVolumes(id: String): Result<BookVolumes> {
        val html: String = client.getHtml(url("novel/${aidOf(id) / 1000}/$id/index.htm")).getOrElse {
            return Result.failure(WebRequestError("网络请求失败", it.message ?: "网络错误"))
        }
        val soup = Ksoup.parse(html)
        val trs = KsoupXpath.selectXpath(soup, "/html/body/table/tbody/tr")
        val volumes = mutableListOf<Volume>()
        var volume: Volume? = null
        var chapters = mutableListOf<ChapterInformation>()
        for (tr in trs) {
            val td = tr.selectFirst("td")
            if (td?.attr("class") == "vcss") {
                volume?.let { volumes.add(it) }
                val vId = td.attr("vid")
                val vTitle = td.text()
                chapters = mutableListOf()
                volume = Volume(vId, vTitle, chapters)
                continue
            }
            for (a in tr.select("td > a")) {
                val href = a.attr("href")
                val cId = href.split(".").firstOrNull() ?: continue
                val cTitle = a.text().ifEmpty { continue }
                chapters.add(ChapterInformation(cId, cTitle))
            }
        }
        volume?.let { volumes.add(it) }
        return Result.success(BookVolumes(id, volumes))
    }

    // MARK: - 正文（组件化 JSON）

    suspend fun getChapterContent(chapterId: String, bookId: String): Result<ChapterContent> {
        val html: String = client.getHtml(url("novel/${aidOf(bookId) / 1000}/$bookId/$chapterId.htm")).getOrElse {
            return Result.failure(WebRequestError("网络请求失败", it.message ?: "网络错误"))
        }
        val soup = Ksoup.parse(html)
        val title = KsoupXpath.selectFirstXpath(soup, "//*[@id=\"title\"]")?.text()
            ?: return Result.failure(WebRequestError("解析错误", "无法解析该章节的标题(id=$chapterId)"))
        if (soup.text().contains("因版权问题")) {
            return Result.failure(WebRequestError("版权错误", "由于「$title」为Wenku8上具有版权文件的书籍的章节, 我们无法提供其数据"))
        }
        val contentEl = KsoupXpath.selectFirstXpath(soup, "//*[@id=\"content\"]")
            ?: return Result.failure(WebRequestError("解析错误", "无法解析该章节的内容(id=$chapterId)"))

        val builder = ContentBuilder()
        var text = ""
        for (node in contentEl.childNodes()) {
            when (node) {
                is com.fleeksoft.ksoup.nodes.TextNode -> text += node.text().replace("\u00A0", "  ")
                is Element -> {
                    val tagName = node.tagName().lowercase()
                    val classList = node.classNames()
                    val isDivImage = tagName == "div" && (classList.contains("divimage") || classList.contains("image"))
                    if (isDivImage) {
                        builder.simpleText(text)
                        text = ""
                        node.selectFirst("img")?.attr("src")?.let { builder.image(resolveImageUrl(it)) }
                    } else if (tagName == "br") {
                        // wenku8 正文用 <br> 分段：遇到 <br> 切分一段（无论当前文本是否空白，避免整章累积成一段）
                        builder.simpleText(text)
                        text = ""
                    }
                }
            }
        }
        if (text.isNotEmpty()) builder.simpleText(text)

        val prevChapter = parseFootLink(soup, 3)
        val nextChapter = parseFootLink(soup, 4)

        return Result.success(
            ChapterContent(
                id = chapterId,
                title = title,
                content = builder.build(),
                prevChapter = prevChapter,
                nextChapter = nextChapter
            )
        )
    }

    private fun resolveImageUrl(src: String): String {
        if (src.startsWith("http")) return src
        return host() + "/" + src.trimStart('/')
    }

    /** 解析页脚上一章/下一章链接（#foottext 下第 n 个 a 标签） */
    private fun parseFootLink(soup: Document, n: Int): String? {
        val a = KsoupXpath.selectFirstXpath(soup, "//*[@id=\"foottext\"]/a[$n]") ?: return null
        val href = a.attr("href")
        if (href == "index.htm" || href.contains("article")) return null
        return href.split(".").firstOrNull()
    }

    // MARK: - 搜索

    sealed class SearchResult {
        data class SingleBook(val bookId: String) : SearchResult()
        data class MultipleBook(val bookId: String) : SearchResult()
        data class Error(val message: String) : SearchResult()
        object End : SearchResult()
    }

    fun search(searchType: String, keyword: String): Flow<SearchResult> = flow {
        val encodedKeyword = Gbk.percentEncode(keyword)
        var targetPage = 1
        var presentPage = 1
        while (presentPage <= targetPage) {
            val html = client.getHtml(
                url("modules/article/search.php?searchtype=$searchType&searchkey=$encodedKeyword&page=$presentPage")
            ).getOrNull()
            if (html == null) {
                emit(SearchResult.Error("Failed to request the web page"))
                return@flow
            }
            if (html.contains("两次搜索的间隔时间不得少于 5 秒")) {
                delay(5_000)
                continue
            }
            val soup = Ksoup.parse(html)
            val menu = KsoupXpath.selectFirstXpath(soup, "//*[@id=\"content\"]/div[1]/div[4]/div/span[1]/fieldset/div/a")
            if (menu != null && menu.text().contains("小说目录")) {
                val id = menu.attr("href").split("/").getOrNull(3)
                if (id == null) {
                    emit(SearchResult.Error("Failed to parse single book id"))
                    return@flow
                }
                emit(SearchResult.SingleBook(id))
                return@flow
            }
            if (targetPage == 1) {
                val page = KsoupXpath.selectFirstXpath(soup, "//*[@id=\"pagelink\"]/em")?.text()?.split("/")
                    ?.getOrNull(1)?.toIntOrNull()
                if (page == null) {
                    emit(SearchResult.Error("Failed to parse total pages"))
                    return@flow
                }
                targetPage = page
            }
            val cards = KsoupXpath.selectXpath(soup, "//*[@id=\"content\"]/table/tbody/tr/td/div")
            val books = parseBookCards(cards)
            for (book in books) {
                emit(SearchResult.MultipleBook(book.id))
            }
            presentPage++
            if (presentPage <= targetPage) delay(5_000)
        }
        emit(SearchResult.End)
    }

    /** 解析书卡（对齐上游 Wenku8Api.getBookInformationListFromBookCards，搜索/探索共用） */
    private fun parseBookCards(cards: List<Element>): List<BookInformation> {
        return cards.mapNotNull { card ->
            if (card.text().contains("因版权问题")) return@mapNotNull null
            val a = card.selectFirst("div > div:nth-child(1) > a") ?: return@mapNotNull null
            val href = a.attr("href")
            val id = href.replace("/book/", "").replace(".htm", "")
            if (id.isEmpty() || id == href) return@mapNotNull null
            val titleText = a.attr("title")
            val titleGroup = titleRegex.find(titleText)?.groups
            val p2 = card.selectFirst("div > div:nth-child(2) > p:nth-child(2)")?.text()
            val p3 = card.selectFirst("div > div:nth-child(2) > p:nth-child(3)")?.text()
            BookInformation(
                id = id,
                title = titleGroup?.get(1)?.value ?: titleText,
                subtitle = titleGroup?.get(2)?.value ?: "",
                coverUrl = card.selectFirst("div > div:nth-child(1) > a > img")?.attr("src")?.takeIf { it.isNotEmpty() },
                author = p2?.split("/")?.getOrNull(0)?.split(":")?.getOrNull(1) ?: "",
                description = card.selectFirst("div > div:nth-child(2) > p:nth-child(5)")?.text()?.replace("简介:", "") ?: "",
                tags = card.selectFirst("div > div:nth-child(2) > p:nth-child(4) > span")?.text()?.split(" ") ?: emptyList(),
                publishingHouse = p2?.split("/")?.getOrNull(1)?.split(":")?.getOrNull(1) ?: "",
                wordCount = p3?.split("/")?.getOrNull(1)?.split(":")?.getOrNull(1)
                    ?.replace("K", "")?.toIntOrNull()?.let { WordCount(it * 1000) },
                lastUpdated = p3?.split("/")?.getOrNull(0)?.split(":")?.getOrNull(1),
                isComplete = p3?.split("/")?.getOrNull(2) == "已完结"
            )
        }
    }

    // MARK: - 探索（对齐上游 Wenku8ExplorePageProvider）

    /** 探索「全部」板块的 6 个栏目（对齐上游 registerExpandedPageDataSource） */
    fun exploreCategories(): List<ExploreCategoryInfo> = listOf(
        ExploreCategoryInfo("all", "轻小说列表", "modules/article/articlelist.php", ""),
        ExploreCategoryInfo("allvisit", "热门轻小说", "modules/article/toplist.php", "&sort=allvisit"),
        ExploreCategoryInfo("anime", "动画化作品", "modules/article/toplist.php", "&sort=anime"),
        ExploreCategoryInfo("lastupdate", "今日更新", "modules/article/toplist.php", "&sort=lastupdate"),
        ExploreCategoryInfo("postdate", "新书一览", "modules/article/toplist.php", "&sort=postdate"),
        ExploreCategoryInfo("completed", "完结全本", "modules/article/articlelist.php", "&fullflag=1")
    )

    /** 全站标签列表（对齐上游硬编码 48 标签） */
    fun tagList(): List<String> = wenku8TagList

    /** 标签 → 书库浏览栏目（对齐上游 tags.php?t= 展开页） */
    fun tagCategory(tag: String): ExploreCategoryInfo = ExploreCategoryInfo(
        id = "tag-$tag",
        title = tag,
        path = "modules/article/tags.php",
        extraParams = "&t=${Gbk.percentEncode(tag)}",
        supportsSort = true
    )

    /** 探索首页推荐块（对齐上游 Wenku8HomeExploreTapPage：3 个 blocktitle 块） */
    suspend fun getHomeBlocks(): Result<List<HomeBlockInfo>> {
        val html: String = client.getHtml(url("index.php")).getOrElse {
            return Result.failure(WebRequestError("网络请求失败", it.message ?: "网络错误"))
        }
        val soup = Ksoup.parse(html)
        val blocks = mutableListOf<HomeBlockInfo>()
        for (index in 0..2) {
            val n = index + 2
            val title = soup.selectFirst("#centers > div:nth-child($n) > div.blocktitle")?.text()
                ?.split("(")?.getOrNull(0)?.trim().orEmpty()
            val idList = soup.select("#centers > div:nth-child($n) > div.blockcontent > div > div > a:nth-child(1)")
                .map { it.attr("href").replace("/book/", "").replace(".htm", "") }
            val titleList = soup.select("#centers > div:nth-child($n) > div.blockcontent > div > div > a:nth-child(3)")
                .map { it.text().split("(").getOrNull(0)?.trim().orEmpty() }
            val coverList = soup.select("#centers > div:nth-child($n) > div.blockcontent > div > div > a:nth-child(1) > img")
                .map { it.attr("src") }
            val books = idList.indices.map { i ->
                BookInformation(
                    id = idList[i],
                    title = titleList.getOrNull(i).orEmpty(),
                    coverUrl = coverList.getOrNull(i)?.takeIf { it.isNotEmpty() }
                )
            }
            if (title.isNotEmpty() && books.isNotEmpty()) {
                blocks.add(HomeBlockInfo(title, books))
            }
        }
        return Result.success(blocks)
    }

    /** 探索栏目/标签展开页分页（对齐上游 HomeBookExpandPageDataSource） */
    suspend fun getExplorePage(category: ExploreCategoryInfo, page: Int): Result<ExplorePageInfo> {
        val html: String = client.getHtml(url("${category.path}?page=$page${category.extraParams}")).getOrElse {
            return Result.failure(WebRequestError("网络请求失败", it.message ?: "网络错误"))
        }
        val soup = Ksoup.parse(html)
        val selector = if (category.path.contains("tags.php")) {
            "#content > table > tbody > tr:nth-child(2) > td > div"
        } else {
            "#content > table.grid > tbody > tr > td > div"
        }
        val books = parseBookCards(soup.select(selector))
        val totalPages = soup.selectFirst("#pagelink > em")?.text()?.split("/")?.getOrNull(1)?.toIntOrNull() ?: 1
        return Result.success(ExplorePageInfo(books, totalPages))
    }

    companion object {
        /** 全站标签（对齐上游 Wenku8Api.tagList） */
        val wenku8TagList = listOf(
            "校园", "青春", "恋爱", "治愈", "群像",
            "竞技", "音乐", "美食", "旅行", "欢乐向",
            "经营", "职场", "斗智", "脑洞", "宅文化",
            "穿越", "奇幻", "魔法", "异能", "战斗",
            "科幻", "机战", "战争", "冒险", "龙傲天",
            "悬疑", "犯罪", "复仇", "黑暗", "猎奇",
            "惊悚", "间谍", "末日", "游戏", "大逃杀",
            "青梅竹马", "妹妹", "女儿", "JK", "JC",
            "大小姐", "性转", "伪娘", "人外",
            "后宫", "百合", "耽美", "NTR", "女性视角"
        )
    }
}

/** 探索栏目（对齐上游 expanded page：id/title/baseUrl/extendedParameters） */
data class ExploreCategoryInfo(
    val id: String,
    val title: String,
    val path: String,
    val extraParams: String,
    val supportsSort: Boolean = false
)

/** 探索首页推荐块（对齐上游 ExploreBooksRow） */
data class HomeBlockInfo(
    val title: String,
    val books: List<BookInformation>
)

/** 探索展开页一页结果 */
data class ExplorePageInfo(
    val books: List<BookInformation>,
    val totalPages: Int
)
