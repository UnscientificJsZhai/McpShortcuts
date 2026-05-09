package com.unscientificjszhai.mcpshortcuts.ui.main

import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ToolCacheEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientState

/**
 * 带有状态的服务器信息包装类。
 *
 * @property server MCP 服务器实体信息。
 * @property state 当前的连接状态。
 * @property tools 缓存的工具列表。
 */
data class ServerWithTools(
    val server: McpServerEntity,
    val state: McpClientState,
    val tools: List<ToolCacheEntity> = emptyList()
)

/**
 * 工具调用状态密封类。
 */
sealed class ToolCallState {
    /**
     * 空闲状态。
     */
    object Idle : ToolCallState()

    /**
     * 加载中状态。
     */
    object Loading : ToolCallState()

    /**
     * 调用成功状态。
     * @property result 返回的结果内容。
     */
    data class Success(val result: String) : ToolCallState()

    /**
     * 调用失败状态。
     * @property message 错误信息。
     */
    data class Error(val message: String) : ToolCallState()

    /**
     * 静默调用成功状态（仅通过 Toast 提示）。
     * @property message 成功信息。
     */
    data class SilentSuccess(val message: String) : ToolCallState()

    /**
     * 静默调用失败状态（仅通过 Toast 提示）。
     * @property message 错误信息。
     */
    data class SilentError(val message: String) : ToolCallState()
}