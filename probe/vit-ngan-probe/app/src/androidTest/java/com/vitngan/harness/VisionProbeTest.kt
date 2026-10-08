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
 * Kiểm tra gateway có chuyển ảnh screenshot tới model không.
 * Mở probe (UI chữ to đã biết) → screenshot (kèm ảnh) → hỏi đọc tiêu đề.
 * Chỉ LOG để người đọc kết luận (model không đơn định).
 */
@RunWith(AndroidJUnit4::class)
class VisionProbeTest {
    @Test fun visionReadsTitle() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val key = BuildConfig.EXPLABS_API_KEY
        assertTrue("thiếu key", key.isNotEmpty())
        val exec = ShizukuShellExecutor(ctx)
        assertTrue(exec.isAvailable())
        val reg = ToolRegistry(exec)
        val prov = AnthropicProvider(apiKey = key, registry = reg)
        val core = AgentCore(reg, ToolContext(ctx.filesDir.absolutePath, System.currentTimeMillis()))
        val sb = StringBuilder()
        fun say(s: String) {
            println("[VISION] $s")
            sb.appendLine(s)
        }
        exec.exec(
            "am start -n com.vitngan.harness.debug/com.vitngan.harness.ProbeActivity", 10_000
        )
        kotlinx.coroutines.delay(2500)
        val history = mutableListOf<Map<String, Any>>(
            mapOf(
                "role" to "user",
                "content" to "Chụp màn hình, rồi đọc to chữ tiêu đề lớn nhất trong ảnh."
            )
        )
        var sawImageClaim = false
        for (turn in 0 until 5) {
            val r = prov.postMessages(history)
            history.add(prov.assistantMessage(r.rawJson))
            say("TURN $turn stop=${r.stopReason} text=${r.text.take(400)}")
            if (r.stopReason != "tool_use" || r.calls.isEmpty()) {
                say("ANSWER: ${r.text.take(800)}")
                if (r.text.contains("VitNgan", ignoreCase = true)) sawImageClaim = true
                break
            }
            for (c in r.calls) say("CALL ${c.name} ${c.input}")
            val steps = core.runTurn(
                r.calls.map { Triple(it.name, it.input + ("confirmed" to "true"), it.id) }
            )
            for (s in steps) {
                say("RESULT ${s.tool} ok=${s.result.ok} img=${s.result.imagePath}")
            }
            history.add(prov.toolResultMessage(steps))
        }
        exec.exec("screencap -p '/sdcard/vitngan/vision_probe.png'", 10_000)
        File(ctx.filesDir, "vision_flow.log").writeText(sb.toString(), Charsets.UTF_8)
        say("SEES_TITLE=$sawImageClaim")
        assertTrue("model phải gọi ít nhất 1 tool", sb.contains("CALL"))
    }
}
