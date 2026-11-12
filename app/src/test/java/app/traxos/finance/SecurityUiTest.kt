package app.traxos.finance

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.security.*
import app.traxos.finance.ui.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecurityUiTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private lateinit var dir: File

    @After
    fun cleanup() {
        models.clear()
        dir.deleteRecursively()
    }

    private fun model(enabled: Boolean): LockViewModel {
        dir =
            File(
                    ApplicationProvider.getApplicationContext<Application>().noBackupFilesDir,
                    "gate-${UUID.randomUUID()}",
                )
                .apply { mkdirs() }
        val store = LockStore(dir, TestLockCipher())
        if (enabled) runBlocking { store.setPin("", "123456", "123456") }
        return LockViewModel(store).also { models.put("gate", it) }
    }

    @Test
    fun lockHidesExistingDialogAndBlocksUnderlyingTouchesWithoutDiscardingThePage() {
        val vm = model(true)
        var count = 0
        compose.setContent {
            TraxosTheme("LIGHT") {
                SecurityGate(vm) {
                    Surface(onClick = { count++ }, modifier = Modifier.fillMaxSize()) {
                        Text("PRIVATE LEDGER")
                    }
                    TraxosDialog("Sensitive action", "Private details", "Commit", { count++ }, {})
                }
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("app_lock").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PRIVATE LEDGER").assertDoesNotExist()
        compose.onNodeWithTag("input_unlock_pin").performTextInput("123456")
        compose.onNodeWithTag("unlock_submit").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { vm.background() }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_dialog").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("app_lock").assertIsDisplayed()
        compose.onNodeWithText("PRIVATE LEDGER").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(Offset(5f, 5f)) }
        assertEquals(0, count)
        compose.runOnIdle { vm.foreground() }
        compose.onNodeWithTag("app_lock").assertIsDisplayed()
    }

    @Test
    fun brandedPinSetupMismatchAndConfirmationWorkOffline() {
        val vm = model(false)
        compose.setContent { TraxosTheme("DARK") { SecuritySettingsScreen(vm) } }
        compose.onNodeWithText("Set up PIN").performClick()
        compose.onNodeWithTag("input_new_pin").performTextInput("123456")
        compose.onNodeWithTag("input_confirm_pin").performTextInput("654321")
        compose.onNodeWithText("Confirm").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("The PINs do not match").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("input_confirm_pin").performTextReplacement("123456")
        compose.onNodeWithText("Confirm").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Change PIN").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { vm.store.status().enabled })
        compose.onNodeWithText("Disable PIN lock").performScrollTo().performClick()
        compose.onNodeWithTag("input_current_pin").performTextInput("000000")
        compose.onNodeWithText("Verify PIN and disable").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Incorrect current PIN").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { vm.store.status().enabled })
    }
}
