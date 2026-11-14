package app.traxos.finance.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.R
import app.traxos.finance.domain.Money
import app.traxos.finance.entry.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

@Composable
internal fun FastEntryScreen(navigate: (String) -> Unit, vm: RefinementViewModel = viewModel()) {
    val app = vm.app
    val repo = app.transactionExtras
    val presets by repo.dao.presets().collectAsStateWithLifecycle(null)
    val rules by repo.dao.rules().collectAsStateWithLifecycle(null)
    val config by repo.dao.settings().collectAsStateWithLifecycle(null)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val p = config ?: FastEntrySettings()
    var archived by rememberSaveable { mutableStateOf(false) }
    fun save(value: FastEntrySettings) {
        vm.run {
            repo.settings(value)
            withContext(Dispatchers.IO) {
                check(
                    app.getSharedPreferences("entry_tile", 0)
                        .edit()
                        .putBoolean("enabled", value.tile)
                        .commit()
                )
            }
            EntrySurfaceWork.kick(app)
        }
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("fast_entry_settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Faster entry", style = MaterialTheme.typography.titleLarge)
        Text(
            "Every action opens review behind app lock. No surface posts money automatically. Voice and SMS still require approval.",
            style = MaterialTheme.typography.bodySmall,
        )
        SectionHeader("Favorites", "Add") { navigate("entry_edit/PRESET/new") }
        TraxosCheckbox(archived, { archived = it }, "Show archived", enabled = !busy)
        if (presets == null) RowSkeleton()
        val active = presets.orEmpty().filter { !it.archived }
        presets
            .orEmpty()
            .filter { !it.archived || archived }
            .forEach { row ->
                TraxosCard {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${row.type.lowercase()} · ${displayMoney(row.amountMinor,row.currency)}${if(row.archived)" · Archived"else""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TraxosTextButton(
                            { navigate("entry_edit/PRESET/${row.id}") },
                            Modifier.weight(1f),
                            !busy && !row.archived,
                        ) {
                            Text("Edit")
                        }
                        TraxosTextButton(
                            {
                                vm.run {
                                    repo.archivePreset(row.id, row.revision, !row.archived)
                                    EntrySurfaceWork.kick(app)
                                }
                            },
                            Modifier.weight(1f),
                            !busy,
                        ) {
                            Text(if (row.archived) "Restore" else "Archive")
                        }
                        if (!row.archived && row.favorite)
                            TraxosTextButton(
                                {
                                    vm.run("Pin requested; confirm in your launcher") {
                                        repo.validatePreset(row)
                                        check(EntryPinning.preset(app, row, p.publicLabels)) {
                                            "Launcher declined the pin request"
                                        }
                                    }
                                },
                                Modifier.weight(1f),
                                !busy,
                            ) {
                                Text("Pin")
                            }
                    }
                    if (!row.archived)
                        Row {
                            TraxosTextButton(
                                {
                                    vm.run {
                                        repo.move(row.id, -1)
                                        EntrySurfaceWork.kick(app)
                                    }
                                },
                                Modifier.weight(1f),
                                !busy && active.indexOf(row) > 0,
                            ) {
                                Text("Up")
                            }
                            TraxosTextButton(
                                {
                                    vm.run {
                                        repo.move(row.id, 1)
                                        EntrySurfaceWork.kick(app)
                                    }
                                },
                                Modifier.weight(1f),
                                !busy && active.indexOf(row) < active.lastIndex,
                            ) {
                                Text("Down")
                            }
                        }
                }
            }
        if (presets?.isEmpty() == true)
            Text(
                "No favorites yet. Save an amount, account and category to review in one tap.",
                style = MaterialTheme.typography.bodySmall,
            )
        Text(
            "The first 8 active favorites appear in transaction forms. Reorder to prioritize them. A pin request is not a confirmed launcher placement; Android asks you to approve it.",
            style = MaterialTheme.typography.bodySmall,
        )
        SectionHeader("Merchant rules", "Add") { navigate("entry_edit/RULE/new") }
        SettingsToggle(
            "Merchant category suggestions",
            "Exact, prefix or whole-phrase rules; explicit category choices win",
            p.rules,
            !busy,
        ) {
            save(p.copy(rules = it))
        }
        rules.orEmpty().forEach { r ->
            TraxosCard {
                Text(r.pattern, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${r.type.lowercase()} · ${r.mode.lowercase()} · priority ${r.priority}${if(!r.enabled)" · Disabled"else""}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosTextButton({ navigate("entry_edit/RULE/${r.id}") }, enabled = !busy) {
                    Text("Edit")
                }
            }
        }
        Text("Widget actions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add Traxos Quick Entry from your Android home-screen widget picker. The widget shows no balance, amount or code.",
            style = MaterialTheme.typography.bodySmall,
        )
        val actions =
            listOf(
                "EXPENSE" to "Expense",
                "INCOME" to "Income",
                "VOICE" to "Voice review",
                "SMART" to "Smart Add",
                "HOME" to "Home",
            ) + active.filter { it.favorite }.map { "PRESET:${it.id}" to "Favorite · ${it.name}" }
        listOf(p.widget1, p.widget2, p.widget3).forEachIndexed { index, selected ->
            TraxosSelector("Widget button ${index+1}", selected, actions, !busy) {
                when (index) {
                    0 -> save(p.copy(widget1 = it))
                    1 -> save(p.copy(widget2 = it))
                    else -> save(p.copy(widget3 = it))
                }
            }
        }
        SettingsToggle(
            "Public favorite labels",
            "Opt in to showing preset names on home screens/launchers; amounts remain hidden",
            p.publicLabels,
            !busy,
        ) {
            save(p.copy(publicLabels = it))
        }
        SettingsToggle(
            "Launcher shortcuts",
            "Publish quick-add, income, voice and Smart Add; launcher capacity varies",
            p.shortcuts,
            !busy,
        ) {
            save(p.copy(shortcuts = it))
        }
        SettingsToggle(
            "Quick Settings tile",
            "Traxos Add opens an expense form, never an automatic payment",
            p.tile,
            !busy,
        ) {
            save(p.copy(tile = it))
        }
        TraxosButton(
            {
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        app.getSystemService(StatusBarManager::class.java).requestAddTileService(
                            ComponentName(app, EntryTile::class.java),
                            "Traxos Add",
                            Icon.createWithResource(app, R.drawable.traxos_mark),
                            app.mainExecutor,
                        ) { result ->
                            vm.status.value =
                                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
                                    "Android added the tile"
                                else "Tile not added. Use Android Quick Settings → Edit if needed."
                        }
                    } catch (e: Exception) {
                        vm.status.value = "Use Android Quick Settings → Edit to add Traxos Add."
                    }
                } else vm.status.value = "Open Quick Settings → Edit and add Traxos Add."
            },
            Modifier.fillMaxWidth(),
            !busy && p.tile,
        ) {
            Text("Add tile")
        }
        TraxosTextButton(
            { vm.run("Refresh queued") { EntrySurfaceWork.kick(app) } },
            enabled = !busy,
        ) {
            Text("Refresh")
        }
        Text(
            "Android may delay widget/shortcut updates. Disabling a favorite prevents stale pinned shortcuts from posting; unavailable favorites open an error instead of choosing another account.",
            style = MaterialTheme.typography.bodySmall,
        )
        val health = app.getSharedPreferences("entry_surface_health", 0)
        if (health.getBoolean("failed", false))
            TraxosNotice(
                "Android could not refresh entry surfaces. Your records are safe; use the in-app form or Refresh.",
                NoticeKind.WARNING,
            )
        if (status.isNotBlank())
            TraxosNotice(status, if (failed) NoticeKind.ERROR else NoticeKind.INFO)
    }
}

