package indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.book

import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookVolumes
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.MutableBookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.MutableChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.book.Volume
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.Wenku8Api
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.autoReconnectionGetWithWenku8Cookie
import indi.dmzz_yyhyy.lightnovelreader.utils.CanBeEmpty
import indi.dmzz_yyhyy.lightnovelreader.utils.CxHttpInit
import indi.dmzz_yyhyy.lightnovelreader.utils.cache.Cache
import indi.dmzz_yyhyy.lightnovelreader.utils.selectFirstXpath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

class Wenku8WebsiteDataSource: Wenku8BookDataSource {
    private val host get() = Wenku8Api.host
    private val titleRegex = Regex("(.*) ?[(（](.*)[)）] ?$")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val cache = Cache(
        timeout = 2 * 60 * 60 * 1000
    )

    private inline fun <reified T: CanBeEmpty> ifCache(id: String, block: () -> T): T {
        val cacheData = cache.getCache<T>(id.hashCode())
        if (cacheData == null) {
            val data = block.invoke()
            if (data.isEmpty()) return data
            cache.cache(id.hashCode(), data)
            return data
        }
        return cacheData
    }

    private fun url(string: String) = "$host/$string"

    override suspend fun getBookInformation(id: Int): BookInformation = ifCache(id.toString()) {
        val soup = autoReconnectionGetWithWenku8Cookie(url("book/$id.htm")) ?: return@ifCache BookInformation.empty(id)
        if (soup.text().contains("因版权问题")) return@ifCache BookInformation.empty()
        val titleGroup = soup
            .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[1]/td/table/tbody/tr/td[1]/span/b")
            ?.text()
            ?.let { titleRegex.find(it)?.groups }
        return@ifCache MutableBookInformation(
            id = id,
            title = titleGroup
                ?.get(1)?.value
                ?: soup
                    .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[1]/td/table/tbody/tr/td[1]/span/b")
                    ?.text()
                ?: return@ifCache BookInformation.empty(id),
            subtitle = titleGroup
                ?.get(2)
                ?.value
                ?: "",
            coverUrl = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[2]/tbody/tr/td[1]/img")
                ?.attr("src")
                ?: return@ifCache BookInformation.empty(),
            author = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[2]/td[2]")
                ?.text()
                ?.replace("小说作者：", "")
                ?: return@ifCache BookInformation.empty(),
            description = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[2]/tbody/tr/td[2]/span[6]")
                ?.text()
                ?: return@ifCache BookInformation.empty(),
            tags = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[2]/tbody/tr/td[2]/span[1]/b")
                ?.text()
                ?.replace("作品Tags：", "")
                ?.split(" ")
                ?: return@ifCache BookInformation.empty(),
            publishingHouse = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[2]/td[1]")
                ?.text()
                ?.replace("文库分类：", "")
                ?: return@ifCache BookInformation.empty(),
            wordCount = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[2]/td[5]")
                ?.text()
                ?.replace("全文长度：", "")
                ?.replace("字", "")
                ?.toIntOrNull()
                ?: return@ifCache BookInformation.empty(),
            lastUpdated = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[2]/td[4]")
                ?.text()
                ?.replace("最后更新：", "")
                ?.let { LocalDate.parse(it, dateTimeFormatter) }
                ?.atStartOfDay()
                ?: return@ifCache BookInformation.empty(),
            isComplete = soup
                .selectFirstXpath("//*[@id=\"content\"]/div[1]/table[1]/tbody/tr[2]/td[3]")
                ?.text()
                ?.contains("已完结")
                ?: return@ifCache BookInformation.empty()
        )
    }

