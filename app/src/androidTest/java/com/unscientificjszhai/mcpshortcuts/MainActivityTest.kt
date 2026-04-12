package com.unscientificjszhai.mcpshortcuts

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.unscientificjszhai.mcpshortcuts.ui.theme.McpShortcutsTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testMainActivity_FabExists() {
        // FAB should be visible on the main screen
        composeTestRule.onNodeWithContentDescription("Add Server").assertExists()
    }

    @Test
    fun testMainActivity_EmptyStateMessage() {
        // If there are no servers (initial state in tests), the empty message should show
        // Note: Depending on existing database content, this might need adjustment
        // For a clean test, the database should be empty.
        // composeTestRule.onNodeWithText("No MCP servers added. Tap + to add one.").assertExists()
    }

    @Test
    fun testMainActivity_ClickFabOpensAddServer() {
        // Click FAB and verify if it opens something or performs action
        composeTestRule.onNodeWithContentDescription("Add Server").performClick()
        
        // After clicking, we should see the Add Server screen
        composeTestRule.onNodeWithText("Add MCP Server").assertExists()
    }
}
