package com.vitngan.harness.tool

import java.net.HttpURLConnection
import java.net.URL

/**
 * web_fetch — cào dữ liệu web (tham khảo Firecrawl MCP scrape + webcrawl-mcp:
 * scrape 1 URL → text thuần + provenance). Không dep mới: HttpURLConnection +
 * lọc HTML thuần. Trang JS-nặng cần Firecrawl key (chưa có) — tool báo rõ.
 */
class WebFetchTool : AgentTool {
    override val definition = ToolDefinition(
        name = "web_fetch",
        description = "Tải URL http/https về text thuần (tối đa ~6KB). " +
            "Trang render JS có thể thiếu nội dung.",
        inputSchema = mapOf("url" to "string"),
        required = listOf("url")
    )

    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val url = args["url"]?.trim() ?: return ToolResult.fail("thiếu url")
        val reason = blockedReason(url)
        if (reason != null) return ToolResult.fail(reason)
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "VitNganHarness/1.0")
                setRequestProperty("Accept", "text/html,*/*")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return ToolResult.fail("HTTP $code: $url")
            }
            val raw = conn.inputStream.use { stream ->
                val buf = ByteArray(200 * 1024)
                var total = 0
                var n = stream.read(buf, total, buf.size - total)
                while (n >= 0 && total < buf.size) {
                    total += n
                    n = stream.read(buf, total, buf.size - total)
                }
                String(buf, 0, total, Charsets.UTF_8)
            }
            val text = WebText.htmlToText(raw).take(6000)
            if (text.isBlank()) {
                ToolResult.fail("trang rỗng (có thể render JS — cần nội dung tĩnh)")
            } else {
                ToolResult.ok(text)
            }
        } catch (e: Exception) {
            ToolResult.fail("fetch lỗi: ${(e.message ?: "unknown").take(300)}")
        }
    }

    companion object {
        /** Chặn SSRF/metadata nội bộ. Trả về lý do hoặc null nếu cho qua. */
        fun blockedReason(url: String): String? {
            val u = try {
                URL(url)
            } catch (_: Exception) {
                return "URL sai: $url"
            }
            if (u.protocol != "http" && u.protocol != "https") {
                return "chỉ cho http/https: $url"
            }
            val host = u.host.lowercase()
            if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") ||
                host == "127.0.0.1" || host.startsWith("127.") ||
                host == "0.0.0.0" || host == "[::1]" || host == "::1" ||
                host == "169.254.169.254" || host == "metadata.google.internal"
            ) {
                return "chặn host nội bộ/metadata: $host"
            }
            return null
        }
    }
}

/** Lọc HTML → text thuần. Pure JVM — test được. */
object WebText {
    private val SCRIPT_STYLE = Regex(
        "(?is)<(script|style|noscript|template)[^>]*>.*?</\\1>"
    )
    private val TAGS = Regex("<[^>]+>")
    private val WS = Regex("[ \\t\\x0B\\f\\r]+")

    fun htmlToText(html: String): String {
        var s = SCRIPT_STYLE.replace(html, "\n")
        s = s.replace(Regex("(?i)<(br|p|div|li|tr|h[1-6]|section|article)[^>]*>"), "\n")
        s = TAGS.replace(s, " ")
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        val lines = s.split("\n").map { WS.replace(it.trim(), " ") }
            .filter { it.isNotBlank() }
        // Bỏ dòng trùng lặp kề nhau (menu/footer lặp).
        val dedup = ArrayList<String>(lines.size)
        for (l in lines) {
            if (dedup.isEmpty() || dedup.last() != l) dedup.add(l)
        }
        return dedup.joinToString("\n")
    }
}
