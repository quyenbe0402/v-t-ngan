package com.vitngan.harness

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * L4 helper: xin quyền Shizuku rồi chờ Owner/driver bấm Allow (tối đa 90s).
 * Chạy 1 lần để persist grant, sau đó ShizukuEchoTest mới qua được.
 */
@RunWith(AndroidJUnit4::class)
class RequestPermissionTest {

    @Test fun requestAndAwaitGrant() = runBlocking {
        ApplicationProvider.getApplicationContext<Context>()
        assertTrue("Shizuku chưa chạy", Shizuku.pingBinder())
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return@runBlocking
        val granted = withTimeout(90_000) {
            suspendCancellableCoroutine { cont ->
                lateinit var listener: Shizuku.OnRequestPermissionResultListener
                listener = Shizuku.OnRequestPermissionResultListener { _, result ->
                    Shizuku.removeRequestPermissionResultListener(listener)
                    if (result == PackageManager.PERMISSION_GRANTED) cont.resume(true)
                    else cont.resumeWithException(Exception("Shizuku permission denied"))
                }
                cont.invokeOnCancellation {
                    runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(42)
            }
        }
        assertTrue(granted)
    }
}
