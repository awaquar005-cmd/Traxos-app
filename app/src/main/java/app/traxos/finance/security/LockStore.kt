package app.traxos.finance.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.traxos.finance.gifts.GiftEnvelope
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface LockCipher {
    fun seal(bytes: ByteArray): ByteArray

    fun open(bytes: ByteArray): ByteArray
}

class KeystoreLockCipher : LockCipher {
    private val alias = "traxos.lock.state.v1"

    private var seenKey = false

    @Synchronized
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            seenKey = true
            return it
        }
        check(create && !seenKey) { "Security key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
            .also { seenKey = true }
    }

    override fun seal(bytes: ByteArray) = GiftEnvelope.seal(key(true), "lock-state:v1", bytes)

    override fun open(bytes: ByteArray) = GiftEnvelope.open(key(false), "lock-state:v1", bytes)
}

interface LockClock {
    fun wall(): Long

    fun elapsed(): Long
}

object AndroidLockClock : LockClock {
    override fun wall() = System.currentTimeMillis()

    override fun elapsed() = android.os.SystemClock.elapsedRealtime()
}

data class LockPreferences(
    val enabled: Boolean = false,
    val deviceAuth: Boolean = false,
    val timeoutSeconds: Int = 0,
    val failedAttempts: Int = 0,
    val remainingMillis: Long = 0,
)

sealed interface PinResult {
    data object Accepted : PinResult

    data object Incorrect : PinResult

    data class Throttled(val millis: Long) : PinResult
}

internal data class StoredLock(
    val salt: ByteArray = byteArrayOf(),
    val verifier: ByteArray = byteArrayOf(),
    val deviceAuth: Boolean = false,
    val timeout: Int = 0,
    val failures: Int = 0,
    val nextWall: Long = 0,
) {
    val enabled
        get() = verifier.isNotEmpty()
}

/**
 * No PIN is persisted. JCA PBKDF2-HMAC-SHA256; the verifier/state is authenticated by Keystore
 * AES-GCM.
 */
