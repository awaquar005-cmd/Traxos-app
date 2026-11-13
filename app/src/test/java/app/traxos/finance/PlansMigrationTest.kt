package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
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
class PlansMigrationTest {
    @Test
    fun shippedV10KeepsExistingFinanceAndVaultAndAddsEmptyPlans() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "upgrade10-${UUID.randomUUID()}.db"
        val cipher = TestGiftCipher()
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/10.json")!!
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
                "INSERT INTO accounts (id,name,type,currency,openingMinor,openingDate,archived,revision,profileId) VALUES ('bank','Keep bank','BANK','INR',10000,20000,0,1,'local')"
            )
            old.execSQL("INSERT INTO categories (id,name,type) VALUES ('food','Food','EXPENSE')")
            old.execSQL(
                "INSERT INTO transactions (id,accountId,categoryId,type,amountMinor,day,zoneId,note,revision,deleted,profileId) VALUES ('tx','bank','food','EXPENSE',2000,20001,'UTC','Keep note',1,0,'local')"
            )
            old.execSQL(
                "INSERT INTO preferences (id,theme,navigationSlots) VALUES (1,'DARK','gifts,home,accounts,more')"
            )
            old.execSQL(
                "INSERT INTO gift_cards VALUES ('gift','Store','Keep gift','STORED_VALUE',?,'fingerprint',100000,70000,'INR',20000,NULL,1,0,0,0,0,2,1,'RECEIVED')",
                arrayOf(cipher.seal("gift", GiftSecret("TEST-KEEP-CODE", "1234").bytes())),
            )
            old.execSQL(
                "INSERT INTO gift_redemptions VALUES ('use','gift','REDEEM',30000,1,2,NULL)"
            )
            old.execSQL(
                "INSERT INTO smart_suggestions VALUES ('inbox','hmac','GIFT',?,'PENDING',1)",
                arrayOf(byteArrayOf(1, 2, 3)),
            )
            old.execSQL(
                "INSERT INTO account_card_secrets VALUES ('bank',?,1,'keep-card-operation')",
                arrayOf(
                    cipher.seal("card:bank", CardSecret("4111111111111111", "123", "12/30").bytes())
                ),
            )
            old.version = 10
        }
        try {
            val db = TraxosDatabase.open(app, name)
            try {
                assertEquals("COBALT", db.ledger().preferences().first()!!.accent)
                LedgerRepository(db).setAccent("FOREST")
                assertEquals("NEWEST", db.ledger().preferences().first()!!.activitySort)
                LedgerRepository(db).setActivitySort("OLDEST")
                assertTrue(db.plans().budgets().first().isEmpty())
                assertTrue(db.plans().goals().first().isEmpty())
                assertEquals(
                    "4111111111111111",
                    CardVaultRepository(db, cipher).read("bank").first.number,
                )
                val row = db.ledger().recent().first().single()
                assertEquals("Keep note", row.record.note)
                assertNull(row.record.merchantId)
                assertEquals("tag", row.categoryIcon)
                assertEquals(8000L, db.ledger().accounts().first().single().balanceMinor)
                assertEquals(
                    "gifts,home,accounts,more",
                    db.ledger().preferences().first()!!.navigationSlots,
                )
                assertEquals("DARK", db.ledger().preferences().first()!!.theme)
                val gift = db.gifts().card("gift")!!
                assertEquals(70000L, gift.remainingMinor)
                assertEquals(
                    "TEST-KEEP-CODE",
                    GiftSecret.read(cipher.open("gift", gift.secret)).code,
                )
                assertEquals(
                    1,
                    db.openHelper.readableDatabase.query("SELECT * FROM smart_suggestions").use {
                        it.count
                    },
                )
                assertFalse(db.ledger().category("food")!!.archived)
            } finally {
                db.close()
            }
            val reopened = TraxosDatabase.open(app, name)
            try {
                assertEquals("FOREST", reopened.ledger().preferences().first()!!.accent)
                assertEquals("OLDEST", reopened.ledger().preferences().first()!!.activitySort)
                assertEquals(8000L, reopened.ledger().accounts().first().single().balanceMinor)
            } finally {
                reopened.close()
            }
        } finally {
            app.deleteDatabase(name)
        }
    }
}
