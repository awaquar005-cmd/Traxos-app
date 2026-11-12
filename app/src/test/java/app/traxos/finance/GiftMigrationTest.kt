package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*
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
class GiftMigrationTest {
    @Test
    fun shippedV3UpgradesPreservingLedgerAndCustomizedNavigation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "gift-migration-${UUID.randomUUID()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/3.json")!!
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
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
            old.execSQL(
                "INSERT INTO preferences VALUES (1,'DARK','ACCOUNTS',1,'settings,accounts,transactions,home')"
            )
            old.execSQL(
                "INSERT INTO outbox_operations VALUES ('operation','saved','TRANSACTION','CREATE',0,1,'keep payload','keep hash',1,1,'LOCAL_ONLY','local')"
            )
            old.version = 3
        }
        var db = TraxosDatabase.open(context, name)
        try {
            assertEquals(8000L, db.ledger().accounts().first().single().balanceMinor)
            assertEquals("keep payload", db.ledger().operation("operation")!!.payload)
            assertEquals(
                "settings,accounts,transactions,home",
                db.ledger().preferences().first()!!.navigationSlots,
            )
            assertTrue(db.ledger().preferences().first()!!.balancesHidden)
            assertTrue(db.gifts().all().isEmpty())
            val cipher = TestGiftCipher()
            val repo = GiftRepository(db, cipher)
            val c =
                GiftDraft(
                        merchant = "Store",
                        name = "Voucher",
                        code = "SAVE-123",
                        kind = "DISCOUNT",
                    )
                    .card()
            repo.save(c, GiftSecret("SAVE-123"), 0)
            repo.updateSettings(GiftSettings(intervals = "7,0", sort = "NEWEST"))
            db.close()
            db = TraxosDatabase.open(context, name)
            assertEquals(
                "SAVE-123",
                GiftRepository(db, cipher).secret(db.gifts().all().single()).code,
            )
            assertEquals("7,0", db.gifts().settings().first()!!.intervals)
            assertEquals("DARK", db.ledger().preferences().first()!!.theme)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
