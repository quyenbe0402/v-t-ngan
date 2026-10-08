package com.vitngan.harness.dash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * E5-2: chạy DashboardServer trong ForegroundService để không bị tắt nền.
 * Type specialUse (máy chủ trạng thái/log trên máy, không phải dataSync chạy tác vụ).
 */
class DashboardService : Service() {

    private var server: DashboardServer? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "VitNgan agent", NotificationManager.IMPORTANCE_MIN)
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("VitNgan agent")
            .setContentText("Dashboard chạy ở cổng $PORT")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(
            NOTIF_ID, notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        server = DashboardServer(PORT).apply { start() }
    }

    override fun onDestroy() {
        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PORT = 8080
        private const val CHANNEL = "vitngan_agent"
        private const val NOTIF_ID = 41
    }
}
