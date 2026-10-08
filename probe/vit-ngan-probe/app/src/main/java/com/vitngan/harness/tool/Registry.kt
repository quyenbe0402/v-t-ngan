package com.vitngan.harness.tool

import com.vitngan.harness.ShellExecutor
import org.json.JSONObject

/** Registry 7 tool MVP + ui_dump (D-019) + web_fetch (D-021 cào web). */
class ToolRegistry(exec: ShellExecutor) {
    val tools: List<AgentTool> = listOf(
        ShellExecTool(exec),
        InstallAppTool(exec),
        SetSystemSettingTool(exec),
        GetInstalledAppsTool(exec),
        UiActionTool(exec),
        FileOpTool(exec),
        ScreenshotTool(exec),
        UiDumpTool(exec),
        WebFetchTool()
    )
    val byName: Map<String, AgentTool> = tools.associateBy { it.definition.name }
    fun anthropicTools(): List<Map<String, Any>> = tools.map { it.definition.toAnthropicMap() }
    fun names(): List<String> = tools.map { it.definition.name }
}

/** Parse tool_use từ Claude response (org.json có sẵn trong Android). */
object AnthropicToolParser {
    data class ParsedCall(val id: String, val name: String, val input: Map<String, String>)

    fun parse(responseJson: String): List<ParsedCall> {
        val out = mutableListOf<ParsedCall>()
        val root = JSONObject(responseJson)
        val content = root.optJSONArray("content") ?: return out
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "tool_use") {
                val inputObj = block.optJSONObject("input") ?: JSONObject()
                val args = mutableMapOf<String, String>()
                val keys = inputObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    args[k] = inputObj.optString(k, "")
                }
                out.add(ParsedCall(block.optString("id"), block.optString("name"), args))
            }
        }
        return out
    }
}
