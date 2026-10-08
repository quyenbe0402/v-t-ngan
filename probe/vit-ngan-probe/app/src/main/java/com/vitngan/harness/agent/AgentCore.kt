package com.vitngan.harness.agent

import com.vitngan.harness.tool.ToolContext
import com.vitngan.harness.tool.ToolLogger
import com.vitngan.harness.tool.ToolRegistry
import com.vitngan.harness.tool.ToolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * E3-4 fix khớp PLAN §5.4 + §5.6:
 * dispatch: (1) policy/whitelist/confirm -> (2) executor.exec -> (3) bọc isError.
 * Lỗi tool trả is_error cho model, KHÔNG ném khỏi vòng lặp.
 * §5.6: thực thi song song các tool_use trong cùng 1 turn.
 */
class AgentCore(
    private val registry: ToolRegistry,
    private val ctx: ToolContext,
    private val log: (String) -> Unit = {}
) {
    data class Step(
        val tool: String,
        val args: Map<String, String>,
        val result: ToolResult,
        val toolUseId: String = ""
    )

    suspend fun runTurn(calls: List<Triple<String, Map<String, String>, String>>): List<Step> =
        coroutineScope {
            calls.map { (name, args, id) ->
                async {
                    val tool = registry.byName[name]
                    if (tool == null) {
                        val r = ToolResult.fail("tool khong ton tai: $name")
                        log("[$id] $name -> is_error=true unknown")
                        return@async Step(name, args, r, id)
                    }
                    // (1) policy/confirm gate: requiresConfirm -> NEED_CONFIRM (Allow/Ask/Deny, mac dinh Ask)
                    if (tool.definition.requiresConfirm && args["confirmed"] != "true") {
                        val r = ToolResult.needConfirm(name + " can xac nhan (confirmed=true)")
                        log("[$id] $name -> NEED_CONFIRM")
                        return@async Step(name, args, r, id)
                    }
                    // (2) exec + (3) boc is_error, khong nem
                    val t0 = System.currentTimeMillis()
                    val r = try {
                        tool.execute(args, ctx)
                    } catch (e: Exception) {
                        ToolResult.fail(e.message ?: "tool crash")
                    }
                    val ms = System.currentTimeMillis() - t0
                    val line = ToolLogger.maskSecrets(
                        ToolLogger.line(name, args.toString(), if (r.ok) 0 else 1, r.output, r.error, ms, ctx.timestampMs)
                    )
                    log("[$id] $line")
                    // Giữ imagePath (screenshot) qua wrap — nếu không model không bao giờ thấy ảnh.
                    val out = ToolResult(r.ok, r.output.take(4096), r.error.take(4096))
                    out.imagePath = r.imagePath
                    Step(name, args, out, id)
                }
            }.awaitAll()
        }

    /** Build tool_result block cho vòng tiếp theo (§5.6, tối đa ~10 vòng). */
    fun toToolResultPayload(step: Step): Map<String, Any> = mapOf(
        "tool_use_id" to step.toolUseId,
        "content" to (if (step.result.ok) step.result.output else step.result.error),
        "is_error" to step.result.isError
    )

    companion object {
        /**
         * Tách steps thành (done, needsConfirm). Bất biến API: mỗi tool_use_id chỉ
         * xuất hiện trong ĐÚNG 1 tool_result message — caller chỉ add toolResultMessage
         * một lần cho mỗi phía, không bao giờ add lại toàn bộ steps.
         */
        fun partition(steps: List<Step>): Pair<List<Step>, List<Step>> {
            val needs = steps.filter { !it.result.ok && it.result.error.startsWith("NEED_CONFIRM") }
            val needIds = needs.map { it.toolUseId }.toSet()
            val done = steps.filter { it.toolUseId !in needIds }
            return done to needs
        }
    }
}
