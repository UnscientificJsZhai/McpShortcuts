package com.unscientificjszhai.mcpshortcuts.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCallHistoryEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientState
import com.unscientificjszhai.mcpshortcuts.ui.call.CallToolActivity
import com.unscientificjszhai.mcpshortcuts.ui.history.ToolCallHistoryActivity
import com.unscientificjszhai.mcpshortcuts.ui.server.AddServerActivity
import com.unscientificjszhai.mcpshortcuts.ui.theme.McpShortcutsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            McpShortcutsTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainActivityViewModel = viewModel()) {
    val context = LocalContext.current
    val serversWithTools by viewModel.serversWithTools.collectAsState()
    val recentHistory by viewModel.recentHistory.collectAsState()
    val pinnedTools by viewModel.pinnedTools.collectAsState()
    val toolCallState by viewModel.toolCallState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(), topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ), actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, AddServerActivity::class.java))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server))
                    }
                })
        }) { innerPadding ->

        if (serversWithTools.isEmpty() && recentHistory.isEmpty() && pinnedTools.isEmpty()) {
            // 无任何数据时显示空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_servers_hint))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding
            ) {
                // ————— 固定工具 section —————
                if (pinnedTools.isNotEmpty()) {
                    item(key = "header_pinned") {
                        SectionHeader(
                            title = stringResource(R.string.header_pinned),
                            icon = {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    items(pinnedTools, key = { "pinned_${it.id}" }) { pinned ->
                        PinnedToolItem(
                            pinned = pinned,
                            onCallClick = { viewModel.callPinnedTool(pinned) },
                            onDeleteClick = { viewModel.deletePinnedTool(pinned) }
                        )
                    }
                }

                // ————— 最近调用 section —————
                if (recentHistory.isNotEmpty()) {
                    item(key = "header_recent") {
                        SectionHeader(
                            title = stringResource(R.string.header_recent),
                            icon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    items(recentHistory, key = { "history_${it.id}" }) { record ->
                        HistoryItem(
                            history = record,
                            innerPadding = innerPadding,
                            onClick = {
                                val intent = Intent(context, ToolCallHistoryActivity::class.java)
                                    .apply { putExtra("historyId", record.id) }
                                context.startActivity(intent)
                            },
                            onDeleteClick = { viewModel.deleteHistory(record) }
                        )
                    }
                }

                // ————— 所有工具 section —————
                item(key = "header_all") {
                    SectionHeader(
                        title = stringResource(R.string.header_all),
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
                items(serversWithTools, key = { "server_${it.server.id}" }) { item ->
                    ServerItem(
                        item = item,
                        onRetry = { viewModel.retryConnect(item.server) },
                        onUpdateTools = { viewModel.updateTools(item.server.id) },
                        onToolClick = { tool ->
                            val intent = Intent(context, CallToolActivity::class.java).apply {
                                putExtra("serverId", item.server.id)
                                putExtra("toolName", tool.name)
                            }
                            context.startActivity(intent)
                        },
                        onEditClick = {
                            val intent = Intent(context, AddServerActivity::class.java).apply {
                                putExtra("serverId", item.server.id)
                            }
                            context.startActivity(intent)
                        },
                        onDeleteClick = { viewModel.deleteServer(item.server) }
                    )
                }
            }
        }
    }

    // 固定工具调用结果弹窗
    when (val state = toolCallState) {
        is ToolCallState.Loading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.calling)) },
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
                onDismissRequest = { viewModel.clearToolCallState() },
                title = { Text(stringResource(R.string.call_result)) },
                text = { Text(state.result) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearToolCallState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        is ToolCallState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearToolCallState() },
                title = { Text(stringResource(R.string.call_failed)) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearToolCallState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        is ToolCallState.SilentSuccess -> {
            androidx.compose.runtime.LaunchedEffect(state) {
                android.widget.Toast.makeText(context, context.getString(R.string.call_success), android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearToolCallState()
            }
        }

        is ToolCallState.SilentError -> {
            androidx.compose.runtime.LaunchedEffect(state) {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearToolCallState()
            }
        }

        ToolCallState.Idle -> { /* 空闲状态 */
        }    
    }
}

/**
 * 分区标题组件。
 */
@Composable
fun SectionHeader(
    title: String,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 固定工具列表项。
 * 点击直接调用，长按删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedToolItem(
    pinned: PinnedToolEntity,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onCallClick,
                onLongClick = { showDeleteDialog = true }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pinned.label, style = MaterialTheme.typography.titleSmall)
                pinned.toolDescription?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.unpin),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    // 确认删除对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.unpin_confirm_title)) },
            text = { Text(stringResource(R.string.unpin_confirm_message, pinned.label)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteClick()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 历史记录列表项。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    history: ToolCallHistoryEntity,
    innerPadding: PaddingValues? = null,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val timeStr = remember(history.calledAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(history.calledAt))
    }
    var showMenu by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.firstOrNull { it.pressed }
                            if (down != null) {
                                innerPadding?.calculateTopPadding()?.let { top ->
                                    pressOffset = DpOffset(
                                        down.position.x.toDp(),
                                        down.position.y.toDp() - top + 16.dp
                                    )
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = history.toolName, style = MaterialTheme.typography.titleSmall)
                    history.toolDescription?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            offset = pressOffset,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_record)) },
                onClick = {
                    showMenu = false
                    onDeleteClick()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_record),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerItem(
    item: ServerWithTools,
    onRetry: () -> Unit,
    onUpdateTools: () -> Unit,
    onToolClick: (ToolCacheEntity) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = item.server.name, style = MaterialTheme.typography.titleLarge)
                    Text(text = item.server.url, style = MaterialTheme.typography.bodySmall)
                }

                StatusIndicator(state = item.state, onRetry = onRetry)
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    if (item.tools.isEmpty()) {
                        Text(
                            stringResource(R.string.no_cached_tools),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        item.tools.forEach { tool ->
                            ToolItem(
                                tool = tool,
                                onClick = { onToolClick(tool) },
                                enabled = item.state is McpClientState.Connected
                            )
                        }
                    }

                    // 更新提示按钮
                    if (item.state is McpClientState.Connected && item.state.hasUpdate) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onUpdateTools, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.tools_update_found))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onEditClick) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_server)) },
            text = { Text(stringResource(R.string.delete_server_confirm_message, item.server.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteClick()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ToolItem(tool: ToolCacheEntity, onClick: () -> Unit, enabled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp)) {
        Text(
            text = tool.name,
            style = MaterialTheme.typography.titleMedium,
            // 禁用状态使用半透明颜色以适配深色/浅色模式
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        tool.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                // 使用 onSurface 带透明度替代硬编码 Color.Gray
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun StatusIndicator(state: McpClientState, onRetry: () -> Unit) {
    when (state) {
        is McpClientState.Connecting -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        is McpClientState.Connected -> {
            // 使用 tertiary 颜色表示正常/成功状态，深色/浅色模式均有良好对比度
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.connected),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        is McpClientState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 使用语义化 error 颜色，深色/浅色模式自动适配
                Text(text = stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.error),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onRetry() })
            }
        }

        is McpClientState.Disconnected -> {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.connect))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    McpShortcutsTheme {
        // 预览占位
    }
}