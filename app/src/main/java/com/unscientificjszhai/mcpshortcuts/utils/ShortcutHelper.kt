package com.unscientificjszhai.mcpshortcuts.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.preference.PreferenceManager
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.ui.chat.ChatActivity

/**
 * 快捷方式辅助类，用于管理 Android 系统的动态快捷方式。
 */
object ShortcutHelper {

    private const val ID_AI_CHAT = "ai_chat"

    /**
     * 根据设置更新系统的动态快捷方式。
     *
     * @param context 应用程序上下文。
     */
    fun updateShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val aiEnabled = prefs.getBoolean("enable_ai_features", false)

        if (aiEnabled) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            }

            val shortcut = ShortcutInfo.Builder(context, ID_AI_CHAT)
                .setShortLabel(context.getString(R.string.ai_chat))
                .setLongLabel(context.getString(R.string.ai_chat))
                .setIcon(
                    Icon.createWithResource(
                        context,
                        R.mipmap.ic_launcher
                    )
                ) // Maybe use a chat icon if available
                .setIntent(intent)
                .build()

            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        } else {
            shortcutManager.removeDynamicShortcuts(listOf(ID_AI_CHAT))
        }
    }
}
