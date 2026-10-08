package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitngan.harness.agent.AgentCore
import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AT-02 install_app: `pm install -r` chính APK probe (đã push lên
 * /data/local/tmp/self.apk — pm không đọc được /sdcard). Chạy RIÊNG CUỐI CÙNG —
 * reinstall giết process instrumentation, không được gộp chung suite khác.
 */
@RunWith(AndroidJUnit4::class)
class AcceptanceInstallTest {
    @Test fun at02_installSelf() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val core = AgentCore(
            ToolRegistry(ShizukuShellExecutor(ctx)),
            ToolContext(ctx.filesDir.absolutePath, System.currentTimeMillis())
        )
        val s = core.runTurn(
            listOf(
                Triple(
                    "install_app", mapOf(
                        "apk_path" to "/data/local/tmp/self.apk",
                        "confirmed" to "true"
                    ), "at02"
                )
            )
        ).single()
        assertTrue("install lỗi: ${s.result.error}", s.result.ok)
    }
}
