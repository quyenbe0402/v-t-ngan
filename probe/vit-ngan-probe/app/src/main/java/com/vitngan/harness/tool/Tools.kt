package com.vitngan.harness.tool

import com.vitngan.harness.ShellExecutor
import com.vitngan.harness.ShellResult

/**
 * E3-2 fix khớp PLAN §5.4:
 * shell_exec(command, timeout_ms) | get_installed_apps(filter, include_system)
 * + timeout NFR-03: doc/get 10s, install 120s.
 */
internal suspend fun sh(exec: ShellExecutor, cmd: String, timeoutMs: Long = 10_000): ShellResult =
    exec.exec(cmd, timeoutMs)

internal fun okOut(r: ShellResult, limit: Int = 4096): ToolResult =
    if (r.code == 0) ToolResult.ok(r.truncatedStdout(limit))
    else ToolResult.fail("code=" + r.code + " stderr=" + r.stderr.take(1000))

class ShellExecTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "shell_exec",
        description = "Chay lenh shell cho phep qua Shizuku (sh -c)",
        inputSchema = mapOf("command" to "string", "timeout_ms" to "string"),
        required = listOf("command")
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val cmd = args["command"] ?: return ToolResult.fail("thieu command")
        val timeout = args["timeout_ms"]?.toLongOrNull() ?: 10_000L
        return try {
            okOut(exec.exec(cmd, timeout))
        } catch (e: SecurityException) {
            ToolResult.needConfirm(e.message ?: cmd)
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "exec failed")
        }
    }
}

class GetInstalledAppsTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "get_installed_apps",
        description = "Liet ke package (pm list packages [-3])",
        inputSchema = mapOf("filter" to "string", "include_system" to "string")
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val sysRaw = args["include_system"]?.trim()?.lowercase()
        val includeSystem = sysRaw == "true" || sysRaw == "yes" || sysRaw == "1"
        var cmd = if (includeSystem) "pm list packages --user current" else "pm list packages -3 --user current"
        val f = args["filter"] ?: ""
        if (f.isNotBlank()) cmd += " | grep '$f'"
        return try {
            okOut(sh(exec, cmd))
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "get_installed_apps failed")
        }
    }
}

class AppOpenTool(private val exec: ShellExecutor) : AgentTool {
    override val definition = ToolDefinition(
        name = "app_open",
        description = "Mo app theo package (monkey)",
        inputSchema = mapOf("package" to "string"),
        required = listOf("package")
    )
    override suspend fun execute(args: Map<String, String>, ctx: ToolContext): ToolResult {
        val pkg = args["package"] ?: return ToolResult.fail("thieu package")
        return try {
            okOut(sh(exec, "monkey -p $pkg -c android.intent.category.LAUNCHER 1"))
        } catch (e: Exception) {
            ToolResult.fail(e.message ?: "app_open failed")
        }
    }
}

