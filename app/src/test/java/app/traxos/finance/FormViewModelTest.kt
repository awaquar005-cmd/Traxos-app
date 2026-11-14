package app.traxos.finance

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.LedgerRepository
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.entry.EntryPreset
import app.traxos.finance.entry.EntryRequest
import app.traxos.finance.entry.MerchantRule
import app.traxos.finance.ui.FormViewModel
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FormViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file = "form-${UUID.randomUUID()}.db"
    private val db = TraxosDatabase.open(app, file)
    private val repo = LedgerRepository(db)
    private val models = mutableListOf<FormViewModel>()

    @After
    fun after() {
        models.forEach { it.viewModelScope.cancel() }
        db.close()
        app.deleteDatabase(file)
        Dispatchers.resetMain()
    }

    private suspend fun form(
        saved: SavedStateHandle = SavedStateHandle(mapOf("id" to "new"))
    ): FormViewModel {
        repo.saveAccount(
            UUID.randomUUID().toString(),
            "bank",
            "Bank",
            "BANK",
            "INR",
            10000,
            openingDate = LocalDate.now().minusYears(1).toEpochDay(),
        )
        return FormViewModel(repo, saved, false).also { vm ->
            models += vm
            vm.loading.filter { !it }.first()
            vm.set("accountId", "bank")
            vm.set("categoryId", "food")
            vm.set("amount", "25.00")
        }
    }

    @Test
    fun saveGateRejectsTenCallbacksAndEmitsOneSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = form()
        repeat(10) { vm.save() }
        assertTrue(vm.busy.value)
        val event = vm.events.receive()
        assertTrue(event.close)
        assertEquals(1L, db.ledger().recordCount())
        assertEquals(2L, db.ledger().operationCount())
        assertTrue(vm.events.tryReceive().isFailure)
    }

    @Test
    fun validationFailureKeepsFieldsAndWritesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = form()
        vm.set("amount", "0")
        vm.set("note", "Keep this draft")
        vm.save()
        vm.error.filter { it != null }.first()
        assertFalse(vm.busy.value)
        assertEquals("Keep this draft", vm.fields.value["note"])
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun recreatedViewModelPreservesOperationIdentityAndDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = SavedStateHandle(mapOf("id" to "new"))
        val original = form(saved)
        original.set("note", "Typed before recreation")
        val op = saved.get<String>("operationId")
        original.viewModelScope.cancel()
        val restored = FormViewModel(repo, saved, false)
        models += restored
        restored.loading.filter { !it }.first()
        assertEquals(op, saved.get<String>("operationId"))
        assertEquals("Typed before recreation", restored.fields.value["note"])
        assertTrue(restored.dirty())
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun merchantSuggestionKeepsDraftAndClearsWhenMerchantChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = form()
        val merchant = UUID.randomUUID().toString()
        repo.saveLabel(UUID.randomUUID().toString(), merchant, "Fresh Mart", "MERCHANT")
        repo.extras.rule(
            MerchantRule(UUID.randomUUID().toString(), "Fresh Mart", "EXACT", "EXPENSE", "travel"),
            0,
        )
        vm.set("categoryId", "")
        vm.set("note", "Keep draft")
        vm.set("merchantId", merchant)
        vm.fields.filter { it["categoryId"] == "travel" }.first()
        assertEquals("Keep draft", vm.fields.value["note"])
        vm.set("merchantId", "")
        assertEquals("", vm.fields.value["categoryId"])
        assertEquals("Keep draft", vm.fields.value["note"])
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun stalePresetCannotReplaceDraftAndValidPresetStillNeedsSave() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = form()
        vm.set("note", "Keep existing draft")
        val preset =
            EntryPreset(
                UUID.randomUUID().toString(),
                "Lunch",
                "EXPENSE",
                "INR",
                15000,
                "bank",
                "food",
            )
        repo.extras.preset(preset, 0)
        repo.extras.archivePreset(preset.id, 1, true)
        vm.applyEntry(EntryRequest("PRESET", preset.id))
        vm.error.filter { it != null }.first()
        assertEquals("Keep existing draft", vm.fields.value["note"])
        repo.extras.archivePreset(preset.id, 2, false)
        vm.applyEntry(EntryRequest("PRESET", preset.id))
        vm.fields.filter { it["amount"] == "150.00" }.first()
        assertEquals("bank", vm.fields.value["accountId"])
        assertEquals("food", vm.fields.value["categoryId"])
        assertEquals(0L, db.ledger().recordCount())
    }
}
