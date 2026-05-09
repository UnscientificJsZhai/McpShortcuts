package com.unscientificjszhai.mcpshortcuts.ui.server

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 添加或编辑 MCP 服务器的 Activity。
 */
@AndroidEntryPoint
class AddServerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边显示，使系统 UI（状态栏/导航栏）能正确识别深色/浅色模式
        enableEdgeToEdge()
        val serverId = intent.getLongExtra("serverId", -1L)
        setContent {
            CustomAppTheme {
                AddServerScreen(
                    onBack = { finish() },
                    serverId = serverId
                )
            }
        }
    }
}
