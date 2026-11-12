package app.traxos.finance

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*
import app.traxos.finance.ui.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: TraxosDatabase

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName
        db.close()
        if (name != null)
            ApplicationProvider.getApplicationContext<Application>().deleteDatabase(name)
    }

    private fun render(theme: String, font: Float = 1f) {
        db =
            TraxosDatabase.open(
                ApplicationProvider.getApplicationContext(),
                "gift-appearance-${UUID.randomUUID()}.db",
            )
        val repo = GiftRepository(db, TestGiftCipher())
        runBlocking {
            repo.save(
                GiftDraft(
                        merchant = "Amazon",
                        name = "A little birthday joy",
                        code = "FIXTURE-1234",
                        original = "1000",
                        remaining = "700",
                        partial = true,
                        expiry = LocalDate.now().plusDays(7).toString(),
                    )
                    .card(),
                GiftSecret("FIXTURE-1234"),
                0,
            )
            repo.save(
                GiftDraft(
                        merchant = "Myntra",
                        name = "Weekend discount",
                        kind = "DISCOUNT",
                        code = "FIXTURE-PROMO",
                    )
                    .card(),
                GiftSecret("FIXTURE-PROMO"),
                0,
            )
        }
        val vm = GiftViewModel(repo)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                TraxosTheme(theme) {
                    Surface(Modifier.fillMaxSize()) { GiftWalletScreen(vm, {}, {}, {}, {}) }
                }
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("gift_wallet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("FIXTURE-1234").assertDoesNotExist()
    }

    @Test
    fun lightAndDarkGiftDesignUsesRealStatesWithoutRevealingCodes() {
        var theme by mutableStateOf("LIGHT")
        // Render separately through one composition so both screenshots share the exact fixture.
        db =
            TraxosDatabase.open(
                ApplicationProvider.getApplicationContext(),
                "gift-lightdark-${UUID.randomUUID()}.db",
            )
        val repo = GiftRepository(db, TestGiftCipher())
        runBlocking {
            repo.save(
                GiftDraft(
                        merchant = "Amazon",
                        name = "Birthday gift",
                        code = "FIXTURE-1234",
                        original = "1000",
                        remaining = "700",
                        partial = true,
                        expiry = LocalDate.now().plusDays(7).toString(),
                    )
                    .card(),
                GiftSecret("FIXTURE-1234"),
                0,
            )
        }
        val vm = GiftViewModel(repo)
        compose.setContent {
            TraxosTheme(theme) {
                Surface(Modifier.fillMaxSize()) { GiftWalletScreen(vm, {}, {}, {}, {}) }
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Birthday gift").fetchSemanticsNodes().isNotEmpty()
        }
        save("gift-wallet-light.png")
        compose.runOnIdle { theme = "DARK" }
        compose.waitForIdle()
        save("gift-wallet-dark.png")
        compose.onNodeWithText("FIXTURE-1234").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowLargeFontWalletKeepsActionsReachable() {
        render("DARK", 2f)
        compose.onNodeWithText("Add card").assertIsDisplayed()
        compose.onNodeWithText("Smart import").assertIsDisplayed()
        compose.onNodeWithTag("gift_wallet").performScrollToNode(hasText("Gift wallet settings"))
        compose.onNodeWithText("Gift wallet settings").assertIsDisplayed()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val activity =
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .single()
            val view = activity.window.decorView
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            view.invalidate()
            view.draw(Canvas(bitmap))
            val f = File("build/reports/gift-wallet/$name")
            f.parentFile!!.mkdirs()
            f.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
