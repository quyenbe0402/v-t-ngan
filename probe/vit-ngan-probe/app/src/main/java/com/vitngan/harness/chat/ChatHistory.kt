package com.vitngan.harness.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lịch sử API (messages cho /v1/messages) lưu theo phiên.
 * Khác tin hiển thị: history chứa tool_use/tool_result blocks để model nhớ ngữ cảnh.
 * Pure JVM — test được trên CI.
 */
object HistoryCodec {

    fun encode(messages: List<Map<String, Any>>): String {
        val arr = JSONArray()
        for (m in messages) arr.put(JSONObject(m.filterValues { it != null } as Map<*, *>))
        return arr.toString()
    }

    fun decode(json: String): List<Map<String, Any>> {
        if (json.isBlank()) return emptyList()
        val out = mutableListOf<Map<String, Any>>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(toMap(o))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun toMap(o: JSONObject): Map<String, Any> {
        val m = mutableMapOf<String, Any>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = when (val v = o.get(k)) {
                is JSONObject -> toMap(v)
                is JSONArray -> (0 until v.length()).map { idx ->
                    val e = v.get(idx)
                    if (e is JSONObject) toMap(e) else e.toString()
                }
                JSONObject.NULL -> ""
                is Boolean -> v
                is Number -> v.toString()
                else -> v.toString()
            }
        }
        return m
    }

    private fun toolUseIds(msg: Map<String, Any>): List<String> {
        val c = msg["content"] as? List<*> ?: return emptyList()
        return c.mapNotNull { b ->
            val m = b as? Map<*, *> ?: return@mapNotNull null
            if (m["type"] == "tool_use") m["id"]?.toString() else null
        }
    }

    private fun toolResultIds(msg: Map<String, Any>): List<String> {
        val c = msg["content"] as? List<*> ?: return emptyList()
        return c.mapNotNull { b ->
            val m = b as? Map<*, *> ?: return@mapNotNull null
            if (m["type"] == "tool_result") m["tool_use_id"]?.toString() else null
        }
    }

    /**
     * Bỏ message assistant có tool_use chưa được resolve (app chết giữa chừng,
     * hoặc pending chưa duyệt mà user gửi tiếp) — nếu không provider 400.
     * Giữ lại mọi message khác (kể cả user message mới sau đó).
     */
    fun sanitize(messages: List<Map<String, Any>>): MutableList<Map<String, Any>> {
        val resolved = messages.flatMap { toolResultIds(it) }.toSet()
        return messages.filterNot { m ->
            m["role"] == "assistant" &&
                toolUseIds(m).isNotEmpty() &&
                toolUseIds(m).any { it !in resolved }
        }.toMutableList()
    }
}
