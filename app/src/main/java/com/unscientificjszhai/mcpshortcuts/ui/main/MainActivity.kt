package com.unscientificjszhai.mcpshortcuts.ui.main

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用的主 Activity。
 * 展示固定工具、最近历史以及所有已配置的 MCP 服务器及其工具。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        enableEdgeToEdge()
        setContent {
            CustomAppTheme {
                MainScreen()
            }
        }
    }
}