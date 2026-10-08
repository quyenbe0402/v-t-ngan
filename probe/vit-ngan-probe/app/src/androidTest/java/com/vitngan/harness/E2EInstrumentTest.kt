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

/**
 * E3-6 e2e instrumentation (L4, cần Shizuku granted + key trong app BuildConfig).
 * Key đọc từ BuildConfig (local.properties/Env, debug only) — không log, không hardcode.
 */
@RunWith(AndroidJUnit4::class)
class E2eInstrumentTest {
    @Test fun e2eEchoHello() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val key = BuildConfig.EXPLABS_API_KEY
        assertTrue("thiếu key (BuildConfig rỗng)", key.isNotEmpty())
        val registry = ToolRegistry(ShizukuShellExecutor(ctx))
        val provider = AnthropicProvider(apiKey = key, registry = registry)
        val core = AgentCore(
            registry,
            ToolContext(ctx.filesDir.absolutePath, System.currentTimeMillis())
        )
        val history = mutableListOf<Map<String, Any>>(
            mapOf("role" to "user", "content" to "chạy echo hello, trả lời ngắn gọn")
        )
        var answer = ""
        for (turn in 0 until 3) {
            val r = provider.postMessages(history)
            history.add(provider.assistantMessage(r.rawJson))
            if (r.stopReason != "tool_use" || r.calls.isEmpty()) {
                answer = r.text
                break
            }
            val steps = core.runTurn(r.calls.map { Triple(it.name, it.input, it.id) })
            history.add(provider.toolResultMessage(steps))
        }
        println("E2E ANSWER: ${answer.take(500)}")
        assertTrue("đáp án phải chứa hello: $answer", answer.contains("hello", ignoreCase = true))
    }
}
