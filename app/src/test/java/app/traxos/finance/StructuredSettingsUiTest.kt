package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StructuredSettingsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun compactCategoriesProfileAndCurrencyPersistAcrossActivityRestart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) {
                compose.onAllNodes(hasTestTag("center_action") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav_settings").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("setting_Profile").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("setting_Profile").performScrollTo().performClick()
            compose.onNodeWithTag("input_profile_name").performTextInput("Local name")
            compose.onNodeWithText("Save display name").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodes(hasText("Save display name") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty() &&
                    runBlocking {
                        app.repository.dao.preferences().first()!!.displayName == "Local name"
                    }
            }
            compose.onNodeWithTag("setting_Account preferences").performScrollTo().performClick()
            compose.onNodeWithTag("selector_Default currency").performClick()
            compose.onNodeWithTag("choice_USD").performClick()
            compose.waitUntil(10000) {
                runBlocking { app.repository.dao.preferences().first()!!.defaultCurrency == "USD" }
            }
            scenario.recreate()
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithTag("selector_Default currency")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("USD").assertExists()
            assertEquals(
                "Local name",
                runBlocking { app.repository.dao.preferences().first()!!.displayName },
            )
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
        }
    }

    @Test
    fun defaultAccountAndCategoryAreAppliedWithoutPostingARecord() {
        runBlocking {
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "a",
                "First bank",
                "BANK",
                "INR",
                10000,
            )
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "b",
                "Chosen bank",
                "BANK",
                "INR",
                20000,
            )
            app.repository.setDefaults("b", "food")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasTestTag("center_action") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("center_action").performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodes(
                        hasTestTag("selector_Account") and hasText("Chosen bank", substring = true)
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose
                .onNodeWithTag("selector_Category")
                .performScrollTo()
                .assert(hasText("Food & drink", substring = true))
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
        }
    }
}
