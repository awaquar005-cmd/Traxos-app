package app.traxos.finance.ui

import android.app.Activity
import android.content.*
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PersonChooser(
    repo: PlanningExtensionsRepository,
    selected: String,
    enabled: Boolean,
    onSelect: (SavedPerson?) -> Unit,
) {
    val people by repo.dao.people().collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var oldId by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            picking = false
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null && uri.scheme == "content")
                    scope.launch {
                        try {
                            val details =
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    context.contentResolver
                                        .query(
                                            uri,
                                            arrayOf(
                                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                            ),
                                            null,
                                            null,
                                            null,
                                        )
                                        ?.use { c ->
                                            require(c.moveToFirst())
                                            c.getString(0).orEmpty().take(80) to
                                                BudgetPeriods.phone(c.getString(1).orEmpty())
                                        }
                                        ?: throw IllegalArgumentException(
                                            "No readable phone selected"
                                        )
                                }
                            name = details.first
                            phone = details.second
                            oldId = ""
                            error = null
                            edit = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error =
                                "The chosen contact could not be read. Add the name and phone manually."
                            name = ""
                            phone = ""
                            oldId = ""
                            edit = true
                        }
                    }
            }
        }
    TraxosSelector(
        "Saved person",
        selected,
        listOf("" to "Name only") +
            people.map { it.id to "${it.name}${if(it.phone.isBlank())"" else " · ${it.phone}"}" },
        enabled && !busy,
        onCreate = {
            oldId = ""
            name = ""
            phone = ""
            error = null
            edit = true
        },
    ) { value ->
        onSelect(people.firstOrNull { it.id == value })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TraxosTextButton(
            {
                try {
                    if (picking) return@TraxosTextButton
                    picking = true
                    picker.launch(
                        Intent(
                            Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        )
                    )
                } catch (e: Exception) {
                    picking = false
                    error = "The contact picker is unavailable or blocked. Enter details manually."
                    edit = true
                }
            },
            Modifier.weight(1f),
            enabled && !busy,
        ) {
            Text("Contact")
        }
        if (selected.isNotBlank())
            TraxosTextButton(
                {
                    people
                        .firstOrNull { it.id == selected }
                        ?.let {
                            oldId = it.id
                            name = it.name
                            phone = it.phone
                            error = null
                            edit = true
                        }
                },
                Modifier.weight(1f),
                enabled && !busy,
            ) {
                Text("Edit")
            }
    }
    if (edit)
        key(oldId) {
            val identity = rememberSaveable { oldId.ifBlank { UUID.randomUUID().toString() } }
            val operation = rememberSaveable { UUID.randomUUID().toString() }
            val old = people.firstOrNull { it.id == oldId }
            TraxosSheet(
                if (old == null) "Save person" else "Edit person",
                { if (!busy) edit = false },
                true,
            ) {
                Text(
                    "Only this name and optional phone are stored. Traxos does not read your address book. Chosen details are included in your encrypted personal backups.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosInput(
                    "Name",
                    name,
                    { name = it.take(80) },
                    enabled = !busy,
                    id = "person_name",
                )
                TraxosInput(
                    "Phone · optional",
                    phone,
                    { phone = it.take(40) },
                    enabled = !busy,
                    id = "person_phone",
                )
                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosTextButton({ edit = false }, Modifier.weight(1f), !busy) {
                        Text("Cancel")
                    }
                    TraxosButton(
                        {
                            if (!busy) {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        repo.person(
                                            identity,
                                            name,
                                            phone,
                                            old?.revision ?: 0,
                                            operation,
                                        )
                                        val saved = requireNotNull(repo.dao.person(identity))
                                        onSelect(saved)
                                        edit = false
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: IllegalArgumentException) {
                                        error = e.message
                                    } catch (e: Exception) {
                                        error =
                                            "Could not save the person. Retry; existing records are kept."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        Modifier.weight(1f),
                        !busy,
                        busy = busy,
                    ) {
                        Text("Save")
                    }
                }
            }
        }
}

