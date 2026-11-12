package app.traxos.finance

import android.app.Application
import app.traxos.finance.data.LedgerRepository
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*

class TraxosApplication : Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get() =
            androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.ERROR)
                .build()

    val database by lazy { TraxosDatabase.open(this) }
    val repository by lazy { LedgerRepository(database) }
    val giftScheduler by lazy { AndroidGiftScheduler(this) }
    val gifts by lazy { GiftRepository(database, KeystoreGiftCipher(), giftScheduler) }

    override fun onCreate() {
        super.onCreate()
        giftScheduler.start()
    }
}
