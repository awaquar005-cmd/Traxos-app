package app.traxos.finance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalDeliveryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    private fun await(tag: String) {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun save(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.waitForIdle()
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            view.invalidate()
            view.draw(Canvas(image))
            val file = File("build/reports/local-ui/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            image.recycle()
        }
    }

    @Test
    fun localBackupRouteHasRealFolderEntryAndNeverClaimsCloudConnection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("center_action")
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Backup").performClick()
            await("setting_Backup folder")
            compose.onNodeWithText("Your folder. Your copy.").assertExists()
            compose
                .onNodeWithTag("input_Backup password")
                .performScrollTo()
                .performTextInput("Fixture password 123")
            compose
                .onNodeWithTag("input_Confirm password")
                .performScrollTo()
                .performTextInput("Different password")
            compose.onNodeWithText("Passwords do not match").assertExists()
            compose.onNodeWithText("Backup", useUnmergedTree = false).assertIsNotEnabled()
            save(scenario, "backup-light")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await("page_home")
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("setting_Backups & recovery").performScrollTo().performClick()
            await("page_backup")
            compose.onNodeWithTag("nav_settings").performClick()
            await("page_settings")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await("page_home")
        }
    }

    @Test
    fun transferFromEntryKeepsPairedAccountingAndUndoWithoutSavingExpenseDraft() {
        runBlocking {
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "from",
                "First bank",
                "BANK",
                "INR",
                100000,
            )
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "to",
                "Pocket cash",
                "CASH",
                "INR",
                0,
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            await("center_action")
            compose.onNodeWithTag("center_action").performClick()
            await("input_amount")
            compose.onNodeWithTag("input_amount").performTextInput("99")
            compose.onNodeWithTag("choice_Transfer").performScrollTo().performClick()
            compose.onNodeWithTag("selector_From account").performScrollTo().performClick()
            compose.onNodeWithTag("choice_First bank · INR").performClick()
            compose.onNodeWithTag("selector_To account").performScrollTo().performClick()
            compose.onNodeWithTag("choice_Pocket cash · INR").performClick()
            compose
                .onNode(hasTestTag("input_amount") and hasAnyAncestor(hasTestTag("traxos_sheet")))
                .performScrollTo()
                .performTextInput("50")
            compose.onNodeWithTag("transfer_save").performClick()
            await("page_home")
            compose.waitUntil(10000) { runBlocking { app.repository.dao.recordCount() == 2L } }
            assertEquals(
                95000L,
                runBlocking {
                    app.repository.dao
                        .accounts()
                        .first()
                        .first { it.account.id == "from" }
                        .balanceMinor
                },
            )
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntil(10000) {
                runBlocking {
                    app.repository.dao
                        .accounts()
                        .first()
                        .first { it.account.id == "from" }
                        .balanceMinor == 100000L
                }
            }
            assertEquals(
                0L,
                runBlocking {
                    app.repository.dao
                        .accounts()
                        .first()
                        .first { it.account.id == "to" }
                        .balanceMinor
                },
            )
        }
    }

    @Test
    fun activityAndSettingsLayoutsKeepWorkingDataAndCompactActions() {
        runBlocking {
            val day = LocalDate.now().toEpochDay()
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "main",
                "Main bank",
                "BANK",
                "INR",
                100000,
                openingDate = day,
            )
            app.repository.saveRecord(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "main",
                "food",
                "EXPENSE",
                42000,
                day,
                "Lunch with friends",
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("center_action")
            compose.onNodeWithTag("nav_transactions").performClick()
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Lunch with friends").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Every transaction.").assertIsDisplayed()
            compose.onNodeWithTag("activity_smart_add").assertDoesNotExist()
            save(scenario, "activity-light")
            compose.onNodeWithTag("nav_settings").performClick()
            await("setting_Backups & recovery")
            save(scenario, "settings-light")
        }
    }
}
