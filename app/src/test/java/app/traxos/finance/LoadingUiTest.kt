package app.traxos.finance

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.traxos.finance.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun localLoadingReplacesSkeletonWithHonestEmptyState() {
        val state = mutableStateOf<ReadState>(ReadState.Loading)
        var createCalls = 0
        compose.setContent {
            TraxosTheme("LIGHT") {
                WithReadState(state.value, {}) { HomeScreen(it, { createCalls++ }, {}) }
            }
        }
        compose.onNodeWithContentDescription("Loading local records").assertExists()
        compose.runOnIdle {
            state.value = ReadState.Ready(emptyList(), emptyList(), emptyList(), emptyList())
        }
        compose.onNodeWithContentDescription("Loading local records").assertDoesNotExist()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Create account"))
        compose.onNodeWithText("Create account").performClick()
        assertEquals(1, createCalls)
        compose.onAllNodesWithText("₹", substring = true).assertCountEquals(0)
    }
}
