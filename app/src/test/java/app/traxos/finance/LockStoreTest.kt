package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.gifts.GiftEnvelope
import app.traxos.finance.security.*
import java.io.File
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class TestLockCipher : LockCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun seal(bytes: ByteArray) = GiftEnvelope.seal(key, "lock-state:v1", bytes)

    override fun open(bytes: ByteArray) = GiftEnvelope.open(key, "lock-state:v1", bytes)
}

class TestLockClock : LockClock {
    var now = 100000L
    var monotonic = 100000L

    override fun wall() = now

    override fun elapsed() = monotonic
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LockStoreTest {
    private lateinit var dir: File
    private lateinit var cipher: TestLockCipher
    private lateinit var clock: TestLockClock
    private lateinit var store: LockStore

    @Before
    fun setup() {
        dir =
            File(
                    ApplicationProvider.getApplicationContext<Application>().noBackupFilesDir,
                    "lock-${UUID.randomUUID()}",
                )
                .apply { mkdirs() }
        cipher = TestLockCipher()
        clock = TestLockClock()
        store = LockStore(dir, cipher, clock)
    }

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun setupConfirmationChangeAndDisableRequireTheCurrentPin() = runBlocking {
        assertFalse(store.status().enabled)
        try {
            store.setPin("", "123456", "654321")
            fail("mismatch accepted")
        } catch (_: IllegalArgumentException) {}
        assertFalse(store.status().enabled)
        assertTrue(store.setPin("", "123456", "123456").enabled)
        assertFalse(
            File(dir, "security-v1.bin").readBytes().toString(Charsets.UTF_8).contains("123456")
        )
        try {
            store.setPin("000000", "654321", "654321")
            fail("PIN bypass")
        } catch (_: IllegalArgumentException) {}
        assertEquals(PinResult.Accepted, store.verify("123456"))
        store.setPin("123456", "654321", "654321")
        assertEquals(PinResult.Incorrect, store.verify("123456"))
        assertEquals(PinResult.Accepted, store.verify("654321"))
        try {
            store.disable("000000")
            fail("disabled without PIN")
        } catch (_: IllegalArgumentException) {}
        assertFalse(store.disable("654321").enabled)
        assertFalse(LockStore(dir, cipher, clock).status().enabled)
    }

    @Test
    fun attemptPenaltyPersistsAndClockChangesOrRestartCannotSkipIt() = runBlocking {
        store.setPin("", "123456", "123456")
        repeat(5) { assertEquals(PinResult.Incorrect, store.verify("000000")) }
        assertEquals(30000L, store.status().remainingMillis)
        clock.now += 999999999
        assertTrue(store.verify("123456") is PinResult.Throttled)
        val restarted = LockStore(dir, cipher, clock)
        assertEquals(30000L, restarted.status().remainingMillis)
        clock.monotonic += 30001
        assertEquals(PinResult.Accepted, restarted.verify("123456"))
        assertEquals(0, restarted.status().failedAttempts)
    }

    @Test
    fun lostOrCorruptConfigurationNeverDisablesTheLockOrDeletesFinancialFiles() = runBlocking {
        val finance =
            File(dir.parentFile, "finance-${UUID.randomUUID()}").apply {
                writeText("existing records")
            }
        try {
            store.setPin("", "123456", "123456")
            val state = File(dir, "security-v1.bin")
            val bytes = state.readBytes()
            state.delete()
            try {
                LockStore(dir, cipher, clock).status()
                fail("missing config bypassed lock")
            } catch (_: IllegalStateException) {}
            state.writeBytes(bytes.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            try {
                LockStore(dir, cipher, clock).status()
                fail("corruption bypassed lock")
            } catch (_: Exception) {}
            assertEquals("existing records", finance.readText())
        } finally {
            finance.delete()
        }
    }

    @Test
    fun lostEncryptionKeyDoesNotReplaceData() = runBlocking {
        store.setPin("", "123456", "123456")
        val before = File(dir, "security-v1.bin").readBytes()
        try {
            LockStore(dir, TestLockCipher(), clock).status()
            fail("lost key bypassed lock")
        } catch (_: Exception) {}
        assertArrayEquals(before, File(dir, "security-v1.bin").readBytes())
    }

    @Test
    fun securityPreferencesPersistOffline() = runBlocking {
        store.setPin("", "123456", "123456")
        store.timeout(60)
        store.deviceAuth(true)
        val p = LockStore(dir, cipher, clock).status()
        assertTrue(p.enabled)
        assertTrue(p.deviceAuth)
        assertEquals(60, p.timeoutSeconds)
    }

    @Test
    fun pinAndTimeoutValidationIsExplicit() = runBlocking {
        listOf("12345", "abcdef", "1234567890123").forEach {
            assertThrows(IllegalArgumentException::class.java) { LockStore.validate(it) }
        }
        store.setPin("", "123456", "123456")
        try {
            store.timeout(999)
            fail("invalid timeout")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0L, LockStore.delayFor(4))
        assertEquals(1800000L, LockStore.delayFor(20))
    }
}
