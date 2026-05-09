package com.unscientificjszhai.mcpshortcuts.ui.server

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class AddServerScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testAddServerScreen_InputAndSaveEnabled() {
        composeTestRule.setContent {
            CustomAppTheme {
                AddServerScreen(onBack = {})
            }
        }

        // Verify Save button is disabled initially
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()

        // Enter server name
        composeTestRule.onNodeWithText("Server Name").performTextInput("Test Server")
        
        // Verify Save button is still disabled
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()

        // Enter server URL
        composeTestRule.onNodeWithText("Server URL (SSE)").performTextInput("http://localhost:8080/sse")

        // Verify Save button is now enabled
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun testAddServerScreen_AddHeader() {
        composeTestRule.setContent {
            CustomAppTheme {
                AddServerScreen(onBack = {})
            }
        }

        // Click Add Header
        composeTestRule.onNodeWithText("Add Header").performClick()

        // Verify header input fields are shown
        composeTestRule.onAllNodesWithText("Key").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Value").assertCountEquals(1)

        // Enter header key and value
        composeTestRule.onNodeWithText("Key").performTextInput("X-Custom")
        composeTestRule.onNodeWithText("Value").performTextInput("Custom-Value")

        // Verify they are entered correctly
        composeTestRule.onNodeWithText("X-Custom").assertExists()
        composeTestRule.onNodeWithText("Custom-Value").assertExists()
    }
}
