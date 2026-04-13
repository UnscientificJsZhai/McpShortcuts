package com.unscientificjszhai.mcpshortcuts.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unscientificjszhai.mcpshortcuts.ui.main.ServerItem
import com.unscientificjszhai.mcpshortcuts.data.database.entity.McpServerEntity
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientState
import com.unscientificjszhai.mcpshortcuts.ui.main.ServerWithTools
import com.unscientificjszhai.mcpshortcuts.ui.theme.McpShortcutsTheme
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import org.junit.Rule
import org.junit.Test

class ServerItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dummyClient = Client(Implementation("test", "1.0"))

    @Test
    fun serverItem_showsUpdateButton_whenUpdateAvailable() {
        val server = McpServerEntity(id = 1, name = "Test Server", url = "http://test.com")
        val state = McpClientState.Connected(dummyClient, hasUpdate = true)
        val item = ServerWithTools(server, state, emptyList())
        
        var updateClicked = false

        composeTestRule.setContent {
            McpShortcutsTheme {
                ServerItem(
                    item = item,
                    onRetry = {},
                    onUpdateTools = { updateClicked = true },
                    onToolClick = {},
                    onDeleteClick = {}
                )
            }
        }

        // Expand the card
        composeTestRule.onNodeWithText("Test Server").performClick()

        // Verify update button is visible
        composeTestRule.onNodeWithText("发现 Tools 更新，点击刷新").assertIsDisplayed()

        // Click update button
        composeTestRule.onNodeWithText("发现 Tools 更新，点击刷新").performClick()

        // Verify callback was called
        assert(updateClicked)
    }

    @Test
    fun serverItem_hidesUpdateButton_whenNoUpdate() {
        val server = McpServerEntity(id = 1, name = "Test Server", url = "http://test.com")
        val state = McpClientState.Connected(dummyClient, hasUpdate = false)
        val item = ServerWithTools(server, state, emptyList())

        composeTestRule.setContent {
            McpShortcutsTheme {
                ServerItem(
                    item = item,
                    onRetry = {},
                    onUpdateTools = {},
                    onToolClick = {},
                    onDeleteClick = {}
                )
            }
        }

        // Expand the card
        composeTestRule.onNodeWithText("Test Server").performClick()

        // Verify update button is NOT visible
        composeTestRule.onNodeWithText("发现 Tools 更新，点击刷新").assertDoesNotExist()
    }
}
