package com.unscientificjszhai.mcpshortcuts.ui.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.unscientificjszhai.mcpshortcuts.data.database.entity.ChatSessionEntity
import com.unscientificjszhai.mcpshortcuts.ui.theme.CustomAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatSessionDrawerItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val session = ChatSessionEntity(
        id = 1L,
        title = "测试会话",
        lastModifiedAt = 123L
    )

    @Test
    fun click_invokesSelectOnly() {
        var clickCount = 0
        var longClickCount = 0

        composeTestRule.setContent {
            CustomAppTheme {
                ChatSessionDrawerItem(
                    session = session,
                    selected = false,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("测试会话").performClick()

        assertEquals(1, clickCount)
        assertEquals(0, longClickCount)
    }

    @Test
    fun longClick_invokesDeleteOnly() {
        var clickCount = 0
        var longClickCount = 0

        composeTestRule.setContent {
            CustomAppTheme {
                ChatSessionDrawerItem(
                    session = session,
                    selected = false,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText("测试会话").performTouchInput {
            longClick()
        }

        assertEquals(0, clickCount)
        assertEquals(1, longClickCount)
    }
}
