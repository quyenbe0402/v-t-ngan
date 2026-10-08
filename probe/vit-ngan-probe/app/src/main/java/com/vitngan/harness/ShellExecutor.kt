package com.vitngan.harness

/**
 * Hợp đồng lõi PLAN §5.2. Agent Core chỉ phụ thuộc interface này.
 */
interface ShellExecutor {
    suspend fun isAvailable(): Boolean
    suspend fun exec(command: String, timeoutMs: Long = 10_000): ShellResult
}

class ShizukuNotAvailable(msg: String) : Exception(msg)
class ShizukuNotGranted(msg: String) : Exception(msg)
class ShellTimeout(cmd: String) : Exception("exec timeout: $cmd")
class ShellUnavailable(msg: String, cause: Throwable? = null) : Exception(msg, cause)
