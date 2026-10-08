package com.vitngan.harness.context

/**
 * Quản lý ngữ cảnh (tham khảo deepseek-harness packages/context + compaction:
 * time-context, session-reference, compaction-basic, tool-result-pruner).
 * Bản probe: model-free, không gọi model thêm.
 *
 * NGUYÊN TẮC AN TOÀN (bắt buộc): không bao giờ tách rời cặp tool_use/tool_result
 * (API 400). Mọi cắt gọn thao tác theo TURN nguyên vẹn: 1 turn = 1 tin user-text
 * + các message tiếp theo tới trước user-text kế tiếp.
 */
object TurnBudget {
    /** Số turn cuối giữ nguyên vẹn. */
    const val KEEP_LAST_TURNS = 6

    private fun isUserText(m: Map<String, Any>): Boolean =
        m["role"] == "user" && m["content"] is String

    /** Chia history thành các turn nguyên vẹn. */
    fun splitTurns(history: List<Map<String, Any>>): List<List<Map<String, Any>>> {
        val turns = mutableListOf<MutableList<Map<String, Any>>>()
        var cur = mutableListOf<Map<String, Any>>()
        for (m in history) {
            if (isUserText(m) && cur.isNotEmpty()) {
                turns.add(cur)
                cur = mutableListOf()
            }
            cur.add(m)
        }
        if (cur.isNotEmpty()) turns.add(cur)
        return turns
    }

    /** Bỏ các turn giữa (giữ turn đầu + KEEP_LAST_TURNS turn cuối). */
    fun dropOldTurns(
        history: List<Map<String, Any>>,
        keepLastTurns: Int = KEEP_LAST_TURNS
    ): List<Map<String, Any>> {
        val turns = splitTurns(history)
        if (turns.size <= keepLastTurns + 1) return history
        val keep = turns.take(1) + turns.takeLast(keepLastTurns)
        return keep.flatten()
    }

    /**
     * Bỏ image block trong tool_result của các turn cũ (chỉ giữ ảnh 2 turn cuối).
     * Chỉ xóa phần tử image trong content list — cặp use/result còn nguyên.
     * tool_result rỗng sau khi xóa → thay bằng text giữ chỗ (API cấm content rỗng).
     */
    fun stripOldImages(
        history: List<Map<String, Any>>,
        keepImagesLastTurns: Int = 2
    ): List<Map<String, Any>> {
        val turns = splitTurns(history)
        if (turns.size <= keepImagesLastTurns) return history
        val out = ArrayList<Map<String, Any>>(history.size)
        val cutoff = turns.size - keepImagesLastTurns
        turns.forEachIndexed { idx, turn ->
            if (idx >= cutoff) {
                out.addAll(turn)
            } else {
                for (m in turn) out.add(stripImagesOfMessage(m))
            }
        }
        return out
    }

    private fun stripImagesOfMessage(msg: Map<String, Any>): Map<String, Any> {
        val content = msg["content"] as? List<*> ?: return msg
        var changed = false
        val kept = ArrayList<Any>()
        for (b in content) {
            val m = b as? Map<*, *>
            if (m?.get("type") == "image") {
                changed = true
            } else {
                kept.add(b as Any)
            }
        }
        if (!changed) return msg
        if (kept.isEmpty()) {
            kept.add(mapOf("type" to "text", "text" to "(ảnh cũ đã bỏ)"))
        }
        return msg + ("content" to kept)
    }

    /** View để gửi: bỏ turn cũ + bỏ ảnh cũ. Không sửa list gốc. */
    fun pruneForSend(history: List<Map<String, Any>>): List<Map<String, Any>> =
        stripOldImages(dropOldTurns(history))

    /** Ước lượng thô token (~4 ký tự/token) để log/giám sát, không chặn cứng. */
    fun estimateTokens(messages: List<Map<String, Any>>): Int {
        var chars = 0
        fun count(v: Any?) {
            when (v) {
                is String -> chars += v.length
                is Map<*, *> -> {
                    v.keys.forEach { count(it) }
                    v.values.forEach { count(it) }
                }
                is List<*> -> v.forEach { count(it) }
                else -> chars += v.toString().length
            }
        }
        messages.forEach { count(it) }
        return chars / 4
    }
}
