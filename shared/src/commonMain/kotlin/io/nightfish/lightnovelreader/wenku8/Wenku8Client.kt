package io.nightfish.lightnovelreader.wenku8

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import platform.Foundation.NSLog
import io.nightfish.lightnovelreader.api.util.Gbk
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * wenku8 网络客户端。Ktor + Darwin 引擎（iOS 原生网络栈）。
 *
 * 与上游差异：
 * - 上游用 JVM 的 `HttpClient(OkHttp)`，这里用 KMP 的 `HttpClient(Darwin)`；
 * - 上游页面用 GB18030 编码，这里取回原始字节后经 [Gbk] 解码（Kotlin/Native 无内置 GB18030）；
 * - Cookie 会话管理：登录成功后持久化 cookie 字符串，重启可复用。
 */
class Wenku8Client(
    engineFactory: HttpClientEngineFactory<*>? = null,
    private val hosts: List<String> = listOf(
        "https://www.wenku8.cc",
        "https://www.wenku8.net",
        "https://www.wenku8.com"
    )
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cookieMutex = Mutex()

    // 引擎：默认 Darwin；可在测试/桌面环境注入其他引擎
    private val client: HttpClient = if (engineFactory != null) {
        HttpClient(engineFactory) {
            configureClient()
        }
    } else {
        HttpClient {
            configureClient()
        }
    }

    private fun io.ktor.client.HttpClientConfig<*>.configureClient() {
        install(UserAgent) {
            // 与上游一致：伪装 iPhone Safari UA
            agent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        defaultRequest {
            savedCookie?.let { cookie ->
                header(HttpHeaders.Cookie, cookie)
            }
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
        }
    }

    // MARK: - Cookie 会话

    /** 登录后的 cookie 字符串（jieqi 系列），持久化到外部存储 */
    var savedCookie: String? = null

    /** 是否已登录：cookie 里带 jieqiUserInfo 即视为登录 */
    val isLoggedIn: Boolean
        get() = savedCookie?.contains("jieqiUserInfo") == true

    private suspend fun requestHeaders() {
        // cookie 已在 defaultRequest 里统一设置，无需每次请求前再改
        kotlinx.coroutines.yield()
    }

    /** 登录：POST 表单，成功后保存 jieqi cookie */
    suspend fun login(username: String, password: String): Result<String> {
        return runCatching {
            val response = client.post("${hosts.first()}/login.php?do=submit") {
                setBody(
                    listOf(
                        "username" to username,
                        "password" to password,
                        "action" to "login",
                        "usecookie" to "31536000",
                        "submit" to "jumpurl"
                    ).joinToString("&") { (k, v) -> "${k}=${urlEncodeForm(v)}" }
                )
                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            }
            val status = response.status.value
            val bytes = response.bodyAsBytes()
            val html = Gbk.decodeToString(bytes)
            val cookies = response.headers[HttpHeaders.SetCookie].orEmpty()
            // 诊断：真机排查登录失败用（Console.app / 系统日志可见）
            NSLog("[Wenku8Login] status=$status bytes=${bytes.size} setCookie=${cookies.take(200)}")
            // 从 Set-Cookie 里收集 jieqi 开头的 cookie
            val jieqiCookies = parseSetCookies(cookies)
            NSLog("[Wenku8Login] parsed=${jieqiCookies.joinToString("; ").take(120)}")
            if (jieqiCookies.any { it.startsWith("jieqiUserInfo") }) {
                savedCookie = jieqiCookies.sorted().joinToString("; ")
                NSLog("[Wenku8Login] SUCCESS")
                return@runCatching "ok"
            }
            val error = loginErrorMessage(html)
            if (error != null) {
                NSLog("[Wenku8Login] error=$error")
                return@runCatching error
            }
            NSLog("[Wenku8Login] no jieqi cookie, html head=${html.take(150)}")
            return@runCatching "登录失败：无法解析响应"
        }.fold(
            onSuccess = { if (it == "ok") Result.success("ok") else Result.failure(Exception(it)) },
            onFailure = { Result.failure(Exception(it.message ?: "网络错误")) }
        )
    }

    private fun parseSetCookies(header: String): List<String> {
        // Set-Cookie 头里 expires 日期含字面逗号（如 "expires=Tue, 24-Aug-2027"），
        // 不能用 split(",")。改为正则提取所有 "jieqiXXX=值" 片段（值到 ; 或行尾为止）。
        val regex = Regex("jieqi[A-Za-z0-9_]*=[^;,\\s]+")
        return regex.findAll(header).map { it.value }.toList()
    }

    private fun loginErrorMessage(html: String): String? {
        val marker = html.substringAfter("错误原因：", "")
        if (marker.isNotEmpty()) {
            return marker.substringBefore("<").trim()
        }
        return null
    }

    private fun urlEncodeForm(s: String): String {
        return s.replace(" ", "%20")
    }

    // MARK: - 页面请求

    /** 取页面原始字节（GB18030）并解码成 UTF-8 字符串 */
    suspend fun getHtml(url: String): Result<String> {
        return runCatching {
            requestHeaders()
            val bytes = client.get(url).bodyAsBytes()
            Gbk.decodeToString(bytes)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(it.message ?: "网络错误")) }
        )
    }

    suspend fun getBytes(url: String): Result<ByteArray> {
        return runCatching {
            requestHeaders()
            client.get(url).bodyAsBytes()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(it.message ?: "网络错误")) }
        )
    }

    suspend fun close() {
        client.close()
    }
}
