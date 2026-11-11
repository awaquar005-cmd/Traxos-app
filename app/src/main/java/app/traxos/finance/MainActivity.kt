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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
