package app.traxos.finance

import android.app.Application
import app.traxos.finance.data.LedgerRepository
import app.traxos.finance.data.TraxosDatabase

class TraxosApplication : Application() {
    val database by lazy { TraxosDatabase.open(this) }
    val repository by lazy { LedgerRepository(database) }
}
