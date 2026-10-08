package com.vitngan.harness

import android.os.SystemClock
import dadb.AdbAuthException
import dadb.AdbException
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File

/**
 * E4-1 Dadb fallback (PLAN §5.7). Dadb gốc KHÔNG có pair() → MVP nhập tay host:port,
 * mặc định adbd local sau `adb tcpip 5555`. API verified từ source mobile-dev-inc/dadb:
 * Dadb.create(host, port, keyPair?) + shell() -> AdbShellResponse(output, errorOutput, exitCode).
 */
data class DadbEndpoint(val host: String = "127.0.0.1", val port: Int = 5555)

interface DadbConnection : Closeable {
    fun shell(cmd: String): ShellResult
}

class RealDadbConnection(private val dadb: Dadb) : DadbConnection {
    override fun shell(cmd: String): ShellResult {
        val t0 = SystemClock.elapsedRealtime()
        val r = dadb.shell(cmd)
        return ShellResult(
            r.exitCode, r.output.trim(), r.errorOutput.trim(),
            SystemClock.elapsedRealtime() - t0
        )
    }

    override fun close() = dadb.close()
}

/** Mở Dadb: thử không key trước, rớt auth thì dùng keypair lưu ở filesDir (tạo 1 lần). */
internal fun openDadbConnection(endpoint: DadbEndpoint, filesDir: File): DadbConnection {
    try {
        return RealDadbConnection(Dadb.create(endpoint.host, endpoint.port, null))
    } catch (_: AdbAuthException) {
        // adbd đòi auth → rớt xuống dùng keypair riêng bên dưới
    }
    val priv = File(filesDir, "dadbkey")
    val pub = File(filesDir, "dadbkey.pub")
    if (!priv.exists()) AdbKeyPair.generate(priv, pub)
    return RealDadbConnection(Dadb.create(endpoint.host, endpoint.port, AdbKeyPair.read(priv, pub)))
}

class DadbShellExecutor(
    private val endpoint: DadbEndpoint = DadbEndpoint(),
    private val filesDir: File,
    private val open: (DadbEndpoint, File) -> DadbConnection = ::openDadbConnection,
    private val defaultTimeoutMs: Long = 10_000
) : ShellExecutor {

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(defaultTimeoutMs) {
                open(endpoint, filesDir).use { it.shell("echo ok").code == 0 }
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(timeoutMs) {
                    open(endpoint, filesDir).use { it.shell(command) }
                }
            } catch (e: TimeoutCancellationException) {
                throw ShellTimeout(command)
            } catch (e: AdbException) {
                throw ShellUnavailable("dadb: ${e::class.simpleName}: ${e.message}", e)
            }
        }
}

/** Shizuku trước, Dadb dự phòng — Agent Core chỉ thấy 1 ShellExecutor. */
class FirstAvailableExecutor(private val ordered: List<ShellExecutor>) : ShellExecutor {

    override suspend fun isAvailable(): Boolean = ordered.any {
        try {
            it.isAvailable()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult {
        var last: Exception? = null
        for (e in ordered) {
            val ok = try {
                e.isAvailable()
            } catch (_: Exception) {
                false
            }
            if (!ok) continue
            try {
                return e.exec(command, timeoutMs)
            } catch (ex: Exception) {
                last = ex
            }
        }
        throw last ?: ShellUnavailable("không có executor khả dụng")
    }
}
