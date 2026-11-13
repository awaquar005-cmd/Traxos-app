package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.PortableBackup
import app.traxos.finance.cloud.*
import app.traxos.finance.data.*
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FixtureDrive : CloudHttp {
    var metadata: JSONObject? = null
    var content: ByteArray? = null
    var createCount = 0
    var uploadCount = 0
    var idRequests = 0
    var loseAck = false
    var corruptDownload = false
    var wrongLocation = false
    var accountEmail = "owner@example.test"
    var accountId = "permission-1"
    var total = 0L

    private fun response(j: JSONObject, status: Int = 200) =
        CloudReply(status, j.toString().toByteArray())

    override suspend fun request(
        method: String,
        url: String,
        token: String?,
        body: ByteArray?,
        headers: Map<String, String>,
    ): CloudReply {
        assertTrue(url.startsWith("https://www.googleapis.com/"))
        assertEquals("fixture-access-token", token)
        return when {
            url.contains("about?") ->
                response(
                    JSONObject()
                        .put(
                            "user",
                            JSONObject()
                                .put("emailAddress", accountEmail)
                                .put("permissionId", accountId),
                        )
                )
            url.contains("generateIds") -> {
                idRequests++
                response(JSONObject().put("ids", JSONArray().put("file-fixture-1")))
            }
            url.contains("uploadType=resumable") -> {
                createCount++
                metadata = JSONObject(body!!.toString(Charsets.UTF_8))
                total = headers.getValue("X-Upload-Content-Length").toLong()
                CloudReply(
                    200,
                    headers =
                        mapOf(
                            "Location" to
                                if (wrongLocation) "https://untrusted.example/upload"
                                else
                                    "https://www.googleapis.com/upload/drive/v3/files?upload_id=fixture"
                        ),
                )
            }
            url.contains("upload_id=") ->
                if (content == null) CloudReply(308) else response(metadata!!)
            url.contains("files/file-fixture-1?") ->
                if (content == null) CloudReply(404) else response(metadata!!)
            else -> error("Unexpected fixture request $method ${url.substringBefore('?')}")
        }
    }

    override suspend fun upload(
        url: String,
        token: String,
        file: File,
        offset: Long,
        length: Int,
        headers: Map<String, String>,
    ): CloudReply {
        uploadCount++
        assertEquals("fixture-access-token", token)
        assertEquals(0L, offset)
        assertEquals(total.toInt(), length)
        content = file.readBytes()
        metadata!!.put("size", content!!.size.toString()).put("md5Checksum", digest(file, "MD5"))
        if (loseAck) {
            loseAck = false
            throw IOException("Fixture lost completion response")
        }
        return response(metadata!!)
    }

    override suspend fun download(
        url: String,
        token: String,
        file: File,
        maxBytes: Long,
    ): CloudReply {
        val bytes = content!!.clone()
        if (corruptDownload) bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(bytes.size <= maxBytes)
        file.writeBytes(bytes)
        return CloudReply(200)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DriveBackupsTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private val databases = mutableListOf<TraxosDatabase>()
    private lateinit var http: FixtureDrive
    private lateinit var files: CloudFiles
    private lateinit var directory: File
    private lateinit var db: TraxosDatabase
    private lateinit var portable: PortableBackup
    private lateinit var service: DriveBackups
    private val user =
        CloudProfile(
            "drive:permission-1",
            "owner@example.test",
            "",
            true,
            googleEmail = "owner@example.test",
        )
    private val password
        get() = "Fixture archive password 123!".toCharArray()

    private fun db() =
        TraxosDatabase.open(context, "drive-test-${UUID.randomUUID()}.db").also { databases += it }

    @Before
    fun setup() =
        runBlocking<Unit> {
            db = db()
            val ledger = LedgerRepository(db)
            val day = LocalDate.now().toEpochDay()
            ledger.saveAccount(
                UUID.randomUUID().toString(),
                "bank",
                "Fixture bank",
                "BANK",
                "INR",
                100000,
                openingDate = day,
            )
            ledger.saveRecord(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "bank",
                "food",
                "EXPENSE",
                2500,
                day,
                "Fixture lunch",
            )
            directory = File(context.cacheDir, "drive-state-${UUID.randomUUID()}")
            files = CloudFiles(directory, TestGiftCipher())
            http = FixtureDrive()
            portable = PortableBackup(context, db, TestGiftCipher())
            service = DriveBackups(context, portable, files, http)
        }

    @After
    fun cleanup() {
        databases.forEach {
            val name = it.openHelper.databaseName!!
            it.close()
            context.deleteDatabase(name)
        }
        directory.deleteRecursively()
        File(context.noBackupFilesDir, "drive-pending").deleteRecursively()
    }

    @Test
    fun uploadIsEncryptedReadBackVerifiedAndRestoresOnAnotherDatabase() =
        runBlocking<Unit> {
            service.prepare(user, "folder-fixture", password)
            service.upload("fixture-access-token", user, password, null) {}
            assertNull(service.pending())
            assertTrue(service.verified(user).isNotBlank())
            assertEquals(1, http.createCount)
            assertFalse(http.content!!.toString(Charsets.ISO_8859_1).contains("Fixture lunch"))
            val target = db()
            val restore =
                DriveBackups(
                    context,
                    PortableBackup(context, target, TestGiftCipher()),
                    files,
                    http,
                )
            val m = http.metadata!!
            val snapshot =
                DriveSnapshot(
                    m.getString("id"),
                    m.getString("name"),
                    m.getString("size").toLong(),
                    "",
                    m.getString("md5Checksum"),
                    m.getJSONObject("appProperties").getString("sha256"),
                )
            restore.inspect("fixture-access-token", user, snapshot, password).use {
                assertEquals(1L, it.counts["transactions"])
                restore.restore(it)
            }
            assertEquals(97500L, target.ledger().accounts().first().single().balanceMinor)
        }

    @Test
    fun lostCompletionAckResumesAfterRepositoryRestartWithoutDuplicateFiles() =
        runBlocking<Unit> {
            service.prepare(user, "folder-fixture", password)
            http.loseAck = true
            assertThrows(IOException::class.java) {
                runBlocking { service.upload("fixture-access-token", user, password, null) {} }
            }
            assertNotNull(service.pending())
            assertEquals("", service.verified(user))
            val restarted = DriveBackups(context, portable, files, http)
            restarted.upload("fixture-access-token", user, password, null) {}
            assertEquals(1, http.createCount)
            assertEquals(1, http.idRequests)
            assertEquals(1, http.uploadCount)
            assertNull(restarted.pending())
        }

    @Test
    fun failedReadBackNeverReportsVerifiedAndKeepsPendingSnapshot() =
        runBlocking<Unit> {
            service.prepare(user, "folder-fixture", password)
            http.corruptDownload = true
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.upload("fixture-access-token", user, password, null) {} }
            }
            assertNotNull(service.pending())
            assertEquals("", service.verified(user))
            http.corruptDownload = false
            service.upload("fixture-access-token", user, password, null) {}
            assertTrue(service.verified(user).isNotBlank())
            assertEquals(1, http.createCount)
        }

    @Test
    fun uploadSessionCannotRedirectBearerTokenToAnotherHost() =
        runBlocking<Unit> {
            service.prepare(user, "folder-fixture", password)
            http.wrongLocation = true
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.upload("fixture-access-token", user, password, null) {} }
            }
            assertEquals(0, http.uploadCount)
            assertNotNull(service.pending())
        }

    @Test
    fun differentOwnerCannotReplaceBindingOrResumeSnapshot() =
        runBlocking<Unit> {
            service.bindIdentity(user)
            val other = user.copy(uid = "drive:other", email = "other@example.test")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.bindIdentity(other) }
            }
            service.prepare(user, "folder-fixture", password)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.upload("fixture-access-token", other, password, null) {} }
            }
            assertEquals(0, http.idRequests)
            assertEquals(user.uid, files.read("drive-binding")!!.getString("uid"))
            http.accountEmail = "other@example.test"
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.checkAccount("fixture-access-token", user) }
            }
        }

    @Test
    fun wrongPasswordOrLocalCopyFailureDoesNotUploadAndDiscardDoesNotTouchLedger() =
        runBlocking<Unit> {
            service.prepare(user, "folder-fixture", password)
            assertThrows(Exception::class.java) {
                runBlocking {
                    service.upload(
                        "fixture-access-token",
                        user,
                        "Wrong archive password 123".toCharArray(),
                        null,
                    ) {}
                }
            }
            assertThrows(Exception::class.java) {
                runBlocking {
                    service.upload(
                        "fixture-access-token",
                        user,
                        password,
                        "content://missing-provider/tree/root",
                    ) {}
                }
            }
            assertEquals(0, http.createCount)
            assertNotNull(service.pending())
            service.discard()
            assertNull(service.pending())
            assertEquals(1L, db.ledger().recordCount())
            assertEquals(97500L, db.ledger().accounts().first().single().balanceMinor)
        }
}
