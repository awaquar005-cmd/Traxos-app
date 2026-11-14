package app.traxos.finance.ui

import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.TraxosApplication
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import app.traxos.finance.entry.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
internal fun SplitFormControls(
    f: Map<String, String>,
    state: ReadState.Ready,
    repository: LedgerRepository,
    enabled: Boolean,
    set: (String, String) -> Unit,
) {
    val currency =
        state.accounts.firstOrNull { it.account.id == f["accountId"] }?.account?.currency ?: return
    val rows = runCatching { SplitDrafts.read(f["splits"].orEmpty()) }.getOrDefault(emptyList())
    var open by rememberSaveable { mutableStateOf(false) }
    TraxosTextButton({ open = true }, Modifier.testTag("transaction_split"), enabled) {
        Text(if (rows.isEmpty()) "Split categories" else "${rows.size} categories · Edit split")
    }
    if (rows.isNotEmpty())
        Text(
            "The full amount changes your account once. Category allocations share that amount.",
            style = MaterialTheme.typography.bodySmall,
        )
    if (open) {
        var draft by rememberSaveable {
            mutableStateOf(
                if (rows.isEmpty())
                    SplitDrafts.write(
                        listOf(
                            SplitDrafts.Row(f["categoryId"].orEmpty(), ""),
                            SplitDrafts.Row("", ""),
                        )
                    )
                else f["splits"].orEmpty()
            )
        }
        val parts = SplitDrafts.read(draft)
        var error by remember { mutableStateOf<String?>(null) }
        fun change(index: Int, category: String? = null, amount: String? = null) {
            draft =
                SplitDrafts.write(
                    parts.mapIndexed { i, r ->
                        if (i == index)
                            r.copy(
                                categoryId = category ?: r.categoryId,
                                amount = amount ?: r.amount,
                            )
                        else r
                    }
                )
            error = null
        }
        TraxosSheet("Split categories", { open = false }, true) {
            Text(
                "Total ${f["amount"].orEmpty()} $currency · exact amounts; no extra transaction.",
                style = MaterialTheme.typography.bodyMedium,
            )
            parts.forEachIndexed { index, row ->
                CategoryPicker(
                    repository,
                    row.categoryId,
                    state.categories,
                    f["type"].orEmpty(),
                    enabled,
                    "Category ${index+1}",
                ) {
                    change(index, category = it)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        TraxosInput(
                            "Amount ${index+1}",
                            row.amount,
                            { change(index, amount = it) },
                            enabled = enabled,
                            number = true,
                            id = "split_$index",
                        )
                    }
                    TraxosTextButton(
                        { draft = SplitDrafts.write(parts.filterIndexed { i, _ -> i != index }) },
                        enabled = parts.size > 2 && enabled,
                    ) {
                        Text("Remove")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton(
                    { draft = SplitDrafts.write(parts + SplitDrafts.Row("", "")) },
                    Modifier.weight(1f),
                    enabled && parts.size < 20,
                ) {
                    Text("Add")
                }
                TraxosTextButton(
                    {
                        try {
                            val total = Money.parse(f["amount"].orEmpty(), currency)
                            require(total >= parts.size)
                            val base = total / parts.size
                            val rem = total % parts.size
                            draft =
                                SplitDrafts.write(
                                    parts.mapIndexed { i, r ->
                                        r.copy(
                                            amount =
                                                Money.editable(
                                                    base + if (i < rem) 1 else 0,
                                                    currency,
                                                )
                                        )
                                    }
                                )
                            error = null
                        } catch (e: Exception) {
                            error = "Enter a total large enough for every category first."
                        }
                    },
                    Modifier.weight(1f),
                    enabled,
                ) {
                    Text("Equal")
                }
                TraxosTextButton(
                    {
                        set("splits", "")
                        open = false
                    },
                    Modifier.weight(1f),
                    enabled,
                ) {
                    Text("Clear")
                }
            }
            error?.let { TraxosNotice(it, NoticeKind.ERROR) }
            val remainder =
                runCatching {
                        Money.parse(f["amount"].orEmpty(), currency) -
                            parts.sumOf { Money.parse(it.amount, currency, true) }
                    }
                    .getOrNull()
            remainder?.let {
                Text(
                    "Unallocated: ${displayMoney(it,currency)}",
                    color =
                        if (it == 0L) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                )
            }
            TraxosButton(
                {
                    try {
                        val allocation = SplitDrafts.parts(draft, currency)
                        EntryRules.validateSplits(
                            f["type"].orEmpty(),
                            Money.parse(f["amount"].orEmpty(), currency),
                            allocation,
                            state.categories,
                            rows.map { it.categoryId }.toSet(),
                        )
                        set("categoryId", allocation.first().categoryId)
                        set("splits", draft)
                        open = false
                    } catch (e: Exception) {
                        error =
                            if (e is IllegalArgumentException) e.message
                            else "Enter valid amounts and different categories."
                    }
                },
                Modifier.fillMaxWidth().testTag("split_apply"),
                enabled,
            ) {
                Text("Apply")
            }
        }
    }
}

internal class ReceiptsViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as TraxosApplication
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val failed = MutableStateFlow(false)
    var job: Job? = null

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun run(parent: (Boolean) -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        parent(true)
        status.value = ""
        failed.value = false
        job =
            viewModelScope.launch(start = CoroutineStart.ATOMIC) {
                try {
                    block()
                    status.value = "Saved"
                } catch (e: CancellationException) {
                    status.value = "Cancelled"
                    throw e
                } catch (e: Exception) {
                    failed.value = true
                    status.value =
                        if (e is IllegalArgumentException) e.message.orEmpty()
                        else "Could not update receipt. Existing records are kept."
                } finally {
                    busy.value = false
                    parent(false)
                }
            }
    }
}

