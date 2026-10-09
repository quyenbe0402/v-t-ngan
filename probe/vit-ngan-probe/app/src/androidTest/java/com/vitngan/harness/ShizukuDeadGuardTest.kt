package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

/**
 * Guard NFR-08 (regression 10-09: chat lúc shizuku_server chết → FATAL
 * IllegalStateException "binder haven't been received" ở checkPermission,
 * app "bị đẩy ra ngoài"). Chỉ chạy khi server đang chết (pingBinder=false);
 * server sống thì skip (luồng grant/bind đã có ShizukuEchoTest/AcceptanceTest).
 */
@RunWith(AndroidJUnit4::class)
class ShizukuDeadGuardTest {
    @Test fun deadServerIsSoftError() = runBlocking {
        assumeFalse("cần shizuku_server chết (pingBinder=false)", Shizuku.pingBinder())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val e = ShizukuShellExecutor(ctx)
        assertFalse(e.isAvailable())
        try {
            e.exec("echo hello", 10_000)
            fail("mong đợi ShizukuNotAvailable khi server chết")
        } catch (ex: ShizukuNotAvailable) {
            assertTrue(ex.message!!.contains("Shizuku"))
        } catch (ex: Exception) {
            fail("không được ném lỗi thô ${ex.javaClass.name}: ${ex.message}")
        }
    }
}
