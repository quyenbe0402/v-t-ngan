package com.vitngan.harness

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * E1-1 B1-min (Shizuku-API 13.1.5, V-04 đã xác minh bằng javap: `newProcess` là
 * private — đường public duy nhất là UserService qua `bindUserService`).
 *
 * Luồng mỗi exec: checks → bind ShellUserService (timeout 15s) → binder call
 * `run(cmd)` (thực thi trong privileged process) → unpack → unbind.
 * Chỉ gọi Shizuku.* sau pingBinder()==true.
 */
class ShizukuShellExecutor(context: Context) : ShellExecutor {

    private val appContext = context.applicationContext

    override suspend fun isAvailable(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult =
        withContext(Dispatchers.IO) {
            if (!Shizuku.pingBinder()) throw ShizukuNotAvailable(
                "Shizuku chưa chạy (pingBinder=false). Mở Shizuku → Start."
            )
            if (Shizuku.isPreV11()) throw ShizukuNotAvailable("Shizuku pre-v11 không hỗ trợ.")
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                throw ShizukuNotGranted("Chưa cấp Shizuku permission. Gọi requestPermission trước.")
            // KHÔNG check CommandPolicy ở đây: backend chỉ thực thi theo lệnh.
            // Gate xác nhận nằm ở AgentCore (requiresConfirm + confirmed=true) và
            // whitelist từng tool. Tránh double-gate làm confirmed flow không bao giờ chạy.

            val args = Shizuku.UserServiceArgs(
                ComponentName(appContext.packageName, ShellUserService::class.java.name)
            ).daemon(false).processNameSuffix("shell").version(1)

            val t0 = SystemClock.elapsedRealtime()
            var conn: ServiceConnection? = null
            try {
                val service = withTimeout(BIND_TIMEOUT_MS) {
                    awaitBind(args).also { conn = it.second }.first
                }
                val packed = withTimeout(timeoutMs + BINDER_MARGIN_MS) {
                    service.run(command)
                }
                val (code, out, err) = ShellUserService.unpack(packed)
                ShellResult(code, out.trim(), err.trim(), SystemClock.elapsedRealtime() - t0)
            } catch (e: android.os.RemoteException) {
                throw ShellUnavailable("Shizuku binder chết giữa chừng — thử lại", e)
            } finally {
                // unbind KHÔNG kill (false): kill=true hạ process service và race
                // onServiceDisconnected vào lần bind kế tiếp (AT-05/06/07 từng fail vì vậy).
                // Service sống tới khi app chết; destroy() tường minh khi cần dọn hẳn.
                conn?.let { runCatching { Shizuku.unbindUserService(args, it, false) } }
            }
        }

    private suspend fun awaitBind(
        args: Shizuku.UserServiceArgs
    ): Pair<IShellService, ServiceConnection> =
        suspendCancellableCoroutine { cont ->
            val c = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    // Shizuku có thể gọi lại nhiều lần (reconnect) — chỉ nhận lần đầu,
                    // nếu không resume lần 2 ném Already resumed và giết process.
                    if (!cont.isActive) return
                    if (binder != null && binder.pingBinder()) {
                        cont.resume(Pair(IShellService.Stub.asInterface(binder), this))
                    } else {
                        cont.resumeWithException(ShellUnavailable("invalid binder từ Shizuku"))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (cont.isActive) {
                        cont.resumeWithException(ShellUnavailable("Shizuku service disconnected"))
                    }
                }
            }
            cont.invokeOnCancellation {
                runCatching { Shizuku.unbindUserService(args, c, false) }
            }
            try {
                Shizuku.bindUserService(args, c)
            } catch (e: Exception) {
                cont.resumeWithException(
                    ShellUnavailable("bindUserService thất bại: ${e.message}", e)
                )
            }
        }

    companion object {
        // Bind đôi khi chậm khi máy bận (Shopee foreground + dump): 25s thay vì 15s
        // (flow thật từng rớt 1 lần "Timed out waiting for 15000 ms").
        private const val BIND_TIMEOUT_MS = 25_000L
        private const val BINDER_MARGIN_MS = 15_000L
    }
}
