package com.vitngan.harness.chat

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Phiên chat + lịch sử lưu trên máy (filesDir/sessions).
 * Pure JVM (org.json + java.io) — test được trên CI, không cần Room cho probe.
 */
data class StoredMsg(val who: String, val text: String)
data class SessionMeta(val id: String, val title: String, val updatedAt: Long)
data class StoredSession(
    val id: String,
    var title: String,
    var updatedAt: Long,
    val messages: MutableList<StoredMsg>,
    /** Lịch sử API (tool_use/tool_result) để model nhớ ngữ cảnh giữa các lượt. */
    val history: MutableList<Map<String, Any>> = mutableListOf()
)

class ChatStore(dir: File) {
    private val root: File = File(dir, "sessions")

    companion object {
        const val MAX_MSGS = 200
        const val MAX_SESSIONS = 20
    }

    init {
        root.mkdirs()
    }

    private fun indexFile(): File = File(root, "index.json")
    private fun sessionFile(id: String): File = File(root, "$id.json")

    fun list(): List<SessionMeta> {
        val f = indexFile()
        if (!f.isFile) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "")
                if (id.isEmpty()) null
                else SessionMeta(id, o.optString("title", "Chat"), o.optLong("updatedAt", 0))
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun load(id: String): StoredSession? {
        val f = sessionFile(id)
        if (!f.isFile) return null
        return try {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            val msgs = mutableListOf<StoredMsg>()
            val arr = o.optJSONArray("messages")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    msgs.add(StoredMsg(m.optString("who", "Bot"), m.optString("text", "")))
                }
            }
            StoredSession(
                o.optString("id", id),
                o.optString("title", "Chat"),
                o.optLong("updatedAt", System.currentTimeMillis()),
                msgs,
                HistoryCodec.decode(o.optString("history", "")).toMutableList()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(s: StoredSession) {
        s.updatedAt = System.currentTimeMillis()
        while (s.messages.size > MAX_MSGS) s.messages.removeAt(0)
        val o = JSONObject()
        o.put("id", s.id)
        o.put("title", s.title)
        o.put("updatedAt", s.updatedAt)
        val arr = JSONArray()
        for (m in s.messages) {
            arr.put(JSONObject().put("who", m.who).put("text", m.text))
        }
        o.put("messages", arr)
        o.put("history", JSONArray(HistoryCodec.encode(s.history.takeLast(30))))
        sessionFile(s.id).writeText(o.toString(), Charsets.UTF_8)
        val idx = list().filter { it.id != s.id }.toMutableList()
        idx.add(0, SessionMeta(s.id, s.title, s.updatedAt))
        writeIndex(idx.take(MAX_SESSIONS))
        pruneFiles(idx.take(MAX_SESSIONS).map { it.id }.toSet())
    }

    fun newSession(title: String = "Chat mới"): StoredSession {
        val s = StoredSession(UUID.randomUUID().toString(), title, System.currentTimeMillis(), mutableListOf())
        save(s)
        return s
    }

    fun delete(id: String): Boolean {
        val ok = sessionFile(id).delete()
        writeIndex(list().filter { it.id != id })
        return ok
    }

    private fun writeIndex(items: List<SessionMeta>) {
        val arr = JSONArray()
        for (m in items) {
            arr.put(JSONObject().put("id", m.id).put("title", m.title).put("updatedAt", m.updatedAt))
        }
        indexFile().writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun pruneFiles(keep: Set<String>) {
        (root.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".json") && it.name != "index.json" }
            .filter { it.nameWithoutExtension !in keep }
            .forEach { runCatching { it.delete() } }
    }
}