class LockStore(
    directory: File,
    private val cipher: LockCipher,
    private val clock: LockClock = AndroidLockClock,
) {
    private val file = AtomicFile(File(directory, "security-v1.bin"))
    private val marker = File(directory, "security-configured")
    private val gate = Mutex()
    private var cached: StoredLock? = null
    private var nextElapsed = 0L

    companion object {
        const val ITERATIONS = 600_000
        val timeouts = listOf(0, 30, 60, 300)

        fun delayFor(failures: Int): Long =
            if (failures < 5) 0 else minOf(1_800_000L, 30_000L * (1L shl minOf(6, failures - 5)))

        fun derive(pin: CharArray, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                pin.fill('\u0000')
            }
        }

        fun validate(pin: String) {
            require(pin.matches(Regex("[0-9]{6,12}"))) { "Use a PIN of 6–12 digits" }
        }
    }

    private fun load(): StoredLock {
        cached?.let {
            return it
        }
        val s =
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                check(!marker.exists()) { "Security configuration is missing" }
                StoredLock()
            } else {
                val j =
                    JSONObject(
                        cipher.open(file.openRead().use { it.readBytes() }).toString(Charsets.UTF_8)
                    )
                require(j.getInt("v") == 1 && j.getInt("iterations") == ITERATIONS)
                fun bytes(name: String) =
                    android.util.Base64.decode(j.getString(name), android.util.Base64.NO_WRAP)
                StoredLock(
                        bytes("salt"),
                        bytes("verifier"),
                        j.getBoolean("deviceAuth"),
                        j.getInt("timeout"),
                        j.getInt("failures"),
                        j.getLong("nextWall"),
                    )
                    .also {
                        require(it.timeout in timeouts && it.failures in 0..20)
                        require(
                            (it.salt.size == 16 && it.verifier.size == 32) ||
                                (it.salt.isEmpty() && it.verifier.isEmpty())
                        )
                    }
            }
        cached = s
        // Reopening cannot skip a persisted penalty by changing wall time or restarting the
        // process.
        if (s.failures >= 5)
            nextElapsed =
                clock.elapsed() +
                    maxOf(delayFor(s.failures), (s.nextWall - clock.wall()).coerceIn(0, 1_800_000))
        return s
    }

    private fun save(s: StoredLock) {
        val j =
            JSONObject()
                .put("v", 1)
                .put("iterations", ITERATIONS)
                .put(
                    "salt",
                    android.util.Base64.encodeToString(s.salt, android.util.Base64.NO_WRAP),
                )
                .put(
                    "verifier",
                    android.util.Base64.encodeToString(s.verifier, android.util.Base64.NO_WRAP),
                )
                .put("deviceAuth", s.deviceAuth)
                .put("timeout", s.timeout)
                .put("failures", s.failures)
                .put("nextWall", s.nextWall)
        val encrypted = cipher.seal(j.toString().toByteArray(Charsets.UTF_8))
        file.baseFile.parentFile!!.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        cached = s
        if (!marker.exists()) {
            check(marker.createNewFile())
        }
    }

    private fun prefs(s: StoredLock) =
        LockPreferences(
            s.enabled,
            s.deviceAuth,
            s.timeout,
            s.failures,
            (nextElapsed - clock.elapsed()).coerceAtLeast(0),
        )

    suspend fun status(): LockPreferences =
        withContext(Dispatchers.IO) { gate.withLock { prefs(load()) } }

    private fun verifyLocked(pin: String): PinResult {
        validate(pin)
        val s = load()
        require(s.enabled) { "Set up a PIN first" }
        val remaining = nextElapsed - clock.elapsed()
        if (remaining > 0) return PinResult.Throttled(remaining)
        val candidate = derive(pin.toCharArray(), s.salt)
        val matches =
            try {
                MessageDigest.isEqual(candidate, s.verifier)
            } finally {
                candidate.fill(0)
            }
        return if (matches) {
            save(s.copy(failures = 0, nextWall = 0))
            nextElapsed = 0
            PinResult.Accepted
        } else {
            val count = (s.failures + 1).coerceAtMost(20)
            val wait = delayFor(count)
            save(s.copy(failures = count, nextWall = clock.wall() + wait))
            nextElapsed = clock.elapsed() + wait
            PinResult.Incorrect
        }
    }

    suspend fun verify(pin: String): PinResult =
        withContext(Dispatchers.Default) { gate.withLock { verifyLocked(pin) } }

    suspend fun setPin(oldPin: String, newPin: String, confirmation: String): LockPreferences =
        withContext(Dispatchers.Default) {
            gate.withLock {
                validate(newPin)
                require(newPin == confirmation) { "The PINs do not match" }
                val old = load()
                if (old.enabled) requireAccepted(verifyLocked(oldPin))
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val updated =
                    load()
                        .copy(
                            salt = salt,
                            verifier = derive(newPin.toCharArray(), salt),
                            failures = 0,
                            nextWall = 0,
                        )
                save(updated)
                nextElapsed = 0
                prefs(updated)
            }
        }

    suspend fun disable(pin: String): LockPreferences =
        withContext(Dispatchers.Default) {
            gate.withLock {
                requireAccepted(verifyLocked(pin))
                val s = StoredLock(timeout = load().timeout)
                save(s)
                nextElapsed = 0
                prefs(s)
            }
        }

    suspend fun timeout(seconds: Int): LockPreferences =
        withContext(Dispatchers.IO) {
            gate.withLock {
                require(seconds in timeouts)
                val s = load()
                require(s.enabled) { "Set a PIN first" }
                save(s.copy(timeout = seconds))
                prefs(load())
            }
        }

    internal suspend fun deviceAuth(enabled: Boolean): LockPreferences =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val s = load()
                require(s.enabled)
                save(s.copy(deviceAuth = enabled, failures = 0, nextWall = 0))
                nextElapsed = 0
                prefs(load())
            }
        }

    fun requireAccepted(result: PinResult) {
        require(result == PinResult.Accepted) {
            if (result is PinResult.Throttled) "Too many attempts. Wait before trying again"
            else "Incorrect current PIN"
        }
    }
}
