package app.traxos.finance

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.traxos.finance.data.*
import app.traxos.finance.domain.ActivityScope
import app.traxos.finance.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivityFiltersUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun invalidRangeCancelAndDraftResetDoNotChangeAppliedFilters() {
        val initial = ActivityScope("2024-02-01", "2024-02-29")
        var result = initial
        var open by mutableStateOf(true)
        compose.setContent {
            TraxosTheme("DARK") {
                if (open)
                    ActivityFilterSheet(
                        result,
                        emptyList(),
                        emptyList(),
                        { open = false },
                        { result = it },
                    )
                else TraxosButton({ open = true }) { Text("Reopen filter") }
            }
        }
        compose
            .onNodeWithTag("input_filter_through")
            .performScrollTo()
            .performTextReplacement("2024-01-01")
        compose.onNodeWithTag("filter_apply").performClick()
        compose.onNodeWithText("End date must be on or after the start date").assertIsDisplayed()
        assertEquals(initial, result)
        compose.onNodeWithContentDescription("Close picker").performClick()
        assertEquals(initial, result)
        compose.onNodeWithText("Reopen filter").performClick()
        compose.onNodeWithTag("input_filter_through").assertTextContains("2024-02-29")
        compose.onNodeWithTag("filter_reset").performClick()
        assertEquals(initial, result)
        compose.onNodeWithTag("filter_apply").performClick()
        assertEquals(ActivityScope(), result)
    }

    @Test
    fun archivedSelectionsAndTypedDatesSurviveNestedPickers() {
        val accounts =
            listOf(
                AccountBalance(
                    Account("bank", "Demo bank", "BANK", "INR", 0, 0, archived = true),
                    0,
                )
            )
        val categories = listOf(Category("food", "Demo food", "EXPENSE", archived = true))
        var result: ActivityScope? = null
        compose.setContent {
            TraxosTheme("LIGHT") {
                ActivityFilterSheet(ActivityScope(), accounts, categories, {}, { result = it })
            }
        }
        compose.onNodeWithTag("input_filter_from").performScrollTo().performTextInput("2024-02-01")
        compose.onNodeWithTag("selector_Account").performScrollTo().performClick()
        compose.onNodeWithText("Demo bank · INR · Archived").performScrollTo().performClick()
        compose.onNodeWithTag("selector_Category").performScrollTo().performClick()
        compose.onNodeWithText("Demo food · Expense · Archived").performScrollTo().performClick()
        compose.onNodeWithTag("selector_Currency").performScrollTo().performClick()
        compose.onNodeWithTag("choice_INR").performScrollTo().performClick()
        compose.onNodeWithTag("filter_apply").performClick()
        assertEquals(ActivityScope("2024-02-01", "", "bank", "food", "INR"), result)
    }
}
