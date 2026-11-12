package app.traxos.finance

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.traxos.finance.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun catalogHasUniqueReachableJourneysAndEveryPageRendersWithSampleBoundary() {
        assertEquals(100, reviewPages.size)
        assertEquals(reviewPages.size, reviewPages.map { it.id }.distinct().size)
        assertTrue(
            reviewPages.flatMap { it.links }.all { target -> reviewPages.any { it.id == target } }
        )
        var current by mutableStateOf(reviewPages.first())
        var mode by mutableStateOf("LIGHT")
        compose.setContent {
            TraxosTheme(mode) {
                Column(Modifier.fillMaxSize()) {
                    Text("UI PREVIEW · Nothing is saved")
                    key(current.id) {
                        ReviewPageContent(current, "Sample", {}, {}, Modifier.weight(1f))
                    }
                }
            }
        }
        reviewPages.forEachIndexed { index, page ->
            compose.runOnIdle {
                current = page
                mode = if (index % 2 == 0) "LIGHT" else "DARK"
            }
            compose.onNodeWithTag("review_page_${page.id}").assertExists()
            compose.onNodeWithText("UI PREVIEW · Nothing is saved").assertExists()
        }
    }

    @Test
    fun previewValidationFeedbackAndErrorRetryAreRealInteractionsWithoutServiceCalls() {
        var state by mutableStateOf("Sample")
        val page = reviewPages.first { it.id == "goal_form" }
        compose.setContent {
            TraxosTheme("DARK") {
                ReviewPageContent(page, state, {}, { state = "Sample" }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("review_primary").performClick()
        compose.onNodeWithText("Enter a sample amount to review this form state").assertExists()
        compose.onNodeWithTag("input_review_Target").performScrollTo().performTextInput("1000")
        compose.onNodeWithTag("review_primary").performClick()
        compose.onNodeWithTag("review_feedback").assertExists()
        compose.runOnIdle { state = "Error" }
        compose.onNodeWithText("Retry preview").performScrollTo().performClick()
        compose.onNodeWithTag("review_primary").assertExists()
        compose.runOnIdle { captureHostPreview(compose.activity, "goal-form-preview-dark.png") }
    }

    @Test
    fun closeAndBackUseAnIsolatedPreviewHierarchy() {
        var closed = false
        compose.setContent {
            TraxosTheme("LIGHT") { DesignReviewScreen("groups", { closed = true }, {}) }
        }
        compose.onNodeWithTag("review_page_groups").assertExists()
        compose.onNodeWithTag("tool_group_detail").performScrollTo().performClick()
        compose.onNodeWithTag("review_page_group_detail").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("review_page_groups").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("review_catalog").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(closed)
    }
}
