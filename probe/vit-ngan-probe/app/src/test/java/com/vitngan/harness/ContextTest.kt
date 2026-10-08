package com.vitngan.harness

import com.vitngan.harness.context.TurnBudget
import org.junit.Assert.*
import org.junit.Test

/** TurnBudget unit: cắt theo turn nguyên vẹn, bỏ ảnh cũ, ước lượng token. */
class ContextTest {

    private fun uMsg(t: String) = mapOf("role" to "user", "content" to t)
    private fun aTool(id: String) = mapOf(
        "role" to "assistant",
        "content" to listOf(mapOf("type" to "tool_use", "id" to id, "name" to "x", "input" to mapOf<String, Any>()))
    )
    private fun uResult(id: String, big: String) = mapOf(
        "role" to "user",
        "content" to listOf(
            mapOf("type" to "tool_result", "tool_use_id" to id, "content" to big, "is_error" to false)
        )
    )
    private fun imgResult(id: String) = mapOf(
        "role" to "user",
        "content" to listOf(
            mapOf("type" to "text", "text" to "shot"),
            mapOf(
                "type" to "image",
                "source" to mapOf("type" to "base64", "media_type" to "image/png", "data" to "AAAABBBB")
            )
        )
    )

    private fun pairInvariant(msgs: List<Map<String, Any>>) {
        val uses = mutableListOf<String>()
        val res = mutableSetOf<String>()
        for (m in msgs) {
            for (b in (m["content"] as? List<*> ?: emptyList<Any>())) {
                val mm = b as? Map<*, *> ?: continue
                if (mm["type"] == "tool_use") uses.add(mm["id"].toString())
                if (mm["type"] == "tool_result") res.add(mm["tool_use_id"].toString())
            }
        }
        assertTrue("tool_result mồ côi (không có tool_use): uses=$uses res=$res", res.all { it in uses })
    }

    @Test fun shortHistoryUntouched() {
        val h = listOf(uMsg("a"), aTool("t1"), uResult("t1", "x"))
        assertSame(h, TurnBudget.pruneForSend(h))
    }

    @Test fun dropsWholeMiddleTurns() {
        val h = mutableListOf<Map<String, Any>>()
        repeat(10) { i ->
            h.add(uMsg("q$i"))
            h.add(aTool("t$i"))
            h.add(uResult("t$i", "R".repeat(500)))
        }
        val out = TurnBudget.pruneForSend(h)
        // Giữ turn đầu + 6 turn cuối = 7 turn = 21 message.
        assertEquals(21, out.size)
        assertEquals("q0", (out[0]["content"] as String))
        assertEquals("q9", (out[18]["content"] as String))
        pairInvariant(out)
    }

    @Test fun stripsOldImagesKeepsPairs() {
        val h = mutableListOf<Map<String, Any>>()
        repeat(4) { i ->
            h.add(uMsg("q$i"))
            h.add(aTool("t$i"))
            if (i < 2) {
                h.add(imgResult("t$i"))
            } else {
                h.add(uResult("t$i", "ok"))
            }
        }
        val out = TurnBudget.pruneForSend(h)
        val hasImage = out.any { m ->
            ((m["content"] as? List<*>) ?: emptyList<Any>()).any { b ->
                (b as? Map<*, *>)?.get("type") == "image"
            }
        }
        assertFalse("ảnh turn cũ phải bị bỏ", hasImage)
        pairInvariant(out)
    }

    @Test fun estimateTokensRough() {
        // Đếm cả key ("role" + "content") lẫn value: (4+7+4+8)/4 = 5.
        val t = TurnBudget.estimateTokens(listOf(uMsg("abcd1234")))
        assertEquals((4 + 7 + 4 + 8) / 4, t)
    }
}
