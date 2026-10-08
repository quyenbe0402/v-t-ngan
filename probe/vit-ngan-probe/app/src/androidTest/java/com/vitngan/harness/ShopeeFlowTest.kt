package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitngan.harness.agent.AgentCore
import com.vitngan.harness.agent.AnthropicProvider
import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Flow Shopee thật (theo yêu cầu Owner): mở Shopee + tìm kem đánh răng.
 * Tự duyệt lệnh (test only), ghi transcript để người đọc đánh giá —
 * không assert nội dung đáp án (model không đơn định).
 */
@RunWith(AndroidJUnit4::class)
class ShopeeFlowTest {
    @Test fun shopeeToothpaste() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val key = BuildConfig.EXPLABS_API_KEY
        assertTrue("thiếu key", key.isNotEmpty())
        val exec = ShizukuShellExecutor(ctx)
        assertTrue("Shizuku chưa sẵn sàng", exec.isAvailable())
        val reg = ToolRegistry(exec)
        val prov = AnthropicProvider(apiKey = key, registry = reg)
        val core = AgentCore(
            reg, ToolContext(ctx.filesDir.absolutePath, System.currentTimeMillis())
        )
        val sb = StringBuilder()
        fun say(s: String) {
            println("[FLOW] $s")
            sb.appendLine(s)
        }
        val history = mutableListOf<Map<String, Any>>(
            mapOf("role" to "user", "content" to "Mở Shopee và tìm kem đánh răng giúp tôi")
        )
        var calls = 0
        for (turn in 0 until 8) {
            val r = prov.postMessages(history, system = AnthropicProvider.DEFAULT_SYSTEM_HINT)
            history.add(prov.assistantMessage(r.rawJson))
            say("TURN $turn stop=${r.stopReason} text=${r.text.take(300)}")
            if (r.stopReason != "tool_use" || r.calls.isEmpty()) {
                say("ANSWER: ${r.text.take(1000)}")
                break
            }
            for (c in r.calls) {
                say("CALL ${c.name} ${c.input}")
                calls++
            }
            val steps = core.runTurn(
                r.calls.map { Triple(it.name, it.input + ("confirmed" to "true"), it.id) }
            )
            for (s in steps) {
                say(
                    "RESULT ${s.tool} ok=${s.result.ok} " +
                        "out=${s.result.output.take(300)} err=${s.result.error.take(200)}"
                )
            }
            history.add(prov.toolResultMessage(steps))
        }
        exec.exec("screencap -p '/sdcard/vitngan/shopee_final.png'", 10_000)
        File(ctx.filesDir, "shopee_flow.log").writeText(sb.toString(), Charsets.UTF_8)
        assertTrue("model phải gọi ít nhất 1 tool, log:\n$sb", calls > 0)
    }
}
