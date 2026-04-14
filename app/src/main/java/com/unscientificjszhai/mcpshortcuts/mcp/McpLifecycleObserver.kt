package com.unscientificjszhai.mcpshortcuts.mcp

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.unscientificjszhai.mcpshortcuts.data.database.dao.McpServerDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpLifecycleObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectionManager: McpConnectionManager,
    private val serverDao: McpServerDao
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // 应用进入前台：终止用于保活的前台服务
        McpForegroundService.stop(context)
        // 恢复所有因进入后台被暂停的非 keepAlive 服务器连接
        connectionManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // 应用进入后台：检查是否有必须保活的服务器
        CoroutineScope(Dispatchers.IO).launch {
            Log.e("McpLifecycleObserver", "onStop: ", )
            try {
                val servers = serverDao.getAllServers().first()
                val hasKeepAlive = servers.any { it.keepAlive }
                if (hasKeepAlive) {
                    McpForegroundService.start(context)
                }
                
                // 通知连接管理器主动断开 keepAlive = false 的服务器释放资源
                connectionManager.onAppBackgrounded(servers)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
