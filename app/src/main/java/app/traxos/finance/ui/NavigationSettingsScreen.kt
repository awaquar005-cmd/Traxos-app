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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your navigation", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose each screen slot, left to right around the center action. Choosing a screen already in use swaps its position.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        slots.forEachIndexed { index, id ->
            TraxosCard(Modifier.testTag("navigation_slot_$index")) {
                TraxosSelector(
                    "Slot ${index+1}",
                    id,
                    screenRegistry.map { it.id to screenLabel(it.id) },
                    !busy,
                    sheet = false,
                ) {
                    vm.navigationSlots(NavigationLayout.replace(slots, index, it))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (busy) "Saving…" else "Position ${index+1} of ${slots.size}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TraxosIconButton(
                        { vm.navigationSlots(NavigationLayout.move(slots, index, index - 1)) },
                        enabled = !busy && index > 0,
                    ) {
                        Icon(
                            TraxosIcons.back,
                            "Move slot ${index+1} earlier",
                            Modifier.rotate(90f).size(20.dp),
                        )
                    }
                    TraxosIconButton(
                        { vm.navigationSlots(NavigationLayout.move(slots, index, index + 1)) },
                        enabled = !busy && index < slots.lastIndex,
                    ) {
                        Icon(
                            TraxosIcons.arrow,
                            "Move slot ${index+1} later",
                            Modifier.rotate(90f).size(20.dp),
                        )
                    }
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
            "Only implemented screens are offered. The center quick action is independent of this layout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
