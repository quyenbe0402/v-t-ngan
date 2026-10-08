package com.vitngan.harness

import com.vitngan.harness.dash.AgentLog
import com.vitngan.harness.dash.DashboardServer
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

/** E5-2 unit: server thật trên JVM, cổng test riêng. */
class DashboardTest {
    @Test fun statusAndLogPage() {
        AgentLog.clear()
        AgentLog.append("line-1 cmd=echo hello code=0")
        val s = DashboardServer(18080)
        s.start()
        try {
            val status = URL("http://127.0.0.1:18080/status").readText()
            assertTrue(status.contains("\"running\":true"))
            assertTrue(status.contains("line-1"))
            val page = URL("http://127.0.0.1:18080/").readText()
            assertTrue(page.contains("<html>"))
            assertTrue(page.contains("line-1"))
            val code = (URL("http://127.0.0.1:18080/nope").openConnection()
                as java.net.HttpURLConnection).responseCode
            assertEquals(404, code)
        } finally {
            s.stop()
        }
        AgentLog.clear()
    }

    @Test fun ringCap() {
        AgentLog.clear()
        repeat(600) { AgentLog.append("l$it") }
        assertEquals(512, AgentLog.size())
        assertTrue(AgentLog.tail(1).single().endsWith("l599"))
        AgentLog.clear()
    }
}
