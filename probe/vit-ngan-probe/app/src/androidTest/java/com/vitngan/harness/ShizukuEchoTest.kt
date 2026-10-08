package com.vitngan.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation — CHỈ chạy trên máy thật đã start Shizuku + granted (E1-2, L4).
 * B1-PASS §10.1: code=0 stdout=hello <10s.
 */
@RunWith(AndroidJUnit4::class)
class ShizukuEchoTest {
    @Test fun echoHello() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val r = ShizukuShellExecutor(ctx).exec("echo hello")
        assertEquals(0, r.code)
        assertTrue(r.stdout.contains("hello"))
    }
}
