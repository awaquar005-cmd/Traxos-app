package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.gifts.GiftInbox
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
class GiftNavigationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        GiftInbox.pending.value = null
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun optionalGiftSlotMoreAndFallbackSettingsSurviveRecreation() {
        val permissions =
            app.packageManager
                .getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toList()
        listOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_MEDIA_IMAGES,
            )
            .forEach { assertFalse(it in permissions) }
        assertTrue(android.Manifest.permission.INTERNET in permissions)
        assertEquals(BuildConfig.SMS_ENABLED, "android.permission.RECEIVE_SMS" in permissions)
        runBlocking {
            app.repository.setNavigationSlots(listOf("gifts", "home", "accounts", "more"))
            app.repository.setCenterAction("ACCOUNTS")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("nav_gifts").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav_gifts").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("gift_wallet").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav_gifts").assertIsSelected()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("page_settings").assertExists()
            scenario.recreate()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("nav_gifts").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav_more").performClick()
            compose.onNodeWithText("Your Traxos tools").assertExists()
            compose
                .onNode(hasText("Gift Cards") and hasAnyAncestor(hasTestTag("page_more")))
                .performClick()
            compose.onNodeWithTag("page_gifts").assertExists()
            assertEquals(0L, runBlocking { app.database.ledger().recordCount() })
            assertTrue(runBlocking { app.database.gifts().all().isEmpty() })
        }
    }
}
