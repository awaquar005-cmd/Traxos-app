package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
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
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnslopComparisonUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun clean() {
        app.database.close()
        app.deleteDatabase("traxos.db")
        app.deleteSharedPreferences("traxos.design.experiment")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun switchRestoresPreviousStylePersistsAndNeverChangesFinanceOrEyeBehavior() {
        runBlocking {
            val repo = app.repository
            val day = LocalDate.now().toEpochDay()
            val category = repo.dao.categories().first().first { it.type == "EXPENSE" }.id
            repo.saveAccount(
                UUID.randomUUID().toString(),
                "bank",
                "Demo bank",
                "BANK",
                "INR",
                10000,
                openingDate = day - 1,
            )
            repo.saveRecord(
                UUID.randomUUID().toString(),
                "entry",
                "bank",
                category,
                "EXPENSE",
                1000,
                day,
                "Demo entry",
            )
            repo.setActivitySort("OLDEST")
        }
        val operations = runBlocking { app.database.ledger().operationCount() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitText("Total balance")
            compose.onNodeWithContentDescription("Hide financial amounts").assertExists()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance & Navigation").performScrollTo().performClick()
            compose
                .onNodeWithContentDescription("Experimental interface")
                .performScrollTo()
                .performClick()
            compose.waitUntil(5000) { !app.designExperiment.enabled.value }
            compose.onNodeWithTag("nav_home").performClick()
            awaitText("TOTAL BALANCE")
            scenario.recreate()
            awaitText("TOTAL BALANCE")
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance & Navigation").performScrollTo().performClick()
            compose
                .onNodeWithContentDescription("Experimental interface")
                .performScrollTo()
                .performClick()
            compose.waitUntil(5000) { app.designExperiment.enabled.value }
            compose.onNodeWithTag("nav_home").performClick()
            awaitText("Total balance")
            compose.onNodeWithContentDescription("Hide financial amounts").performClick()
            compose.waitUntil(10000) {
                runBlocking { app.database.ledger().preferences().first()!!.balancesHidden }
            }
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithContentDescription(
                        "Show financial amounts",
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose
                .onNodeWithContentDescription("Show financial amounts", useUnmergedTree = true)
                .assertIsDisplayed()
            assertEquals(1L, runBlocking { app.database.ledger().recordCount() })
            assertEquals(
                9000L,
                runBlocking { app.database.ledger().accounts().first().single().balanceMinor },
            )
            assertEquals(operations, runBlocking { app.database.ledger().operationCount() })
            assertEquals(
                "OLDEST",
                runBlocking { app.database.ledger().preferences().first()!!.activitySort },
            )
        }
    }
}
