package app.traxos.finance

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import app.traxos.finance.backup.ExportScope
import app.traxos.finance.regional.DisplayOptions
import app.traxos.finance.ui.ExportRequest
import app.traxos.finance.ui.ExportViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TraxosApplication::class)
class ExportDeliveryTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    private fun request() = ExportRequest("JSON", ExportScope(), false, DisplayOptions(), "")

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun cancelledPickerAndRepeatedTapsNeverStartExport() {
        val vm = ExportViewModel(app)
        assertTrue(vm.prepare(request()))
        assertFalse(vm.prepare(request()))
        vm.result(null)
        assertFalse(vm.busy.value)
        assertFalse(vm.selecting.value)
        assertTrue(vm.status.value.contains("Cancelled"))
        assertNull(vm.saved.value)
        assertTrue(vm.prepare(request()))
        vm.result(null)
    }

    @Test
    fun deniedDestinationHasNoFalseSuccessAndRemovesOwnedTemporaryFile() = runBlocking {
        val vm = ExportViewModel(app)
        vm.prepare(request())
        vm.result(Uri.parse("content://unavailable-provider/document/output"))
        withTimeout(15000) { vm.busy.first { !it } }
        assertTrue(vm.failed.value)
        assertNull(vm.saved.value)
        assertTrue(vm.status.value.contains("incomplete"))
        assertTrue(java.io.File(app.cacheDir, "readable-exports").listFiles().orEmpty().isEmpty())
        assertEquals(0L, app.database.ledger().recordCount())
    }

    @Test
    fun immediateCancellationAlwaysReleasesBusyGateAndKeepsData() = runBlocking {
        val vm = ExportViewModel(app)
        vm.prepare(request())
        vm.result(Uri.parse("content://unavailable-provider/document/output"))
        vm.cancel()
        withTimeout(15000) { vm.busy.first { !it } }
        assertNull(vm.saved.value)
        assertTrue(vm.status.value.contains("Cancelled"))
        assertTrue(java.io.File(app.cacheDir, "readable-exports").listFiles().orEmpty().isEmpty())
        assertEquals(0L, app.database.ledger().recordCount())
    }
}
