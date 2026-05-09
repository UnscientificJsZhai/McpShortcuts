package com.unscientificjszhai.mcpshortcuts.ui.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 工具调用历史记录详情页面。
 * 展示调用参数和结果，提供"再次调用"和"保存为固定工具"功能。
 */
@AndroidEntryPoint
class ToolCallHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CustomAppTheme {
                ToolCallHistoryScreen(onBack = { finish() })
            }
        }
    }
}