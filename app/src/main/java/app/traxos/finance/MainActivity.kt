package app.traxos.finance

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.traxos.finance.ui.*

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveGiftIntent(intent)
    }

    private fun receiveGiftIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("open_gifts", false) == true) {
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
        super.onCreate(savedInstanceState)
        receiveGiftIntent(intent)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val repository = (application as TraxosApplication).repository
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
            TraxosTheme(theme) { TraxosApp(vm) }
        }
    }
}
