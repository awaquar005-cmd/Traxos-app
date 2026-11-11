package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PreferencesMigrationTest {
    @Test
    fun migratePublishedV1PreservesLedgerAndDefaultsNewPreferences() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "migration-${UUID.randomUUID()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/1.json")!!
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        val old = app.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(
                    entity
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName"))
                )
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName"))
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL(
                "INSERT INTO accounts VALUES ('bank','Existing bank','BANK','INR',10000,20000,0,1,'local')"
            )
            old.execSQL("INSERT INTO categories VALUES ('food','Food','EXPENSE')")
            old.execSQL(
                "INSERT INTO transactions VALUES ('saved','bank','food','EXPENSE',2500,20001,'Asia/Kolkata','Keep my note',1,0,'local')"
            )
            old.execSQL("INSERT INTO preferences VALUES (1,'DARK')")
            old.execSQL(
                "INSERT INTO outbox_operations VALUES ('old-operation','saved','TRANSACTION','CREATE',0,1,'original payload','original hash',1,1,'LOCAL_ONLY','local')"
            )
            old.version = 1
        } finally {
            old.close()
        }
        var db = TraxosDatabase.open(app, name)
        try {
            val repository = LedgerRepository(db)
            val pref = db.ledger().preferences().first()!!
            assertEquals("DARK", pref.theme)
            assertEquals("ADD_TRANSACTION", pref.centerAction)
            assertFalse(pref.balancesHidden)
            assertEquals(7500L, db.ledger().accounts().first().single().balanceMinor)
            assertEquals("Keep my note", db.ledger().record("saved")!!.note)
            assertEquals("original payload", db.ledger().operation("old-operation")!!.payload)
            repository.setCenterAction("ACCOUNTS")
            repository.setBalancesHidden(true)
            repository.setTheme("LIGHT")
            db.close()
            db = TraxosDatabase.open(app, name)
            val restored = db.ledger().preferences().first()!!
            assertEquals("ACCOUNTS", restored.centerAction)
            assertTrue(restored.balancesHidden)
            assertEquals("LIGHT", restored.theme)
            assertEquals(1L, db.ledger().recordCount())
            assertEquals(1L, db.ledger().operationCount())
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }

    @Test
    fun independentPreferenceUpdatesAndValidation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "preferences-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(app, name)
        try {
            val repo = LedgerRepository(db)
            db.ledger().preferences().first()
            repo.setCenterAction("HOME")
            repo.setTheme("DARK")
            repo.setBalancesHidden(true)
            val pref = db.ledger().preferences().first()!!
            assertEquals("HOME", pref.centerAction)
            assertEquals("DARK", pref.theme)
            assertTrue(pref.balancesHidden)
            try {
                repo.setCenterAction("GOALS")
                fail("Unsupported shortcut accepted")
            } catch (_: IllegalArgumentException) {}
            assertEquals("HOME", db.ledger().preferences().first()!!.centerAction)
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
