package com.vitngan.harness.tool

/**
 * E3-1 fix khớp PLAN §5.4:
 * ToolDefinition(name, description, inputSchema, annotations)
 * + required / requiresConfirm để enforce §5.5.
 * Regex tên: ^[a-zA-Z0-9_-]{1,128}$
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, String>,
    val required: List<String> = emptyList(),
    val requiresConfirm: Boolean = false,
    val annotations: Map<String, Boolean> = emptyMap()
) {
    init {
        require(name.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))) { "ten tool sai regex: $name" }
    }

    fun toAnthropicMap(): Map<String, Any> {
        val props = inputSchema.mapValues { (_, t) -> mapOf("type" to t) }
        return mapOf(
            "name" to name,
            "description" to description,
            "input_schema" to mapOf(
                "type" to "object",
                "properties" to props,
                "required" to required
            )
        )
    }
}

data class ToolCall(val name: String, val args: Map<String, String> = emptyMap())

data class ToolResult(val ok: Boolean, val output: String, val error: String = "") {
    val isError: Boolean get() = !ok

    /** Đường dẫn ảnh PNG đính kèm (screenshot) — model nhìn màn hình ở lượt sau. */
    var imagePath: String? = null

    companion object {
        fun ok(output: String) = ToolResult(true, output)
        fun fail(error: String) = ToolResult(false, "", error)
        fun needConfirm(reason: String) = ToolResult(false, "", "NEED_CONFIRM: $reason")
    }
}

/** §5.5 + §5.9: context có filesDir + timestamp cho log/screenshot. */
data class ToolContext(
    val filesDir: String,
    val timestampMs: Long = System.currentTimeMillis()
)

interface AgentTool {
    val definition: ToolDefinition
    suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult
}

/** Logger §5.5 + NFR-05: timestamp, tool, lệnh, code, stdout/stderr 4KB, ms. */
object ToolLogger {
    fun line(tool: String, cmd: String, code: Int, out: String, err: String, ms: Long, ts: Long): String {
        val o = out.take(4096).replace("\n", "\\n")
        val e = err.take(4096).replace("\n", "\\n")
        return "ts=$ts tool=$tool cmd=${cmd.take(500)} code=$code ms=$ms out=$o err=$e"
    }
    fun maskSecrets(s: String): String =
        s.replace(Regex("sk-ant-[A-Za-z0-9-_]+"), "sk-ant-***")
            .replace(Regex("xpl_[A-Za-z0-9]+"), "xpl_***")
            .replace(Regex("Bearer [A-Za-z0-9._-]+"), "Bearer ***")
}

