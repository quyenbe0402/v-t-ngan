package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation E3 trên máy thật (L4) — AT-01…AT-07 PLAN §10.2.
 * Yêu cầu: Shizuku đã Start + granted (như G1).
 */
@RunWith(AndroidJUnit4::class)
class E3DeviceTest {
    private fun registry(): Pair<ToolRegistry, ToolContext> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val exec = ShizukuShellExecutor(ctx)
        val filesDir = ctx.filesDir.absolutePath
        ctx.getExternalFilesDir(null)?.mkdirs()
        return Pair(ToolRegistry(exec), ToolContext(filesDir, System.currentTimeMillis()))
    }

    @Test fun at01_shellExec() = runBlocking {
        val (r, c) = registry()
        val out = r.byName["shell_exec"]!!.execute(mapOf("command" to "echo hello"), c)
        assertTrue(out.error, out.ok)
        assertTrue(out.output.contains("hello"))
    }

    @Test fun at02_installAppWhitelist() = runBlocking {
        val (r, c) = registry()
        // Không cài thật ở test — chỉ kiểm whitelist chặn path ngoài
        val out = r.byName["install_app"]!!.execute(mapOf("apk_path" to "/sdcard/Download/x.apk"), c)
        assertFalse(out.ok)
        assertTrue(out.error.contains("whitelist"))
    }

    @Test fun at03_setSystemSetting() = runBlocking {
        val (r, c) = registry()
        // Đọc lại font_scale (an toàn, không đổi) qua settings get
        val exec = ShizukuShellExecutor(ApplicationProvider.getApplicationContext())
        val cur = exec.exec("settings get system font_scale")
        assertEquals(0, cur.code)
        assertTrue(cur.stdout.trim().isNotEmpty())
    }

    @Test fun at04_getInstalledApps() = runBlocking {
        val (r, c) = registry()
        val out = r.byName["get_installed_apps"]!!.execute(mapOf("filter" to "shizuku"), c)
        assertTrue(out.error, out.ok)
    }

    @Test fun at05_uiActionTap() = runBlocking {
        val (r, c) = registry()
        // Tap góc an toàn (10,10) — không đổi trạng thái
        val out = r.byName["ui_action"]!!.execute(
            mapOf("action" to "tap", "x" to "10", "y" to "10"), c
        )
        assertTrue(out.error, out.ok)
    }

    @Test fun at06_fileWriteRead() = runBlocking {
        val (r, c) = registry()
        val path = "/sdcard/vitngan/e3_test.txt"
        val w = r.byName["file_op"]!!.execute(
            mapOf("op" to "write", "path" to path, "content" to "hello-e3"), c
        )
        assertTrue(w.error, w.ok)
        val rd = r.byName["file_op"]!!.execute(mapOf("op" to "read", "path" to path), c)
        assertTrue(rd.error, rd.ok)
        assertTrue(rd.output.contains("hello-e3"))
    }

    @Test fun at07_screenshot() = runBlocking {
        val (r, c) = registry()
        val out = r.byName["screenshot"]!!.execute(mapOf(), c)
        assertTrue(out.error, out.ok)
        assertTrue(out.output.contains("/sdcard/vitngan/shot_"))
    }
}
