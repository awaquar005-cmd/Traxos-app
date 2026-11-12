package app.traxos.finance.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import app.traxos.finance.data.Preferences

@Composable
internal fun SmsSettings(prefs: Preferences, vm: MainViewModel, busy: Boolean) {
    Text(
        "Play build: SMS permissions and automatic SMS reception are excluded. Share or paste text into Smart Add for private offline review.",
        style = MaterialTheme.typography.bodySmall,
    )
}
