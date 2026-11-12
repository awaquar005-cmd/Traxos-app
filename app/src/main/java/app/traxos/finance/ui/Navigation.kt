package app.traxos.finance.ui

import androidx.compose.foundation.BorderStroke
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
import app.traxos.finance.domain.NavigationLayout

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

internal data class ScreenEntry(val id: String, val label: String, val icon: ImageVector)

internal val screenRegistry =
    listOf(
        ScreenEntry("home", "Home", TraxosIcons.home),
        ScreenEntry("transactions", "Activity", TraxosIcons.ledger),
        ScreenEntry("accounts", "Accounts", TraxosIcons.wallet),
        ScreenEntry("settings", "Settings", TraxosIcons.settings),
        ScreenEntry("gifts", "Gift Cards", TraxosIcons.gift),
        ScreenEntry("more", "More", TraxosIcons.tools),
        ScreenEntry("smart", "Smart Add", TraxosIcons.spark),
        ScreenEntry("reports", "Reports", TraxosIcons.report),
    )

internal fun screenLabel(id: String) =
    if (id == "transactions") "Transactions" else screenRegistry.first { it.id == id }.label

@Composable
internal fun TraxosBottomBar(
    route: String,
    shortcut: CenterShortcut,
    navigate: (String) -> Unit,
    shortcutEnabled: Boolean = true,
    slots: List<String> = NavigationLayout.defaults,
    systemInsets: Boolean = true,
    launchShortcut: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (systemInsets) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val entries =
                NavigationLayout.validate(slots).map { id -> screenRegistry.first { it.id == id } }
            entries.take(2).forEach { item ->
                NavItem(
                    item.id,
                    item.label,
                    item.icon,
                    route,
                    navigate,
                    Modifier.weight(1f),
                    if (systemInsets) "" else "preview_",
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = launchShortcut,
                    enabled = shortcutEnabled,
                    shape = Radius.card,
                    border = BorderStroke(.5.dp, Color.White.copy(alpha = .20f)),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = Color.White,
                    modifier =
                        Modifier.size(48.dp)
                            .testTag(if (systemInsets) "center_action" else "preview_center_action"),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(shortcut.icon, "Shortcut: ${shortcut.label}", Modifier.size(23.dp))
                    }
                }
            }
            entries.drop(2).forEach { item ->
                NavItem(
                    item.id,
                    item.label,
                    item.icon,
                    route,
                    navigate,
                    Modifier.weight(1f),
                    if (systemInsets) "" else "preview_",
                )
            }
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
    tagPrefix: String = "",
) {
    val selected = key == route
    Column(
        modifier
            .heightIn(min = 52.dp)
            .testTag("${tagPrefix}nav_$key")
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
