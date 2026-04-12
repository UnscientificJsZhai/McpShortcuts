package com.unscientificjszhai.mcpshortcuts.ui.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unscientificjszhai.mcpshortcuts.ui.ToolCallState
import com.unscientificjszhai.mcpshortcuts.ui.theme.McpShortcutsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            McpShortcutsTheme {
                ToolCallHistoryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCallHistoryScreen(
    viewModel: ToolCallHistoryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val history by viewModel.history.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val savedAsPinned by viewModel.savedAsPinned.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 保存成功提示
    LaunchedEffect(savedAsPinned) {
        if (savedAsPinned) {
            snackbarHostState.showSnackbar("已保存到固定工具")
            viewModel.resetSavedAsPinned()
        }
    }

    // 控制"保存为固定工具"对话框的显示
    var showSaveDialog by remember { mutableStateOf(false) }
    var labelInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = history?.toolName ?: "调用记录") },
                // 添加颜色配置以匹配系统 UI 区域的深色/浅色模式
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 保存为固定工具按钮
                    IconButton(onClick = {
                        labelInput = history?.toolName ?: ""
                        showSaveDialog = true
                    }) {
                        Icon(Icons.Default.Star, contentDescription = "保存为固定工具")
                    }
                }
            )
        },
        bottomBar = {
            // 再次调用按钮
            Button(
                onClick = { viewModel.callAgain() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = history != null && callState !is ToolCallState.Loading
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("再次调用")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (history == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 工具描述
                history?.toolDescription?.let { desc ->
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium)
                }

                // 调用时间
                history?.calledAt?.let { timestamp ->
                    val dateStr = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date(timestamp))
                    Text(
                        text = "调用时间：$dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 调用参数
                Text("调用参数", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = formatJson(history?.argumentsJson ?: "{}"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // 调用结果
                Text("调用结果", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = history?.resultJson ?: "（无结果）",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    // 保存为固定工具对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存为固定工具") },
            text = {
                Column {
                    Text(
                        "请为该快捷调用输入标签（留空则使用工具名）：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("标签") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveAsPinned(labelInput)
                    showSaveDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 再次调用的结果弹窗
    when (val state = callState) {
        is ToolCallState.Loading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("调用中...") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = { }
            )
        }
        is ToolCallState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearCallState() },
                title = { Text("调用结果") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(state.result)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearCallState() }) {
                        Text("确定")
                    }
                }
            )
        }
        is ToolCallState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearCallState() },
                title = { Text("调用失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearCallState() }) {
                        Text("确定")
                    }
                }
            )
        }
        ToolCallState.Idle -> { /* 空闲状态，无需处理 */ }
    }
}

/**
 * 简单格式化 JSON 字符串，增加缩进使其更易读。
 */
private fun formatJson(json: String): String {
    return try {
        var indent = 0
        val sb = StringBuilder()
        var inString = false
        json.forEach { c ->
            when {
                c == '"' && (sb.isEmpty() || sb.last() != '\\') -> {
                    inString = !inString
                    sb.append(c)
                }
                inString -> sb.append(c)
                c == '{' || c == '[' -> {
                    indent++
                    sb.append(c)
                    sb.append('\n')
                    sb.append("  ".repeat(indent))
                }
                c == '}' || c == ']' -> {
                    indent--
                    sb.append('\n')
                    sb.append("  ".repeat(indent))
                    sb.append(c)
                }
                c == ',' -> {
                    sb.append(c)
                    sb.append('\n')
                    sb.append("  ".repeat(indent))
                }
                c == ':' -> sb.append(": ")
                c != ' ' && c != '\n' && c != '\r' && c != '\t' -> sb.append(c)
            }
        }
        sb.toString()
    } catch (e: Exception) {
        json
    }
}
