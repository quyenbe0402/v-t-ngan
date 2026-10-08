package com.vitngan.harness

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitngan.harness.dash.DashboardService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/**
 * AT-11 Dashboard smoke (PLAN §10.2): start service in-process (shell không được
 * start service non-exported), GET loopback, stop. Không cần UI.
 */
@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    @Test fun statusServes() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.startForegroundService(Intent(ctx, DashboardService::class.java))
        try {
            var status = ""
            var lastErr = "none"
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 15_000) {
                try {
                    status = URL("http://127.0.0.1:8080/status").readText()
                    break
                } catch (e: Exception) {
                    lastErr = e::class.simpleName + ": " + e.message
                    kotlinx.coroutines.delay(500)
                }
            }
            assertTrue("status phải có running:true (lastErr=$lastErr)", status.contains("\"running\":true"))
            val page = URL("http://127.0.0.1:8080/").readText()
            assertTrue(page.contains("VitNgan agent log"))
        } finally {
            ctx.stopService(Intent(ctx, DashboardService::class.java))
        }
    }
}
