package com.vitngan.harness.dash

/**
 * E5-2/E3 log dùng chung: vòng xoay 512 dòng (giống giới hạn replay EventLog Hermes).
 * Pure JVM — test được trên CI.
 */
object AgentLog {
    private const val CAP = 512
    private val buf = ArrayDeque<String>()

    @Synchronized
    fun append(line: String) {
        if (buf.size >= CAP) buf.removeFirst()
        buf.addLast(line)
    }

    @Synchronized
    fun tail(n: Int = 100): List<String> = buf.takeLast(n.coerceAtLeast(0))

    @Synchronized
    fun clear() {
        buf.clear()
    }

    @Synchronized
    fun size(): Int = buf.size
}
