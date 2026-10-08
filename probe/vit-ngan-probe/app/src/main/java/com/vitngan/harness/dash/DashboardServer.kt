package com.vitngan.harness.dash

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * E5-2 Dashboard nhúng (PLAN §5.8, D-006: NanoHTTPD cho MVP).
 * GET /status -> JSON trạng thái; GET / -> trang log; còn lại 404.
 */
class DashboardServer(port: Int) : NanoHTTPD(port) {

    fun statusMap(): Map<String, Any> {
        val tail = AgentLog.tail(1)
        return mapOf(
            "running" to true,
            "logLines" to AgentLog.size(),
            "lastLine" to (tail.firstOrNull() ?: "")
        )
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/status" -> newFixedLengthResponse(
                Response.Status.OK, "application/json",
                JSONObject(statusMap() as Map<*, *>).toString()
            )
            "/" -> newFixedLengthResponse(Response.Status.OK, "text/html", page())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    private fun page(): String {
        val rows = AgentLog.tail(100).joinToString("\n") {
            "<li>" + it.replace("&", "&amp;").replace("<", "&lt;") + "</li>"
        }
        return "<html><head><meta charset='utf-8'><title>VitNgan log</title></head>" +
            "<body><h1>VitNgan agent log</h1><ul>$rows</ul></body></html>"
    }
}
