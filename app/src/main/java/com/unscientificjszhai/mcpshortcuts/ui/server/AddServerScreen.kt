package com.unscientificjszhai.mcpshortcuts.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    onBack: () -> Unit,
    viewModel: AddServerViewModel = viewModel()
) {
    val serverName by viewModel.serverName.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add MCP Server") },
                // 添加颜色配置以让系统 UI 区域匹配深色/浅色模式
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                label = { Text("Server Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text("Server URL (SSE)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Headers (Optional)", style = MaterialTheme.typography.titleMedium)
            
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
                            label = { Text("Key") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = header.value,
                            onValueChange = { viewModel.updateHeader(index, header.key, it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Value") },
                            singleLine = true
                        )
                        IconButton(onClick = { viewModel.removeHeader(index) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove")
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
                        Text("Add Header")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (testResult != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = testResult!!,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
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
                    Text(if (isTesting) "Testing..." else "Test Connection")
                }
                
                Button(
                    onClick = { viewModel.saveServer(onSuccess = onBack) },
                    enabled = serverName.isNotBlank() && serverUrl.isNotBlank() && !isTesting
                ) {
                    Text("Save")
                }
            }
        }
    }
}
