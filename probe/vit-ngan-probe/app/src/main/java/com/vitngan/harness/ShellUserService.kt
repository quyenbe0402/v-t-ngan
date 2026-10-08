package com.vitngan.harness

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Chạy trong privileged process do Shizuku khởi tạo (uid 2000/adb).
 *
 * QUAN TRỌNG: đây KHÔNG phải Android Service, KHÔNG khai báo <service> trong
 * Manifest. Shizuku server khởi tạo trực tiếp qua constructor (theo demo
 * chính thức RikkaApps/Shizuku-API). AIDL giữ `destroy() = 16777114` cho server.
 */
class ShellUserService : IShellService.Stub {

    constructor() {
        Log.i(TAG, "constructor")
    }

    @Keep
    constructor(context: Context) {
        Log.i(TAG, "constructor with Context=$context")
    }

    override fun destroy() {
        Log.i(TAG, "destroy")
        System.exit(0)
    }

    override fun run(cmd: String): String {
        val proc = try {
            ProcessBuilder("sh", "-c", cmd).start()
        } catch (e: Exception) {
            return pack(-1, "", "start failed: ${e.message}")
        }
        val out = StringBuilder()
        val err = StringBuilder()
        val t1 = thread(isDaemon = true) { pump(proc.inputStream, out) }
        val t2 = thread(isDaemon = true) { pump(proc.errorStream, err) }
        val done = try {
            proc.waitFor(EXEC_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            false
        }
        t1.join(2000)
        t2.join(2000)
        if (!done) {
            try {
                proc.destroyForcibly()
            } catch (_: Exception) {
            }
            return pack(124, out.toString(), err.toString() + "\n[TIMEOUT ${EXEC_TIMEOUT_S}s]")
        }
        return pack(proc.exitValue(), out.toString(), err.toString())
    }

    private fun pump(input: InputStream, sb: StringBuilder) {
        try {
            input.bufferedReader().forEachLine { sb.append(it).append('\n') }
        } catch (_: Exception) {
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "ShellUserService"
        const val EXEC_TIMEOUT_S = 10L

        /** Tầng client gọi sau binder call để tách code/stdout/stderr. */
        fun unpack(packed: String): Triple<Int, String, String> {
            val parts = packed.split('\n', limit = 3)
            require(parts.size == 3) { "packed ShellResult sai format" }
            val code = parts[0].toInt()
            return Triple(code, unb64(parts[1]), unb64(parts[2]))
        }

        internal fun pack(code: Int, stdout: String, stderr: String): String {
            var o = stdout
            var e = stderr
            if (o.length > MAX_OUT) o = o.take(MAX_OUT) + "…[truncated]"
            if (e.length > MAX_OUT) e = e.take(MAX_OUT) + "…[truncated]"
            return "$code\n${b64(o)}\n${b64(e)}"
        }

        private const val MAX_OUT = 262144 // trần binder transaction

        private fun b64(s: String): String =
            Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        private fun unb64(s: String): String =
            if (s.isEmpty()) "" else String(Base64.decode(s, Base64.NO_WRAP), Charsets.UTF_8)

        @Suppress("unused")
        fun nowMs(): Long = SystemClock.elapsedRealtime()
    }
}
