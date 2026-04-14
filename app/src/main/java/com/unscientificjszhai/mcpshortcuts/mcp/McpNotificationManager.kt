package com.unscientificjszhai.mcpshortcuts.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.unscientificjszhai.mcpshortcuts.R
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val gson = Gson()

    companion object {
        const val FOREGROUND_SERVICE_CHANNEL_ID = "mcp_foreground_service"
        const val FOREGROUND_SERVICE_NOTIFICATION_ID = 10001

        // 生成对应服务器的渠道 ID
        fun getServerChannelId(serverId: Long) = "server_$serverId"
    }

    init {
        // 创建前台服务的专属系统级 Notification Channel
        val channel = NotificationChannel(
            FOREGROUND_SERVICE_CHANNEL_ID,
            "Background Connection Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps MCP servers connected in the background"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 为特定的 MCP 服务器创建独立的通知渠道
     */
    fun createChannelForServer(serverId: Long, serverName: String) {
        val channelId = getServerChannelId(serverId)
        val channel = NotificationChannel(
            channelId,
            "Server: $serverName",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Custom notifications from $serverName"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 同步清理不再需要的服务器通知渠道
     */
    fun deleteChannelForServer(serverId: Long) {
        notificationManager.deleteNotificationChannel(getServerChannelId(serverId))
    }

    /**
     * 将从 MCP 协议层收到的自定义 Notification 转为系统广播及通知发送给用户
     */
    fun showServerNotification(
        serverId: Long,
        serverName: String,
        notification: JSONRPCNotification
    ) {
        // 创建或保证 Channel 存在
        createChannelForServer(serverId, serverName)

        // 尝试解析参数内容作为通知的主体
        val paramsText = try {
            when (val params = notification.params) {
                is String -> params
                is Map<*, *> -> gson.toJson(params)
                is JsonElement -> params.toString()
                else -> params?.toString() ?: "No content"
            }
        } catch (_: Exception) {
            "No content"
        }

        val androidNotification = NotificationCompat.Builder(context, getServerChannelId(serverId))
            .setSmallIcon(R.drawable.ic_notification) // TODO: 最好替换为具有透明背景的矢量图标
            .setContentTitle("Message from $serverName")
            .setContentText(paramsText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // 使用一个基于 serverId + 方法名哈希的 ID 让同一服务器同类方法不互相覆盖太多，或者粗犷点使用当前时间
        val notificationId = (serverId.toString() + notification.method).hashCode()
        notificationManager.notify(notificationId, androidNotification)
    }

    /**
     * 构建用于前台服务持有的持久通知
     */
    fun buildForegroundServiceNotification(): Notification {
        return NotificationCompat.Builder(context, FOREGROUND_SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.foreground_service_notification_title))
            .setContentText(context.getString(R.string.foreground_service_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
