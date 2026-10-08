package com.vitngan.harness

import com.vitngan.harness.chat.ChatStore
import com.vitngan.harness.chat.StoredMsg
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** ChatStore unit: roundtrip/sắp xếp/xóa/cắt giới hạn/file hỏng — không cần máy. */
class ChatSessionTest {
    private fun store(): ChatStore =
        ChatStore(Files.createTempDirectory("chat").toFile())

    @Test fun saveLoadRoundTrip() {
        val st = store()
        val s = st.newSession("Test")
        s.messages.add(StoredMsg("Bạn", "chào"))
        s.messages.add(StoredMsg("Bot", "chào bác"))
        st.save(s)
        val back = st.load(s.id)!!
        assertEquals("Test", back.title)
        assertEquals(2, back.messages.size)
        assertEquals("chào bác", back.messages[1].text)
        assertEquals(1, st.list().size)
    }

    @Test fun listNewestFirstAndRetitle() {
        val st = store()
        val a = st.newSession("A")
        Thread.sleep(5)
        st.newSession("B")
        assertEquals("B", st.list().first().title)
        a.title = "A2"
        st.save(a)
        assertEquals("A2", st.list().first().title)
    }

    @Test fun deleteRemoves() {
        val st = store()
        val s = st.newSession("X")
        assertTrue(st.delete(s.id))
        assertTrue(st.list().isEmpty())
        assertNull(st.load(s.id))
        assertFalse(st.delete(s.id))
    }

    @Test fun trimsAndPrunes() {
        val st = store()
        val s = st.newSession("Big")
        repeat(ChatStore.MAX_MSGS + 10) { s.messages.add(StoredMsg("Bạn", "m$it")) }
        st.save(s)
        assertEquals(ChatStore.MAX_MSGS, st.load(s.id)!!.messages.size)
        repeat(ChatStore.MAX_SESSIONS + 3) { st.newSession("S$it") }
        assertTrue(st.list().size <= ChatStore.MAX_SESSIONS)
    }

    @Test fun corruptFileSafe() {
        val dir = Files.createTempDirectory("chatbad").toFile()
        val st = ChatStore(dir)
        val s = st.newSession("C")
        java.io.File(dir, "sessions/${s.id}.json").writeText("{hong", Charsets.UTF_8)
        assertNull(st.load(s.id))
    }

    private fun uMsg(t: String) = mapOf("role" to "user", "content" to t)
    private fun aTool(id: String) = mapOf(
        "role" to "assistant",
        "content" to listOf(mapOf("type" to "tool_use", "id" to id, "name" to "x", "input" to mapOf<String, Any>()))
    )
    private fun uResult(id: String) = mapOf(
        "role" to "user",
        "content" to listOf(
            mapOf("type" to "tool_result", "tool_use_id" to id, "content" to "ok", "is_error" to false)
        )
    )

    @Test fun historyRoundTrip() {
        val hist = mutableListOf(uMsg("hi"), aTool("t1"), uResult("t1"))
        val back = com.vitngan.harness.chat.HistoryCodec.decode(
            com.vitngan.harness.chat.HistoryCodec.encode(hist)
        )
        assertEquals(3, back.size)
        assertEquals("hi", back[0]["content"])
        @Suppress("UNCHECKED_CAST")
        val blocks = (back[2]["content"] as List<Map<String, Any>>)
        assertEquals(false, blocks[0]["is_error"])
    }

    @Test fun sanitizeDropsUnresolvedTail() {
        // App chết giữa chừng: tool_use t2 không có result → bỏ message đó, giữ còn lại.
        val msgs = listOf(uMsg("m1"), aTool("t1"), uResult("t1"), uMsg("m2"), aTool("t2"), uMsg("m3 mới"))
        val out = com.vitngan.harness.chat.HistoryCodec.sanitize(msgs)
        assertEquals(5, out.size)
        assertEquals("m3 mới", out[4]["content"])
        // Mọi tool_use còn lại đều đã resolve.
        val ids = out.flatMap {
            ((it["content"] as? List<*>) ?: emptyList<Any>()).mapNotNull { b ->
                val m = b as? Map<*, *>
                if (m?.get("type") == "tool_use") m["id"]?.toString() else null
            }
        }
        val res = out.flatMap {
            ((it["content"] as? List<*>) ?: emptyList<Any>()).mapNotNull { b ->
                val m = b as? Map<*, *>
                if (m?.get("type") == "tool_result") m["tool_use_id"]?.toString() else null
            }
        }.toSet()
        assertTrue(res.containsAll(ids))
    }

    @Test fun sessionPersistsHistory() {
        val st = store()
        val s = st.newSession("H")
        s.history.addAll(listOf(uMsg("hi"), aTool("t1"), uResult("t1")))
        st.save(s)
        assertEquals(3, st.load(s.id)!!.history.size)
    }
}
