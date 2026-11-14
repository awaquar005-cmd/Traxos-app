package app.traxos.finance

import android.app.Application
import app.traxos.finance.data.LedgerRepository
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*

open class TraxosApplication : Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get() =
            androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.ERROR)
                .build()

    val cloudFiles by lazy {
        app.traxos.finance.cloud.CloudFiles(noBackupFilesDir, KeystoreGiftCipher("traxos.cloud"))
    }
    val cloudHttp by lazy { app.traxos.finance.cloud.GoogleHttps(this) }

    fun cloudConfig(): app.traxos.finance.cloud.CloudConfig {
        val p = getSharedPreferences("cloud_config", 0)
        return app.traxos.finance.cloud.CloudConfig(
            p.getString("project", "micro-atrium-514609-u5").orEmpty(),
            p.getString("apiKey", "").orEmpty(),
            p.getString("web", "").orEmpty(),
        )
    }

    val cloudAccounts by lazy {
        app.traxos.finance.cloud.CloudAccounts(cloudFiles, cloudHttp, ::cloudConfig)
    }
    val driveBackups by lazy {
        app.traxos.finance.cloud.DriveBackups(
            this,
            app.traxos.finance.backup.PortableBackup(this, database, KeystoreGiftCipher()),
            cloudFiles,
            cloudHttp,
        )
    }
    open val lockStore by lazy {
        app.traxos.finance.security.LockStore(
            noBackupFilesDir,
            app.traxos.finance.security.KeystoreLockCipher(),
        )
    }
    val cardVault by lazy {
        app.traxos.finance.data.CardVaultRepository(
            database,
            KeystoreGiftCipher("traxos.card.references"),
        )
    }
    val recurring by lazy { app.traxos.finance.data.RecurringRepository(database) }
    val recurringScheduler by lazy { app.traxos.finance.recurring.RecurringScheduler(this) }
    val financeScheduler by lazy { app.traxos.finance.obligations.FinanceScheduler(this) }
    val groups by lazy { app.traxos.finance.data.GroupsRepository(database) }
    val obligations by lazy { app.traxos.finance.data.ObligationsRepository(database) }
    val plans by lazy { app.traxos.finance.data.PlansRepository(database) }
    val database by lazy { TraxosDatabase.open(this) }
    val repository by lazy { LedgerRepository(database) }
    val giftScheduler by lazy { AndroidGiftScheduler(this) }
    val gifts by lazy { GiftRepository(database, KeystoreGiftCipher(), giftScheduler) }

    val smart by lazy {
        app.traxos.finance.smart.SmartRepository(
            database,
            KeystoreGiftCipher("traxos.smart"),
            repository,
            gifts,
        )
    }

    override fun onCreate() {
        super.onCreate()
        giftScheduler.start()
        recurringScheduler.start()
        financeScheduler.start()
    }
}
