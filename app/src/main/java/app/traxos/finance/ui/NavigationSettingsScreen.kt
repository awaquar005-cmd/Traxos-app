package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.domain.NavigationLayout

@Composable
internal fun NavigationSettingsScreen(vm: MainViewModel) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preference =
        prefs
            ?: run {
                ScreenSkeleton("settings")
                return
            }
    val slots = NavigationLayout.decode(preference.navigationSlots)
    var restore by rememberSaveable { mutableStateOf(false) }
    if (restore)
        TraxosDialog(
            "Restore default navigation?",
            "The four screen slots return to their default order. Your center quick action and financial records are kept.",
            "Restore defaults",
            {
                restore = false
                vm.navigationSlots(NavigationLayout.defaults)
            },
            { restore = false },
        )
    var previewRoute by rememberSaveable { mutableStateOf("home") }
    var assign by rememberSaveable { mutableStateOf<String?>(null) }
    if (assign != null)
        TraxosSheet("Choose a slot", { assign = null }) {
            slots.forEachIndexed { index, id ->
                TraxosChoiceRow("Slot ${index+1} · ${screenLabel(id)}", false) {
                    vm.navigationSlots(NavigationLayout.replace(slots, index, assign!!))
                    assign = null
                }
            }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Arrange your everyday tools", style = MaterialTheme.typography.titleLarge)
        Text(
            "Four screen slots around an independent center action. Changes save immediately; duplicate screens swap positions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsGroup("LIVE BOTTOM BAR PREVIEW") {
            TraxosBottomBar(
                previewRoute,
                CenterShortcut.from(preference.centerAction),
                { previewRoute = it },
                !busy,
                slots,
                systemInsets = false,
            ) {
                previewRoute =
                    when (preference.centerAction) {
                        "HOME" -> "home"
                        "ACCOUNTS" -> "accounts"
                        "TRANSACTIONS" -> "transactions"
                        else -> "transactions"
                    }
            }
            Text(
                "Tap to preview the active indicator",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsGroup("ACTIVE SCREEN SLOTS · ${slots.size} OF 4") {
            slots.forEachIndexed { index, id ->
                Column(
                    Modifier.testTag("navigation_slot_$index"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            screenRegistry.first { it.id == id }.icon,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Slot ${index+1}",
                            Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TraxosIconButton(
                            { vm.navigationSlots(NavigationLayout.move(slots, index, index - 1)) },
                            enabled = !busy && index > 0,
                        ) {
                            Icon(
                                TraxosIcons.back,
                                "Move slot ${index+1} earlier",
                                Modifier.rotate(90f).size(18.dp),
                            )
                        }
                        TraxosIconButton(
                            { vm.navigationSlots(NavigationLayout.move(slots, index, index + 1)) },
                            enabled = !busy && index < slots.lastIndex,
                        ) {
                            Icon(
                                TraxosIcons.arrow,
                                "Move slot ${index+1} later",
                                Modifier.rotate(90f).size(18.dp),
                            )
                        }
                    }
                    TraxosSelector(
                        "Slot ${index+1}",
                        id,
                        screenRegistry.map { it.id to screenLabel(it.id) },
                        !busy,
                        sheet = false,
                    ) {
                        vm.navigationSlots(NavigationLayout.replace(slots, index, it))
                    }
                }
                if (index == 1)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            CenterShortcut.from(preference.centerAction).icon,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text("Center action", style = MaterialTheme.typography.titleSmall)
                            Text(
                                CenterShortcut.from(preference.centerAction).label +
                                    " · configured separately",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
            }
        }
        SettingsGroup("AVAILABLE SCREENS") {
            screenRegistry
                .filter { it.id !in slots }
                .forEach { entry ->
                    TraxosButton(
                        { assign = entry.id },
                        Modifier.fillMaxWidth(),
                        !busy,
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Icon(entry.icon, null, Modifier.size(20.dp))
                        Text(entry.label, Modifier.weight(1f))
                        Text("Assign", style = MaterialTheme.typography.labelSmall)
                    }
                }
            Text(
                "Future modules are reviewable in Full UI Review; only live screens can become real tabs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsGroup("NAVIGATION PRESETS") {
            listOf(
                    "Default ledger" to NavigationLayout.defaults,
                    "Wallet focus" to listOf("home", "gifts", "accounts", "more"),
                    "Smart tracking" to listOf("home", "transactions", "smart", "more"),
                )
                .forEach { (label, layout) ->
                    TraxosChoiceRow(label, slots == layout, enabled = !busy) {
                        vm.navigationSlots(layout)
                    }
                }
        }
        TraxosButton(
            { restore = true },
            Modifier.fillMaxWidth(),
            !busy,
            tone = ButtonTone.SECONDARY,
        ) {
            Text("Restore default navigation")
        }
        Text(
            if (busy) "Saving layout…" else "Layout saved on this phone",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
