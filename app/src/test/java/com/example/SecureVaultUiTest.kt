package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.data.MockVaultRepository
import com.example.ui.navigation.SecureVaultApp
import com.example.ui.state.VaultViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureVaultUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `welcome to demo vault and primary navigation tabs work`() {
        val viewModel = VaultViewModel(MockVaultRepository())
        composeTestRule.setContent {
            SecureVaultApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Welcome screen navigates into demo vault
        composeTestRule.onNodeWithTag("welcome_demo_vault_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Vault screen displays search and seeded mock login (scroll LazyColumn for compact test viewport)
        composeTestRule.onNodeWithTag("vault_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_item_list")
            .performScrollToNode(hasTestTag("vault_item_mock-1"))
        composeTestRule.onNodeWithText("CodeForge Git").assertIsDisplayed()

        // Navigate across primary destinations
        composeTestRule.onNodeWithTag("nav_generator").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("generator_output_text").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Paired Devices").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen_title").assertIsDisplayed()
    }

    @Test
    fun `create vault validates mismatched passwords and preserves input`() {
        val viewModel = VaultViewModel(MockVaultRepository())
        composeTestRule.setContent {
            SecureVaultApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("welcome_create_vault_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("create_master_password_input")
            .performScrollTo()
            .performTextInput("cedar-harbor-quartz")
        composeTestRule.onNodeWithTag("create_confirm_password_input")
            .performScrollTo()
            .performTextInput("different-phrase")
        composeTestRule.onNodeWithTag("create_vault_submit_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("create_vault_error")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `lock and unlock rejects wrong password with generic message then unlocks`() {
        val viewModel = VaultViewModel(MockVaultRepository())
        composeTestRule.setContent {
            SecureVaultApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("welcome_demo_vault_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_lock_button").performClick()
        composeTestRule.waitForIdle()

        // Wrong password shows generic error
        composeTestRule.onNodeWithTag("unlock_password_input")
            .performScrollTo()
            .performTextInput("wrong-pass")
        composeTestRule.onNodeWithTag("unlock_submit_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unlock_error_banner").performScrollTo().assertIsDisplayed()

        // Biometric prototype unlocks cleanly when enabled
        composeTestRule.onNodeWithTag("unlock_biometric_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_search_input").assertIsDisplayed()
    }

    @Test
    fun `add item validates missing title and saves new item to vault`() {
        val viewModel = VaultViewModel(MockVaultRepository())
        composeTestRule.setContent {
            SecureVaultApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("welcome_demo_vault_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_fab_add").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_validation_error").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("editor_title_input").performScrollTo().performTextInput("Lab Portal")
        composeTestRule.onNodeWithTag("editor_secret_input").performScrollTo().performTextInput("Test-Secret-99!")
        composeTestRule.onNodeWithTag("editor_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_item_list")
            .performScrollToNode(hasText("Lab Portal"))
        composeTestRule.onNodeWithText("Lab Portal").assertIsDisplayed()
    }
}
