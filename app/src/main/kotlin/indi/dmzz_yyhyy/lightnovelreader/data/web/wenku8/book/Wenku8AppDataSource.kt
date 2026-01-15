package indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.book

import android.util.Log
import androidx.core.net.toUri
import cxhttp.CxHttp
import cxhttp.response.Response
import cxhttp.response.bodyOrNull
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookVolumes
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.MutableBookInformation
import indi.dmzz_yyhyy.lightnovelreader.data.book.MutableChapterContent
import indi.dmzz_yyhyy.lightnovelreader.data.book.Volume
import indi.dmzz_yyhyy.lightnovelreader.data.web.wenku8.Wenku8Api
import indi.dmzz_yyhyy.lightnovelreader.utils.CanBeEmpty
import indi.dmzz_yyhyy.lightnovelreader.utils.CxHttpInit
import indi.dmzz_yyhyy.lightnovelreader.utils.cache.Cache
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.encoding.Base64
import kotlin.random.Random

private val titleRegex = Regex("(.*) ?[(（](.*)[)）] ?$")

class Wenku8AppDataSource(
    val host: String,
    val ua: () -> String
) : Wenku8BookDataSource {
    init {
        CxHttpInit.init()
    }

    private val cache = Cache(
        timeout = 2 * 60 * 60 * 1000
    )
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var allBookChapterListCache: List<ChapterInformation> = emptyList()
    private var allBookChapterListCacheId: Int = -1

    private val requestLimiter = Semaphore(4)
    private val pendingJobs = Channel<Unit>(capacity = 25, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private inline fun <reified T> ifCache(id: String, block: () -> T): T {
        val cacheData = cache.getCache<T>(id.hashCode())
        if (cacheData == null) {
            val data = block.invoke()
            if (data is CanBeEmpty && data.isEmpty()) return data
            cache.cache(id.hashCode(), data)
            return data
        }
        return cacheData
    }


    suspend fun wenku8Api(request: String): Document? =
        ifCache(request) {
            if (!pendingJobs.trySend(Unit).isSuccess) {
                Log.w("Wenku8API", "request dropped: $request")
            }
            return try {
                requestLimiter.withPermit {
                    withTimeoutOrNull(15_000L) {
                        delay(1)
                        suspend fun post(): Response {
                            return CxHttp
                                .post(host) {
                                    formBody {
                                        append("request", Base64.encode(request.toByteArray()))
                                        append("timetoken", Instant.now().toEpochMilli().toString())
                                        append("appver", "1.21")
                                    }
                                    header("user-agent", ua.invoke())
                                }
                                .scope(this)
                                .await()
                        }

                        var retryTime = 5
                        var retryDelay = 1500L
                        var response = post()
                        while (!response.isSuccessful && retryTime >= 1 && response.code % 100 < 4) {
                            response = post()
                            retryTime--
                            delay(retryDelay)
                            retryDelay *= 2
                        }
                        delay(Random.nextLong(300, 450))
                        val doc = response.bodyOrNull<String>()?.let(Jsoup::parse)
                            ?.outputSettings(
                                Document.OutputSettings()
                                    .prettyPrint(false)
                                    .syntax(Document.OutputSettings.Syntax.xml)
                            )
                        return@withTimeoutOrNull doc
                    }.also {
                        if (it == null) Log.w("Wenku8API", "request timeout: $request")
                    }
                }
            } finally {
                pendingJobs.tryReceive().getOrNull()
            }
        }

    override suspend fun getBookInformation(id: Int): BookInformation = ifCache(id.toString()) {
        return@ifCache wenku8Api("action=book&do=meta&aid=$id&t=0")?.let {
            val titleGroup = it
                .selectFirst("[name=Title]")?.text()
                ?.let { it1 -> titleRegex.find(it1)?.groups }
            try {
                MutableBookInformation(
                    id = id,
                    title = titleGroup?.get(1)?.value ?: it.selectFirst("[name=Title]")?.text()
                    ?: "",
                    subtitle = titleGroup?.get(2)?.value ?: "",
                    coverUrl = "https://img.wenku8.com/image/${id / 1000}/$id/${id}s.jpg",
                    author = it.selectFirst("[name=Author]")?.attr("value") ?: "",
                    description = wenku8Api("action=book&do=intro&aid=$id&t=0")?.text() ?: "",
                    tags = it.selectFirst("[name=Tags]")?.attr("value")?.split(" ") ?: emptyList(),
                    publishingHouse = it.selectFirst("[name=PressId]")?.attr("value") ?: "",
                    wordCount = it.selectFirst("[name=BookLength]")?.attr("value")?.toInt() ?: -1,
                    lastUpdated = LocalDate.parse(
                        it.selectFirst("[name=LastUpdate]")?.attr("value"), dateTimeFormatter
                    ).atStartOfDay(),
                    isComplete = it.selectFirst("[name=BookStatus]")?.attr("value") == "已完成"
                )
            } catch (e: NullPointerException) {
                e.printStackTrace()
                BookInformation.empty()
            }
        } ?: BookInformation.empty()
    }

    override suspend fun getBookVolumes(id: Int): BookVolumes = ifCache(id.toString()) {
        return@ifCache BookVolumes(
            id,
            wenku8Api("action=book&do=list&aid=$id&t=0")
                ?.select("volume")
                ?.map { element ->
                    Volume(
                        volumeId = element.attr("vid").toInt(),
                        volumeTitle = element.ownText(),
                        chapters = element.select("volume > chapter")
                            .map {
                                ChapterInformation(
                                    id = it.attr("cid").toInt(),
                                    title = it.text(),
                                )
                            }
                    )
                } ?: emptyList()
        )
    }

    override suspend fun getChapterContent(chapterId: Int, bookId: Int): ChapterContent =
        ifCache("$chapterId $bookId") {
            if (allBookChapterListCacheId != bookId) {
                allBookChapterListCacheId = bookId
                allBookChapterListCache = getBookVolumes(bookId).let { bookVolumes ->
                    var list = emptyList<ChapterInformation>()
                    bookVolumes.volumes.forEach {
                        list = list + it.chapters
                    }
                    return@let list
                }
            }
            return@ifCache wenku8Api("action=book&do=text&aid=$bookId&cid=$chapterId&t=0")
                .let { document ->
                    document
                        ?.wholeText()
                        ?.let { s ->
                            var title = ""
                            var content = ""
                            s.split("\n").forEachIndexed { index, line ->
                                if (content != "") return@forEachIndexed
                                if (title == "" && line.any { !it.isWhitespace() }) {
                                    title = line.trim()
                                    return@forEachIndexed
                                }
                                if (title != "" && line.any { !it.isWhitespace() }) {
                                    content = s.split("\n").drop(index).joinToString("\n")
                                    return@forEachIndexed
                                }
                            }
                            val imagesResult = Regex("(http.*?)(<!--image-->)")
                                .findAll(document.toString())
                                .toList()
                            imagesResult.forEach {
                                content = content.replace(it.groups[1]?.value ?: it.value, "[image]${it.groups[1]?.value ?: ""}[image]")
                            }
                            MutableChapterContent(
                                id = chapterId,
                                title = title,
                                content = content,
                                lastChapter = allBookChapterListCache
                                    .indexOfFirst { it.id == chapterId }
                                    .let {
                                        if (it == -1) it else allBookChapterListCache.getOrNull(it - 1)?.id ?: -1
                                    },
                                nextChapter = allBookChapterListCache
                                    .indexOfFirst { it.id == chapterId }
                                    .let {
                                        if (it == -1) it else allBookChapterListCache.getOrNull(it + 1)?.id ?: -1
                                    }
                            )
                        } ?: ChapterContent.empty()
                }
        }

    override fun search(searchType: String, keyword: String): Flow<BookInformation> = flow {
        val encodedKeyword = URLEncoder.encode(keyword, "gb2312")
        delay(1)
        wenku8Api(
            "action=search&searchtype=$searchType&searchkey=${
                URLEncoder.encode(
                    encodedKeyword,
                    "utf-8"
                )
            }"
        )
            ?.select("item")
            ?.forEach { element ->
                emit(Wenku8Api.getBookInformation(element.attr("aid").toInt()))
            }
            ?.let {
                emit(BookInformation.empty())
            }
    }
}