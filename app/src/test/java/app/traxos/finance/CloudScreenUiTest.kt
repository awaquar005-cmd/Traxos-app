package app.traxos.finance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudScreenUiTest {
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
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun save(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.waitForIdle()
        scenario.onActivity { a ->
            val view = a.window.decorView
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            view.invalidate()
            view.draw(Canvas(bitmap))
            val file = File("build/reports/cloud/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun accountSetupIsHonestAndDriveWorksIndependentlyOfFirebaseConfiguration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("center_action")
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("setting_Sync center").performScrollTo().performClick()
            await("page_cloud_setup")
            compose.onNodeWithText("Import").assertIsDisplayed()
            compose.onNodeWithText("Continue with Google").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Continue with Google").performTouchInput { click() }
            compose.onNodeWithText("Google account setup").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            save(scenario, "account-light")
            compose.onNodeWithText("Authorize").performScrollTo().assertIsEnabled()
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await("page_settings")
        }
    }

    @Test
    fun folderButtonActuallyLaunchesSystemTreePickerWithWriteAndPersistentFlags() {
        runBlocking { app.repository.setTheme("DARK") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("center_action")
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("setting_Backups & recovery").performScrollTo().performClick()
            await("setting_Backup folder")
            save(scenario, "folder-dark")
            compose.onNodeWithTag("setting_Backup folder").performClick()
            scenario.onActivity { activity ->
                val launched = Shadows.shadowOf(activity).nextStartedActivityForResult
                assertNotNull(launched)
                assertEquals(
                    android.content.Intent.ACTION_OPEN_DOCUMENT_TREE,
                    launched.intent.action,
                )
                assertTrue(
                    launched.intent.flags and
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
                )
                assertTrue(
                    launched.intent.flags and
                        android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
                )
            }
        }
    }

    @Test
    fun overflowMoreToolsAcceptsActualTouchAndClosesBeforeNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            await("center_action")
            repeat(2) {
                compose.onNodeWithTag("more_actions").performTouchInput { click() }
                compose.onNodeWithTag("menu_more").performScrollTo().performTouchInput { click() }
                await("page_more")
                compose.onNodeWithTag("traxos_menu").assertDoesNotExist()
                compose.onNodeWithTag("tool_home").performScrollTo().performTouchInput { click() }
                await("page_home")
            }
        }
    }

    @Test
    fun debtsCanBeCreatedAndRepaidThroughCustomDialogsWithoutLedgerPosting() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("center_action")
            compose.onNodeWithTag("more_actions").performClick()
            compose.onNodeWithTag("menu_more").performScrollTo().performClick()
            compose.onNodeWithTag("tool_debts").performScrollTo().performClick()
            await("debt_wallet")
            compose.onNodeWithText("Add").performClick()
            compose.onNodeWithTag("input_debt_person").performTextReplacement("Fixture person")
            compose.onNodeWithTag("input_debt_amount").performTextReplacement("1000")
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(15000) {
                runBlocking { app.database.debts().threads().first().size == 1 }
            }
            compose.onNodeWithText("Fixture person").performScrollTo().performClick()
            compose.onNodeWithText("Repay").performScrollTo().performClick()
            compose.onNodeWithTag("input_debt_payment").performTextReplacement("300")
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(15000) {
                runBlocking {
                    app.database.debts().threads().first().single().outstandingMinor == 70000L
                }
            }
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
            save(scenario, "debts-light")
        }
    }
}
