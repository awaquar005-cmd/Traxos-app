package app.traxos.finance

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
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
class RegionalSettingsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
        app.getSharedPreferences("display_options", 0).edit().clear().commit()
    }

    private fun await(tag: String) {
        compose.waitUntil(15000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun settings(section: String) {
        await("center_action")
        compose.onNodeWithTag("nav_settings").performClick()
        await("setting_$section")
        compose.onNodeWithTag("setting_$section").performScrollTo().performClick()
    }

    private fun save(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.waitForIdle()
        scenario.onActivity { a ->
            val v = a.window.decorView
            v.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val image = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(image))
            v.invalidate()
            v.draw(Canvas(image))
            val file = File("build/reports/regional/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    @Test
    fun manualRateAndFormattingControlsPersistWithoutPostingRecords() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settings("Currency & region")
            await("regional_settings")
            compose.onNodeWithText("Add").performScrollTo().performClick()
            await("input_manual_rate")
            compose.onNodeWithTag("input_manual_rate").performTextReplacement("83.125")
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(15000) { runBlocking { app.regional.dao.rates().first().size == 1 } }
            compose
                .onNodeWithTag("input_conversion_amount")
                .performScrollTo()
                .performTextReplacement("1")
            compose.onNodeWithText("Convert").performScrollTo().performClick()
            compose.onNodeWithText("Estimate", substring = true).assertExists()
            compose.onNodeWithTag("selector_Date format").performScrollTo().performClick()
            compose.onNodeWithText("yyyy-MM-dd").performClick()
            compose.waitUntil(15000) { app.deviceOptions.state.value.date == "yyyy-MM-dd" }
            scenario.recreate()
            await("regional_settings")
            assertEquals("yyyy-MM-dd", app.deviceOptions.state.value.date)
            assertEquals(0L, runBlocking { app.database.ledger().recordCount() })
            save(scenario, "regional-light")
        }
    }

    @Test
    fun accessibilityOptionsApplyAndPersistInDarkTheme() {
        runBlocking { app.repository.setTheme("DARK") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settings("Accessibility")
            await("accessibility_settings")
            compose.onNodeWithTag("selector_Additional text size").performClick()
            compose.onNodeWithText("+20%").performClick()
            compose.waitUntil(15000) { app.deviceOptions.state.value.textScale == "1.2" }
            compose
                .onNodeWithContentDescription("High-contrast text")
                .performScrollTo()
                .performClick()
            compose.waitUntil(15000) { app.deviceOptions.state.value.contrast }
            compose.onNodeWithContentDescription("Haptic feedback").performScrollTo().performClick()
            compose.waitUntil(15000) { !app.deviceOptions.state.value.haptics }
            save(scenario, "accessibility-dark")
            scenario.recreate()
            await("accessibility_settings")
            assertTrue(app.deviceOptions.state.value.contrast)
            assertFalse(app.deviceOptions.state.value.haptics)
        }
    }

    @Test
    fun exportsRequireDisclosureAndLaunchRealDocumentPicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settings("Exports")
            await("export_screen")
            compose.onNodeWithTag("export_start").performScrollTo().performClick()
            compose.onNodeWithText("Export unencrypted data?").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("export_start").performScrollTo().performClick()
            compose.onNodeWithTag("dialog_confirm").performClick()
            var intent: Intent? = null
            scenario.onActivity { intent = Shadows.shadowOf(it).nextStartedActivity }
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
            assertEquals("application/pdf", intent?.type)
            assertEquals(0L, runBlocking { app.database.ledger().recordCount() })
        }
    }

    @Test
    fun helpSearchPrivacyAndReentryAreRealRoutes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settings("Help & support")
            await("help_screen")
            compose.onNodeWithTag("input_help_search").performTextReplacement("amounts and manual")
            compose.onNodeWithText("Amounts and manual conversion").performScrollTo().performClick()
            compose.onNodeWithText("Rates").performScrollTo().performClick()
            await("regional_settings")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await("help_screen")
            compose.onNodeWithTag("input_help_search").performTextClearance()
            compose.onNodeWithTag("setting_Privacy & permissions").performScrollTo().performClick()
            await("privacy_screen")
            compose
                .onNodeWithText("Your financial records work offline.", substring = true)
                .assertExists()
            save(scenario, "privacy-light")
            compose.onNodeWithTag("nav_home").performClick()
            await("page_home")
            compose.onNodeWithTag("nav_settings").performClick()
            await("setting_Profile")
            compose.onNodeWithTag("help_screen").assertDoesNotExist()
        }
    }
}
