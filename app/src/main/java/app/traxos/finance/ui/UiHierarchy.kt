package app.traxos.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Surface(shape = Radius.control, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

internal fun settingsIcon(title: String): ImageVector =
    when {
        title.contains("Profile") -> TraxosIcons.people
        title.contains("Security") || title.contains("Privacy") -> TraxosIcons.shield
        title.contains("Smart") || title.contains("review", true) -> TraxosIcons.spark
        title.contains("Notification") -> TraxosIcons.clock
        title.contains("Data") || title.contains("Backup") -> TraxosIcons.cloud
        title.contains("account", true) -> TraxosIcons.wallet
        title.contains("Navigation", true) || title.contains("navigation") -> TraxosIcons.tools
        else -> TraxosIcons.settings
    }

@Composable
internal fun ThemePicker(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    // Keep one readable line even with large accessibility fonts; scroll rather than clip.
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val selectorWidth = maxOf(maxWidth, 252.dp * scale)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(selectorWidth)) {
                TraxosSegment(
                    listOf("LIGHT" to "Light", "DARK" to "Dark", "SYSTEM" to "System"),
                    value,
                    enabled,
                    onChange,
                )
            }
        }
    }
}

@Composable
internal fun AccentPicker(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    Text(
        "ACCENT PALETTE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        accentOptions.forEach { option ->
            TraxosButton(
                { onChange(option.id) },
                Modifier.widthIn(min = 64.dp).testTag("accent_${option.id}").semantics {
                    selected = value == option.id
                },
                enabled,
                tone = ButtonTone.SECONDARY,
                contentPadding = PaddingValues(8.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(Radius.small).background(option.color))
                    Text(
                        (if (value == option.id) "✓ " else "") + option.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

internal data class ToolEntry(
    val route: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

@Composable
internal fun MoreScreen(navigate: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your Traxos tools", style = MaterialTheme.typography.titleLarge)
            Text(
                "Daily essentials first. Explore future screens without changing your money.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsGroup("EVERYDAY") {
            listOf(
                    ToolEntry("home", "Home", "Dashboard and recent activity", TraxosIcons.home),
                    ToolEntry(
                        "transactions",
                        "Transactions",
                        "Your searchable ledger",
                        TraxosIcons.ledger,
                    ),
                    ToolEntry(
                        "accounts",
                        "Accounts",
                        "Cash, banks, wallets and cards",
                        TraxosIcons.wallet,
                    ),
                    ToolEntry(
                        "gifts",
                        "Gift Cards",
                        "Codes, balances and expiry",
                        TraxosIcons.gift,
                    ),
                    ToolEntry(
                        "smart",
                        "Smart Add",
                        "Review shared text and SMS",
                        TraxosIcons.spark,
                    ),
                    ToolEntry(
                        "transfer",
                        "Transfer between accounts",
                        "Own money · not income or expense",
                        TraxosIcons.transfer,
                    ),
                )
                .forEach { ToolRow(it, false, navigate) }
        }
        SettingsGroup("MAKE IT YOURS") {
            listOf(
                    ToolEntry(
                        "settings",
                        "Settings",
                        "Profile, appearance and security",
                        TraxosIcons.settings,
                    ),
                    ToolEntry(
                        "categories",
                        "Categories",
                        "Your income and expense labels",
                        TraxosIcons.tag,
                    ),
                    ToolEntry(
                        "navigation_settings",
                        "Bottom navigation",
                        "Preview and customize your layout",
                        TraxosIcons.tools,
                    ),
                )
                .forEach { ToolRow(it, false, navigate) }
        }
        SettingsGroup("FULL V3 UI REVIEW") {
            TraxosNotice(
                "Preview screens use fictional sample data. They never save transactions or activate unfinished services.",
                NoticeKind.INFO,
            )
            ToolRow(
                ToolEntry(
                    "ui_review",
                    "Explore all screen flows",
                    "Onboarding, plans, people, backup and more",
                    TraxosIcons.tools,
                ),
                true,
                navigate,
            )
            listOf(
                    ToolEntry(
                        "goals",
                        "Savings & budgets",
                        "Targets, allocations and monthly limits",
                        TraxosIcons.target,
                    ),
                    ToolEntry(
                        "debts",
                        "Debts & people",
                        "Lend, borrow and repayment timelines",
                        TraxosIcons.people,
                    ),
                    ToolEntry(
                        "groups",
                        "Shared groups",
                        "Invites, splits and settlements",
                        TraxosIcons.people,
                    ),
                    ToolEntry(
                        "subscriptions",
                        "Subscriptions",
                        "Renewals, trials and reminder flows",
                        TraxosIcons.clock,
                    ),
                    ToolEntry(
                        "backup",
                        "Backup & restore",
                        "Portability, merge and conflict review",
                        TraxosIcons.cloud,
                    ),
                )
                .forEach { entry ->
                    ToolRow(entry.copy(route = "ui_review?screen=${entry.route}"), true, navigate)
                }
        }
    }
}

@Composable
internal fun ToolRow(entry: ToolEntry, preview: Boolean, navigate: (String) -> Unit) {
    TraxosButton(
        { navigate(entry.route) },
        Modifier.fillMaxWidth().testTag("tool_${entry.route}"),
        tone = ButtonTone.GHOST,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Icon(entry.icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (preview)
            Text(
                "PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        Icon(
            TraxosIcons.arrow,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
