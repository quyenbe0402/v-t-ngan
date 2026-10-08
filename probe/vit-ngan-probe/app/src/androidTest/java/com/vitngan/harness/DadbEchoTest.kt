package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AT-09 Dadb fallback (PLAN §10.2). Cần `adb tcpip 5555` trước.
 * Nếu adbd đòi auth key chưa tin cậy → isAvailable=false, test báo SKIP-like fail
 * có thông điệp rõ (ghi nhận giới hạn, không chặn gate Shizuku).
 */
@RunWith(AndroidJUnit4::class)
class DadbEchoTest {
    @Test fun echoViaDadb() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val e = DadbShellExecutor(DadbEndpoint(), ctx.filesDir)
        assertTrue("dadb không khả dụng (chạy adb tcpip 5555 + chấp nhận auth nếu hỏi)", e.isAvailable())
        val r = e.exec("echo hello", 10_000)
        assertEquals(0, r.code)
        assertTrue(r.stdout.contains("hello"))
    }
}
