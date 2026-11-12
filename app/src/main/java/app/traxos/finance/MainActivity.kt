package app.traxos.finance

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.traxos.finance.security.*
import app.traxos.finance.ui.*

class MainActivity : FragmentActivity() {
    private lateinit var security: LockViewModel
    private lateinit var devicePrompt: BiometricPrompt

    override fun onStart() {
        super.onStart()
        if (::security.isInitialized) security.foreground()
    }

    override fun onResume() {
        super.onResume()
        if (::security.isInitialized) security.foreground()
    }

    override fun onPause() {
        if (::security.isInitialized) security.shield()
        super.onPause()
    }

    override fun onStop() {
        if (::security.isInitialized && !isChangingConfigurations) security.background()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveGiftIntent(intent)
    }

    private fun receiveGiftIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("open_smart", false) == true) {
            app.traxos.finance.gifts.GiftInbox.pending.value =
                app.traxos.finance.gifts.GiftIncoming.SmartOpen
            intent.removeExtra("open_smart")
        } else if (intent?.getBooleanExtra("open_gifts", false) == true) {
            app.traxos.finance.gifts.GiftInbox.pending.value =
                app.traxos.finance.gifts.GiftIncoming.Open
            intent.removeExtra("open_gifts")
        } else if (intent?.action == android.content.Intent.ACTION_SEND) {
            val incoming =
                if (intent.type == "text/plain")
                    intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.take(16000)?.let {
                        app.traxos.finance.gifts.GiftIncoming.Text(it)
                    }
                else if (intent.type?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    val uri =
                        intent.getParcelableExtra<android.net.Uri>(
                            android.content.Intent.EXTRA_STREAM
                        )
                    uri?.takeIf { it.scheme == "content" }
                        ?.let { app.traxos.finance.gifts.GiftIncoming.Image(it) }
                } else null
            app.traxos.finance.gifts.GiftInbox.pending.value = incoming
            intent.action = null
            intent.removeExtra(android.content.Intent.EXTRA_TEXT)
            intent.removeExtra(android.content.Intent.EXTRA_STREAM)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Traxos)
        super.onCreate(savedInstanceState)
        receiveGiftIntent(intent)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val app = application as TraxosApplication
        val repository = app.repository
        security =
            ViewModelProvider(
                this,
                viewModelFactory { initializer { LockViewModel(app.lockStore) } },
            )[LockViewModel::class.java]
        devicePrompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        security.completeDevice(result.cryptoObject?.cipher)
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        security.deviceFailed(
                            code == BiometricPrompt.ERROR_USER_CANCELED ||
                                code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                code == BiometricPrompt.ERROR_CANCELED
                        )
                    }

                    override fun onAuthenticationFailed() {
                        security.error.value = "Not recognized. Try again or use your Traxos PIN."
                    }
                },
            )
        val showDevice: (javax.crypto.Cipher) -> Unit = { cipher ->
            val builder =
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Traxos")
                    .setSubtitle("Authenticate securely on this device")
                    .setAllowedAuthenticators(DeviceAuthentication.authenticators())
            if (android.os.Build.VERSION.SDK_INT < 30)
                builder.setNegativeButtonText("Use Traxos PIN")
            devicePrompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
        }
        setContent {
            val vm: MainViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { MainViewModel(repository, createSavedStateHandle()) }
                        }
                )
            val theme by vm.theme.collectAsStateWithLifecycle()
            val dark = theme == "DARK" || (theme == "SYSTEM" && isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TraxosTheme(theme) {
                CompositionLocalProvider(
                    LocalLockController provides security,
                    LocalDevicePrompt provides showDevice,
                ) {
                    SecurityGate(security) { TraxosApp(vm) }
                }
            }
        }
    }
}
