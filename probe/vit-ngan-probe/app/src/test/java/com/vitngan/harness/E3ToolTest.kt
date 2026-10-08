package com.vitngan.harness

import com.vitngan.harness.agent.AgentCore
import com.vitngan.harness.agent.AnthropicProvider
import com.vitngan.harness.tool.AnthropicToolParser
import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolLogger
import com.vitngan.harness.tool.ToolRegistry
import com.vitngan.harness.tool.UiActionTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Fake executor cho unit CI — không cần máy thật. */
class FakeShellExecutor(
    val outputs: Map<String, ShellResult> = emptyMap(),
    val failOn: Set<String> = emptySet()
) : ShellExecutor {
    val seen = mutableListOf<String>()
    override suspend fun isAvailable(): Boolean = true
    override suspend fun exec(command: String, timeoutMs: Long): ShellResult {
        seen.add(command)
        if (failOn.any { command.contains(it) }) throw SecurityException("can xac nhan: $command")
        return outputs[command] ?: ShellResult(0, "ok:$command", "", 1)
    }
}

class E3ToolTest {
    private val ctx = ToolContext("/data/user/0/com.vitngan.harness/files", 123L)

    @Test fun registryHas7ToolsSpecNames() {
        val r = ToolRegistry(FakeShellExecutor())
        // 7 MVP + ui_dump (D-019) + web_fetch (D-021).
        assertEquals(9, r.tools.size)
        assertEquals(
            listOf(
                "shell_exec", "install_app", "set_system_setting",
                "get_installed_apps", "ui_action", "file_op", "screenshot",
                "ui_dump", "web_fetch"
            ),
            r.names()
        )
    }

    @Test fun anthropicMapShape() {
        val r = ToolRegistry(FakeShellExecutor())
        val maps = r.anthropicTools()
        assertEquals(9, maps.size)
        val first = maps[0]
        assertTrue(first.containsKey("name"))
        assertTrue(first.containsKey("input_schema"))
    }

    @Test fun shellExecTimeoutParam() = runBlocking {
        val r = ToolRegistry(FakeShellExecutor())
        val out = r.byName["shell_exec"]!!.execute(
            mapOf("command" to "echo hello", "timeout_ms" to "5000"), ctx
        )
        assertTrue(out.ok)
        assertTrue(out.output.contains("echo hello"))
    }

    @Test fun includeSystemAcceptsYesTrue1() = runBlocking {
        // Hồi quy flow cài Free Fire: model gửi include_system=yes, code cũ chỉ nhận "true".
        for (v in listOf("yes", "true", "1", "YES")) {
            val fake = FakeShellExecutor()
            val r = ToolRegistry(fake)
            r.byName["get_installed_apps"]!!.execute(
                mapOf("filter" to "x", "include_system" to v), ctx
            )
            assertTrue(
                "include_system=$v phải liệt kê cả system: ${fake.seen}",
                fake.seen.any { it == "pm list packages --user current | grep 'x'" }
            )
        }
    }

    @Test fun installAppWhitelistBlocked() = runBlocking {
        val r = ToolRegistry(FakeShellExecutor())
        val out = r.byName["install_app"]!!.execute(mapOf("apk_path" to "/sdcard/Download/a.apk"), ctx)
        assertFalse(out.ok)
        assertTrue(out.error.contains("whitelist"))
    }

    @Test fun installAppAllowsStagingTmp() {
        assertTrue(
            com.vitngan.harness.tool.isInstallSourceAllowed(
                "/data/local/tmp/self.apk", "/data/user/0/com.vitngan.harness/files"
            )
        )
    }

    @Test fun runTurnKeepsScreenshotImage() = runBlocking {
        // Hồi quy lỗi Shopee 2026-10-08: AgentCore wrap lại ToolResult làm rớt
        // imagePath → model không bao giờ thấy ảnh screenshot.
        val path = "/sdcard/vitngan/shot_1.png"
        val fake = FakeShellExecutor(
            mapOf(
                "screencap -p '$path'" to ShellResult(0, "", "", 1),
                "test -f '$path' && echo EXISTS" to ShellResult(0, "EXISTS", "", 1)
            )
        )
        val reg = ToolRegistry(fake)
        val core = AgentCore(reg, ctx)
        val step = core.runTurn(
            listOf(Triple("screenshot", mapOf("path" to path), "s1"))
        ).single()
        assertTrue(step.result.ok)
        assertEquals(path, step.result.imagePath)
    }

