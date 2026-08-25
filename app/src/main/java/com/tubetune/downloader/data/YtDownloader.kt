package com.tubetune.downloader.data

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/**
 * OkHttp 實作的 NewPipe Extractor Downloader。
 *
 * 必須忠實執行 Request 的 HTTP method 與 body：
 * YouTube 的 InnerTube 搜尋/播放請求都是 POST JSON（youtubei/v1/search、
 * youtubei/v1/player 等），若一律發出無 body 的 GET，YouTube 會回傳
 * 405 或 HTML 頁面，NewPipe Extractor 便會拋出
 * "Got HTML document, expected JSON response"。
 * Content-Type 由 extractor 在 headers 中帶入，body 本身不再重設 media type，
 * 避免送出重複的 Content-Type header。
 */
class YtDownloader : Downloader() {

    private companion object {
        val BODY_REQUIRED_METHODS = setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        val builder = OkRequest.Builder().url(request.url())
        val body = when {
            data != null -> data.toRequestBody(null)
            request.httpMethod() in BODY_REQUIRED_METHODS -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        builder.method(request.httpMethod(), body)

        request.headers().forEach { (key, values) ->
            values.forEach { v -> builder.addHeader(key, v) }
        }

        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                body,
                resp.request.url.toString()
            )
        }
    }
}
