package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.domain.NavigationLayout
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
class NavigationPreferencesTest {
    @Test
    fun shippedV2UpgradesWithoutLosingSettingsOrFinancialData() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "nav-${UUID.randomUUID()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/2.json")!!
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        val old = app.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                old.execSQL(
                    e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))
                )
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", e.getString("tableName"))
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL(
                "INSERT INTO accounts VALUES ('bank','Keep bank','BANK','INR',10000,20000,0,1,'local')"
            )
            old.execSQL("INSERT INTO categories VALUES ('food','Food','EXPENSE')")
            old.execSQL(
                "INSERT INTO transactions VALUES ('saved','bank','food','EXPENSE',2000,20001,'Asia/Kolkata','Existing note',1,0,'local')"
            )
            old.execSQL("INSERT INTO preferences VALUES (1,'DARK','ACCOUNTS',1)")
            old.execSQL(
                "INSERT INTO outbox_operations VALUES ('operation','saved','TRANSACTION','CREATE',0,1,'keep payload','keep hash',1,1,'LOCAL_ONLY','local')"
            )
            old.version = 2
        } finally {
            old.close()
        }
        var db = TraxosDatabase.open(app, name)
        try {
            val repo = LedgerRepository(db)
            val pref = db.ledger().preferences().first()!!
            assertEquals("DARK", pref.theme)
            assertEquals("ACCOUNTS", pref.centerAction)
            assertTrue(pref.balancesHidden)
            assertEquals(NavigationLayout.defaults, NavigationLayout.decode(pref.navigationSlots))
            val moved = NavigationLayout.move(NavigationLayout.defaults, 3, 0)
            repo.setNavigationSlots(moved)
            try {
                repo.setNavigationSlots(listOf("home", "home", "accounts", "settings"))
                fail("Duplicate allowed")
            } catch (_: IllegalArgumentException) {}
            db.close()
            db = TraxosDatabase.open(app, name)
            val read = db.ledger().preferences().first()!!
            assertEquals(moved, NavigationLayout.decode(read.navigationSlots))
            assertEquals("DARK", read.theme)
            assertEquals("ACCOUNTS", read.centerAction)
            assertTrue(read.balancesHidden)
            assertEquals("Existing note", db.ledger().record("saved")!!.note)
            assertEquals("keep payload", db.ledger().operation("operation")!!.payload)
            assertEquals(8000L, db.ledger().accounts().first().single().balanceMinor)
            LedgerRepository(db).setNavigationSlots(NavigationLayout.defaults)
            assertEquals("ACCOUNTS", db.ledger().preferences().first()!!.centerAction)
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
