package app.traxos.finance.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.BuildConfig
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.*

internal val settingSections =
    listOf(
        "profile" to "Profile",
        "security" to "Security & Privacy",
        "appearance" to "Appearance & Navigation",
        "smart" to "Smart Tracking",
        "notifications" to "Notifications",
        "data" to "Accounts & Data",
        "about" to "About & Support",
    )

@Composable
internal fun SettingsScreen(vm: MainViewModel, openSection: (String) -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    if (prefs == null) {
        ScreenSkeleton("settings")
        return
    }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preference = prefs!!
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsGroup("PROFILE") {
            SettingsRow(
                "Profile",
                preference.displayName.ifBlank { "Local profile · on this phone" },
            ) {
                openSection("profile")
            }
        }
        SettingsGroup("SYNC & RECOVERY") {
            SettingsRow("Sync center", "Not configured · Google and Supabase setup required") {
                openSection("cloud_setup")
            }
            SettingsRow(
                "Backups & recovery",
                "Google Drive and chosen-folder recovery · setup guide",
            ) {
                openSection("cloud_setup")
            }
        }
        SettingsGroup("PREFERENCES") {
            SettingsRow("Appearance & Navigation", "Theme, accent, dock and display preferences") {
                openSection("appearance")
            }
            SettingsRow("Bottom navigation", "Preview, reorder and choose your screen slots") {
                openSection("navigation")
            }
            SettingsRow("Currency & region", "Default currency; no automatic conversions") {
                openSection("data")
            }
        }
        SettingsGroup("SECURITY & TRACKING") {
            SettingsRow("Security & Privacy", "PIN, biometrics, auto-lock and masking") {
                openSection("security")
            }
            SettingsRow("Smart Tracking", "SMS, text import and QR/barcode preferences") {
                openSection("smart")
            }
            SettingsRow("Notifications", "Gift expiry and suggestion alerts") {
                openSection("notifications")
            }
        }
        SettingsGroup("MONEY & TOOLS") {
            SettingsRow("Monthly reports", "Category totals and matching activity") {
                openSection("reports")
            }
            SettingsRow("Deleted transactions", "Restore saved history; no automatic purge") {
                openSection("deleted")
            }
            SettingsRow(
                "Accounts & Data",
                "Accounts, categories, currency and saved entry options",
            ) {
                openSection("data")
            }
            Text(
                "Saved on this phone · no portable backup yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsGroup("HELP & DESIGN REVIEW") {
            SettingsRow("Full UI review", "Explore the V3 page flows · sample data only") {
                openSection("review")
            }
            SettingsRow(
                "About & Support",
                "Version ${BuildConfig.VERSION_NAME} · privacy and help",
            ) {
                openSection("about")
            }
        }
    }
}

