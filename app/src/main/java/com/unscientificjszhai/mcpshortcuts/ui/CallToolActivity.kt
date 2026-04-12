package com.unscientificjszhai.mcpshortcuts.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.unscientificjszhai.mcpshortcuts.data.ToolInputSchema
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.ui.theme.McpShortcutsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json

@AndroidEntryPoint
class CallToolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            McpShortcutsTheme {
                CallToolScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallToolScreen(viewModel: CallToolViewModel = viewModel(), onBack: () -> Unit) {
    val tool by viewModel.tool.collectAsState()
    val toolCallState by viewModel.toolCallState.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Parsed", "JSON")

    var jsonInput by remember { mutableStateOf("{}") }
    val parsedArguments = remember { mutableStateOf<Map<String, Any?>>(mapOf()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = tool?.name ?: "Call Tool") },
                // 添加颜色配置以匹配系统 UI（状态栏区域）的深色/浅色模式
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        viewModel.callTool(parsedArguments.value)
                    } else {
                        viewModel.callToolWithJson(jsonInput)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = tool != null && toolCallState !is ToolCallState.Loading
            ) {
                Text("Call Tool")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ParsedTab(
                        tool = tool,
                        onArgumentsChanged = { parsedArguments.value = it }
                    )
                    1 -> JsonTab(
                        jsonInput = jsonInput,
                        onJsonInputChanged = { jsonInput = it }
                    )
                }
            }
        }
    }

    // Tool Call Result Handling
    when (val state = toolCallState) {
        is ToolCallState.Loading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Calling Tool...") },
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
                title = { Text("Result") },
                text = { 
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(state.result)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearToolCallState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ToolCallState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearToolCallState() },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearToolCallState() }) {
                        Text("OK")
                    }
                }
            )
        }
        ToolCallState.Idle -> { /* Do nothing */ }
    }
}

@Composable
fun ParsedTab(tool: ToolCacheEntity?, onArgumentsChanged: (Map<String, Any?>) -> Unit) {
    if (tool == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val json = remember { Json { ignoreUnknownKeys = true } }
    var parseError by remember { mutableStateOf<String?>(null) }
    
    val schema = remember(tool.inputSchema) {
        try {
            if (tool.inputSchema != null) {
                parseError = null
                json.decodeFromString<ToolInputSchema>(tool.inputSchema)
            } else {
                null
            }
        } catch (e: Exception) {
            parseError = "Failed to parse input schema: ${e.message}"
            null
        }
    }
    
    if (parseError != null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            // 使用语义化颜色替代硬编码 Color.Red
            Text(text = parseError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Pre-parse properties for rendering
    data class PropertyInfo(
        val name: String,
        val type: String,
        val description: String?,
        val label: String
    )

    val propertyInfos = remember(schema) {
        schema?.properties?.map { (name, definition) ->
            PropertyInfo(
                name = name,
                type = definition.type ?: "string",
                description = definition.description,
                label = if (schema.required.contains(name)) "$name *" else name
            )
        } ?: emptyList()
    }

    val inputValues = remember(schema) { 
        val map = mutableMapOf<String, String>()
        schema?.properties?.keys?.forEach { name ->
             map[name] = ""
        }
        mutableStateOf(map as Map<String,String>)
    }

    // Function to extract result arguments from input values
    fun getResultArgs(inputs: Map<String, String>): Map<String, Any?> {
        val resultArgs = mutableMapOf<String, Any?>()
        propertyInfos.forEach { info ->
            val value = inputs[info.name] ?: ""
            if (value.isNotEmpty() || (schema?.required?.contains(info.name) == true)) {
                when (info.type) {
                    "number", "integer" -> resultArgs[info.name] = value.toDoubleOrNull()
                    "boolean" -> resultArgs[info.name] = value.toBoolean()
                    else -> resultArgs[info.name] = value
                }
            }
        }
        return resultArgs
    }

    // Update parent whenever input values change
    androidx.compose.runtime.SideEffect {
        onArgumentsChanged(getResultArgs(inputValues.value))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (tool.description != null) {
            Text(text = tool.description, style = MaterialTheme.typography.bodyMedium)
        }

        if (propertyInfos.isEmpty()) {
            Text("This tool takes no parameters.")
        } else {
            propertyInfos.forEach { info ->
                when (info.type) {
                    "boolean" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = inputValues.value[info.name] == "true",
                                onCheckedChange = { checked ->
                                    val newMap = inputValues.value.toMutableMap()
                                    newMap[info.name] = checked.toString()
                                    inputValues.value = newMap
                                }
                            )
                            Text(text = info.label)
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = inputValues.value[info.name] ?: "",
                            onValueChange = { value ->
                                val newMap = inputValues.value.toMutableMap()
                                newMap[info.name] = value
                                inputValues.value = newMap
                            },
                            label = { Text(info.label) },
                            placeholder = { info.description?.let { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JsonTab(jsonInput: String, onJsonInputChanged: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Enter raw JSON for tool parameters:",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = jsonInput,
            onValueChange = onJsonInputChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Arguments (JSON)") },
            placeholder = { Text("{\"param\": \"value\"}") }
        )
    }
}
