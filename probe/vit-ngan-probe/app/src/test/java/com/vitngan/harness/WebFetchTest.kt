package com.vitngan.harness

import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolRegistry
import com.vitngan.harness.tool.WebFetchTool
import com.vitngan.harness.tool.WebText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** WebFetch unit: lọc HTML + chặn SSRF — không chạm mạng thật. */
class WebFetchTest {
    private val ctx = ToolContext("/tmp", 1L)

    @Test fun stripsTagsAndScripts() {
        val html = "<html><head><script>var x=1;</script><style>.a{}</style></head>" +
            "<body><h1>Tiêu đề</h1><p>Đoạn <b>đậm</b> &amp; thoát.</p></body></html>"
        val t = WebText.htmlToText(html)
        assertTrue(t.contains("Tiêu đề"))
        assertTrue(t.contains("Đoạn đậm & thoát."))
        assertFalse(t.contains("var x=1"))
        assertFalse(t.contains("<b>"))
    }

    @Test fun blocksLocalAndMetadata() {
        assertNotNull(WebFetchTool.blockedReason("file:///etc/passwd"))
        assertNotNull(WebFetchTool.blockedReason("http://localhost:8080/"))
        assertNotNull(WebFetchTool.blockedReason("http://127.0.0.1/"))
        assertNotNull(WebFetchTool.blockedReason("http://169.254.169.254/"))
        assertNotNull(WebFetchTool.blockedReason("not a url"))
        assertNull(WebFetchTool.blockedReason("https://example.com/a"))
    }

    @Test fun blockedShortCircuits() = runBlocking {
        val r = ToolRegistry(FakeShellExecutor())
        val out = r.byName["web_fetch"]!!.execute(mapOf("url" to "http://127.0.0.1/"), ctx)
        assertFalse(out.ok)
        assertTrue(out.error.contains("nội bộ"))
    }
}
