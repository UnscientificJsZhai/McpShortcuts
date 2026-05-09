package com.unscientificjszhai.mcpshortcuts.ui.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.preference.PreferenceManager
import com.unscientificjszhai.mcpshortcuts.ui.settings.SettingsActivity
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 聊天 Activity。
 * 负责展示 AI 聊天界面。如果 AI 功能未开启，则显示引导开启的界面。
 */
@AndroidEntryPoint
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val aiEnabled = prefs.getBoolean("enable_ai_features", false)

        enableEdgeToEdge()
        setContent {
            CustomAppTheme {
                if (aiEnabled) {
                    ChatScreen()
                } else {
                    AiDisabledScreen(onGoToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        finish()
                    }, onBack = {
                        finish()
                    })
                }
            }
        }
    }
}
