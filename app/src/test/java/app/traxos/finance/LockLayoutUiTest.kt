package app.traxos.finance

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.security.*
import app.traxos.finance.ui.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val models = ViewModelStore()
    private lateinit var dir: File

    @After
    fun close() {
        models.clear()
        dir.deleteRecursively()
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-xhdpi")
    fun normalUnlockCentersTheUnboxedFiveSquareBrand() {
        dir =
            File(
                    ApplicationProvider.getApplicationContext<Application>().noBackupFilesDir,
                    "center-${UUID.randomUUID()}",
                )
                .apply { mkdirs() }
        val store = LockStore(dir, TestLockCipher())
        runBlocking { store.setPin("", "123456", "123456") }
        val vm = LockViewModel(store)
        models.put("lock", vm)
        compose.setContent {
            TraxosTheme("LIGHT") { SecurityGate(vm) { Text("UNLOCKED FIXTURE") } }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("app_lock").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Traxos five-square mark").assertIsDisplayed()
        compose.onNodeWithTag("unlock_submit").assertIsDisplayed()
        compose.runOnIdle { captureHostPreview(compose.activity, "lock-centered-light.png") }
    }

    @Test
    fun darkLargeFontKeypadKeepsUnlockVisibleAndWorksOffline() {
        dir =
            File(
                    ApplicationProvider.getApplicationContext<Application>().noBackupFilesDir,
                    "layout-${UUID.randomUUID()}",
                )
                .apply { mkdirs() }
        val store = LockStore(dir, TestLockCipher())
        runBlocking { store.setPin("", "123456", "123456") }
        val vm = LockViewModel(store)
        models.put("lock", vm)
        compose.setContent {
            TraxosTheme("DARK") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    SecurityGate(vm) { Text("UNLOCKED FIXTURE") }
                }
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("app_lock").fetchSemanticsNodes().isNotEmpty()
        }
        for (key in 1..6) compose.onNodeWithTag("pin_key_$key").performScrollTo().performClick()
        compose.onNodeWithTag("unlock_submit").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { captureHostPreview(compose.activity, "lock-dark-large.png") }
        compose.onNodeWithTag("unlock_submit").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("UNLOCKED FIXTURE").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

internal fun captureHostPreview(activity: android.app.Activity, name: String) {
    val view = activity.window.decorView
    view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
    val bitmap =
        android.graphics.Bitmap.createBitmap(
            view.width,
            view.height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
    view.draw(android.graphics.Canvas(bitmap))
    view.invalidate()
    view.draw(android.graphics.Canvas(bitmap))
    val output = File("build/reports/ux-refinement/$name")
    output.parentFile!!.mkdirs()
    output.outputStream().use {
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
    }
    bitmap.recycle()
}
