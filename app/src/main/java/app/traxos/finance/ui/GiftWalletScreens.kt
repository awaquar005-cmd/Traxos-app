package app.traxos.finance.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun GiftWalletScreen(
    vm: GiftViewModel,
    open: (String) -> Unit,
    add: () -> Unit,
    import: () -> Unit,
    settings: () -> Unit,
    hidden: Boolean = false,
) {
    val today = giftToday()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val prefs by vm.settings.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    LaunchedEffect(search) {
        delay(250)
        query = search.trim()
    }
    val error by vm.error.collectAsStateWithLifecycle()
    if (cards == null) {
        ScreenSkeleton("accounts")
        return
    }
    val all = cards.orEmpty()
    val visible =
        remember(all, query, filter, prefs.sort, today) {
            val result =
                all.filter {
                    (it.merchant.contains(query, true) || it.name.contains(query, true)) &&
                        when (filter) {
                            "ACTIVE" -> GiftRules.state(it, today) in listOf("ACTIVE", "EXPIRING")
                            "EXPIRING" -> GiftRules.state(it, today) == "EXPIRING"
                            "PAST" -> GiftRules.state(it, today) in listOf("REDEEMED", "EXPIRED")
                            "ARCHIVED" -> it.archived
                            else -> !it.archived
                        }
                }
            when (prefs.sort) {
                "MERCHANT" -> result.sortedBy { it.merchant.lowercase() }
                "NEWEST" -> result.sortedByDescending { it.createdAt }
                "BALANCE" ->
                    result.sortedWith(
                        compareBy<GiftCard> { it.currency }.thenByDescending { it.remainingMinor }
                    )
                else -> result.sortedBy { it.expiryDay ?: Long.MAX_VALUE }
            }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("gift_wallet"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Value waiting for you", style = MaterialTheme.typography.headlineSmall) }
        item {
            TraxosCard(Modifier.fillMaxWidth()) {
                val active =
                    all.filter {
                        GiftRules.state(it, today) in listOf("ACTIVE", "EXPIRING") &&
                            it.kind != "DISCOUNT"
                    }
                Text("AVAILABLE STORED VALUE", style = MaterialTheme.typography.labelSmall)
                val sums = active.filter { it.remainingMinor != null }.groupBy { it.currency }
                if (sums.isEmpty())
                    Text("No confirmed balance", style = MaterialTheme.typography.titleMedium)
                sums.forEach { (currency, cards) ->
                    Text(
                        giftWalletMoney(
                            hidden,
                            cards.fold(0L) { n, c -> Math.addExact(n, c.remainingMinor!!) },
                            currency,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Text(
                    "${all.count { GiftRules.state(it,today) in listOf("ACTIVE","EXPIRING") }} active · ${all.count { GiftRules.state(it,today)=="EXPIRING" }} expiring soon · ${all.count { GiftRules.state(it,today) in listOf("REDEEMED","EXPIRED") }} redeemed/expired",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "${active.count { it.remainingMinor==null }} unknown balances · Discount codes excluded",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosButton(add, Modifier.weight(1f)) { Text("Add card") }
                TraxosButton(import, Modifier.weight(1f), tone = ButtonTone.SECONDARY) {
                    Text("Smart import")
                }
            }
        }
        item { TraxosInput("Search wallet", search, { search = it }, id = "gift_search") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    TraxosSelector(
                        "Filter",
                        filter,
                        listOf(
                            "ALL" to "All cards",
                            "ACTIVE" to "Active",
                            "EXPIRING" to "Expiring soon",
                            "PAST" to "Redeemed / expired",
                            "ARCHIVED" to "Archived",
                        ),
                    ) {
                        filter = it
                    }
                }
                Box(Modifier.weight(1f)) {
                    TraxosSelector(
                        "Sort",
                        prefs.sort,
                        GiftRules.sorts.map {
                            it to it.lowercase().replaceFirstChar(Char::titlecase)
                        },
                    ) {
                        vm.changeSettings { current -> current.copy(sort = it) }
                    }
                }
            }
        }
        if (error != null) item { TraxosNotice(error!!, NoticeKind.ERROR) }
        if (visible.isEmpty())
            item {
                Message(
                    "${if(all.isEmpty()) "Your gift wallet starts here" else "No matching cards"}",
                    "Add a gift card or review text, a photo or a barcode. Nothing is saved without your confirmation.",
                    "Add card",
                    add,
                )
            }
        items(visible, key = { it.id }) { c ->
            TraxosCard {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        c.merchant,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        GiftRules.state(c, today).lowercase().replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (GiftRules.state(c, today) in listOf("EXPIRING", "EXPIRED"))
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    c.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (c.kind == "DISCOUNT") "Discount voucher · No cash balance"
                    else
                        c.remainingMinor?.let { giftWalletMoney(hidden, it, c.currency) }
                            ?: "Balance not confirmed",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    c.expiryDay?.let { "Expires ${LocalDate.ofEpochDay(it)}" }
                        ?: "No expiry recorded",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosTextButton({ open(c.id) }) { Text("Open card") }
            }
        }
        item { TraxosTextButton(settings) { Text("Gift wallet settings") } }
        item {
            Text(
                "Gift value is kept separate from cash/bank balances. Merchant terms determine redemption rules.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun GiftFormScreen(
    vm: GiftViewModel,
    id: String,
    back: () -> Unit,
    saved: (String) -> Unit,
    scan: () -> Unit = {},
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var details by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }
    LaunchedEffect(draft?.code, draft?.merchant, draft?.kind) {
        delay(300)
        val d = draft
        if (d != null) {
            try {
                duplicate = vm.duplicate(d.code, d.id, d.kind, d.merchant)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                duplicate = false
            }
        }
    }
    var discard by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id == "new") {
            if (vm.draft.value == null) vm.begin()
        } else if (vm.draft.value?.id != id) vm.edit(id)
    }
    androidx.activity.compose.BackHandler { if (!busy) discard = true }
    if (discard)
        TraxosDialog(
            "Discard this draft?",
            "Unsaved gift card details will be removed from memory.",
            "Discard",
            {
                vm.discard()
                back()
            },
            { discard = false },
            destructive = true,
            enabled = !busy,
        )
    val keyboard = LocalSoftwareKeyboardController.current
    TraxosFormPanel {
        Column(Modifier.fillMaxWidth().testTag("gift_form")) {
            TraxosToolbar(
                if (id == "new") "Add gift card" else "Edit gift card",
                back = { if (!busy) discard = true },
            )
            val d = draft
            if (d == null) {
                if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
                else ScreenSkeleton("accounts")
                return@Column
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TraxosInput(
                    "Merchant",
                    d.merchant,
                    { v -> vm.update { it.copy(merchant = v) } },
                    enabled = !busy,
                    id = "gift_merchant",
                )
                val ledger =
                    remember(vm.repository) {
                        app.traxos.finance.data.LedgerRepository(vm.repository.db)
                    }
                SavedLabelPicker(ledger, "MERCHANT") { merchant ->
                    vm.update { it.copy(merchant = merchant.name) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosButton(scan, Modifier.weight(1f), !busy, tone = ButtonTone.SECONDARY) {
                        Text("Scan QR / barcode")
                    }
                    TraxosButton(
                        {
                            val clip =
                                context
                                    .getSystemService(ClipboardManager::class.java)
                                    .primaryClip
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    .orEmpty()
                            val e = GiftParser.parse(clip)
                            if (e.code.isNotBlank()) vm.applyExtraction(e, null)
                            else if (
                                !ImportTextSafety.isLink(clip) && !ImportTextSafety.isOtp(clip)
                            )
                                vm.update { it.copy(code = clip.take(512)) }
                            else
                                vm.action {
                                    throw IllegalArgumentException(
                                        "Links and one-time passwords cannot be gift codes"
                                    )
                                }
                        },
                        Modifier.weight(1f),
                        !busy,
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Paste code")
                    }
                }
                TraxosInput(
                    "Gift card code",
                    d.code,
                    { v -> vm.update { it.copy(code = v) } },
                    enabled = !busy,
                    id = "gift_code",
                    secret = true,
                )
                if (duplicate)
                    TraxosNotice("This code is already in your wallet", NoticeKind.WARNING)
                if (d.kind != "DISCOUNT") {
                    TraxosInput(
                        "Amount · remaining value, if known",
                        d.remaining,
                        { v -> vm.update { it.copy(remaining = v) } },
                        enabled = !busy && !d.historyLocked,
                        number = true,
                        id = "gift_remaining",
                        placeholder = if (d.revision == 0L && !d.historyLocked) d.original else "",
                    )
                    Text(
                        if (d.revision == 0L && !d.historyLocked && d.original.isNotBlank())
                            "${d.currency} · Blank uses original value"
                        else "${d.currency} · Leave blank if unknown",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TraxosInput(
                    "Expiry (optional · YYYY-MM-DD)",
                    d.expiry,
                    { v -> vm.update { it.copy(expiry = v) } },
                    enabled = !busy,
                    id = "gift_expiry",
                )
                TraxosTextButton({ vm.update { it.copy(expiry = "") } }, enabled = !busy) {
                    Text("No expiry")
                }
                TraxosButton(
                    { details = !details },
                    Modifier.fillMaxWidth(),
                    !busy,
                    tone = ButtonTone.GHOST,
                ) {
                    Text(if (details) "Less details" else "More details")
                }
                if (details)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TraxosInput(
                            "Card name",
                            d.name,
                            { v -> vm.update { it.copy(name = v) } },
                            enabled = !busy,
                            id = "gift_name",
                        )
                        TraxosSelector(
                            "Card type",
                            d.kind,
                            listOf(
                                "STORED_VALUE" to "Stored-value card",
                                "SINGLE_USE" to "Single-use voucher",
                                "DISCOUNT" to "Discount / promotional code",
                            ),
                            !busy && !d.historyLocked,
                        ) { v ->
                            vm.update { it.copy(kind = v, partial = false) }
                        }
                        TraxosInput(
                            "PIN (optional)",
                            d.pin,
                            { v -> vm.update { it.copy(pin = v) } },
                            enabled = !busy,
                            id = "gift_pin",
                            secret = true,
                        )
                        if (d.kind != "DISCOUNT") {
                            TraxosSelector(
                                "Currency",
                                d.currency,
                                Money.currencies.map { it to it },
                                !busy && !d.historyLocked,
                            ) { v ->
                                vm.update { it.copy(currency = v) }
                            }
                            TraxosInput(
                                "Original value (optional)",
                                d.original,
                                { v -> vm.update { it.copy(original = v) } },
                                enabled = !busy && !d.historyLocked,
                                number = true,
                                id = "gift_original",
                            )
                            Text(
                                if (d.historyLocked)
                                    "Use redemption and refund actions to change balances once history exists."
                                else
                                    "Leave unknown balances blank. Traxos cannot verify merchant balances.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (d.kind == "STORED_VALUE")
                                TraxosCheckbox(
                                    d.partial,
                                    { v -> vm.update { it.copy(partial = v) } },
                                    "Merchant permits partial redemption",
                                    enabled = !busy,
                                )
                        } else
                            Text(
                                "Discount vouchers are not money and are excluded from wallet totals.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        TraxosSelector(
                            "Acquired by",
                            d.source,
                            listOf(
                                "PURCHASED" to "Purchased",
                                "RECEIVED" to "Received as gift",
                                "PROMOTIONAL" to "Promotional",
                            ),
                            !busy,
                        ) { v ->
                            vm.update { it.copy(source = v) }
                        }
                        TraxosDateField("Purchase / received date", d.received, !busy) { v ->
                            vm.update { it.copy(received = v) }
                        }
                        TraxosInput(
                            "Notes (optional)",
                            d.notes,
                            { v -> vm.update { it.copy(notes = v) } },
                            enabled = !busy,
                            multiline = true,
                            id = "gift_notes",
                        )
                        GiftAttachment(vm, d, busy)
                    }
                Text(
                    "Saved only on this phone. Losing app data or Keystore keys can make codes unrecoverable. No encrypted backup is available yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
                    TraxosButton(
                        {
                            keyboard?.hide()
                            vm.save(saved)
                        },
                        Modifier.fillMaxWidth().testTag("gift_save"),
                        !busy && !duplicate,
                        busy,
                    ) {
                        Text(if (busy) "Saving…" else "Save gift card")
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftAttachment(vm: GiftViewModel, d: GiftDraft, busy: Boolean) {
    val context = LocalContext.current
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                vm.action {
                    val bitmap = BarcodeImageImport.load(context, uri)
                    try {
                        val bytes =
                            java.io.ByteArrayOutputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, it)
                                it.toByteArray()
                            }
                        require(bytes.size <= 700_000) { "Crop the image to reduce its size" }
                        vm.draft.value = vm.draft.value?.copy(image = bytes)
                    } finally {
                        bitmap.recycle()
                    }
                }
        }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TraxosButton({ pick.launch("image/*") }, enabled = !busy, tone = ButtonTone.SECONDARY) {
            Text(if (d.image == null) "Attach image / QR" else "Replace image")
        }
        if (d.image != null)
            TraxosTextButton({ vm.update { it.copy(image = null) } }, enabled = !busy) {
                Text("Remove")
            }
    }
}

@Composable
internal fun GiftDetailScreen(vm: GiftViewModel, id: String, edit: () -> Unit, back: () -> Unit) {
    val today = giftToday()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val card = cards?.firstOrNull { it.id == id }
    var secret by remember(id, card?.revision) { mutableStateOf<GiftSecret?>(null) }
    var reveal by remember(id, card?.revision) { mutableStateOf(false) }
    var action by remember(id) { mutableStateOf("") }
    var amount by remember(id) { mutableStateOf("") }
    var op by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, id, card?.revision) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                secret = null
                reveal = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            secret = null
        }
    }
    LaunchedEffect(id, prefs.hideCodes, card?.revision) {
        if (!prefs.hideCodes && card != null)
            vm.action {
                val value = vm.repository.secret(card)
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    secret = value
                    reveal = true
                }
            }
    }
    val history by
        remember(id) { vm.repository.dao.history(id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
    if (cards == null) {
        ScreenSkeleton("accounts")
        return
    }
    if (card == null) {
        Message("Card unavailable", "Return to your wallet to choose a card.", "Back", back)
        return
    }
    val c = card
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun copyCode(value: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("TRAXOS gift code", value)
        clip.description.extras =
            PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        android.os
            .Handler(android.os.Looper.getMainLooper())
            .postDelayed(
                {
                    if (clipboard.primaryClip?.getItemAt(0)?.text?.toString() == value)
                        if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                        else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                },
                60000,
            )
        scope.launch { vm.events.send("Code copied · Clipboard clears in one minute") }
    }
    if (action == "REDEEM")
        TraxosSheet("Record redemption", { if (!busy) action = "" }, scrollable = true) {
            Text(
                "This records your use; it does not contact the merchant or create a bank expense.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (c.kind != "DISCOUNT")
                TraxosInput(
                    "Amount used",
                    amount,
                    { amount = it },
                    number = true,
                    id = "gift_redeem_amount",
                    enabled = !busy,
                )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    vm.action {
                        val used =
                            if (c.kind == "DISCOUNT") null else Money.parse(amount, c.currency)
                        vm.repository.redeem(c.id, c.revision, used, op)
                        action = ""
                        vm.events.send("Redemption recorded · Undo is available in history")
                    }
                },
                Modifier.fillMaxWidth(),
                !busy,
                busy,
            ) {
                Text("Confirm redemption")
            }
        }
    if (action in listOf("DELETE", "ARCHIVE", "UNDO", "REFUND", "EXPIRE", "RESTORE_EXPIRY"))
        TraxosDialog(
            when (action) {
                "RESTORE_EXPIRY" -> "Use the recorded expiry again?"
                "EXPIRE" -> "Mark this card expired?"
                "DELETE" -> "Delete this gift card?"
                "ARCHIVE" -> if (c.archived) "Restore archived card?" else "Archive this card?"
                "REFUND" -> "Restore the last redeemed value?"
                else -> "Undo the last redemption?"
            },
            when (action) {
                "RESTORE_EXPIRY" ->
                    "The recorded expiry date will determine availability and reminders again."
                "EXPIRE" ->
                    "This stops reminders and removes the card from available totals. The recorded expiry date is kept."
                "DELETE" -> "The card will leave your wallet and its reminders will be cancelled."
                "ARCHIVE" -> "Archived cards keep their history and stop expiry reminders."
                else ->
                    "Only the latest redemption can be reversed. Confirm the merchant restored the value when recording a refund. Cash/bank records are unchanged."
            },
            "Confirm",
            {
                vm.action {
                    when (action) {
                        "RESTORE_EXPIRY" -> vm.repository.markExpired(c.id, c.revision, false)
                        "EXPIRE" -> vm.repository.markExpired(c.id, c.revision)
                        "DELETE" -> {
                            vm.repository.delete(c.id, c.revision)
                            back()
                        }
                        "ARCHIVE" -> vm.repository.archive(c.id, c.revision, !c.archived)
                        else -> vm.repository.reverse(c.id, c.revision, op, action == "REFUND")
                    }
                    action = ""
                    vm.events.send("Gift wallet updated")
                }
            },
            { if (!busy) action = "" },
            destructive = action == "DELETE",
            enabled = !busy,
        )
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("gift_detail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(c.merchant, style = MaterialTheme.typography.headlineSmall)
        Text(c.name, style = MaterialTheme.typography.titleMedium)
        TraxosCard {
            Text(GiftRules.state(c, today), style = MaterialTheme.typography.labelMedium)
            Text(
                if (c.kind == "DISCOUNT") "Discount voucher"
                else c.remainingMinor?.let { Money.format(it, c.currency) } ?: "Unknown balance",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Original: ${c.originalMinor?.let {Money.format(it,c.currency)} ?: "Not recorded"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                c.expiryDay?.let { "Expiry · ${LocalDate.ofEpochDay(it)}" } ?: "No expiry recorded"
            )
            Text(
                "${c.source.lowercase().replaceFirstChar(Char::titlecase)} · ${LocalDate.ofEpochDay(c.receivedDay)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (c.partialAllowed) "Partial use confirmed by you" else "Full redemption only",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TraxosCard {
            Text("Sensitive details", style = MaterialTheme.typography.titleMedium)
            Text(
                if (reveal) secret?.code ?: "Loading…" else "•••• •••• ••••",
                style = MaterialTheme.typography.titleLarge,
            )
            if (reveal) {
                if (secret?.pin?.isNotBlank() == true) Text("PIN · ${secret!!.pin}")
                if (secret?.notes?.isNotBlank() == true) Text(secret!!.notes)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosButton(
                    {
                        if (reveal) {
                            reveal = false
                            secret = null
                        } else
                            vm.action {
                                val value = vm.repository.secret(c)
                                if (
                                    owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                ) {
                                    secret = value
                                    reveal = true
                                }
                            }
                    },
                    enabled = !busy,
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text(if (reveal) "Hide" else "Reveal")
                }
                TraxosButton(
                    {
                        vm.action {
                            val s = vm.repository.secret(c)
                            copyCode(s.code)
                        }
                    },
                    enabled = !busy,
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("Copy code")
                }
            }
            if (reveal && secret?.image != null) {
                val bitmap =
                    remember(secret?.image) {
                        secret?.image?.let {
                            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                        }
                    }
                if (bitmap != null) {
                    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
                    Image(
                        bitmap.asImageBitmap(),
                        "Attached gift card image",
                        Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    )
                }
            }
        }
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        if (GiftRules.state(c, today) in listOf("ACTIVE", "EXPIRING"))
            TraxosButton(
                {
                    amount = c.remainingMinor?.let { Money.editable(it, c.currency) }.orEmpty()
                    op = UUID.randomUUID().toString()
                    vm.clearError()
                    action = "REDEEM"
                },
                Modifier.fillMaxWidth(),
                !busy,
            ) {
                Text("Record redemption")
            }
        TraxosButton(edit, Modifier.fillMaxWidth(), !busy, tone = ButtonTone.SECONDARY) {
            Text("Edit card")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraxosTextButton({ action = "ARCHIVE" }, enabled = !busy) {
                Text(if (c.archived) "Restore" else "Archive")
            }
            TraxosTextButton({ action = "DELETE" }, enabled = !busy) { Text("Delete") }
        }
        if (GiftRules.state(c, today) in listOf("ACTIVE", "EXPIRING"))
            TraxosTextButton({ action = "EXPIRE" }, enabled = !busy) { Text("Mark expired") }
        if (c.manuallyExpired)
            TraxosTextButton({ action = "RESTORE_EXPIRY" }, enabled = !busy) {
                Text("Use recorded expiry again")
            }
        Text("Redemption history", style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty())
            Text("No redemptions recorded", style = MaterialTheme.typography.bodySmall)
        history.forEach { entry ->
            TraxosCard {
                Text(
                    entry.kind.lowercase().replaceFirstChar(Char::titlecase),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    entry.amountMinor?.let { Money.format(it, c.currency) }
                        ?: "Single-use discount redeemed"
                )
                Text(
                    java.time.Instant.ofEpochMilli(entry.createdAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (history.firstOrNull()?.kind == "REDEEM") {
            TraxosButton(
                {
                    op = UUID.randomUUID().toString()
                    action = "UNDO"
                },
                Modifier.fillMaxWidth(),
                !busy,
                tone = ButtonTone.SECONDARY,
            ) {
                Text("Undo last redemption")
            }
            TraxosTextButton(
                {
                    op = UUID.randomUUID().toString()
                    action = "REFUND"
                },
                enabled = !busy,
            ) {
                Text("Record merchant refund of last redemption")
            }
        }
    }
}

private fun giftWalletMoney(hidden: Boolean, minor: Long, currency: String) =
    if (hidden) "•••• $currency" else Money.format(minor, currency)

@Composable
private fun giftToday(): Long {
    val owner = LocalLifecycleOwner.current
    val day by
        produceState(LocalDate.now().toEpochDay(), owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val now = java.time.ZonedDateTime.now()
                    value = now.toLocalDate().toEpochDay()
                    val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                    delay(java.time.Duration.between(now, next).toMillis().coerceAtLeast(1000))
                }
            }
        }
    return day
}