@Composable
internal fun SettingsRow(title: String, summary: String, onClick: () -> Unit) {
    TraxosButton(
        onClick,
        Modifier.fillMaxWidth().testTag("setting_$title"),
        tone = ButtonTone.GHOST,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Icon(
            settingsIcon(title),
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            TraxosIcons.arrow,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingsToggle(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TraxosSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
internal fun SettingsDetailScreen(
    section: String,
    vm: MainViewModel,
    gifts: GiftViewModel,
    openNavigation: () -> Unit,
    openAccounts: () -> Unit,
    openData: () -> Unit,
    openCatalog: (String) -> Unit = {},
) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val read by vm.reads.collectAsStateWithLifecycle()
    val giftPrefs by gifts.settings.collectAsStateWithLifecycle()
    val giftBusy by gifts.busy.collectAsStateWithLifecycle()
    val giftError by gifts.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs =
        p
            ?: run {
                ScreenSkeleton("settings")
                return
            }
    var privacy by remember { mutableStateOf(false) }
    if (privacy)
        TraxosSheet("Privacy information", { privacy = false }, scrollable = true) {
            Text(
                "Personal records stay on this phone. The app has no internet permission, cloud AI, advertising or analytics upload. Imported content is untrusted: links and OTPs are ignored."
            )
            Text(
                "Gift secrets and Smart Add suggestions are encrypted with separate Android Keystore keys. Security state is encrypted; the existing bank/cash ledger and metadata are app-private but not encrypted. App lock gates access; it is not full database encryption. Screenshot protection is disabled in development builds for UI review."
            )
            Text(
                "No portable backup or PIN reset is available. Uninstalling, clearing data or losing keys can lose records or access. No online profile, email change or photo upload is implemented."
            )
            TraxosButton({ privacy = false }, Modifier.fillMaxWidth()) { Text("Done") }
        }
    if (section == "security") {
        val lock = LocalLockController.current
        if (lock != null)
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsToggle(
                        "Hide financial amounts",
                        "Dashboard and lists; entry fields stay visible",
                        prefs.balancesHidden,
                        !busy,
                        vm::balancesHidden,
                    )
                    SettingsToggle(
                        "Hide gift codes until Reveal",
                        "Codes/PINs are never notification content",
                        giftPrefs.hideCodes,
                        !giftBusy,
                    ) {
                        gifts.changeSettings { current -> current.copy(hideCodes = it) }
                    }
                }
                Box(Modifier.weight(1f)) { SecuritySettingsScreen(lock) }
            }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (section) {
            "profile" -> {
                var name by remember(prefs.displayName) { mutableStateOf(prefs.displayName) }
                Text("Local profile", style = MaterialTheme.typography.titleLarge)
                TraxosInput(
                    "Display name",
                    name,
                    { name = it.take(80) },
                    enabled = !busy,
                    id = "profile_name",
                )
                TraxosButton({ vm.displayName(name) }, Modifier.fillMaxWidth(), !busy, busy) {
                    Text("Save display name")
                }
                Text(
                    "No online account is connected. Email changes and profile photo uploads are unavailable; this local name does not authenticate an account.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingsRow("Account preferences", "Choose default currency and entry accounts") {
                    openData()
                }
            }
            "appearance" -> {
                Text("Appearance", style = MaterialTheme.typography.titleLarge)
                ThemePicker(prefs.theme, !busy, vm::theme)
                AccentPicker(prefs.accent, !busy, vm::accent)
                SettingsRow(
                    "Customize navigation",
                    "Change, reorder or reset the four screen slots",
                    openNavigation,
                )
                Text("Center quick action", style = MaterialTheme.typography.titleMedium)
                CenterShortcut.entries.forEach { choice ->
                    TraxosChoiceRow(
                        choice.label,
                        prefs.centerAction == choice.name,
                        enabled = !busy,
                    ) {
                        vm.centerAction(choice.name)
                    }
                }
                Text(
                    "Page transitions are instant to prevent overlapping text. Native sheet motion follows Android settings; no additional animation control is simulated.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            "smart" -> {
                SettingsToggle(
                    "Smart Add suggestions",
                    "Parse only messages you share/paste; approval is always required",
                    prefs.smartEnabled,
                    !busy,
                ) {
                    vm.smartEnabled(it)
                }
                SettingsToggle(
                    "Gift text detection",
                    "Extract gift/voucher details locally",
                    giftPrefs.textDetection,
                    !giftBusy,
                ) {
                    gifts.changeSettings { current -> current.copy(textDetection = it) }
                }
                SettingsToggle(
                    "QR / barcode scanning",
                    "Offline QR/barcode values only; no text recognition",
                    giftPrefs.imageDetection,
                    !giftBusy,
                ) {
                    gifts.changeSettings { current -> current.copy(imageDetection = it) }
                }
                SettingsToggle(
                    "Open import confirmation automatically",
                    "Off leaves shared results waiting for Review; saving still needs confirmation",
                    giftPrefs.autoReview,
                    !giftBusy,
                ) {
                    gifts.changeSettings { current -> current.copy(autoReview = it) }
                }
                Text(
                    "No automatic approval. Transfers, card settlements and refunds require matching and cannot be posted as ordinary expenses/income.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SmsSettings(prefs, vm, busy)
                val health = context.getSharedPreferences("smart_health", Context.MODE_PRIVATE)
                var failed by remember { mutableStateOf(health.getBoolean("ingestFailure", false)) }
                if (failed) {
                    TraxosNotice(
                        "Incoming processing needs review. Check Smart Add or share/paste manually. Financial entries are never added automatically.",
                        NoticeKind.WARNING,
                    )
                    TraxosTextButton({
                        health.edit { putBoolean("ingestFailure", false) }
                        failed = false
                    }) {
                        Text("Dismiss import warning")
                    }
                }
                SettingsRow("Default transaction choices", "Account and expense category", openData)
            }
            "notifications" -> {
                Text("Gift expiry alerts", style = MaterialTheme.typography.titleLarge)
                SettingsToggle(
                    "Expiry reminders",
                    "Local, around 09:00; Android can delay delivery",
                    giftPrefs.reminders,
                    !giftBusy,
                ) {
                    gifts.changeSettings { current -> current.copy(reminders = it) }
                }
                var intervals by
                    remember(giftPrefs.intervals) { mutableStateOf(giftPrefs.intervals) }
                TraxosInput(
                    "Days before expiry · 0 is expiry day",
                    intervals,
                    { intervals = it },
                    enabled = !giftBusy,
                )
                TraxosButton(
                    { gifts.changeSettings { current -> current.copy(intervals = intervals) } },
                    Modifier.fillMaxWidth(),
                    !giftBusy,
                    giftBusy,
                ) {
                    Text("Save reminder intervals")
                }
                if (BuildConfig.SMS_ENABLED)
                    SettingsToggle(
                        "Transaction suggestion alerts",
                        "Generic Review notification; never posts a transaction",
                        prefs.suggestionNotifications,
                        !busy,
                    ) {
                        vm.suggestionNotices(it)
                    }
                else
                    Text(
                        "Automatic SMS suggestion alerts are unavailable in this Play build. Shared/pasted suggestions are reviewed inside Smart Add.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                NotificationPermissionRow()
            }
            "data" -> {
                TraxosSelector(
                    "Default currency",
                    prefs.defaultCurrency,
                    Money.currencies.map { it to it },
                    !busy,
                ) {
                    vm.currency(it)
                }
                Text(
                    "Used for new accounts and suggested gift/import currency. Existing financial records and balances are never converted.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosSelector(
                    "Activity order",
                    prefs.activitySort,
                    listOf("NEWEST" to "Newest first", "OLDEST" to "Oldest first"),
                    !busy,
                ) {
                    vm.activitySort(it)
                }
                val ready = read as? ReadState.Ready
                if (ready != null) {
                    AccountPicker(
                        vm.repository,
                        prefs.defaultAccountId,
                        ready.accounts,
                        !busy,
                        "Default transaction account",
                    ) {
                        vm.defaultAccount(it)
                    }
                    TraxosTextButton({ vm.defaultAccount("") }, enabled = !busy) {
                        Text("Use recent account")
                    }
                    CategoryPicker(
                        vm.repository,
                        prefs.defaultExpenseCategoryId,
                        ready.categories,
                        "EXPENSE",
                        !busy,
                        "Default expense category",
                    ) {
                        vm.defaultCategory(it)
                    }
                    TraxosTextButton({ vm.defaultCategory("") }, enabled = !busy) {
                        Text("Use recent expense category")
                    }
                }
                SettingsGroup("MANAGE ACCOUNTS & LABELS") {
                    SettingsRow(
                        "Manage financial accounts",
                        "Cash, bank, wallet and card balances",
                        openAccounts,
                    )
                    SettingsRow(
                        "Categories",
                        "Create, rename, style or archive",
                        { openCatalog("categories") },
                    )
                    SettingsRow(
                        "Merchants",
                        "Saved merchant names",
                        { openCatalog("labels/MERCHANT") },
                    )
                    SettingsRow(
                        "Descriptions",
                        "Frequently used transaction notes",
                        { openCatalog("labels/DESCRIPTION") },
                    )
                }
                SettingsGroup("LEDGER TOOLS") {
                    SettingsRow(
                        "Deleted transactions",
                        "Restore financial entries",
                        { openCatalog("deleted") },
                    )
                    SettingsRow(
                        "Monthly reports",
                        "Calendar-month category summaries",
                        { openCatalog("reports") },
                    )
                }
                Text(
                    "Export, portable encrypted backup and cloud sync are unavailable. Records are saved locally; uninstalling/clearing storage deletes them. App lock does not add a recovery mechanism.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            "about" -> {
                BrandMark()
                Text(
                    "TRAXOS ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.FLAVOR} · Offline first",
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingsRow(
                    "Feedback",
                    "Open the project's GitHub issue page; submitting is your choice",
                ) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/awaquar005-cmd/Traxos-app/issues".toUri(),
                        )
                    )
                }
                SettingsRow(
                    "Privacy information",
                    "Storage, permissions, lock and recovery limits",
                ) {
                    privacy = true
                }
                Text(
                    "Store rating is unavailable: this development build has no published Google Play listing.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (giftError != null) TraxosNotice(giftError!!, NoticeKind.ERROR)
        if (busy || giftBusy)
            Text("Saving preference…", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NotificationPermissionRow() {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = it
            (context.applicationContext as app.traxos.finance.TraxosApplication)
                .giftScheduler
                .reconcile()
        }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                    allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Text(
        if (allowed) "Android notifications enabled; channel settings still apply."
        else "Android notifications are blocked. Wallet badges remain available.",
        style = MaterialTheme.typography.bodySmall,
    )
    TraxosButton(
        {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
            )
                request.launch(Manifest.permission.POST_NOTIFICATIONS)
            else
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
        },
        Modifier.fillMaxWidth(),
        tone = ButtonTone.SECONDARY,
    ) {
        Text("Manage notification permission")
    }
}
