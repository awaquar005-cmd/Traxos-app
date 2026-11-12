package app.traxos.finance

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.traxos.finance.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandedControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dialogCancelAndConfirmAreBrandedAndClose() {
        var open by mutableStateOf(true)
        var confirms = 0
        compose.setContent {
            TraxosTheme("DARK") {
                if (open)
                    TraxosDialog(
                        "Remove this record?",
                        "The account balance changes and history is retained.",
                        "Remove record",
                        {
                            confirms++
                            open = false
                        },
                        { open = false },
                        destructive = true,
                    )
            }
        }
        compose.onNodeWithTag("traxos_dialog").assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithTag("traxos_dialog").assertDoesNotExist()
        assertEquals(0, confirms)
        compose.runOnIdle { open = true }
        compose.onNodeWithText("Remove record").performScrollTo().performClick()
        assertEquals(1, confirms)
        compose.onNodeWithTag("traxos_dialog").assertDoesNotExist()
    }

    @Test
    fun selectorsUseCustomPopupAndSheetAndDismissAfterSelection() {
        var currency by mutableStateOf("INR")
        var account by mutableStateOf("a")
        compose.setContent {
            TraxosTheme("LIGHT") {
                Column {
                    TraxosSelector(
                        "Currency",
                        currency,
                        listOf("INR" to "INR", "USD" to "USD"),
                        sheet = false,
                    ) {
                        currency = it
                    }
                    TraxosSelector("Account", account, listOf("a" to "Cash", "b" to "Bank")) {
                        account = it
                    }
                }
            }
        }
        compose.onNodeWithTag("selector_Currency").performClick()
        compose.onNodeWithTag("traxos_menu").assertExists()
        compose.onNodeWithTag("choice_USD").performClick()
        assertEquals("USD", currency)
        compose.onNodeWithTag("traxos_menu").assertDoesNotExist()
        compose.onNodeWithTag("selector_Account").performClick()
        compose.onNodeWithTag("traxos_sheet").assertExists()
        compose.onNodeWithContentDescription("Close picker").performClick()
        compose.onNodeWithTag("traxos_sheet").assertDoesNotExist()
        assertEquals("a", account)
        compose.onNodeWithTag("selector_Account").performClick()
        compose.onNodeWithTag("choice_Bank").performClick()
        assertEquals("b", account)
    }

    @Test
    fun switchCheckboxAndDisabledBusyControlsHaveRealState() {
        var checked by mutableStateOf(false)
        var switched by mutableStateOf(false)
        var calls = 0
        compose.setContent {
            TraxosTheme("DARK") {
                Column {
                    TraxosCheckbox(checked, { checked = it }, "Fixture checkbox")
                    TraxosSwitch(switched, { switched = it }, label = "Fixture switch")
                    TraxosButton({ calls++ }, enabled = false) { Text("Disabled action") }
                    TraxosButton({ calls++ }, busy = true) { Text("Saving action") }
                }
            }
        }
        compose.onNodeWithText("Fixture checkbox").performClick()
        assertTrue(checked)
        compose.onNodeWithContentDescription("Fixture switch").performClick()
        assertTrue(switched)
        compose.onNodeWithText("Disabled action").assertIsNotEnabled()
        compose.onNodeWithText("Saving action").assertIsNotEnabled()
        assertEquals(0, calls)
        compose.onNodeWithContentDescription("In progress").assertExists()
    }

    @Test
    fun customFeedbackUndoAndDismissCallbacksWorkForAllKinds() {
        var undoCalls = 0
        var dismissCalls = 0
        val kind = mutableStateOf(NoticeKind.UNDO)
        val data =
            object : SnackbarData {
                override val visuals: SnackbarVisuals
                    get() =
                        TraxosSnackbarVisuals("Fixture feedback", kind.value, actionLabel = "Undo")

                override fun performAction() {
                    undoCalls++
                }

                override fun dismiss() {
                    dismissCalls++
                }
            }
        compose.setContent { TraxosTheme("LIGHT") { TraxosSnackbar(data) } }
        compose.onNodeWithText("Undo").performClick()
        assertEquals(1, undoCalls)
        for (value in NoticeKind.entries) {
            compose.runOnIdle { kind.value = value }
            compose.onNodeWithTag("traxos_feedback").assertExists()
        }
        compose.onNodeWithContentDescription("Dismiss message").performClick()
        assertEquals(1, dismissCalls)
    }
}