@Composable
internal fun EntryOptionEditor(
    kind: String,
    id: String,
    close: () -> Unit,
    vm: RefinementViewModel = viewModel(),
) {
    val repo = vm.app.transactionExtras
    val presetRows by repo.dao.presets().collectAsStateWithLifecycle(null)
    val ruleRows by repo.dao.rules().collectAsStateWithLifecycle(null)
    val accounts by vm.app.repository.dao.accounts().collectAsStateWithLifecycle(emptyList())
    val categories by vm.app.repository.dao.categories().collectAsStateWithLifecycle(emptyList())
    val labels by vm.app.repository.dao.labels().collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(status, failed) { if (submitted && status == "Saved" && !failed) close() }
    if (presetRows == null || ruleRows == null) {
        ScreenSkeleton("settings")
        return
    }
    val old = presetRows.orEmpty().firstOrNull { it.id == id }
    val rule = ruleRows.orEmpty().firstOrNull { it.id == id }
    if (id != "new" && old == null && rule == null) {
        TraxosNotice("This saved option is unavailable. Go back and reopen it.", NoticeKind.ERROR)
        return
    }
    val uuid = rememberSaveable { if (id == "new") UUID.randomUUID().toString() else id }
    var name by rememberSaveable { mutableStateOf(old?.name ?: rule?.pattern ?: "") }
    var type by rememberSaveable { mutableStateOf(old?.type ?: rule?.type ?: "EXPENSE") }
    var account by rememberSaveable { mutableStateOf(old?.accountId ?: rule?.accountId.orEmpty()) }
    var category by rememberSaveable {
        mutableStateOf(old?.categoryId ?: rule?.categoryId.orEmpty())
    }
    var amount by rememberSaveable {
        mutableStateOf(old?.let { Money.editable(it.amountMinor, it.currency) } ?: "")
    }
    var note by rememberSaveable { mutableStateOf(old?.note.orEmpty()) }
    var merchant by rememberSaveable { mutableStateOf(old?.merchantId.orEmpty()) }
    var favorite by rememberSaveable { mutableStateOf(old?.favorite ?: true) }
    var mode by rememberSaveable { mutableStateOf(rule?.mode ?: "EXACT") }
    var priority by rememberSaveable { mutableStateOf((rule?.priority ?: 0).toString()) }
    var enabled by rememberSaveable { mutableStateOf(rule?.enabled ?: true) }
    var details by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val unit = accounts.firstOrNull { it.account.id == account }?.account?.currency ?: "INR"
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
            .testTag("entry_option_editor"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (kind == "PRESET") "Favorite entry" else "Merchant category rule",
            style = MaterialTheme.typography.titleLarge,
        )
        TraxosInput(
            if (kind == "PRESET") "Name" else "Merchant phrase",
            name,
            { name = it.take(80) },
            enabled = !busy,
            id = "entry_option_name",
        )
        TraxosSegment(listOf("EXPENSE" to "Expense", "INCOME" to "Income"), type, !busy) {
            type = it
            category = ""
        }
        if (kind == "PRESET") {
            AccountPicker(
                vm.app.repository,
                account,
                accounts.filter {
                    !it.account.archived && (type != "INCOME" || it.account.type != "CREDIT_CARD")
                },
                !busy,
            ) {
                account = it
            }
            TraxosInput(
                "Amount · $unit",
                amount,
                { amount = it },
                enabled = !busy,
                number = true,
                id = "preset_amount",
            )
        } else {
            TraxosSelector(
                "Match",
                mode,
                listOf(
                    "EXACT" to "Exact merchant",
                    "PREFIX" to "Starts with phrase",
                    "CONTAINS" to "Contains whole phrase",
                ),
                !busy,
            ) {
                mode = it
            }
            TraxosSelector(
                "Account scope",
                account,
                listOf("" to "All accounts") +
                    accounts
                        .filter { !it.account.archived }
                        .map { it.account.id to it.account.name },
                !busy,
            ) {
                account = it
            }
            TraxosInput(
                "Priority · 0–1000",
                priority,
                { priority = it },
                enabled = !busy,
                number = true,
                id = "rule_priority",
            )
            SettingsToggle(
                "Enabled",
                "Archived categories/accounts are never silently substituted",
                enabled,
                !busy,
            ) {
                enabled = it
            }
            Text(
                "Higher priority wins, then exact/prefix/contains specificity, phrase length and stable ID. No regex or network lookup. Suggestions remain editable.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CategoryPicker(vm.app.repository, category, categories, type, !busy) { category = it }
        if (kind == "PRESET") {
            SettingsToggle(
                "Favorite",
                "Show among the first 8 ordered favorite chips",
                favorite,
                !busy,
            ) {
                favorite = it
            }
            TraxosTextButton({ details = !details }, enabled = !busy) {
                Text(if (details) "Less" else "Details")
            }
            if (details) {
                SavedLabelPicker(vm.app.repository, "MERCHANT") { merchant = it.id }
                labels.firstOrNull { it.id == merchant }?.let { Text(it.name) }
                TraxosTextButton({ merchant = "" }, enabled = !busy) { Text("Clear") }
                TraxosInput(
                    "Optional note",
                    note,
                    { note = it.take(500) },
                    enabled = !busy,
                    id = "preset_note",
                )
            }
        }
        if (status.isNotBlank())
            TraxosNotice(status, if (failed) NoticeKind.ERROR else NoticeKind.INFO)
        TraxosButton(
            {
                submitted = true
                vm.run {
                    if (kind == "PRESET")
                        repo.preset(
                            EntryPreset(
                                uuid,
                                name,
                                type,
                                unit,
                                Money.parse(amount, unit),
                                account,
                                category,
                                merchant.takeIf { it.isNotBlank() },
                                note,
                                favorite,
                                old?.position ?: presetRows.orEmpty().count { !it.archived },
                                revision = old?.revision ?: 1,
                            ),
                            old?.revision ?: 0,
                        )
                    else
                        repo.rule(
                            MerchantRule(
                                uuid,
                                name,
                                mode,
                                type,
                                category,
                                account.takeIf { it.isNotBlank() },
                                priority =
                                    priority.toIntOrNull()
                                        ?: throw IllegalArgumentException("Enter priority 0–1000"),
                                enabled = enabled,
                            ),
                            rule?.revision ?: 0,
                        )
                    EntrySurfaceWork.kick(vm.app)
                }
            },
            Modifier.fillMaxWidth().testTag("entry_option_save"),
            !busy,
            busy,
        ) {
            Text("Save")
        }
        if (confirmDelete && rule != null)
            TraxosDialog(
                "Delete merchant rule?",
                "Existing transactions and categories are kept. Future entries will no longer use this suggestion.",
                "Delete",
                {
                    confirmDelete = false
                    submitted = true
                    vm.run {
                        require(repo.dao.deleteRule(rule.id, rule.revision) == 1) {
                            "Rule changed; reopen it"
                        }
                        EntrySurfaceWork.kick(vm.app)
                    }
                },
                { confirmDelete = false },
                destructive = true,
                enabled = !busy,
            )
        if (kind == "RULE" && rule != null)
            TraxosTextButton({ confirmDelete = true }, enabled = !busy) { Text("Delete") }
    }
}

@Composable
internal fun ReceiptSettingsScreen(
    navigate: (String) -> Unit,
    vm: RefinementViewModel = viewModel(),
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    if (discard)
        TraxosDialog(
            "Discard receipt drafts?",
            "Only unfinished, unsaved transaction attachments are removed. Saved transaction receipts and all financial records remain.",
            "Discard",
            {
                discard = false
                vm.run { vm.app.database.extras().discardAllDrafts() }
            },
            { discard = false },
            destructive = true,
            enabled = !busy,
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Receipt storage", style = MaterialTheme.typography.titleLarge)
        ReceiptLookup(vm.app, navigate)
        Text(
            "Attach receipts from the transaction form. Images are oriented, downsampled and encrypted; PDF size/header is checked and limited to 1 MB. Up to 5 attachments per record, including deleted history. No OCR or upload."
        )
        Text(
            "New-entry attachments stay encrypted as drafts until Save. Discarding that form removes drafts. Interrupted drafts remain private until you explicitly discard them here; nothing posts money automatically."
        )
        Text(
            "Open creates an unencrypted cache copy for a viewer you choose. Copies older than 15 minutes are removed on the next surface/app refresh; Android can delay work. Clear now revokes grants and removes copies, but cannot erase data already copied by another app."
        )
        TraxosButton(
            { vm.run { vm.app.receiptStore.clearCache() } },
            Modifier.fillMaxWidth(),
            !busy,
            busy,
        ) {
            Text("Clear cache")
        }
        TraxosTextButton({ discard = true }, enabled = !busy) { Text("Discard drafts") }
        if (status.isNotBlank())
            TraxosNotice(status, if (failed) NoticeKind.ERROR else NoticeKind.INFO)
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
private fun ReceiptLookup(app: app.traxos.finance.TraxosApplication, navigate: (String) -> Unit) {
    val search = remember { kotlinx.coroutines.flow.MutableStateFlow("") }
    val query by search.collectAsStateWithLifecycle()
    val source =
        remember(app) {
            search.debounce(300).distinctUntilChanged().flatMapLatest {
                app.database.extras().receiptRecords(it)
            }
        }
    val rows by source.collectAsStateWithLifecycle(null)
    TraxosInput(
        "Find transaction · note, account or ISO date",
        query,
        { search.value = it },
        id = "receipt_search",
    )
    Text(
        "Latest50 matches. Refine search for older records. Includes linked loan/card/group movements; no record is posted here.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (rows == null) RowSkeleton()
    rows.orEmpty().forEach { row ->
        TraxosTextButton({ navigate("receipt_view/${row.record.id}") }, Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(row.record.note.ifBlank { row.record.type }, maxLines = 1)
                Text(
                    "${row.accountName} · ${displayDate(row.record.day)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            MoneyText(row.record.amountMinor, row.currency)
        }
    }
}

@Composable
internal fun ReceiptRecordScreen(id: String, vm: ReceiptsViewModel = viewModel()) {
    val record by
        produceState<app.traxos.finance.data.LedgerRecord?>(null, id) {
            value = vm.app.repository.dao.record(id)
        }
    var importing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        record?.let { row ->
            Text(row.note.ifBlank { row.type }, style = MaterialTheme.typography.titleMedium)
            Text(displayDate(row.day))
            if (row.deleted)
                TraxosNotice(
                    "Restore this transaction before adding a receipt.",
                    NoticeKind.WARNING,
                )
            ReceiptControls(id, false, !row.deleted, {}, { importing = it })
        } ?: RowSkeleton()
    }
}
