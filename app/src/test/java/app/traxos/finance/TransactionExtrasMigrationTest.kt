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
class TransactionExtrasMigrationTest {
    @Test
    fun v15PreservesFinancePreferencesAndReopens() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "upgrade15-${UUID.randomUUID()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/15.json")!!
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
                "INSERT INTO accounts(id,name,type,currency,openingMinor,openingDate,archived,revision,profileId) VALUES('bank','Keep bank','BANK','INR',10000,20000,0,1,'local')"
            )
            old.execSQL("INSERT INTO categories(id,name,type) VALUES('food','Food','EXPENSE')")
            old.execSQL(
                "INSERT INTO transactions(id,accountId,categoryId,type,amountMinor,day,zoneId,note,revision,deleted,profileId) VALUES('tx','bank','food','EXPENSE',2000,20001,'UTC','Keep note',1,0,'local')"
            )
            old.execSQL(
                "INSERT INTO preferences(id,theme,navigationSlots) VALUES(1,'DARK','gifts,home,accounts,more')"
            )
            old.version = 15
        }
        try {
            repeat(2) {
                val db = TraxosDatabase.open(app, name)
                try {
                    assertEquals(8000L, db.ledger().accounts().first().single().balanceMinor)
                    assertEquals("Keep note", db.ledger().record("tx")!!.note)
                    assertEquals("DARK", db.ledger().preferences().first()!!.theme)
                    assertTrue(db.extras().splits("tx").isEmpty())
                    assertEquals(
                        2000L,
                        db.trends().points(20000, 20002).first().single().expenseMinor,
                    )
                    assertEquals(16, db.openHelper.readableDatabase.version)
                } finally {
                    db.close()
                }
            }
        } finally {
            app.deleteDatabase(name)
        }
    }
}
