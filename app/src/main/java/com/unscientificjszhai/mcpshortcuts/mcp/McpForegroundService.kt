package com.unscientificjszhai.mcpshortcuts.mcp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 用于保持 MCP 连接的前台服务。
 * 当应用进入后台且存在需要保持连接的服务器时启动。
 */
@AndroidEntryPoint
class McpForegroundService : Service() {

    /**
     * 通知管理器。
     */
    @Inject
    lateinit var notificationManager: McpNotificationManager

    companion object {
        private const val ACTION_START = "ACTION_START"
        private const val ACTION_STOP = "ACTION_STOP"

        /**
         * 启动前台服务。
         *
         * @param context 上下文。
         */
        fun start(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        /**
         * 停止前台服务。
         *
         * @param context 上下文。
         */
        fun stop(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent) // Always use startService for stopping via intent, or just stopService
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                try {
                    val notification = notificationManager.buildForegroundServiceNotification()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Android 14+ requires specific foreground service type
                        startForeground(
                            McpNotificationManager.FOREGROUND_SERVICE_NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(
                            McpNotificationManager.FOREGROUND_SERVICE_NOTIFICATION_ID,
                            notification
                        )
                    }
                } catch (e: Exception) {
                    // Fail gracefully if permissions missing (e.g. Android 14 requirements)
                    e.printStackTrace()
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY // System will try to recreate the service if it is killed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need binding, just started state
    }
}
