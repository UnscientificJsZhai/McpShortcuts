package com.unscientificjszhai.mcpshortcuts.ui.chat


/**
 * 工具调用审批模式。
 */
enum class ApprovalMode {
    /**
     * 手动模式。AI 请求调用工具时需要用户手动点击执行。
     */
    MANUAL,

    /**
     * 自动模式。AI 请求调用工具时将自动执行，无需用户确认。
     */
    AUTOMATIC
}