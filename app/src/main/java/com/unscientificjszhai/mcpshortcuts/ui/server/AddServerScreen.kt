package com.unscientificjszhai.mcpshortcuts.ui.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unscientificjszhai.mcpshortcuts.R

// ... (other imports)

/**
 * 添加或编辑服务器屏幕的 Compose 实现。
 *
 * @param onBack 点击返回按钮时的回调。
 * @param serverId 如果是编辑模式，则为服务器 ID；如果是添加模式，则为 -1。
 * @param viewModel 用于管理添加服务器逻辑的 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    onBack: () -> Unit,
    serverId: Long = -1L,
    viewModel: AddServerViewModel = viewModel()
) {
    LaunchedEffect(serverId) {
        if (serverId != -1L) {
            viewModel.initData(serverId)
        }
    }

    val serverName by viewModel.serverName.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val keepAlive by viewModel.keepAlive.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    val topBarTitle =
        if (serverId != -1L) stringResource(R.string.edit_mcp_server) else stringResource(R.string.add_mcp_server)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                // 添加颜色配置以让系统 UI 区域匹配深色/浅色模式
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = serverName,
                onValueChange = { viewModel.updateServerName(it) },
                label = { Text(stringResource(R.string.server_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text(stringResource(R.string.server_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.headers_optional),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(headers) { index, header ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = header.key,
                            onValueChange = { viewModel.updateHeader(index, it, header.value) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.key)) },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = header.value,
                            onValueChange = { viewModel.updateHeader(index, header.key, it) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.value)) },
                            singleLine = true
                        )
                        IconButton(onClick = { viewModel.removeHeader(index) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove)
                            )
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = { viewModel.addHeader() },
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_header))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (testResult != null) {
                        val resultText = when (val res = testResult!!) {
                            is TestResult.Connecting -> stringResource(R.string.connecting)
                            is TestResult.UrlEmpty -> stringResource(R.string.url_empty_error)
                            is TestResult.Success -> stringResource(
                                R.string.test_success_found_tools,
                                res.toolNames
                            )

                            is TestResult.Timeout -> stringResource(R.string.connection_timeout)
                            is TestResult.Error -> stringResource(
                                R.string.error_with_message,
                                res.message ?: ""
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = resultText,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateKeepAlive(!keepAlive) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = keepAlive,
                    onCheckedChange = { viewModel.updateKeepAlive(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.keep_alive))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = !isTesting
                ) {
                    Text(if (isTesting) stringResource(R.string.testing) else stringResource(R.string.test_connection))
                }

                Button(
                    onClick = { viewModel.saveServer(onSuccess = onBack) },
                    enabled = serverName.isNotBlank() && serverUrl.isNotBlank() && !isTesting
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
