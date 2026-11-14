package app.traxos.finance

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.ContactsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import app.traxos.finance.data.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
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
class DebtPlanningUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    private fun id() = UUID.randomUUID().toString()

    private val today = LocalDate.now().toEpochDay()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    private fun await(tag: String) {
        compose.waitUntil(15000) {
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(value: String) {
        compose.waitUntil(15000) {
            compose.onAllNodes(hasText(value) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun more(tool: String) {
        await("center_action")
        compose.onNodeWithTag("more_actions").performClick()
        compose.onNodeWithTag("menu_more").performScrollTo().performClick()
        compose.onNodeWithTag("tool_$tool").performScrollTo().performClick()
    }

    private fun save(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.waitForIdle()
        scenario.onActivity { a ->
            val v = a.window.decorView
            v.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
            v.invalidate()
            v.draw(Canvas(bitmap))
            val file = File("build/reports/debt-planning/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun seed() = runBlocking {
        app.repository.saveAccount(
            id(),
            "bank",
            "Bank fixture",
            "BANK",
            "INR",
            500000,
            openingDate = today - 100,
        )
    }

    @Test
    fun newLoanAndRepaymentCanRecordAccountMovementsWithoutSpending() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("debts")
            await("debt_wallet")
            compose.onNodeWithText("Add").performClick()
            await("input_debt_person")
            compose.onNodeWithTag("input_debt_person").performTextReplacement("Loan fixture")
            compose.onNodeWithTag("input_debt_amount").performTextReplacement("1000")
            compose.onNodeWithTag("selector_Account movement").performScrollTo().performClick()
            compose.onNodeWithText("Record a real movement").performClick()
            compose.onNodeWithTag("selector_Account").performScrollTo().performClick()
            compose.onNodeWithText("Bank fixture · INR").performClick()
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(15000) {
                runBlocking { app.planning.dao.debtFunding().first().size == 1 }
            }
            await("debt_wallet")
            text("Loan fixture")
            compose.onNodeWithText("Loan fixture").performScrollTo().performClick()
            compose.onNodeWithText("Repay").performScrollTo().performClick()
            compose.onNodeWithTag("input_debt_payment").performTextReplacement("300")
            compose.onNodeWithTag("selector_Account movement").performScrollTo().performClick()
            compose.onNodeWithText("Record a real movement").performClick()
            compose.onNodeWithTag("selector_Account").performScrollTo().performClick()
            compose.onNodeWithText("Bank fixture · INR").performClick()
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(15000) {
                runBlocking {
                    app.database.debts().threads().first().single().outstandingMinor == 70000L
                }
            }
            assertEquals(
                430000L,
                runBlocking { app.repository.dao.accounts().first().single().balanceMinor },
            )
            assertEquals(
                0L,
                runBlocking {
                    app.repository.dao.summary(today, today + 1).first().sumOf { it.expenseMinor }
                },
            )
            save(scenario, "debts-linked-light")
        }
    }

    @Test
    fun accountBackedGoalAndCustomBudgetFormsPersistWithoutCreatingExpenses() {
        seed()
        runBlocking { app.repository.setTheme("DARK") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("plans")
            await("plans_page")
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("New").fetchSemanticsNodes().size == 2
            }
            compose.onAllNodesWithText("New")[1].performScrollTo().performClick()
            await("input_goal_name")
            compose.onNodeWithTag("input_goal_name").performTextReplacement("Goal fixture")
            compose.onNodeWithTag("input_goal_target").performTextReplacement("1000")
            compose.onNodeWithText("No deadline").performScrollTo().performClick()
            compose.onNodeWithTag("goal_save").performScrollTo().performClick()
            compose.waitUntil(15000) {
                runBlocking { app.database.plans().goals().first().size == 1 }
            }
            await("plans_page")
            text("Add")
            compose.onNodeWithText("Add").performScrollTo().performClick()
            compose.onNodeWithTag("input_goal_progress").performTextReplacement("100")
            compose.onNodeWithTag("selector_Progress backing").performScrollTo().performClick()
            compose.onNodeWithText("Earmark existing account money").performClick()
            compose.onNodeWithTag("selector_Savings account").performScrollTo().performClick()
            compose.onNodeWithText("Bank fixture · INR").performClick()
            compose.onNodeWithTag("goal_progress_save").performScrollTo().performClick()
            compose.waitUntil(15000) {
                runBlocking { app.database.plans().goals().first().single().savedMinor == 10000L }
            }
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
            assertNotNull(
                runBlocking { app.planning.dao.goalPreferences().first().single().deadlineDay }
            )
            save(scenario, "goals-backed-dark")
            compose.onNodeWithText("Cycles").performScrollTo().performClick()
            await("period_budgets")
            compose.onNodeWithText("Add").performClick()
            await("input_period_name")
            compose.onNodeWithText("Custom dates").performClick()
            compose.onNodeWithTag("input_period_name").performTextReplacement("Range fixture")
            compose.onNodeWithTag("input_period_limit").performTextReplacement("500")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(15000) {
                runBlocking { app.planning.dao.budgets().first().size == 1 }
            }
            await("period_budgets")
            save(scenario, "periods-dark")
        }
    }

    @Test
    fun contactSelectionLaunchesOnlyThePhonePickerWithoutAddressBookPermission() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("debts")
            await("debt_wallet")
            compose.onNodeWithText("Add").performClick()
            await("input_debt_person")
            compose.onNodeWithText("Details").performScrollTo().performClick()
            compose.onNodeWithText("Contact").performScrollTo().performClick()
            scenario.onActivity { activity ->
                val launched = Shadows.shadowOf(activity).nextStartedActivityForResult
                assertNotNull(launched)
                assertEquals(Intent.ACTION_PICK, launched.intent.action)
                assertEquals(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    launched.intent.data,
                )
            }
            val permissions =
                app.packageManager
                    .getPackageInfo(
                        app.packageName,
                        android.content.pm.PackageManager.GET_PERMISSIONS,
                    )
                    .requestedPermissions
                    .orEmpty()
            assertFalse("android.permission.READ_CONTACTS" in permissions)
        }
    }

    @Test
    fun sharingRequiresReviewAndOpensAnIntentWithoutClaimingSent() {
        val d =
            DebtThread(
                id(),
                "Person fixture",
                "LENT",
                "INR",
                10000,
                10000,
                today,
                today + 7,
                "Private note",
                operationId = id(),
            )
        runBlocking { app.planning.saveDebt(d, 0, null, null, null, false, "7,1,0") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("debts")
            await("debt_wallet")
            compose.onNodeWithText("Person fixture").performClick()
            compose.onNodeWithText("Share").performScrollTo().performClick()
            await("input_debt_share_message")
            compose
                .onNodeWithTag("input_debt_share_message")
                .assertTextContains("Person fixture", substring = true)
            compose
                .onNodeWithTag("input_debt_share_message")
                .assertTextContains("As of", substring = true)
            scenario.onActivity { a -> assertNull(Shadows.shadowOf(a).nextStartedActivity) }
            compose.onNodeWithTag("debt_share_open").performScrollTo().performClick()
            scenario.onActivity { a ->
                val intent = Shadows.shadowOf(a).nextStartedActivity
                assertNotNull(intent)
                if (intent.action == Intent.ACTION_SEND) {
                    assertEquals("com.whatsapp", intent.`package`)
                    assertFalse(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Private note"))
                } else assertEquals(Intent.ACTION_CHOOSER, intent.action)
            }
        }
    }
}
