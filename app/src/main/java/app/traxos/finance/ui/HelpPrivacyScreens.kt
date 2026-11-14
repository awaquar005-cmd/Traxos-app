package app.traxos.finance.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.traxos.finance.BuildConfig

internal data class HelpArticle(
    val title: String,
    val body: String,
    val route: String = "",
    val action: String = "Open",
)

internal val helpArticles =
    listOf(
        HelpArticle(
            "Keep your records safe",
            "Keep Traxos installed when updating. Never clear app storage or uninstall to fix an update. Choose a writable subfolder, create a password-encrypted backup, and keep its password separately. Verify recovery before relying on a copy. Card-vault details, app PIN and online sessions are excluded. Losing both local data and your password can make recovery impossible.",
            "backup",
            "Backup",
        ),
        HelpArticle(
            "Restore without replacing data",
            "Backups & recovery authenticates a selected archive and shows its contents before additive restore. Existing same-ID disagreements abort the restore rather than silently replacing records. No plaintext JSON import is offered. Keep the original backup until recovery is checked.",
            "backup",
            "Restore",
        ),
        HelpArticle(
            "Amounts and manual conversion",
            "Accounts remain in their original currency. Add a dated manual source→target rate; estimates use the latest rate not later than the transaction date. Inverse rates are optional, direct rates take priority, and rates are never chained. Rounding follows your setting. A missing rate leaves an amount unconverted; estimates do not change balances.",
            "settings/regional",
            "Rates",
        ),
        HelpArticle(
            "Transfers, cards and IOUs",
            "Transfers and card settlements are paired movements, never extra expenses. Loan principal uses separate account movements or matches existing records. Manage linked records from their original feature to keep Undo consistent. Recording a movement does not initiate or reverse a real bank payment.",
            "debts",
            "Debts",
        ),
        HelpArticle(
            "Budgets and savings allocations",
            "Monthly, custom and salary periods compare actual personal spending against limits. Goal earmarks do not move money. Optional paired transfers record real own-account movements. Later spending can underfund earmarks; review the warning rather than treating an allocation as extra cash.",
            "plans",
            "Plans",
        ),
        HelpArticle(
            "Smart Add and SMS permission",
            "Personal APK SMS detection is opt-in where Android permits it. Denied or revoked access keeps share/paste-text import available. Suggestions require explicit approval. OTPs and links are ignored; there is no cloud AI or automatic financial posting. Play builds remove SMS scanning permissions.",
            "smart",
            "Review",
        ),
        HelpArticle(
            "Gift cards and QR images",
            "Manual, paste/share and offline QR/barcode entry remain available. OCR text recognition was removed. Saved gift images and secret codes use app encryption. Reveal codes only when needed. Redemption remains separate from ordinary bank expenses.",
            "gifts",
            "Wallet",
        ),
        HelpArticle(
            "PIN and biometrics",
            "The local PIN is separate from any online password. Biometrics use Android's trusted prompt. There is no silent PIN-reset bypass or portable Keystore recovery. Offline PIN fallback remains available when configured; do not clear storage if you forget a PIN.",
            "settings/security",
            "Security",
        ),
        HelpArticle(
            "Reminders not arriving",
            "Enable Android notification permission and the relevant channel. Review finance reminder master switches, per-item offsets and quiet hours. WorkManager delivery is best effort and may be delayed by battery restrictions; no exact-time guarantee is made. Financial amounts and secrets are omitted from notification text.",
            "settings/notifications",
            "Alerts",
        ),
        HelpArticle(
            "Exports and privacy",
            "PDF/CSV are activity reports. Typed JSON preserves full financial metadata, stable IDs and exact integers, with explicit optional gift-secret inclusion. All exports are unencrypted. Encrypted recovery archives remain the safer recovery path. Card-vault/PIN/login secrets are never exported.",
            "settings/exports",
            "Export",
        ),
        HelpArticle(
            "Online account and Drive",
            "Local use does not need an online account. Drive authorization is separate from account sign-in. Firebase account sign-in requires your actual public project configuration. Group live sharing still awaits backend setup. An unavailable backend is not simulated as connected.",
            "cloud_setup",
            "Account",
        ),
        HelpArticle(
            "Navigation and accessibility",
            "Fresh Settings entry opens the homepage. Nested Back returns to its parent; Home always opens the dashboard. Customize implemented destinations, retain accessible creation/settings routes, and use additional text size/high contrast/haptic preferences without shrinking system accessibility settings.",
            "settings/accessibility",
            "Display",
        ),
    )

