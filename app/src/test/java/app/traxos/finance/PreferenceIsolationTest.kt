package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreferenceIsolationTest {
    @Test
    fun independentSettingsDoNotRevertEachOtherOrMutateFinancialRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "pref-isolation-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(context, name)
        try {
            val ledger = LedgerRepository(db)
            ledger.saveAccount(UUID.randomUUID().toString(), "bank", "Bank", "BANK", "INR", 10000)
            val gifts = GiftRepository(db, TestGiftCipher())
            coroutineScope {
                listOf(
                        async { ledger.setDefaultAccount("bank") },
                        async { ledger.setDefaultCategory("food") },
                        async { ledger.setCurrency("USD") },
                        async { ledger.setSmartEnabled(false) },
                        async { ledger.setSuggestionNotices(true) },
                        async { gifts.changeSettings { it.copy(hideCodes = false) } },
                        async { gifts.changeSettings { it.copy(intervals = "14,0") } },
                    )
                    .awaitAll()
            }
            val prefs = db.ledger().preferences().first()!!
            assertEquals("bank", prefs.defaultAccountId)
            assertEquals("food", prefs.defaultExpenseCategoryId)
            assertEquals("USD", prefs.defaultCurrency)
            assertFalse(prefs.smartEnabled)
            assertTrue(prefs.suggestionNotifications)
            val giftPrefs = db.gifts().settings().first()!!
            assertFalse(giftPrefs.hideCodes)
            assertEquals("14,0", giftPrefs.intervals)
            assertEquals(0L, db.ledger().recordCount())
            assertEquals(10000L, db.ledger().accounts().first().single().balanceMinor)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