@Composable
internal fun ReceiptControls(
    owner: String,
    isNew: Boolean,
    enabled: Boolean,
    onChanged: () -> Unit,
    parentBusy: (Boolean) -> Unit,
    vm: ReceiptsViewModel = viewModel(key = "receipts_$owner"),
) {
    val context = LocalContext.current
    val saved by vm.app.database.extras().receipts(owner).collectAsStateWithLifecycle(emptyList())
    val drafts by vm.app.database.extras().drafts(owner).collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var selecting by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            selecting = false
            parentBusy(false)
            if (uri != null)
                vm.run(parentBusy) {
                    vm.app.receiptStore.add(owner, uri)
                    onChanged()
                }
            else vm.status.value = "Receipt selection cancelled"
        }
    var preview by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<ReceiptMeta?>(null) }
    Text("Receipts", style = MaterialTheme.typography.titleMedium)
    Text(
        if (isNew)
            "Draft attachments are encrypted now and linked only when you Save. Up to 5 images/PDFs; no OCR."
        else "Attachments save independently of financial edits. Deleted receipts can be restored.",
        style = MaterialTheme.typography.bodySmall,
    )
    TraxosTextButton(
        {
            if (!selecting && !busy) {
                selecting = true
                parentBusy(true)
                try {
                    picker.launch(arrayOf("image/*", "application/pdf"))
                } catch (e: Exception) {
                    selecting = false
                    parentBusy(false)
                    vm.failed.value = true
                    vm.status.value = "Android Files is unavailable; existing receipts are kept."
                }
            }
        },
        Modifier.testTag("receipt_add"),
        enabled && !busy && !selecting,
    ) {
        Text("Attach")
    }
    val rows = saved + drafts
    if (rows.isEmpty()) Text("No receipts attached", style = MaterialTheme.typography.bodySmall)
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraxosTextButton(
                { preview = r.id },
                Modifier.weight(1f),
                enabled && !busy && !selecting,
            ) {
                Text(
                    r.name + (if (r.deleted) " · Deleted" else ""),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TraxosTextButton(
                {
                    if (r.deleted) vm.run { vm.app.receiptStore.delete(r.id, r.revision, false) }
                    else remove = r
                },
                enabled = enabled && !busy && !selecting,
            ) {
                Text(if (r.deleted) "Restore" else "Delete")
            }
        }
    }
    if (busy) TraxosTextButton({ vm.job?.cancel() }) { Text("Cancel") }
    if (status.isNotBlank()) TraxosNotice(status, if (failed) NoticeKind.ERROR else NoticeKind.INFO)
    remove?.let { r ->
        TraxosDialog(
            "Delete receipt?",
            "The attachment is hidden, not the transaction. You can restore it from this list.",
            "Delete",
            {
                remove = null
                vm.run {
                    if (r in saved) vm.app.receiptStore.delete(r.id, r.revision, true)
                    else vm.app.database.extras().deleteDraft(r.id, owner)
                    onChanged()
                }
            },
            { remove = null },
            destructive = true,
            enabled = enabled && !busy && !selecting,
        )
    }
    if (preview.isNotBlank()) {
        var payload by remember(preview) { mutableStateOf<Pair<String, ByteArray>?>(null) }
        var error by remember(preview) { mutableStateOf<String?>(null) }
        LaunchedEffect(preview) {
            try {
                payload = vm.app.receiptStore.bytes(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Receipt cannot be decrypted/opened. Existing data is kept."
            }
        }
        DisposableEffect(preview) { onDispose { payload?.second?.fill(0) } }
        TraxosSheet("Receipt", { preview = "" }, true) {
            if (payload == null && error == null) RowSkeleton()
            error?.let { TraxosNotice(it, NoticeKind.ERROR) }
            payload?.let { (mime, bytes) ->
                if (mime.startsWith("image/")) {
                    val bitmap by
                        produceState<android.graphics.Bitmap?>(null, bytes) {
                            value =
                                withContext(Dispatchers.IO) {
                                    android.graphics.BitmapFactory.decodeByteArray(
                                        bytes,
                                        0,
                                        bytes.size,
                                    )
                                }
                        }
                    bitmap?.let {
                        Image(
                            it.asImageBitmap(),
                            "Transaction receipt",
                            Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        )
                    }
                }
                Text(
                    "Open creates a temporary unencrypted viewer copy. Only the app you choose receives read access. Clear copies from Receipt settings; Android may delay cleanup.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosButton(
                    {
                        vm.run {
                            val (uri, kind) = vm.app.receiptStore.external(preview)
                            withContext(Dispatchers.Main) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, kind)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    !busy,
                ) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
internal fun FavoriteFormChips(vm: FormViewModel, main: MainViewModel) {
    val app = LocalContext.current.applicationContext as? TraxosApplication ?: return
    val presets by app.transactionExtras.dao.presets().collectAsStateWithLifecycle(emptyList())
    var choice by remember { mutableStateOf<EntryPreset?>(null) }
    val rows = presets.filter { it.favorite && !it.archived }.take(8)
    if (rows.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { p ->
                CompactAction(
                    p.name,
                    { if (vm.dirty()) choice = p else vm.applyEntry(EntryRequest("PRESET", p.id)) },
                    enabled = !vm.busy.value && !vm.attachmentBusy.value,
                )
            }
        }
    }
    choice?.let { p ->
        TraxosDialog(
            "Replace draft?",
            "Amount, account, categories, note and unsaved receipt attachments will be replaced by this favorite. Saved transactions stay unchanged.",
            "Replace",
            {
                choice = null
                vm.applyEntry(EntryRequest("PRESET", p.id))
            },
            { choice = null },
        )
    }
}