@Composable
internal fun HelpSupportScreen(navigate: (String) -> Unit) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf(false) }
    val report =
        "TRAXOS ${BuildConfig.VERSION_NAME}\nBuild ${BuildConfig.VERSION_CODE} · ${BuildConfig.FLAVOR}\nAndroid API ${Build.VERSION.SDK_INT}\nRoom schema15\nDescribe the steps and expected result below.\n\nNo accounts, amounts, identifiers, codes, PINs, message text or device IDs are included."
    if (diagnostics)
        TraxosSheet("Support report", { diagnostics = false }, true) {
            Text(report)
            Text(
                "Review anything you add. Never include gift codes, full card details, passwords or private transactions in a public issue.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                TraxosTextButton(
                    {
                        val clipboard =
                            context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Traxos support", report)
                        )
                        diagnostics = false
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Copy")
                }
                TraxosTextButton(
                    {
                        try {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, report),
                                    "Share support report",
                                )
                            )
                        } catch (e: Exception) {
                            error = "No sharing app is available"
                        }
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Share")
                }
            }
        }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("help_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Help & support", style = MaterialTheme.typography.titleLarge)
        TraxosInput("Search help", search, { search = it }, id = "help_search")
        val articles =
            helpArticles.filter {
                search.isBlank() ||
                    it.title.contains(search, true) ||
                    it.body.contains(search, true)
            }
        if (articles.isEmpty()) Text("No matching help. Try backup, PIN, rates or reminders.")
        articles.forEach { article ->
            TraxosCard {
                TraxosTextButton(
                    { expanded = if (expanded == article.title) "" else article.title },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(article.title)
                }
                if (expanded == article.title) {
                    Text(article.body, style = MaterialTheme.typography.bodyMedium)
                    if (article.route.isNotBlank())
                        TraxosTextButton({ navigate(article.route) }) { Text(article.action) }
                }
            }
        }
        SettingsRow("Support report", "Review/copy/share only non-sensitive build information") {
            diagnostics = true
        }
        SettingsRow(
            "Report an issue",
            "Opens the public GitHub issue page; nothing is sent automatically",
        ) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/awaquar005-cmd/Traxos-app/issues".toUri(),
                    )
                )
            } catch (e: Exception) {
                error =
                    "No browser is available. Open github.com/awaquar005-cmd/Traxos-app/issues manually."
            }
        }
        SettingsRow(
            "Privacy & permissions",
            "Local storage, optional services and real Android controls",
        ) {
            navigate("settings/privacy")
        }
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
    }
}

@Composable
internal fun PrivacySettingsScreen(navigate: (String) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("privacy_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Privacy & permissions", style = MaterialTheme.typography.titleLarge)
        Text(
            "Your financial records work offline. General Room financial metadata is app-private plaintext; app lock is an access gate, not full database encryption. Gift secrets, the card vault and security state use separate Android Keystore encryption. Device key loss can make secrets unrecoverable."
        )
        Text(
            "Optional Google/Firebase services use the network only for account/Drive actions. Backups are password-encrypted before upload and exclude card vault, app PIN and online credentials. No ads, cloud AI or analytics SDK is added; Google SDKs may process operational diagnostics under their policies."
        )
        Text(
            "SMS processing is optional in the Personal build and requires Android permission. No financial entry is saved without approval. Shared links and OTPs are ignored. Photos and contacts use explicit system pickers; only the chosen content is copied. Local profile photos are not uploaded or backed up. OCR is not available."
        )
        Text(
            "PDF/CSV/JSON exports are unencrypted. JSON can explicitly include gift codes, PINs and images; card-vault and login secrets remain excluded. Protect files you save or share. Saved people names/phones are included inside encrypted financial snapshots. WhatsApp sharing always requires your preview and explicit send."
        )
        Text(
            "Uninstalling or clearing storage removes local data. There is no silent PIN-reset bypass. Development builds allow screenshots for UI review; release builds protect them. Account sign-out does not erase your local financial records."
        )
        SettingsRow(
            "Android app permissions",
            "Review SMS, microphone and notification access; revoke when desired",
        ) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri(),
                    )
                )
            } catch (e: Exception) {
                error = "Android app settings are unavailable"
            }
        }
        SettingsRow("Security controls", "PIN, biometrics and sensitive-data visibility") {
            navigate("settings/security")
        }
        SettingsRow("Backups & recovery", "Create or inspect your encrypted personal snapshot") {
            navigate("backup")
        }
        SettingsRow("Exports", "Choose exactly which plaintext report to create") {
            navigate("settings/exports")
        }
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
    }
}

@Composable
internal fun NotificationChannels() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    listOf(
            "gift-expiry" to "Gift expiry channel",
            "finance_due" to "Money & planning channel",
            "smart-add" to "Smart Add channel",
        )
        .filter { it.first != "smart-add" || BuildConfig.SMS_ENABLED }
        .forEach { (id, label) ->
            SettingsRow(
                label,
                "Sound, vibration, lock-screen visibility and Android delivery controls",
            ) {
                try {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(
                        NotificationChannel(id, label, NotificationManager.IMPORTANCE_DEFAULT)
                    )
                    context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, id)
                    )
                } catch (e: Exception) {
                    error =
                        "Channel settings are unavailable. Use Android app notification settings instead."
                }
            }
        }
    error?.let { TraxosNotice(it, NoticeKind.ERROR) }
}
