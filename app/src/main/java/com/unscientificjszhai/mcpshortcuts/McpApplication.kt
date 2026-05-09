package com.unscientificjszhai.mcpshortcuts

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import com.unscientificjszhai.mcpshortcuts.mcp.McpLifecycleObserver
import com.unscientificjszhai.mcpshortcuts.utils.ShortcutHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用程序类，负责初始化 Hilt、注册生命周期观察者以及处理全局配置变更。
 */
@HiltAndroidApp
class McpApplication : Application() {

    /**
     * MCP 生命周期观察者，用于管理 MCP 服务的连接与生命周期。
     */
    @Inject
    lateinit var lifecycleObserver: McpLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        ShortcutHelper.updateShortcuts(this)

        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "enable_ai_features") {
                ShortcutHelper.updateShortcuts(this)
            }
        }
}
