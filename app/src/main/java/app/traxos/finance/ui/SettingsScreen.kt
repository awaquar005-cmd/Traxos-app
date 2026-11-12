package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.BuildConfig

@Composable
internal fun SettingsScreen(vm: MainViewModel, openNavigation: () -> Unit) {
    val loadedPreferences by vm.preferences.collectAsStateWithLifecycle()
    if (loadedPreferences == null) {
        ScreenSkeleton("settings")
        return
    }
    val preferences = requireNotNull(loadedPreferences)
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Make it yours", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (busy) "Saving your preference…" else "A familiar rhythm, your way.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TraxosCard {
            Text("Navigation customization", style = MaterialTheme.typography.titleMedium)
            Text(
                "Reorder or change the four screen slots. The center quick action is configured separately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TraxosButton(openNavigation, Modifier.fillMaxWidth(), tone = ButtonTone.SECONDARY) {
                Text("Customize navigation")
            }
        }
        TraxosCard {
            Text("Center quick action", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose the shortcut in the middle of the bottom bar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CenterShortcut.entries.forEach { choice ->
                ChoiceRow(choice.label, preferences.centerAction == choice.name, !busy) {
                    vm.centerAction(choice.name)
                }
            }
        }
        TraxosCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Hide financial amounts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Dashboard and lists. Entry and edit fields stay visible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TraxosSwitch(
                    checked = preferences.balancesHidden,
                    onCheckedChange = vm::balancesHidden,
                    enabled = !busy,
                )
            }
        }
        TraxosCard {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            listOf("SYSTEM" to "Follow system", "LIGHT" to "Light", "DARK" to "Dark").forEach {
                (key, label) ->
                ChoiceRow(label, preferences.theme == key, !busy) { vm.theme(key) }
            }
        }
        TraxosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandMark()
                Text(
                    "TRAXOS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Native Android · Your offline ledger",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Saved on this phone only. Uninstalling, clearing storage or losing the phone can remove your records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Development build. App-private storage; encrypted storage and backup are not implemented. No advertising, analytics or cloud SDKs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TraxosChoiceRow(label, selected, enabled = enabled, onClick = onClick)
}
