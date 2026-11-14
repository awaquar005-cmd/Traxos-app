package app.traxos.finance

import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import app.traxos.finance.entry.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TraxosApplication::class)
class EntrySurfaceWorkerTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun surfaceRefreshRespectsDisabledSettingsAndNeverWritesFinance() = runBlocking {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.transactionExtras.settings(FastEntrySettings(shortcuts = false, tile = false))
        assertEquals(
            ListenableWorker.Result.success(),
            TestListenableWorkerBuilder<EntrySurfaceWorker>(app).build().doWork(),
        )
        assertFalse(app.getSharedPreferences("entry_tile", 0).getBoolean("enabled", true))
        val manager = app.getSystemService(ShortcutManager::class.java)
        assertTrue(manager.dynamicShortcuts.isEmpty())
        app.transactionExtras.settings(FastEntrySettings(shortcuts = true, tile = true))
        assertEquals(
            ListenableWorker.Result.success(),
            TestListenableWorkerBuilder<EntrySurfaceWorker>(app).build().doWork(),
        )
        assertTrue(app.getSharedPreferences("entry_tile", 0).getBoolean("enabled", false))
        assertTrue(manager.dynamicShortcuts.size <= manager.maxShortcutCountPerActivity)
        for (shortcut in manager.dynamicShortcuts) {
            assertTrue(shortcut.intent!!.action!!.startsWith(EntryInbox.PREFIX))
            assertFalse(shortcut.intent!!.hasExtra("amountMinor"))
            assertFalse(shortcut.intent!!.hasExtra("accountId"))
        }
        assertEquals(0L, app.repository.dao.recordCount())
    }
}