    @Test fun fileWriteReadShape() = runBlocking {
        val r = ToolRegistry(FakeShellExecutor())
        val bad = r.byName["file_op"]!!.execute(mapOf("op" to "delete", "path" to "/sdcard/vitngan/a.txt"), ctx)
        assertFalse(bad.ok)
        val w = r.byName["file_op"]!!.execute(
            mapOf("op" to "write", "path" to "/sdcard/vitngan/a.txt", "content" to "hi"), ctx
        )
        assertTrue(w.ok)
    }

    @Test fun uiActionBuildTap() {
        val cmd = UiActionTool.buildCommand(mapOf("action" to "tap", "x" to "100", "y" to "200"))
        assertEquals("input tap 100 200", cmd)
    }

    @Test fun uiDumpExtractsText() = runBlocking {
        val dumpLine = "text=\"Kem đánh răng\" bounds=\"[100,200][300,400]\""
        val r = ToolRegistry(
            FakeShellExecutor(
                mapOf(
                    "uiautomator dump /sdcard/vitngan/ui.xml" to ShellResult(0, "dumped", "", 1),
                    "grep -o 'text=\"[^\"]\\+\"[^>]*bounds=\"\\[[0-9,]*\\]\\[[0-9,]*\\]\"' " +
                        "/sdcard/vitngan/ui.xml | grep -v 'text=\"\"' | head -80" to
                        ShellResult(0, dumpLine, "", 1)
                )
            )
        )
        val out = r.byName["ui_dump"]!!.execute(mapOf(), ctx)
        assertTrue(out.ok)
        assertTrue(out.output.contains("Kem đánh răng"))
        assertEquals(200 to 300, com.vitngan.harness.tool.UiDumpTool.center("[100,200][300,400]"))
    }

    @Test fun parserToolUse() {
        val json = "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}," +
            "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"shell_exec\"," +
            "\"input\":{\"command\":\"echo hello\"}}]}"
        val calls = AnthropicToolParser.parse(json)
        assertEquals(1, calls.size)
        assertEquals("shell_exec", calls[0].name)
        assertEquals("echo hello", calls[0].input["command"])
    }

    @Test fun providerBuildRequest() {
        val r = ToolRegistry(FakeShellExecutor())
        val p = AnthropicProvider(apiKey = "k", registry = r)
        val req = p.buildRequest("chay echo hello", maxTokens = 1024)
        assertTrue(req.contains("\"tool_choice\""))
        assertTrue(req.contains("shell_exec"))
    }

    @Test fun agentParallelIsError() = runBlocking {
        val reg = ToolRegistry(FakeShellExecutor())
        val core = AgentCore(reg, ctx)
        val steps = core.runTurn(
            listOf(
                Triple("shell_exec", mapOf("command" to "echo a"), "c1"),
                Triple("no_tool", mapOf(), "c2"),
                Triple("shell_exec", mapOf("command" to "echo b"), "c3")
            )
        )
        assertEquals(3, steps.size)
        assertTrue(steps[0].result.ok)
        assertTrue(steps[1].result.isError)
        val payload = core.toToolResultPayload(steps[1])
        assertEquals(true, payload["is_error"])
    }

    @Test fun loggerMasksSecrets() {
        val s = ToolLogger.maskSecrets("key sk-ant-abc123 Bearer xyz.789")
        assertFalse(s.contains("sk-ant-abc123"))
        assertFalse(s.contains("Bearer xyz.789"))
    }

    @Test fun partitionKeepsSingleResultPerId() {
        val steps = listOf(
            AgentCore.Step("a", mapOf(), com.vitngan.harness.tool.ToolResult.ok("1"), "id1"),
            AgentCore.Step(
                "b", mapOf(),
                com.vitngan.harness.tool.ToolResult.needConfirm("b can xac nhan"), "id2"
            ),
            AgentCore.Step("c", mapOf(), com.vitngan.harness.tool.ToolResult.fail("x"), "id3")
        )
        val (done, needs) = AgentCore.partition(steps)
        assertEquals(listOf("id1", "id3"), done.map { it.toolUseId })
        assertEquals(listOf("id2"), needs.map { it.toolUseId })
        // Bất biến API: mỗi id đúng 1 phía — gửi 2 lần là 400 (lỗi user gặp 2026-10-08).
        val all = done.map { it.toolUseId } + needs.map { it.toolUseId }
        assertEquals(all.size, all.toSet().size)
        assertEquals(3, all.size)
    }
}

