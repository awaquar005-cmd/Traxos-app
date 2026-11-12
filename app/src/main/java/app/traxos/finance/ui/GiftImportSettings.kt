package app.traxos.finance.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.gifts.*
import kotlinx.coroutines.flow.first

@Composable
internal fun GiftImportScreen(
    vm: GiftViewModel,
    incoming: GiftIncoming?,
    consumed: () -> Unit,
    back: () -> Unit,
    review: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var detected by remember { mutableStateOf(false) }
    var resultReady by remember { mutableStateOf(false) }
    var extracted by remember { mutableStateOf(GiftExtraction()) }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    fun present(result: GiftExtraction, attachment: ByteArray? = null, openReview: Boolean = true) {
        extracted = result
        image = attachment
        resultReady = true
        detected = openReview
    }
    fun fromImage(uri: android.net.Uri, automatic: Boolean = false) {
        vm.action {
            val effective = vm.repository.dao.settings().first() ?: GiftSettings()
            require(effective.imageDetection) {
                "Enable image detection in Gift wallet settings first"
            }
            val bitmap = GiftImageImport.load(context, uri)
            try {
                val result = GiftImageImport.detect(bitmap, true)
                present(result.extraction, result.image, !automatic || effective.autoReview)
            } finally {
                bitmap.recycle()
            }
        }
    }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) fromImage(it)
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null)
                vm.action {
                    try {
                        val result = GiftImageImport.detect(bitmap, false)
                        require(result.extraction.code.isNotBlank()) {
                            "No single barcode found. Try a closer, well-lit image or import a cropped screenshot"
                        }
                        present(result.extraction, result.image)
                    } finally {
                        bitmap.recycle()
                    }
                }
        }
    LaunchedEffect(incoming) {
        when (val value = incoming) {
            is GiftIncoming.Text -> {
                text = value.value.take(16000)
                val effective = vm.repository.dao.settings().first() ?: GiftSettings()
                if (effective.textDetection)
                    present(GiftParser.parse(text), openReview = effective.autoReview)
            }
            is GiftIncoming.Image -> fromImage(value.uri, true)
            else -> Unit
        }
        consumed()
    }
    if (detected)
        TraxosSheet(
            "Gift card detected — Add to Traxos?",
            {
                detected = false
                resultReady = false
                image = null
            },
            scrollable = true,
        ) {
            Text(
                "Check every detail. Unknown or ambiguous values stay blank. Traxos never verifies balances with a merchant.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosInput(
                "Merchant",
                extracted.merchant,
                { extracted = extracted.copy(merchant = it) },
            )
            TraxosInput(
                "Gift card code",
                extracted.code,
                { extracted = extracted.copy(code = it) },
                secret = true,
            )
            TraxosInput(
                "Amount",
                extracted.amount,
                { extracted = extracted.copy(amount = it) },
                number = true,
            )
            TraxosCheckbox(
                extracted.amountIsBalance,
                { extracted = extracted.copy(amountIsBalance = it) },
                "Detected amount is the remaining balance",
            )
            TraxosInput(
                "Currency (ISO code)",
                extracted.currency,
                { extracted = extracted.copy(currency = it.uppercase()) },
            )
            TraxosInput(
                "Expiry (YYYY-MM-DD)",
                extracted.expiry,
                { extracted = extracted.copy(expiry = it) },
            )
            TraxosButton(
                {
                    vm.begin(extracted, image)
                    detected = false
                    resultReady = false
                    text = ""
                    image = null
                    review()
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("Add · Review full details")
            }
            TraxosTextButton({
                detected = false
                resultReady = false
                image = null
            }) {
                Text("Reject")
            }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Bring a gift into your wallet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Paste or share a message, scan a barcode, or select a screenshot. Everything is processed on this phone. Nothing is saved until you review and tap Save.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (resultReady && !detected)
            TraxosButton({ detected = true }, Modifier.fillMaxWidth(), !busy) {
                Text("Review detected card")
            }
        TraxosInput(
            "Message text",
            text,
            { text = it.take(16000) },
            enabled = !busy,
            multiline = true,
            id = "gift_import_text",
            secret = true,
        )
        TraxosTextButton(
            {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                text =
                    clipboard.primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        ?.take(16000)
                        .orEmpty()
            },
            enabled = !busy && settings.textDetection,
        ) {
            Text("Paste from clipboard")
        }
        TraxosButton(
            { present(GiftParser.parse(text)) },
            Modifier.fillMaxWidth(),
            !busy && settings.textDetection && text.isNotBlank(),
        ) {
            Text("Detect gift card")
        }
        TraxosButton(
            { pick.launch("image/*") },
            Modifier.fillMaxWidth(),
            !busy && settings.imageDetection,
            tone = ButtonTone.SECONDARY,
        ) {
            Text("Import screenshot · Offline OCR")
        }
        TraxosButton(
            {
                try {
                    camera.launch(null)
                } catch (_: android.content.ActivityNotFoundException) {
                    vm.action {
                        throw IllegalArgumentException(
                            "No camera app available. Import a barcode image instead"
                        )
                    }
                }
            },
            Modifier.fillMaxWidth(),
            !busy && settings.imageDetection,
            tone = ButtonTone.SECONDARY,
        ) {
            Text("Scan QR / barcode · Take photo")
        }
        if (busy) TraxosNotice("Reading on this phone…", NoticeKind.PROGRESS)
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        Text(
            "Camera scanning uses your phone's camera app, then reads the captured frame offline. For small or dense codes, import a cropped full-resolution image. Latin-script OCR is bundled. No SMS, camera or photo-library permission is requested by Traxos; no links are opened.",
            style = MaterialTheme.typography.bodySmall,
        )
        TraxosTextButton(back) { Text("Back to wallet") }
    }
}

