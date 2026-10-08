package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitngan.harness.agent.AgentCore
import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AT-01/03/04/05/06/07 trên máy thật (PLAN §10.2-10.3).
 * Chạy sau khi Shizuku granted. AT-02 nằm riêng ở AcceptanceInstallTest
 * (pm install -r tự thân giết process instrumentation).
 */
@RunWith(AndroidJUnit4::class)
class AcceptanceTest {
    private lateinit var exec: ShizukuShellExecutor
    private lateinit var core: AgentCore
    private lateinit var toolCtx: ToolContext

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        exec = ShizukuShellExecutor(ctx)
        toolCtx = ToolContext(ctx.filesDir.absolutePath, System.currentTimeMillis())
        core = AgentCore(ToolRegistry(exec), toolCtx)
    }

    private suspend fun runTool(
        name: String, args: Map<String, String>, id: String, confirmed: Boolean = false
    ): AgentCore.Step {
        val a = if (confirmed) args + ("confirmed" to "true") else args
        return core.runTurn(listOf(Triple(name, a, id))).single()
    }

    @Test fun at01_shellExec() = runBlocking {
        val s = runTool("shell_exec", mapOf("command" to "echo hello"), "at01")
        assertTrue("is_error phải false: ${s.result.error}", s.result.ok)
        assertTrue(s.result.output.contains("hello"))
        assertFalse(core.toToolResultPayload(s)["is_error"] as Boolean)
    }

    @Test fun at04_getInstalledApps() = runBlocking {
        exec.exec("mkdir -p /sdcard/vitngan", 10_000)
        val s = runTool("get_installed_apps", mapOf("filter" to "vitngan"), "at04")
        assertTrue(s.result.ok)
        assertTrue(s.result.output.contains("com.vitngan.harness.debug"))
    }

    @Test fun at06_fileRoundTrip() = runBlocking {
        exec.exec("mkdir -p /sdcard/vitngan", 10_000)
        val w = runTool(
            "file_op",
            mapOf("op" to "write", "path" to "/sdcard/vitngan/at06.txt", "content" to "vitngan-at06"),
            "at06w"
        )
        assertTrue(w.result.ok)
        val r = runTool(
            "file_op", mapOf("op" to "read", "path" to "/sdcard/vitngan/at06.txt"), "at06r"
        )
        assertTrue(r.result.ok)
        assertTrue(r.result.output.contains("vitngan-at06"))
    }

    @Test fun at07_screenshot() = runBlocking {
        exec.exec("mkdir -p /sdcard/vitngan", 10_000)
        val s = runTool("screenshot", mapOf(), "at07")
        assertTrue(s.result.ok)
        val check = exec.exec("test -f '${s.result.output}' && echo EXISTS", 10_000)
        assertEquals(0, check.code)
        assertTrue(check.stdout.contains("EXISTS"))
    }

    @Test fun at05_uiTapHome() = runBlocking {
        exec.exec("input keyevent KEYCODE_HOME", 10_000)
        val s = runTool(
            "ui_action", mapOf("action" to "tap", "x" to "610", "y" to "1500"), "at05"
        )
        assertTrue(s.result.ok)
    }

    @Test fun at03_fontScaleWithRestore() = runBlocking {
        val before = exec.exec("settings get system font_scale", 10_000).stdout.trim()
        assertTrue(before.isNotEmpty())
        try {
            val set = runTool(
                "set_system_setting",
                mapOf("namespace" to "system", "key" to "font_scale", "value" to "1.15"),
                "at03s", confirmed = true
            )
            assertTrue(set.result.ok)
            assertTrue(set.result.output.contains("1.15"))
        } finally {
            val back = runTool(
                "set_system_setting",
                mapOf("namespace" to "system", "key" to "font_scale", "value" to before),
                "at03b", confirmed = true
            )
            assertTrue(back.result.ok)
            assertTrue(back.result.output.contains(before))
        }
    }
}
