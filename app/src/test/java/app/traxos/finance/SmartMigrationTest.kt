package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
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
class SmartMigrationTest {
    @Test
    fun shippedV4KeepsEncryptedGiftLedgerAndNavigationAfterUpgradeAndRestart() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "v4-smart-${UUID.randomUUID()}.db"
        val cipher = TestGiftCipher()
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/4.json")!!
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        app.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
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
                "INSERT INTO transactions VALUES ('record','bank','food','EXPENSE',2000,20001,'UTC','Keep note',1,0,'local')"
            )
            old.execSQL(
                "INSERT INTO preferences VALUES (1,'DARK','ACCOUNTS',1,'gifts,home,accounts,more')"
            )
            old.execSQL(
                "INSERT INTO gift_cards VALUES ('gift','Store','Keep gift','STORED_VALUE',?, 'keep-fingerprint',100000,70000,'INR',20000,NULL,1,0,0,0,0,2,1,'RECEIVED')",
                arrayOf(cipher.seal("gift", GiftSecret("KEEP-CODE", "1234").bytes())),
            )
            old.execSQL(
                "INSERT INTO gift_redemptions VALUES ('redeem','gift','REDEEM',30000,1,2,NULL)"
            )
            old.version = 4
        }
        var db = TraxosDatabase.open(app, name)
        try {
            assertEquals(8000L, db.ledger().accounts().first().single().balanceMinor)
            assertEquals(
                "gifts,home,accounts,more",
                db.ledger().preferences().first()!!.navigationSlots,
            )
            val gifts = GiftRepository(db, cipher)
            assertEquals("KEEP-CODE", gifts.secret(db.gifts().card("gift")!!).code)
            assertEquals(70000L, db.gifts().card("gift")!!.remainingMinor)
            db.ledger().currency("USD")
            db.ledger().displayName("Local name")
            db.ledger().smartSettings(false, false, false)
            val smartCipher = TestGiftCipher()
            val repo = SmartRepository(db, smartCipher, LedgerRepository(db), gifts)
            val s = repo.offer("Amazon gift card code: RESTART-1234")!!.first
            db.close()
            db = TraxosDatabase.open(app, name)
            val restored =
                SmartRepository(db, smartCipher, LedgerRepository(db), GiftRepository(db, cipher))
            assertEquals("RESTART-1234", restored.read(db.smart().get(s.id)!!).code)
            assertEquals("USD", db.ledger().preferences().first()!!.defaultCurrency)
            assertFalse(db.ledger().preferences().first()!!.smartEnabled)
            assertEquals("Local name", db.ledger().preferences().first()!!.displayName)
            assertEquals(1L, db.ledger().recordCount())
            assertEquals(1, db.gifts().history("gift").first().size)
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
