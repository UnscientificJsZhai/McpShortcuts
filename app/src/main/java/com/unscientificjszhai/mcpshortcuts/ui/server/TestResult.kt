package com.unscientificjszhai.mcpshortcuts.ui.server

/**
 * 服务器连接测试结果密封类。
 */
sealed class TestResult {
    /**
     * 正在连接中。
     */
    object Connecting : TestResult()

    /**
     * 连接并获取工具列表成功。
     * @property toolNames 获取到的工具名称列表（逗号分隔）。
     */
    data class Success(val toolNames: String) : TestResult()

    /**
     * 连接超时。
     */
    object Timeout : TestResult()

    /**
     * 发生错误。
     * @property message 错误信息。
     */
    data class Error(val message: String?) : TestResult()

    /**
     * URL 为空。
     */
    object UrlEmpty : TestResult()
}