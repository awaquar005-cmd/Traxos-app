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
    saved: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val globalPrefs by vm.localPreferences.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var detected by remember { mutableStateOf(false) }
    var resultReady by remember { mutableStateOf(false) }
    var reviewDetails by remember { mutableStateOf(false) }
    var extracted by remember { mutableStateOf(GiftExtraction()) }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    fun present(result: GiftExtraction, attachment: ByteArray? = null, openReview: Boolean = true) {
        extracted = result
        image = attachment
        resultReady = true
        detected = openReview
    }
    var sourceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cropOpen by remember { mutableStateOf(false) }
    var cameraPath by
        androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    // UI bitmaps follow GC lifetime: recycling on page disposal can race an in-flight ML Kit task.
    fun recognize(bitmap: android.graphics.Bitmap, automatic: Boolean = false) {
        vm.action {
            val effective = vm.repository.dao.settings().first() ?: GiftSettings()
            require(effective.imageDetection) {
                "Enable QR/barcode scanning in Gift wallet settings first"
            }
            val result = BarcodeImageImport.detect(bitmap)
            present(result.extraction, result.image, !automatic || effective.autoReview)
            cropOpen = false
        }
    }
    fun fromImage(uri: android.net.Uri, automatic: Boolean = false) {
        vm.action {
            val effective = vm.repository.dao.settings().first() ?: GiftSettings()
            require(effective.imageDetection) {
                "Enable QR/barcode scanning in Gift wallet settings first"
            }
            val bitmap = BarcodeImageImport.load(context, uri)
            sourceBitmap = bitmap
            if (uri.authority == "${context.packageName}.capture") {
                cameraPath?.let { java.io.File(it).delete() }
                cameraPath = null
            }
            val result = BarcodeImageImport.detect(bitmap)
            present(result.extraction, result.image, !automatic || effective.autoReview)
        }
    }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) fromImage(it)
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val path = cameraPath
            if (ok && path != null) {
                val file = java.io.File(path)
                val uri =
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.capture",
                        file,
                    )
                fromImage(uri)
            } else if (path != null) java.io.File(path).delete()
        }
    if (cropOpen && sourceBitmap != null)
        GiftCropSheet(sourceBitmap!!, busy, { if (!busy) cropOpen = false }) { bitmap ->
            vm.action {
                try {
                    val result = BarcodeImageImport.detect(bitmap)
                    present(result.extraction, result.image)
                    cropOpen = false
                } finally {
                    if (bitmap !== sourceBitmap) bitmap.recycle()
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
            if (extracted.warnings.isNotEmpty())
                TraxosNotice(extracted.warnings.joinToString(" · "), NoticeKind.WARNING)
            if (sourceBitmap != null)
                TraxosTextButton(
                    {
                        detected = false
                        cropOpen = true
                    },
                    enabled = !busy,
                ) {
                    Text("Crop / rescan")
                }
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
            TraxosTextButton({ reviewDetails = !reviewDetails }) {
                Text(if (reviewDetails) "Less optional details" else "Optional details")
            }
            if (reviewDetails)
                TraxosInput(
                    "PIN (optional)",
                    extracted.pin,
                    { extracted = extracted.copy(pin = it) },
                    secret = true,
                )
            extracted.candidates
                .groupBy { it.field }
                .filter { it.value.distinctBy { v -> v.value }.size > 1 }
                .forEach { (field, values) ->
                    TraxosSelector(
                        "Choose detected $field",
                        "",
                        values
                            .distinctBy { it.value }
                            .map {
                                it.value to
                                    if (field == "code" || field == "pin")
                                        "Candidate ${values.indexOf(it)+1} · tap to review"
                                    else it.value.replace('|', ' ')
                            },
                        !busy,
                    ) { value ->
                        extracted =
                            when (field) {
                                "merchant" -> extracted.copy(merchant = value)
                                "code" -> extracted.copy(code = value)
                                "pin" -> extracted.copy(pin = value)
                                "amount" ->
                                    extracted.copy(
                                        amount = value.substringBefore('|'),
                                        amountIsBalance =
                                            values.firstOrNull { it.value == value }?.isBalance ==
                                                true,
                                        currency = value.substringAfter('|', extracted.currency),
                                    )
                                "expiry" -> extracted.copy(expiry = value)
                                else -> extracted
                            }
                    }
                }
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
                    vm.begin(extracted, image, globalPrefs?.defaultCurrency ?: "INR")
                    vm.save { id ->
                        detected = false
                        resultReady = false
                        text = ""
                        image = null
                        if (saved != null) saved(id) else back()
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = !busy,
                busy = busy,
            ) {
                Text("Add gift card")
            }
            TraxosTextButton(
                {
                    vm.begin(extracted, image, globalPrefs?.defaultCurrency ?: "INR")
                    detected = false
                    review()
                },
                enabled = !busy,
            ) {
                Text("More details before saving")
            }
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
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
            "Paste or share a message, or scan a QR/barcode. Everything is processed on this phone. Nothing is saved until you review and tap Save.",
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
            Text("Choose QR / barcode image")
        }
        TraxosButton(
            {
                try {
                    val folder = java.io.File(context.cacheDir, "gift-capture").apply { mkdirs() }
                    folder
                        .listFiles()
                        ?.filter { System.currentTimeMillis() - it.lastModified() > 3600000 }
                        ?.forEach { it.delete() }
                    val file = java.io.File.createTempFile("capture-", ".jpg", folder)
                    cameraPath = file.absolutePath
                    camera.launch(
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.capture",
                            file,
                        )
                    )
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
            Text("Scan QR / barcode photo")
        }
        if (sourceBitmap != null)
            TraxosButton(
                { cropOpen = true },
                Modifier.fillMaxWidth(),
                !busy,
                tone = ButtonTone.SECONDARY,
            ) {
                Text("Crop / rotate / rescan image")
            }
        if (busy) TraxosNotice("Reading on this phone…", NoticeKind.PROGRESS)
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        Text(
            "Only QR/barcode values are read from photos. Printed text is not scanned. No camera or photo-library permission is requested by Traxos; links are never opened.",
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
                vm.changeSettings { current -> current.copy(reminders = it) }
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
                { vm.changeSettings { current -> current.copy(intervals = intervals) } },
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
                vm.changeSettings { current -> current.copy(hideCodes = it) }
            }
            Text(
                "Codes and images are always omitted from lists and notifications. Hide is a display preference, not an app lock.",
                style = MaterialTheme.typography.bodySmall,
            )
            GiftToggle("Detect shared / pasted text", prefs.textDetection, !busy) {
                vm.changeSettings { current -> current.copy(textDetection = it) }
            }
            GiftToggle("QR / barcode scanning", prefs.imageDetection, !busy) {
                vm.changeSettings { current -> current.copy(imageDetection = it) }
            }
            GiftToggle("Open import confirmation automatically", prefs.autoReview, !busy) {
                vm.changeSettings { current -> current.copy(autoReview = it) }
            }
            Text(
                "Turning this off leaves shared-item results waiting for Review. Every mode requires confirmation and Save; unconfirmed cards are never stored. Incoming SMS suggestions depend on your installed build and consent in Smart Tracking settings.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosSelector(
                "Default sorting",
                prefs.sort,
                GiftRules.sorts.map { it to it.lowercase().replaceFirstChar(Char::titlecase) },
                !busy,
            ) {
                vm.changeSettings { current -> current.copy(sort = it) }
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
                "smart" to "Smart Add",
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
