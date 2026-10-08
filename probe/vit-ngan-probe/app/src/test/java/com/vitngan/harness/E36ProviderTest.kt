package com.vitngan.harness

import com.vitngan.harness.agent.AgentCore
import com.vitngan.harness.agent.AnthropicProvider
import com.vitngan.harness.tool.ToolRegistry
import org.junit.Assert.*
import org.junit.Test

/** E3-6: logic message đa vòng, không chạm mạng. */
class E36ProviderTest {

    @Test fun postMessagesKeepsHistoryOrder() {
        val p = AnthropicProvider(apiKey = "k", registry = ToolRegistry(FakeShellExecutor()))
        val hist = listOf(
            mapOf("role" to "user", "content" to "hi"),
            mapOf("role" to "assistant", "content" to listOf(mapOf("type" to "text", "text" to "ok"))),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "tool_result", "tool_use_id" to "t1",
                        "content" to "hello", "is_error" to false
                    )
                )
            )
        )
        val body = p.buildRequestMessages(hist, maxTokens = 1024)
        val hi = body.indexOf("\"hi\"")
        val ok = body.indexOf("\"ok\"")
        val hello = body.indexOf("\"hello\"")
        assertTrue(hi in 0 until ok)
        assertTrue(ok in 0 until hello)
        assertTrue(body.contains("tool_result"))
    }

    @Test fun assistantMessageRoundTrip() {
        val p = AnthropicProvider(apiKey = "k", registry = ToolRegistry(FakeShellExecutor()))
        val raw = "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"de\"}," +
            "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"shell_exec\"," +
            "\"input\":{\"command\":\"echo hello\"}}],\"stop_reason\":\"tool_use\"}"
        val m = p.assistantMessage(raw)
        assertEquals("assistant", m["role"])
        @Suppress("UNCHECKED_CAST")
        val blocks = m["content"] as List<Map<String, Any>>
        assertEquals(2, blocks.size)
        assertEquals("tool_use", blocks[1]["type"])
        @Suppress("UNCHECKED_CAST")
        val input = blocks[1]["input"] as Map<String, Any>
        assertEquals("echo hello", input["command"])
    }

    @Test fun imageBlockAttached() {
        // PNG 1x1 thật, đọc từ bytes.
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
            0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0x0F, 0x00, 0x00,
            0x01, 0x01, 0x01, 0x00, 0x1B, 0x5C, 0xCD.toByte(), 0xC8.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
        val f = java.io.File.createTempFile("shot", ".png")
        f.writeBytes(png)
        val p = AnthropicProvider(apiKey = "k", registry = ToolRegistry(FakeShellExecutor()))
        val r = com.vitngan.harness.tool.ToolResult.ok("/shot.png").also { it.imagePath = f.absolutePath }
        val msg = p.toolResultMessage(
            listOf(AgentCore.Step("screenshot", mapOf(), r, "t9"))
        )
        @Suppress("UNCHECKED_CAST")
        val blocks = (msg["content"] as List<Map<String, Any>>)
        @Suppress("UNCHECKED_CAST")
        val content = blocks[0]["content"] as List<Map<String, Any>>
        assertEquals(2, content.size)
        assertEquals("image", content[1]["type"])
        // Thiếu file → chỉ chữ, không crash.
        val r2 = com.vitngan.harness.tool.ToolResult.ok("/nope.png").also {
            it.imagePath = "/nope.png"
        }
        val msg2 = p.toolResultMessage(
            listOf(AgentCore.Step("screenshot", mapOf(), r2, "t8"))
        )
        @Suppress("UNCHECKED_CAST")
        val blocks2 = (msg2["content"] as List<Map<String, Any>>)
        assertTrue(blocks2[0]["content"] is String)
        f.delete()
    }
}