@Composable
internal fun DebtFundingFields(
    repo: PlanningExtensionsRepository,
    currency: String,
    incoming: Boolean,
    amount: String,
    date: String,
    mode: String,
    account: String,
    record: String,
    enabled: Boolean,
    onMode: (String) -> Unit,
    onAccount: (String) -> Unit,
    onRecord: (String) -> Unit,
) {
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val records by repo.db.groups().linkCandidates().collectAsStateWithLifecycle(emptyList())
    TraxosSelector(
        "Account movement",
        mode,
        listOf(
            "NONE" to "No account change",
            "NEW" to "Record a real movement",
            "MATCH" to "Match existing transaction",
        ),
        enabled,
    ) {
        onMode(it)
    }
    if (mode != "NONE") {
        AccountPicker(
            LedgerRepository(repo.db),
            account,
            accounts,
            enabled,
            currency = currency,
            allowCard = false,
        ) {
            onAccount(it)
            onRecord("")
        }
        if (mode == "MATCH") {
            val minor = runCatching { Money.parse(amount, currency) }.getOrNull()
            val day = runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull()
            val candidates =
                records.filter {
                    it.accountId == account &&
                        it.type == (if (incoming) "INCOME" else "EXPENSE") &&
                        it.amountMinor == minor &&
                        it.day == day
                }
            TraxosSelector(
                "Existing transaction",
                record,
                candidates.map {
                    it.id to
                        "${it.note.ifBlank{it.type}} · ${Money.format(it.amountMinor,currency)} · ${LocalDate.ofEpochDay(it.day)}"
                },
                enabled,
            ) {
                onRecord(it)
            }
            if (candidates.isEmpty())
                Text(
                    "No matching account, amount, date and direction. Your existing ledger is kept; check the fields above.",
                    style = MaterialTheme.typography.bodySmall,
                )
        }
        Text(
            if (incoming) "This records money received, not income."
            else
                "This records money paid, not spending. Traxos does not send money from your bank.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun DebtExtrasSheet(
    debtId: String,
    section: String,
    dismiss: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(key = "debt_extension_$debtId"),
) {
    val repo = vm.app.planning
    val threads by vm.app.database.debts().threads().collectAsStateWithLifecycle(emptyList())
    val debt = threads.firstOrNull { it.id == debtId }
    val prefs by repo.dao.debtPreferences().collectAsStateWithLifecycle(null)
    val funding by repo.dao.debtFunding().collectAsStateWithLifecycle(emptyList())
    val people by repo.dao.people().collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val done by vm.completed.collectAsStateWithLifecycle()
    val initial = remember { done }
    LaunchedEffect(done) { if (done > initial) dismiss() }
    if (debt == null || prefs == null) return
    val d = debt
    val old = prefs.orEmpty().firstOrNull { it.debtId == debtId }
    val opening = funding.firstOrNull { it.debtId == debtId && it.movementId == null && it.active }
    key(section) {
        var person by rememberSaveable { mutableStateOf(old?.personId.orEmpty()) }
        var reminders by rememberSaveable { mutableStateOf(old?.reminders ?: false) }
        var offsets by rememberSaveable { mutableStateOf(old?.offsets ?: "7,1,0") }
        val operation = rememberSaveable { UUID.randomUUID().toString() }
        var mode by rememberSaveable { mutableStateOf("NONE") }
        var account by rememberSaveable { mutableStateOf("") }
        var record by rememberSaveable { mutableStateOf("") }
        var unlink by remember { mutableStateOf(false) }
        if (unlink)
            TraxosDialog(
                "Unlink opening?",
                "The IOU remains. A movement created with this loan is tombstoned; a matched pre-existing transaction is kept and returns to its ordinary report treatment.",
                "Unlink",
                { vm.run { repo.unlinkOpening(debtId) } },
                { unlink = false },
                destructive = true,
                enabled = !busy,
            )
        TraxosSheet(
            if (section == "FUNDING") "Opening account movement" else "Person & reminders",
            { if (!busy) dismiss() },
            true,
        ) {
            if (section == "FUNDING") {
                if (opening != null) {
                    Text(
                        "Opening is linked to your ledger.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TraxosButton(
                        { unlink = true },
                        Modifier.fillMaxWidth(),
                        !busy,
                        tone = ButtonTone.DESTRUCTIVE,
                    ) {
                        Text("Unlink")
                    }
                } else {
                    DebtFundingFields(
                        repo,
                        d.currency,
                        d.direction == "BORROWED",
                        Money.editable(d.originalMinor, d.currency),
                        LocalDate.ofEpochDay(d.day).toString(),
                        mode,
                        account,
                        record,
                        !busy,
                        { mode = it },
                        { account = it },
                        { record = it },
                    )
                    TraxosButton(
                        {
                            vm.run {
                                require(mode != "NONE") { "Choose record or match" }
                                repo.linkDebt(
                                    d.id,
                                    null,
                                    account,
                                    if (mode == "MATCH") record else null,
                                    operation,
                                )
                            }
                        },
                        Modifier.fillMaxWidth(),
                        !busy,
                        busy = busy,
                    ) {
                        Text("Link")
                    }
                }
            } else {
                PersonChooser(repo, person, !busy) { person = it?.id.orEmpty() }
                people
                    .firstOrNull { it.id == person }
                    ?.let {
                        Text(
                            "${it.name} · ${it.phone.ifBlank{"No phone saved"}}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                TraxosCheckbox(
                    reminders,
                    { reminders = it },
                    "Due reminders",
                    enabled = !busy && d.dueDay != null,
                )
                if (d.dueDay == null)
                    Text(
                        "Set a due date in Edit before enabling reminders.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (reminders)
                    TraxosInput(
                        "Days before due · 0 means due date",
                        offsets,
                        { offsets = it },
                        enabled = !busy,
                    )
                TraxosButton(
                    {
                        vm.run {
                            repo.debtPreferences(
                                DebtPreferences(
                                    d.id,
                                    person.takeIf { it.isNotBlank() },
                                    reminders,
                                    offsets,
                                    operationId = operation,
                                ),
                                old?.revision ?: 0,
                            )
                        }
                    },
                    Modifier.fillMaxWidth(),
                    !busy,
                    busy = busy,
                ) {
                    Text("Save")
                }
            }
            error?.let { TraxosNotice(it, NoticeKind.ERROR) }
        }
    }
}

@Composable
internal fun DebtShareSheet(debt: DebtThread, person: SavedPerson?, dismiss: () -> Unit) {
    val context = LocalContext.current
    var includeNote by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }
    val asOf = remember {
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z"))
    }
    LaunchedEffect(includeNote) {
        text =
            "Traxos · ${debt.person}\n" +
                (if (debt.direction == "LENT") "Recorded amount owed to me: "
                else "Recorded amount I owe: ") +
                Money.format(debt.outstandingMinor, debt.currency) +
                "\nAs of $asOf" +
                debt.dueDay?.let { "\nDue ${LocalDate.ofEpochDay(it)}" }.orEmpty() +
                (if (includeNote && debt.note.isNotBlank()) "\n${debt.note}" else "")
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) opening = false
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    TraxosSheet("Review before sharing", dismiss, true) {
        Text(
            "Person: ${debt.person}${person?.phone?.takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Select the intended recipient in WhatsApp or the share sheet, then press Send there. Traxos cannot confirm delivery.",
            style = MaterialTheme.typography.bodySmall,
        )
        TraxosCheckbox(includeNote, { includeNote = it }, "Include reason / note")
        TraxosInput(
            "Message",
            text,
            { text = it.take(1500) },
            multiline = true,
            id = "debt_share_message",
        )
        feedback?.let { TraxosNotice(it, NoticeKind.INFO) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraxosTextButton(
                {
                    val clip = ClipData.newPlainText("Traxos balance", text)
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        clip.description.extras =
                            android.os.PersistableBundle().apply {
                                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    feedback = "Copied. Clear the clipboard after sharing."
                },
                Modifier.weight(1f),
                !opening,
            ) {
                Text("Copy")
            }
            TraxosButton(
                {
                    if (!opening) {
                        opening = true
                        val send =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text)
                        try {
                            context.startActivity(Intent(send).setPackage("com.whatsapp"))
                            feedback =
                                "WhatsApp opened; choose the recipient and review before sending."
                        } catch (e: ActivityNotFoundException) {
                            try {
                                context.startActivity(Intent.createChooser(send, "Share balance"))
                                feedback = "Share sheet opened, not sent."
                            } catch (e: Exception) {
                                feedback = "No sharing app is available. Use Copy."
                                opening = false
                            }
                        } catch (e: SecurityException) {
                            feedback = "Sharing is blocked on this device. Use Copy."
                            opening = false
                        }
                    }
                },
                Modifier.weight(1f).testTag("debt_share_open"),
                !opening && text.isNotBlank(),
            ) {
                Text("Share")
            }
        }
    }
}