    override suspend fun getBookVolumes(id: Int): BookVolumes = ifCache(id.toString()) {
        val soup = autoReconnectionGetWithWenku8Cookie(url("novel/${id / 1000}/$id/index.htm")) ?: return@ifCache BookVolumes.empty(id)
        val trs = soup.selectXpath("/html/body/table/tbody/tr")
        val volumes = mutableListOf<Volume>()
        var volume: Volume? = null
        var chapters = mutableListOf<ChapterInformation>()
        trs.forEach { tr ->
            if (tr.selectFirst("td")?.attr("class") == "vcss") {
                volume?.let(volumes::add)
                val td = tr.selectFirst("td")
                val vId = td?.attr("vid")?.toIntOrNull() ?: return@ifCache BookVolumes.empty()
                val title = td.text().ifEmpty { return@ifCache BookVolumes.empty() }
                chapters = mutableListOf()
                volume = Volume(vId, title, chapters)
                return@forEach
            }
            for (td in tr.select("td > a")) {
                val id = td
                    .attr("href")
                    .split(".")
                    .firstOrNull()
                    ?.toIntOrNull()
                    ?: return@ifCache BookVolumes.empty()
                val title = td.text().ifEmpty { return@ifCache BookVolumes.empty() }
                chapters.add(ChapterInformation(id, title))
            }
        }
        volume?.let(volumes::add)
        return@ifCache BookVolumes(id, volumes)
    }

    override suspend fun getChapterContent(
        chapterId: Int,
        bookId: Int
    ): ChapterContent = ifCache("$chapterId $bookId") {
        val soup = autoReconnectionGetWithWenku8Cookie(
            url("novel/${bookId / 1000}/$bookId/$chapterId.htm")
        ) ?: return@ifCache ChapterContent.empty(chapterId)
        if (soup.text().contains("因版权问题")) return@ifCache ChapterContent.empty(chapterId)

        val contentElement = soup.selectFirstXpath("//*[@id=\"content\"]")
            ?: return@ifCache ChapterContent.empty(chapterId)
        println("OK contentElement is \n $contentElement")

        var content = ""

        contentElement.childNodes().forEach { node ->
            if (node is org.jsoup.nodes.TextNode) {
                val t = node.text()
                if (t.isNotBlank()) {
                    content += t.replace("\u00A0", " ")
                }
                return@forEach
            }

            if (node is org.jsoup.nodes.Element) {
                if (node.id() == "contentdp") return@forEach

                if (node.tagName() == "br") {
                    content += "\n"
                    return@forEach
                }

                if (node.tagName() == "div" && node.hasClass("divimage")) {
                    node.selectFirst("img")?.attr("src")?.let { src ->
                        content += "[image]$src[image]"
                    }
                    return@forEach
                }

                val t = node.text()
                if (t.isNotBlank()) {
                    content += t
                }
            }
        }

        MutableChapterContent(
            id = chapterId,
            title = soup.selectFirstXpath("//*[@id=\"title\"]")?.text()
                ?: return@ifCache ChapterContent.empty(chapterId),
            content = content,
            lastChapter = soup.selectFirstXpath("//*[@id=\"foottext\"]/a[3]").let {
                it ?: return@let -1
                if (it.attr("href") == "index.htm") -1
                else it.attr("href").split(".").firstOrNull()?.toIntOrNull() ?: -1
            },
            nextChapter = soup.selectFirstXpath("//*[@id=\"foottext\"]/a[4]").let {
                it ?: return@let -1
                if (it.attr("href") == "index.htm") -1
                else it.attr("href").split(".").firstOrNull()?.toIntOrNull() ?: -1
            }
        )
    }


    override fun search(searchType: String, keyword: String): Flow<BookInformation> = flow {
        val encodedKeyword = URLEncoder.encode(keyword, "gb2312")

        var targetPage = 1
        var presentPage = 1
        while(presentPage <= targetPage) {
            val soup = autoReconnectionGetWithWenku8Cookie(url("modules/article/search.php?searchtype=$searchType&searchkey=$encodedKeyword&page=$presentPage"))
            if (soup == null) {
                emit(BookInformation.empty())
                return@flow
            }
            if (soup.text().contains("错误原因：对不起，两次搜索的间隔时间不得少于 5 秒")) {
                delay(5.seconds)
                continue
            }
            if (targetPage == 1) {
                val page = soup.selectFirstXpath("//*[@id=\"pagelink\"]/em")?.text()?.split("/")?.getOrNull(1)?.toIntOrNull()
                if (page == null) {
                    emit(BookInformation.empty())
                    return@flow
                }
                targetPage = page
            }

            val books = Wenku8Api.getBookInformationListFromBookCards(soup.selectXpath("//*[@id=\"content\"]/table/tbody/tr/td/div"))
            for (information in books) {
                emit(information)
            }

            presentPage++
            delay(5.seconds)
        }
    }

    init {
        CxHttpInit.init()
    }
}