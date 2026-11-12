package app.traxos.finance

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.security.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LockSessionTest {
    private lateinit var dir: File
    private val models = ViewModelStore()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir =
            File(
                    ApplicationProvider.getApplicationContext<Application>().noBackupFilesDir,
                    "session-${UUID.randomUUID()}",
                )
                .apply { mkdirs() }
    }

    @After
    fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    @Test
    fun coldStartIsLockedAndBackgroundTimeoutNeverRestoresAnExpiredSession() = runBlocking {
        val clock = TestLockClock()
        val store = LockStore(dir, TestLockCipher(), clock)
        store.setPin("", "123456", "123456")
        store.timeout(30)
        val vm = LockViewModel(store, clock)
        models.put("lock", vm)
        withTimeout(10000) { vm.ready.first { it } }
        assertTrue(vm.locked.value)
        vm.unlock("123456")
        withTimeout(10000) { vm.locked.first { !it } }
        vm.busy.first { !it }
        vm.background()
        clock.monotonic += 20000
        vm.foreground()
        assertFalse(vm.locked.value)
        vm.background()
        clock.monotonic += 31000
        vm.foreground()
        assertTrue(vm.locked.value)
        val cold = LockViewModel(store, clock)
        models.put("cold", cold)
        withTimeout(10000) { cold.ready.first { it } }
        assertTrue(cold.locked.value)
        cold.completeDevice(null)
        cold.busy.first { !it }
        assertTrue(cold.locked.value)
    }

    @Test
    fun noEligibleBiometricsKeepsPinAsTheWorkingFallback() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowBiometricManager>(
                app.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            )
            .setCanAuthenticate(false)
        val store = LockStore(dir, TestLockCipher())
        store.setPin("", "123456", "123456")
        val vm = LockViewModel(store)
        models.put("lock", vm)
        withTimeout(10000) { vm.ready.first { it } }
        var shown = false
        vm.requestDevice(app, true, "123456") { shown = true }
        withTimeout(10000) { vm.busy.first { !it } }
        assertFalse(shown)
        assertFalse(vm.preferences.value.deviceAuth)
        assertTrue(vm.locked.value)
        vm.unlock("123456")
        withTimeout(10000) { vm.locked.first { !it } }
        assertFalse(vm.locked.value)
    }
}