@Composable
internal fun GiftSettingsScreen(vm: GiftViewModel) {
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var intervals by remember(prefs.intervals) { mutableStateOf(prefs.intervals) }
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
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
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = it
            if (it)
                (context.applicationContext as app.traxos.finance.TraxosApplication)
                    .giftScheduler
                    .reconcile()
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Gift wallet preferences", style = MaterialTheme.typography.headlineSmall)
        TraxosCard {
            GiftToggle("Expiry reminders", prefs.reminders, !busy) {
                vm.settings(prefs.copy(reminders = it))
            }
            Text(
                "Local reminders around 09:00. Android battery settings may delay delivery. Notification text never includes codes, PINs or balances.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosInput(
                "Days before expiry · 0 means expiry day",
                intervals,
                { intervals = it },
                enabled = !busy,
            )
            TraxosButton(
                { vm.settings(prefs.copy(intervals = intervals)) },
                enabled = !busy,
                tone = ButtonTone.SECONDARY,
            ) {
                Text("Save reminder intervals")
            }
            Text(
                if (allowed)
                    "Notifications are enabled for Traxos. Check the Gift card expiry channel in system settings."
                else "Notifications are blocked by Android. Wallet expiry badges still work.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosTextButton({
                if (
                    Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                )
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else
                    context.startActivity(
                        android.content
                            .Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(
                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                context.packageName,
                            )
                    )
            }) {
                Text("Manage notification permission")
            }
        }
        TraxosCard {
            GiftToggle("Hide codes until Reveal", prefs.hideCodes, !busy) {
                vm.settings(prefs.copy(hideCodes = it))
            }
            Text(
                "Codes and images are always omitted from lists and notifications. Hide is a display preference, not an app lock.",
                style = MaterialTheme.typography.bodySmall,
            )
            GiftToggle("Detect shared / pasted text", prefs.textDetection, !busy) {
                vm.settings(prefs.copy(textDetection = it))
            }
            GiftToggle("Offline image / barcode detection", prefs.imageDetection, !busy) {
                vm.settings(prefs.copy(imageDetection = it))
            }
            GiftToggle("Open import confirmation automatically", prefs.autoReview, !busy) {
                vm.settings(prefs.copy(autoReview = it))
            }
            Text(
                "Turning this off leaves shared-item results waiting for Review. Every mode requires confirmation and Save; unconfirmed cards are never stored. Direct SMS reading is unavailable: no SMS permission is requested.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosSelector(
                "Default sorting",
                prefs.sort,
                GiftRules.sorts.map { it to it.lowercase().replaceFirstChar(Char::titlecase) },
                !busy,
            ) {
                vm.settings(prefs.copy(sort = it))
            }
        }
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        Text(
            "Codes, PINs, notes and attachments use Android Keystore AES-GCM encryption. Merchant names, dates, currency and balances remain app-private Room metadata. There is no app lock, encrypted whole database or portable recovery yet. Do not uninstall or clear data.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GiftToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TraxosSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
internal fun MoreScreen(navigate: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your Traxos tools", style = MaterialTheme.typography.headlineSmall)
        listOf(
                "home" to "Home",
                "transactions" to "Transactions",
                "accounts" to "Accounts",
                "gifts" to "Gift Cards",
                "settings" to "Settings",
            )
            .forEach { (id, label) ->
                TraxosButton(
                    { navigate(id) },
                    Modifier.fillMaxWidth(),
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text(label)
                }
            }
    }
}
