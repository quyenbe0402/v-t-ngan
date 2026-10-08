package com.vitngan.harness

/**
 * E1-1 probe B1. Pure JVM — không phụ thuộc Android/Shizuku để unit test được.
 * NFR-03: timeout mặc định 10s. NFR-05: log cắt 4KB (xử lý ở tầng log, không ở đây).
 */
data class ShellResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
    val ms: Long
) {
    val isOk: Boolean get() = code == 0
    fun truncatedStdout(limit: Int = 4096): String =
        if (stdout.length <= limit) stdout else stdout.take(limit) + "…[truncated]"
}
