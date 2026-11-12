package app.traxos.finance.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.security.SecureRandom
import javax.crypto.Cipher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Authentication state is intentionally never stored in SavedStateHandle or a bundle. */
class LockViewModel(val store: LockStore, private val clock: LockClock = AndroidLockClock) :
    ViewModel() {
    val ready = MutableStateFlow(false)
    val locked = MutableStateFlow(true)
    val shielded = MutableStateFlow(false)
    val preferences = MutableStateFlow(LockPreferences())
    val error = MutableStateFlow<String?>(null)
    val fatal = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val episode = MutableStateFlow(0L)
    val promptPending = MutableStateFlow(false)
    private var automaticPromptClaimed = false
    private var backgroundAt: Long? = null

    private fun lockNow() {
        if (!locked.value) {
            locked.value = true
            episode.value++
            automaticPromptClaimed = false
        }
    }

    /** A ViewModel-owned lease survives rotation/cancellation and is reset only for a new lock. */
    fun claimAutomaticDevicePrompt(): Boolean {
        if (
            !ready.value ||
                !locked.value ||
                shielded.value ||
                fatal.value ||
                busy.value ||
                !preferences.value.enabled ||
                !preferences.value.deviceAuth ||
                preferences.value.remainingMillis > 0 ||
                promptPending.value ||
                automaticPromptClaimed
        )
            return false
        automaticPromptClaimed = true
        return true
    }

    private var timeoutJob: Job? = null
    private var expectedCipher: Cipher? = null
    private var challenge: ByteArray? = null
    private var enablingDevice = false

    init {
        reload()
        viewModelScope.launch {
            combine(ready, locked, shielded) { r, l, s -> r && l && !s }
                .collectLatest { visible ->
                    if (visible) {
                        while (currentCoroutineContext().isActive) {
                            if (preferences.value.enabled) {
                                try {
                                    preferences.value = store.status()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    failClosed()
                                }
                            }
                            delay(1000)
                        }
                    }
                }
        }
    }

    fun reload() {
        viewModelScope.launch {
            try {
                preferences.value = store.status()
                fatal.value = false
                locked.value = preferences.value.enabled
                ready.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failClosed()
            }
        }
    }

    private fun failClosed() {
        locked.value = true
        fatal.value = true
        ready.value = true
        error.value =
            "Security state could not be verified. Keep app data. There is no PIN-reset bypass or automatic data deletion."
    }

    fun shield() {
        shielded.value = true
    }

    fun foreground() {
        timeoutJob?.cancel()
        val at = backgroundAt
        if (
            at != null &&
                preferences.value.enabled &&
                clock.elapsed() - at >= preferences.value.timeoutSeconds * 1000L
        )
            lockNow()
        backgroundAt = null
        shielded.value = false
    }

    fun background() {
        shielded.value = true
        backgroundAt = clock.elapsed()
        if (preferences.value.enabled) {
            if (preferences.value.timeoutSeconds == 0) lockNow()
            else {
                timeoutJob?.cancel()
                timeoutJob =
                    viewModelScope.launch {
                        delay(preferences.value.timeoutSeconds * 1000L)
                        lockNow()
                    }
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error.value = e.message ?: "Check your security details"
            } catch (_: Exception) {
                error.value =
                    "Security action could not finish. Keep your data and use your current Traxos PIN."
            } finally {
                busy.value = false
            }
        }
    }

    fun unlock(pin: String) = action {
        when (val result = store.verify(pin)) {
            PinResult.Accepted -> {
                preferences.value = store.status()
                locked.value = false
            }
            PinResult.Incorrect -> {
                preferences.value = store.status()
                error.value = "Incorrect PIN"
            }
            is PinResult.Throttled -> {
                preferences.value = store.status()
                error.value = "Too many attempts. Wait before trying again"
            }
        }
    }

    fun setPin(old: String, new: String, confirm: String, done: () -> Unit) = action {
        preferences.value = store.setPin(old, new, confirm)
        locked.value = false
        done()
    }

    fun disable(pin: String, done: () -> Unit) = action {
        preferences.value = store.disable(pin)
        locked.value = false
        done()
    }

    fun timeout(seconds: Int) = action { preferences.value = store.timeout(seconds) }

    fun disableDevice(pin: String, done: () -> Unit) = action {
        store.requireAccepted(store.verify(pin))
        preferences.value = store.deviceAuth(false)
        done()
    }

    fun requestDevice(
        context: Context,
        enabling: Boolean,
        currentPin: String = "",
        show: (Cipher) -> Unit,
    ) {
        if (promptPending.value) return
        action {
            require(DeviceAuthentication.available(context)) {
                "No eligible strong biometric or supported device credential is enrolled. Use your Traxos PIN or phone security settings."
            }
            if (enabling) store.requireAccepted(store.verify(currentPin))
            else require(preferences.value.enabled && preferences.value.deviceAuth)
            expectedCipher = withContext(Dispatchers.IO) { DeviceAuthentication.prepare(enabling) }
            challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
            enablingDevice = enabling
            promptPending.value = true
            try {
                show(expectedCipher!!)
            } catch (e: Exception) {
                promptPending.value = false
                expectedCipher = null
                challenge?.fill(0)
                challenge = null
                throw e
            }
        }
    }

    fun deviceFailed(cancelled: Boolean) {
        promptPending.value = false
        expectedCipher = null
        challenge?.fill(0)
        challenge = null
        busy.value = false
        error.value =
            if (cancelled) "Device authentication cancelled. Use your Traxos PIN."
            else
                "Device authentication unavailable. Unlock with PIN; re-enable device authentication in Security settings if enrollment changed."
    }

    fun completeDevice(cipher: Cipher?) = action {
        promptPending.value = false
        val expected = expectedCipher
        val bytes = challenge
        require(expected != null && cipher === expected && bytes != null) {
            "No matching device authentication request"
        }
        try {
            withContext(Dispatchers.IO) { expected.doFinal(bytes) }
            preferences.value =
                if (enablingDevice) store.deviceAuth(true)
                else store.deviceAuth(preferences.value.deviceAuth)
            locked.value = false
        } finally {
            bytes?.fill(0)
            challenge = null
            expectedCipher = null
        }
    }
}
