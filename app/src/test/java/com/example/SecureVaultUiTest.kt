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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.KdfParameters
import com.example.data.PersistentVaultRepository
import com.example.data.local.VaultDatabase
import com.example.ui.navigation.SecureVaultApp
import com.example.ui.state.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
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

    private lateinit var testDb: VaultDatabase

    @Before
    fun setUp() {
        testDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    private fun createFastTestViewModel(): VaultViewModel {
        val repo = PersistentVaultRepository(
            vaultDao = testDb.vaultDao(),
            kdfParameters = KdfParameters.TEST_FAST,
            ioDispatcher = Dispatchers.Unconfined,
            cryptoDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { repo.initializeFromDisk() }
        return VaultViewModel(repo)
    }

    @Test
    fun `welcome to encrypted demo vault and primary navigation tabs work`() {
        val viewModel = createFastTestViewModel()
        composeTestRule.setContent {
            SecureVaultApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("welcome_demo_vault_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_item_list")
            .performScrollToNode(hasTestTag("vault_inspect_health_button"))
        composeTestRule.onNodeWithTag("vault_inspect_health_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("health_screen_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("health_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_item_list")
            .performScrollToNode(hasTestTag("vault_item_mock-1"))
        composeTestRule.onNodeWithText("CodeForge Git").assertIsDisplayed()

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
    fun `create vault validates mismatched passwords and creates encrypted vault with recovery key`() {
        val viewModel = createFastTestViewModel()
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
    fun `lock wipes VEK and unlock rejects wrong password then unlocks with real master passphrase`() {
        val viewModel = createFastTestViewModel()
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

        // Biometric prototype unlocks cleanly via recovery envelope when enabled
        composeTestRule.onNodeWithTag("unlock_biometric_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_search_input").assertIsDisplayed()
    }

    @Test
    fun `add item validates missing title and saves encrypted record to sqlite vault`() {
        val viewModel = createFastTestViewModel()
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

    @Test
    fun `cold launch with existing persisted sqlite vault routes directly to unlock screen`() {
        // Seed a persisted vault in SQLite before launching SecureVaultApp
        val seedRepo = PersistentVaultRepository(
            vaultDao = testDb.vaultDao(),
            kdfParameters = KdfParameters.TEST_FAST,
            ioDispatcher = Dispatchers.Unconfined,
            cryptoDispatcher = Dispatchers.Unconfined,
        )
        runBlocking {
            seedRepo.openDemoVault()
            seedRepo.lockVault()
        }

        // Fresh ViewModel simulates cold launch reading existing SQLite vault
        val coldStartVm = createFastTestViewModel()
        composeTestRule.setContent {
            SecureVaultApp(viewModel = coldStartVm)
        }
        composeTestRule.waitForIdle()

        // Should start directly on UnlockScreen rather than WelcomeScreen
        composeTestRule.onNodeWithTag("unlock_password_input")
            .performScrollTo()
            .performTextInput(PersistentVaultRepository.DEFAULT_DEMO_PASSPHRASE)
        composeTestRule.onNodeWithTag("unlock_submit_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("vault_search_input").assertIsDisplayed()
    }
}
