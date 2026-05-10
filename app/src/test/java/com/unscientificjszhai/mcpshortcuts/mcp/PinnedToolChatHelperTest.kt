package com.unscientificjszhai.mcpshortcuts.mcp

import com.unscientificjszhai.mcpshortcuts.data.database.entity.PinnedToolEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * [PinnedToolChatHelper] 的单元测试。
 */
class PinnedToolChatHelperTest {

    private val helper = PinnedToolChatHelper()

    @Test
    fun `getPinnedToolFunctionDefinition should return correct tool definition`() {
        val definition = helper.getPinnedToolFunctionDefinition()

        assertTrue(definition.isFunction())
        val function = definition.asFunction().function()
        assertEquals(PinnedToolChatHelper.PINNED_TOOL_FUNCTION_NAME, function.name())
        assertTrue(function.description().isPresent)

        val parameters = function.parameters()
        assertTrue(parameters.isPresent)
    }

    @Test
    fun `buildPinnedToolsPrompt should return null if list is empty`() {
        assertNull(helper.buildPinnedToolsPrompt(emptyList()))
    }

    @Test
    fun `buildPinnedToolsPrompt should format list correctly`() {
        val pinnedTools = listOf(
            PinnedToolEntity(
                id = 1,
                serverId = 10,
                toolName = "test_tool",
                toolDescription = null,
                argumentsJson = "{}",
                label = "Test Label",
                pinnedAt = 1000L,
                ignoreResult = false
            ),
            PinnedToolEntity(
                id = 2,
                serverId = 11,
                toolName = "another_tool",
                toolDescription = null,
                argumentsJson = "{}",
                label = "", // 空标签，应回退到工具名
                pinnedAt = 2000L,
                ignoreResult = false
            )
        )

        val prompt = helper.buildPinnedToolsPrompt(pinnedTools)
        assertNotNull(prompt)
        assertTrue(prompt!!.contains("- id: 1, title: Test Label"))
        assertTrue(prompt.contains("- id: 2, title: another_tool"))
        assertTrue(prompt.contains(PinnedToolChatHelper.PINNED_TOOL_FUNCTION_NAME))
    }

    @Test
    fun `parsePinnedToolId should return long id from valid JSON`() {
        val json = """{"id": 123}"""
        assertEquals(123L, helper.parsePinnedToolId(json))
    }

    @Test
    fun `parsePinnedToolId should return null for invalid JSON or missing id`() {
        assertNull(helper.parsePinnedToolId("""{"id": "not_a_number"}"""))
        assertNull(helper.parsePinnedToolId("""{"other": 123}"""))
        assertNull(helper.parsePinnedToolId("invalid_json"))
    }
}
