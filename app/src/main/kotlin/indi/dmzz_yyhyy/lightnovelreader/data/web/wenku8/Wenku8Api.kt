package indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8


import android.util.Log
import androidx.navigation.NavController
import cxhttp.CxHttp
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookVolumes
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.MutableBookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.MutableChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.book.Volume
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSource
import indi.dmzz_yyhyy.lightnovelreader.data.web.exploration.ExplorationExpandedPageDataSource
import indi.dmzz_yyhyy.lightnovelreader.data.web.exploration.ExplorationPageDataSource
import indi.dmzz_yyhyy.lightnovelreader.data.web.exploration.filter.IsCompletedSwitchFilter
import indi.dmzz_yyhyy.lightnovelreader.data.web.exploration.filter.SingleChoiceFilter
import indi.dmzz_yyhyy.lightnovelreader.data.web.exploration.filter.WordCountFilter
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.book.BookRequestDispatcher
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.exploration.Wenku8AllExplorationPage
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.exploration.Wenku8HomeExplorationPage
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.exploration.Wenku8TagsExplorationPage
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.exploration.expanedpage.HomeBookExpandPageDataSource
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.exploration.expanedpage.filter.FirstLetterSingleChoiceFilter
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.exploration.expanedpage.filter.PublishingHouseSingleChoiceFilter
import indi.dmzz_yyhyy.lightnovelreader.ui.home.exploration.expanded.navigateToExplorationExpandDestination
import indi.dmzz_yyhyy.lightnovelreader.utils.CanBeEmpty
import indi.dmzz_yyhyy.lightnovelreader.utils.UserAgentGenerator
import indi.dmzz_yyhyy.lightnovelreader.utils.cache.Cache
import indi.dmzz_yyhyy.lightnovelreader.utils.update
import io.nightfish.potatoautoproxy.ProxyPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Wenku8Api: WebBookDataSource {
    private val tagList = listOf(
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
    private val bookRequestDispatcher = BookRequestDispatcher()
    private val isOffLineStateFlow = MutableStateFlow(false)

    private var allBookChapterListCacheId: Int = -1
    var allBookChapterListCache: List<ChapterInformation> = emptyList()
    private val DATA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    override val explorationExpandedPageDataSourceMap = mutableMapOf<String, ExplorationExpandedPageDataSource>()
    private var coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val titleRegex = Regex("(.*) ?[(（](.*)[)）] ?$")
    private val hosts = listOf("https://www.wenku8.cc", "https://www.wenku8.net", "https://www.wenku8.com")
    private var isLocalIpUnableUse = true
    private val cache = Cache(
        timeout = 5 * 60 * 1000
    )
    private val _cache = Cache(
        timeout = 2 * 60 * 60 * 1000
    )
    var host  =  hosts[0]

    init {
        coroutineScope.launch {
            while (currentCoroutineContext().isActive) {
                offLine = isOffLine()
                isOffLineStateFlow.emit(offLine)
                delay(if (offLine) 3000 else 100000)
            }
        }
    }

    private inline fun <reified T: CanBeEmpty> ifCache(id: String, block: () -> T): T {
        val cacheData = _cache.getCache<T>(id.hashCode())
        if (cacheData == null) {
            val data = block.invoke()
            if (data.isEmpty()) return data
            _cache.cache(id.hashCode(), data)
            return data
        }
        return cacheData
    }

    override var offLine: Boolean = true

    override val isOffLineFlow = isOffLineStateFlow

    override suspend fun isOffLine(): Boolean = withContext(Dispatchers.IO) {
        suspend fun webSite(index: Int): Boolean {
            return !CxHttp
                .get(hosts[index]) {
                    header("user-agent", UserAgentGenerator.generate())
                    header("cookie",wenku8Cookies().map { "${it.key}=${it.value}" }.joinToString(separator = ";"))
                }
                .await()
                .isSuccessful
                .also { host = hosts[index] }
        }
        return@withContext webSite(0) && webSite(1) && webSite(2)
    }

    override val id: Int = "wenku8".hashCode()

    override suspend fun getBookInformation(id: Int): BookInformation = ifCache(id.toString()) {
        bookRequestDispatcher.getBookInformation(id)
    }

    override suspend fun getBookVolumes(id: Int): BookVolumes = ifCache(id.toString()) {
        bookRequestDispatcher.getBookVolumes(id)
    }

    override suspend fun getChapterContent(chapterId: Int, bookId: Int): ChapterContent = ifCache("$chapterId $bookId")  {
        bookRequestDispatcher.getChapterContent(chapterId, bookId)
    }

    override val explorationPageDataSourceMap: Map<String, ExplorationPageDataSource> =
        mapOf(
            Pair("首页", Wenku8HomeExplorationPage),
            Pair("全部", Wenku8AllExplorationPage),
            Pair("分类", Wenku8TagsExplorationPage)
        )

    override val explorationPageIdList: List<String> = listOf("首页", "全部", "分类")

    override fun search(searchType: String, keyword: String): Flow<BookInformation> {
        return bookRequestDispatcher.search(searchType, keyword)
    }

    override fun stopAllSearch() {
        coroutineScope.cancel()
        coroutineScope = CoroutineScope(Dispatchers.IO)
    }

    override val searchTypeIdList =
        listOf("articlename", "author")

    override val searchTypeMap: Map<String, String> =
        mapOf(
            Pair("articlename", "按书名搜索"),
            Pair("author", "按作者名搜索"),
        )

    override val searchTipMap: Map<String, String> =
        mapOf(
            Pair("articlename", "请输入书本名称"),
            Pair("author", "请输入作者名称"),
        )

    suspend fun getBookInformationListFromBookCards(elements: Elements): List<BookInformation> =
        elements
            .map { element ->
                if (element.text().contains("因版权问题"))
                    getBookInformation(element
                        .selectFirst("div > div:nth-child(1) > a")
                        ?.attr("href")
                        ?.replace("/book/", "")
                        ?.replace(".htm", "")
                        ?.toInt() ?: -1
                    )
                else {
                    val titleGroup = element.selectFirst("div > div:nth-child(1) > a")
                        ?.attr("title")
                        ?.let { it1 -> titleRegex.find(it1)?.groups }
                    MutableBookInformation(
                        id = element.selectFirst("div > div:nth-child(1) > a")
                            ?.attr("href")
                            ?.replace("/book/", "")
                            ?.replace(".htm", "")
                            ?.toInt() ?: -1,
                        title = titleGroup?.get(1)?.value ?: element.selectFirst("div > div:nth-child(1) > a")
                            ?.attr("title") ?: "",
                        subtitle = titleGroup?.get(2)?.value ?: "",
                        coverUrl = element.selectFirst("div > div:nth-child(1) > a > img")
                            ?.attr("src") ?: "",
                        author = element.selectFirst("div > div:nth-child(2) > p:nth-child(2)")
                            ?.text()?.split("/")?.getOrNull(0)
                            ?.split(":")?.getOrNull(1) ?: "",
                        description = element.selectFirst("div > div:nth-child(2) > p:nth-child(5)")
                            ?.text()?.replace("简介:", "") ?: "",
                        tags = element.selectFirst("div > div:nth-child(2) > p:nth-child(4) > span")
                            ?.text()?.split(" ") ?: emptyList(),
                        publishingHouse = element.selectFirst("div > div:nth-child(2) > p:nth-child(2)")
                            ?.text()?.split("/")?.getOrNull(1)
                            ?.split(":")?.getOrNull(1) ?: "",
                        wordCount = element.selectFirst("div > div:nth-child(2) > p:nth-child(3)")
                            ?.text()?.split("/")?.getOrNull(1)
                            ?.split(":")?.getOrNull(1)
                            ?.replace("K", "")?.toInt()?.times(1000) ?: -1,
                        lastUpdated = element.selectFirst("div > div:nth-child(2) > p:nth-child(3)")
                            ?.text()?.split("/")?.getOrNull(0)
                            ?.split(":")?.getOrNull(1)
                            ?.let {
                                LocalDate.parse(it, DATA_TIME_FORMATTER)
                            }
                            ?.atStartOfDay() ?: LocalDateTime.MIN,
                        isComplete = element.selectFirst("div > div:nth-child(2) > p:nth-child(3)")
                            ?.text()?.split("/")?.getOrNull(2) == "已完结"
                    )
                }
            }

    private fun registerExplorationExpandedPageDataSource(id: String, expandedPageDataSource: ExplorationExpandedPageDataSource) =
            explorationExpandedPageDataSourceMap.put(id, expandedPageDataSource)

    init {
        registerExplorationExpandedPageDataSource(
            id = "allBook",
            expandedPageDataSource = HomeBookExpandPageDataSource(
                title = "轻小说列表",
                filtersBuilder = {
                    listOf(
                        IsCompletedSwitchFilter { this.refresh() },
                        FirstLetterSingleChoiceFilter { choice ->
                            val arg = when (choice) {
                                "任意" -> ""
                                "0~9" -> "&initial=1"
                                else -> "&initial=${choice}"
                            }
                            this.arg = arg
                            this.refresh()
                        },
                        PublishingHouseSingleChoiceFilter { this.refresh() },
                        WordCountFilter { this.refresh() }
                    )
                }
            )
        )
        registerExplorationExpandedPageDataSource(
            id = "allCompletedBook",
            expandedPageDataSource = HomeBookExpandPageDataSource(
                title = "完结全本",
                filtersBuilder = {
                    listOf(
                        IsCompletedSwitchFilter { this.refresh() },
                        FirstLetterSingleChoiceFilter { choice ->
                            val arg = when (choice) {
                                "任意" -> ""
                                "0~9" -> "&initial=1"
                                else -> "&initial=${choice}"
                            }
                            this.arg = arg
                            this.refresh()
                        },
                        PublishingHouseSingleChoiceFilter { this.refresh() },
                        WordCountFilter { this.refresh() }
                    )
                },
                extendedParameters = "&fullflag=1"
            )
        )
        listOf("allvisit", "anime", "lastupdate", "postdate").forEach { id ->
            val nameMap = mapOf(
                Pair("allvisit", "热门轻小说"),
                Pair("anime", "动画化作品"),
                Pair("lastupdate", "今日更新"),
                Pair("postdate", "新书一览"),
            )
            registerExplorationExpandedPageDataSource(
                id = "${id}Book",
                expandedPageDataSource = HomeBookExpandPageDataSource(
                    baseUrl = "$host/modules/article/toplist.php",
                    title = nameMap[id] ?: "",
                    filtersBuilder = {
                        listOf(
                            IsCompletedSwitchFilter { this.refresh() },
                            FirstLetterSingleChoiceFilter { choice ->
                                val arg = when (choice) {
                                    "任意" -> ""
                                    "0~9" -> "&initial=1"
                                    else -> "&initial=${choice}"
                                }
                                this.arg = arg
                                this.refresh()
                            },
                            PublishingHouseSingleChoiceFilter { this.refresh() },
                            WordCountFilter { this.refresh() }
                        )
                    },
                    extendedParameters = "&sort=$id",
                    contentSelector = "#content > table > tbody > tr > td > div"
                )
            )
        }
        tagList.forEach { tag ->
            registerExplorationExpandedPageDataSource(
                id = tag,
                expandedPageDataSource = HomeBookExpandPageDataSource(
                    baseUrl = "$host/modules/article/tags.php",
                    title = tag,
                    filtersBuilder = {
                        val choicesMap = mapOf(
                            Pair("默认", ""),
                            Pair("按更新时间排序", ""),
                            Pair("按热度排序", "&v=1"),
                            Pair("仅动画化", "&v=3")
                        )
                        listOf(
                            IsCompletedSwitchFilter { this.refresh() },
                            SingleChoiceFilter(
                                title = "排序",
                                dialogTitleId = R.string.key_pub_filter_title,
                                descriptionId = R.string.key_pub_filter_desc,
                                choices = listOf("默认", "按更新时间排序", "按热度排序", "仅动画化"),
                                defaultChoice = "默认"
                            ) {
                                this.arg = choicesMap[it.trim()] ?: ""
                                this.refresh()
                            },
                            PublishingHouseSingleChoiceFilter { this.refresh() },
                            WordCountFilter { this.refresh() }
                        )
                    },
                    extendedParameters = "&t=${URLEncoder.encode(tag, "gb2312")}",
                    contentSelector = "#content > table > tbody > tr:nth-child(2) > td > div"
                )
            )
        }
    }

    override fun progressBookTagClick(tag: String, navController: NavController) {
        if (tagList.contains(tag))
            navController.navigateToExplorationExpandDestination(tag)
    }

    override fun getCoverUrlInVolume(bookId: Int, volume: Volume, volumeChapterContentMap: Map<Int, ChapterContent>): String? {
        return volume.chapters
            .find { it.title.endsWith("插图") }
            ?.let { chapterInformation ->
                val chapterContent = volumeChapterContentMap[chapterInformation.id] ?: return null
                if (chapterContent.isEmpty()) return null
                chapterContent.content.split("[image]")
                    .filter(String::isNotEmpty)
                    .forEach { singleText ->
                        if (singleText.startsWith("http://") || singleText.startsWith("https://")) {
                            return singleText
                        }
                    }
                return null
            }
    }
}