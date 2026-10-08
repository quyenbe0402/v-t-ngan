package com.vitngan.harness.agent

import com.vitngan.harness.tool.AnthropicToolParser
import com.vitngan.harness.tool.ToolLogger
import com.vitngan.harness.tool.ToolRegistry
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * E3-5 fix khớp PLAN §5.6 + docs/API_CONFIG.md:
 * POST /v1/messages, header anthropic-version: 2023-06-01,
 * body: model, max_tokens >= 1024, tools[{name,description,input_schema}],
 * tool_choice auto, messages. stop_reason == tool_use -> chay song song,
 * tra role:user tool_result{tool_use_id, content, is_error} ngay sau message tool_use.
 * Toi da ~10 vong. Key ngoai (EncryptedSharedPreferences/Env), timeout 15s/120s.
 * Khong gui thinking.budget_tokens tren route free.
 */
class AnthropicProvider(
    private val baseUrl: String = "https://api.experientiallabs.ai/v1",
    private val apiKey: String,
    private val model: String = "claude-haiku-5.5",
    private val registry: ToolRegistry,
    private val maxTurns: Int = 10
) {
    data class ChatResult(
        val text: String,
        val calls: List<AnthropicToolParser.ParsedCall>,
        val stopReason: String,
        val rawJson: String
    )

    fun buildRequest(userText: String, history: List<Map<String, Any>> = emptyList(), maxTokens: Int = 4096): String {
        val start = mapOf("role" to "user", "content" to userText)
        return buildRequestMessages(history + start, maxTokens)
    }

    /** E3-6: POST với history đầy đủ cho vòng lặp tool (không log key — xem ToolLogger). */
    fun buildRequestMessages(
        messages: List<Map<String, Any>>,
        maxTokens: Int = 4096,
        system: String? = null
    ): String {
        require(maxTokens >= 1024) { "max_tokens >= 1024 (§5.6)" }
        val toolsJson = JSONArray()
        for (t in registry.anthropicTools()) {
            toolsJson.put(JSONObject(t as Map<*, *>))
        }
        val body = JSONObject()
        body.put("model", model)
        body.put("max_tokens", maxTokens)
        if (system != null) body.put("system", system)
        val msgs = JSONArray()
        for (h in messages) msgs.put(JSONObject(h as Map<*, *>))
        body.put("messages", msgs)
        body.put("tools", toolsJson)
        val tc = JSONObject()
        tc.put("type", "auto")
        body.put("tool_choice", tc)
        return body.toString()
    }

    fun chat(userText: String, maxTokens: Int = 4096): ChatResult =
        postMessages(listOf(mapOf("role" to "user", "content" to userText)), maxTokens)

    /** E3-6: POST 1 lượt với history đầy đủ (blocking — gọi từ Dispatchers.IO). */
    fun postMessages(
        messages: List<Map<String, Any>>,
        maxTokens: Int = 4096,
        system: String? = null
    ): ChatResult {
        val payload = buildRequestMessages(messages, maxTokens, system)
        val url = URL("$baseUrl/messages")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
        }
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream.bufferedReader().readText()
        if (code !in 200..299)
            throw RuntimeException("provider $code: ${ToolLogger.maskSecrets(raw).take(1000)}")
        return parseResponse(raw)
    }

    fun parseResponse(raw: String): ChatResult {
        val root = JSONObject(raw)
        val stop = root.optString("stop_reason", "")
        val sb = StringBuilder()
        val content = root.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val b = content.optJSONObject(i) ?: continue
                if (b.optString("type") == "text") sb.append(b.optString("text", ""))
            }
        }
        val calls = AnthropicToolParser.parse(raw)
        return ChatResult(sb.toString().take(4096), calls, stop, raw)
    }

    /** Build message tool_result cho vong tiep theo (§5.6). */
    fun toolResultMessage(steps: List<AgentCore.Step>): Map<String, Any> {
        val blocks = steps.map { s ->
            val text = if (s.result.ok) s.result.output else s.result.error
            val img = readImageBlock(s.result.imagePath)
            if (img != null) {
                mapOf(
                    "type" to "tool_result",
                    "tool_use_id" to s.toolUseId,
                    "content" to listOf(
                        mapOf("type" to "text", "text" to text.take(1000)),
                        img
                    ),
                    "is_error" to s.result.isError
                )
            } else {
                mapOf(
                    "type" to "tool_result",
                    "tool_use_id" to s.toolUseId,
                    "content" to text,
                    "is_error" to s.result.isError
                )
            }
        }
        return mapOf("role" to "user", "content" to blocks)
    }

    companion object {
        /** Trần ảnh đính kèm: file PNG ≤ 2MB, quá thì bỏ ảnh giữ chữ. */
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

        fun readImageBlock(path: String?): Map<String, Any>? {
            if (path.isNullOrBlank()) return null
            return try {
                val f = java.io.File(path)
                if (!f.isFile || f.length() > MAX_IMAGE_BYTES) return null
                val bytes = f.readBytes()
                val b64 = try {
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (_: RuntimeException) {
                    java.util.Base64.getEncoder().encodeToString(bytes) // Unit JVM
                }
                mapOf(
                    "type" to "image",
                    "source" to mapOf("type" to "base64", "media_type" to "image/png", "data" to b64)
                )
            } catch (_: Exception) {
                null
            }
        }

        fun maskKey(k: String): String =
            if (k.length <= 8) "***" else k.take(4) + "***" + k.takeLast(4)

        /** Gợi ý mặc định: mở app bằng deep link, ĐỌC màn hình bằng ui_dump. */
        const val DEFAULT_SYSTEM_HINT: String =
            "Bạn điều khiển điện thoại Android qua các tool shell. " +
                "Muốn mở app/trang: ưu tiên mở deep link bằng shell_exec, ví dụ mở " +
                "tìm kiếm Shopee: am start -a android.intent.action.VIEW " +
                "-d 'https://shopee.vn/search?keyword=kem+đánh+răng'. " +
                "Muốn BIẾT màn hình đang hiện gì: gọi ui_dump (trả về chữ + tọa độ " +
                "dạng bounds), rồi tap/swipe/input_text theo đúng tọa độ đó. " +
                "screenshot chỉ để LƯU ảnh cho người xem, model KHÔNG nhìn được ảnh. " +
                "Trả lời ngắn gọn bằng tiếng Việt."
    }

    /** Dựng lại message assistant từ response để nối vào history (tool_use đứng liền trước tool_result). */
    fun assistantMessage(raw: String): Map<String, Any> {
        val root = JSONObject(raw)
        val blocks = mutableListOf<Map<String, Any>>()
        val content = root.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val b = content.optJSONObject(i) ?: continue
                blocks.add(jsonToMap(b))
            }
        }
        return mapOf("role" to "assistant", "content" to blocks)
    }

    private fun jsonToMap(o: JSONObject): Map<String, Any> {
        val m = mutableMapOf<String, Any>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = when (val v = o.get(k)) {
                is JSONObject -> jsonToMap(v)
                is JSONArray -> (0 until v.length()).map { idx ->
                    val e = v.get(idx)
                    if (e is JSONObject) jsonToMap(e) else e.toString()
                }
                JSONObject.NULL -> ""
                else -> v.toString()
            }
        }
        return m
    }
}

