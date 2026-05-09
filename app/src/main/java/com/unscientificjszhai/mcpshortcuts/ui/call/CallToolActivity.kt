package com.unscientificjszhai.mcpshortcuts.ui.call

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unscientificjszhai.mcpshortcuts.R
import com.unscientificjszhai.mcpshortcuts.data.ToolInputSchema
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.ui.main.ToolCallState
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json

/**
 * 调用工具的 Activity。
 * 该 Activity 负责承载工具调用的界面。
 */
@AndroidEntryPoint
class CallToolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CustomAppTheme {
                CallToolScreen(onBack = { finish() })
            }
        }
    }
}

/**
 * 工具调用屏幕的 Compose 实现。
 *
 * @param viewModel 用于管理工具调用逻辑的 ViewModel。
 * @param onBack 点击返回按钮时的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallToolScreen(viewModel: CallToolViewModel = viewModel(), onBack: () -> Unit) {
    val tool by viewModel.tool.collectAsState()
    val toolCallState by viewModel.toolCallState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.tab_parsed), stringResource(R.string.tab_json))

    var jsonInput by remember { mutableStateOf("{}") }
    val parsedArguments = remember { mutableStateOf<Map<String, Any?>>(mapOf()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = tool?.name ?: stringResource(R.string.call_tool)) },
                // 添加颜色配置以匹配系统 UI（状态栏区域）的深色/浅色模式
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
                Text(stringResource(R.string.call_tool))
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
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(state.result)
                    }
                },
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
                title = { Text(stringResource(R.string.error)) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearToolCallState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        is ToolCallState.SilentSuccess, is ToolCallState.SilentError -> {
            viewModel.clearToolCallState()
        }

        ToolCallState.Idle -> { /* Do nothing */
        }
    }
}

/**
 * 解析参数标签页。
 * 根据工具的输入 Schema 生成动态表单供用户填写。
 *
 * @param tool 缓存的工具实体信息。
 * @param onArgumentsChanged 当用户输入的参数发生变化时的回调。
 */
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
        } catch (_: Exception) {
            parseError = "placeholder" // Set later
            null
        }
    }

    if (schema == null && tool.inputSchema != null && parseError == "placeholder") {
        parseError = stringResource(R.string.parse_error, stringResource(R.string.invalid_schema))
    }

    if (parseError != null) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = parseError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    /**
     * 属性信息包装类。
     *
     * @property name 属性在 JSON 中的键名。
     * @property type 属性的类型。
     * @property description 属性的描述。
     * @property label 界面上显示的标签文本。
     */
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
                type = definition.typeString ?: "string",
                description = definition.descriptionString,
                label = if (schema.required.contains(name)) "$name *" else name
            )
        } ?: emptyList()
    }

    val inputValues = remember(schema) {
        val map = mutableMapOf<String, String>()
        schema?.properties?.keys?.forEach { name ->
            map[name] = ""
        }
        mutableStateOf(map as Map<String, String>)
    }

    /**
     * 从输入值中提取结果参数。
     *
     * @param inputs 当前输入的字符串 Map。
     * @return 解析后的参数 Map。
     */
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
    SideEffect {
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
            Text(stringResource(R.string.no_parameters))
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

/**
 * JSON 原始输入标签页。
 * 提供一个文本域供用户直接编写 JSON 格式的调用参数。
 *
 * @param jsonInput 当前的 JSON 输入字符串。
 * @param onJsonInputChanged 当 JSON 输入内容发生变化时的回调。
 */
@Composable
fun JsonTab(jsonInput: String, onJsonInputChanged: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.json_input_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = jsonInput,
            onValueChange = onJsonInputChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text(stringResource(R.string.arguments_json)) },
            placeholder = { Text(stringResource(R.string.json_placeholder)) }
        )
    }
}
