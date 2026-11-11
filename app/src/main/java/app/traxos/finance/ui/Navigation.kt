package app.traxos.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class CenterShortcut(val label: String) {
    ADD_TRANSACTION("Add Transaction"),
    ACCOUNTS("Accounts"),
    TRANSACTIONS("Transactions"),
    HOME("Home");

    val icon: ImageVector
        get() =
            when (this) {
                ADD_TRANSACTION -> TraxosIcons.plus
                ACCOUNTS -> TraxosIcons.wallet
                TRANSACTIONS -> TraxosIcons.ledger
                HOME -> TraxosIcons.home
            }

    companion object {
        fun from(value: String) = entries.firstOrNull { it.name == value } ?: ADD_TRANSACTION
    }
}

@Composable
internal fun TraxosBottomBar(
    route: String,
    shortcut: CenterShortcut,
    navigate: (String) -> Unit,
    shortcutEnabled: Boolean = true,
    launchShortcut: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem("home", "Home", TraxosIcons.home, route, navigate, Modifier.weight(1f))
            NavItem(
                "transactions",
                "Activity",
                TraxosIcons.ledger,
                route,
                navigate,
                Modifier.weight(1f),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = launchShortcut,
                    enabled = shortcutEnabled,
                    shape = Radius.card,
                    color = Ink.cobalt,
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp).testTag("center_action"),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(shortcut.icon, "Shortcut: ${shortcut.label}", Modifier.size(23.dp))
                    }
                }
            }
            NavItem(
                "accounts",
                "Accounts",
                TraxosIcons.wallet,
                route,
                navigate,
                Modifier.weight(1f),
            )
            NavItem(
                "settings",
                "Settings",
                TraxosIcons.settings,
                route,
                navigate,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavItem(
    key: String,
    label: String,
    icon: ImageVector,
    route: String,
    navigate: (String) -> Unit,
    modifier: Modifier,
) {
    val selected = key == route
    Column(
        modifier
            .heightIn(min = 52.dp)
            .testTag("nav_$key")
            .selectable(selected = selected, role = Role.Tab, onClick = { navigate(key) })
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            null,
            Modifier.size(20.dp),
            tint =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.width(12.dp)
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    Radius.small,
                )
        )
    }
}
