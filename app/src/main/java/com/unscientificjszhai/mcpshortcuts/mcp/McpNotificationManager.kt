package com.unscientificjszhai.mcpshortcuts.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.unscientificjszhai.mcpshortcuts.R
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 通知管理器。
 * 负责创建通知渠道以及发送与 MCP 相关的系统通知。
 *
 * @property context 应用上下文。
 */
@Singleton
class McpNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        /**
         * 前台服务的通知渠道 ID。
         */
        const val FOREGROUND_SERVICE_CHANNEL_ID = "mcp_foreground_service"

        /**
         * 前台服务的通知 ID。
         */
        const val FOREGROUND_SERVICE_NOTIFICATION_ID = 10001

        /**
         * 根据服务器 ID 生成对应服务器的渠道 ID。
         *
         * @param serverId 服务器 ID。
         * @return 返回渠道 ID 字符串。
         */
        fun getServerChannelId(serverId: Long) = "server_$serverId"
    }

    init {
        // 创建前台服务的专属系统级 Notification Channel
        val channel = NotificationChannel(
            FOREGROUND_SERVICE_CHANNEL_ID,
            context.getString(R.string.channel_foreground_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_foreground_service_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 为特定的 MCP 服务器创建独立的通知渠道。
     *
     * @param serverId 服务器 ID。
     * @param serverName 服务器名称，用于显示在渠道设置中。
     */
    fun createChannelForServer(serverId: Long, serverName: String) {
        val channelId = getServerChannelId(serverId)
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.channel_server_name, serverName),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_server_description, serverName)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 同步清理不再需要的服务器通知渠道。
     *
     * @param serverId 服务器 ID。
     */
    fun deleteChannelForServer(serverId: Long) {
        notificationManager.deleteNotificationChannel(getServerChannelId(serverId))
    }

    /**
     * 将从 MCP 协议层收到的自定义通知转为系统通知。
     *
     * @param serverId 发送通知的服务器 ID。
     * @param serverName 服务器名称。
     * @param notification 收到的 JSON-RPC 通知对象。
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
            notification.params?.toString() ?: context.getString(R.string.notification_no_content)
        } catch (_: Exception) {
            context.getString(R.string.notification_no_content)
        }

        val androidNotification = NotificationCompat.Builder(context, getServerChannelId(serverId))
            .setSmallIcon(R.drawable.ic_notification) // TODO: 最好替换为具有透明背景的矢量图标
            .setContentTitle(context.getString(R.string.notification_server_title, serverName))
            .setContentText(paramsText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // 使用一个基于 serverId + 方法名哈希的 ID 让同一服务器同类方法不互相覆盖太多，或者粗犷点使用当前时间
        val notificationId = (serverId.toString() + notification.method).hashCode()
        notificationManager.notify(notificationId, androidNotification)
    }

    /**
     * 构建用于前台服务持有的持久通知。
     *
     * @return 返回构建好的 [Notification] 实例。
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
