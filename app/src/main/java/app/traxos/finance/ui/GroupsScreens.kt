package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun GroupsWallet(
    vm: MoneyToolsViewModel = viewModel(),
    currency: String,
    navigate: (String) -> Unit,
) {
    val groups by vm.app.groups.groups.collectAsStateWithLifecycle(null)
    var archived by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("groups_wallet"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Shared groups", "Add") { navigate("money_edit/GROUP/new") } }
        item {
            TraxosNotice(
                "Local groups · balances are this phone’s estimate. Live invitations and sharing await backend setup.",
                NoticeKind.INFO,
            )
        }
        item { TraxosCheckbox(archived, { archived = it }, "Show archived") }
        if (groups == null) item { SkeletonBlock(Modifier.fillMaxWidth().height(64.dp)) }
        else if (groups.orEmpty().none { it.archived == archived })
            item {
                Text(
                    "No groups yet. Create a group and add the people who share expenses.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        items(groups.orEmpty().filter { it.archived == archived }, key = { it.id }) { g ->
            TraxosCard {
                SettingsRow(
                    g.name,
                    "${g.currency} · Local only${if(g.archived)" · Archived" else ""}",
                ) {
                    navigate("group/${g.id}")
                }
            }
        }
    }
}

@Composable
internal fun GroupDetail(
    id: String,
    navigate: (String) -> Unit,
    hidden: Boolean,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.groups
    val snapshot by remember(id) { repo.db.groups().snapshot(id) }.collectAsStateWithLifecycle(null)
    val group = snapshot?.group
    val members =
        snapshot
            ?.members
            .orEmpty()
            .sortedWith(compareBy<GroupMember> { it.archived }.thenBy { it.name })
    val events = snapshot?.eventRows.orEmpty().map { it.event }.sortedByDescending { it.createdAt }
    val shares = snapshot?.eventRows.orEmpty().flatMap { it.shares }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var memberEditor by remember { mutableStateOf(false) }
    var member by remember { mutableStateOf<GroupMember?>(null) }
    var detail by rememberSaveable { mutableStateOf("") }
    var reverse by rememberSaveable { mutableStateOf("") }
    if (group == null) {
        Text("Loading group…", Modifier.padding(16.dp))
        return
    }
    val balances = remember(members, events, shares) { GroupMath.balances(members, events, shares) }
    if (memberEditor) GroupMemberSheet(id, member, { memberEditor = false })
    if (reverse.isNotBlank())
        TraxosDialog(
            "Reverse event?",
            "Keep the original in history and reverse its effect on the group. Linked transactions remain in your ledger with their ordinary report treatment.",
            "Reverse",
            {
                val e = reverse
                reverse = ""
                vm.run { repo.reverse(e, UUID.randomUUID().toString()) }
            },
            { reverse = "" },
            destructive = true,
            enabled = !busy,
        )
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        var name by rememberSaveable { mutableStateOf(group.name) }
        val operation = rememberSaveable { UUID.randomUUID().toString() }
        val completed by vm.completed.collectAsStateWithLifecycle()
        val initial = remember { completed }
        LaunchedEffect(completed) { if (completed > initial) renaming = false }
        TraxosSheet("Rename group", { if (!busy) renaming = false }) {
            TraxosInput("Name", name, { name = it.take(80) }, enabled = !busy)
            error?.let { TraxosNotice(it, NoticeKind.ERROR) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton({ renaming = false }, Modifier.weight(1f), !busy) {
                    Text("Cancel")
                }
                TraxosButton(
                    { vm.run { repo.rename(id, group.revision, name, operation) } },
                    Modifier.weight(1f),
                    !busy,
                    busy = busy,
                ) {
                    Text("Save")
                }
            }
        }
    }
    val selected = events.firstOrNull { it.id == detail }
    if (selected != null)
        TraxosSheet("Group history", { detail = "" }, true) {
            Text(
                selected.title.ifBlank { selected.kind },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${LocalDate.ofEpochDay(selected.day)} · ${selected.kind.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
            MoneyText(selected.amountMinor, group.currency, hidden = hidden)
            if (selected.kind == "EXPENSE") {
                Text(
                    "Paid by ${members.firstOrNull{it.id==selected.payerId}?.name.orEmpty()} · ${selected.splitMethod.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                shares
                    .filter { it.eventId == selected.id }
                    .forEach { s ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                members.firstOrNull { it.id == s.memberId }?.name.orEmpty(),
                                Modifier.weight(1f),
                            )
                            MoneyText(s.amountMinor, group.currency, hidden = hidden)
                        }
                    }
            } else if (selected.kind == "SETTLEMENT")
                Text(
                    "${members.firstOrNull{it.id==selected.payerId}?.name} → ${members.firstOrNull{it.id==selected.receiverId}?.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            if (
                selected.reversalOf == null &&
                    events.none { it.reversalOf == selected.id } &&
                    !group.archived
            )
                TraxosButton(
                    {
                        detail = ""
                        reverse = selected.id
                    },
                    Modifier.fillMaxWidth(),
                    !busy,
                    tone = ButtonTone.DESTRUCTIVE,
                ) {
                    Text("Reverse")
                }
            else
                Text(
                    "Preserved history · reversed events are excluded from balances.",
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("group_detail"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(group.name, if (!group.archived) "Edit" else null) { renaming = true }
            Text(
                "${group.currency} · Local only · not synchronized",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            TraxosCard {
                Text("Your net balance", style = MaterialTheme.typography.labelSmall)
                MoneyText(balances[group.selfId] ?: 0, group.currency, true, hidden = hidden)
                Text(
                    "Positive: you should receive. Negative: you owe.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { error?.let { TraxosNotice(it, NoticeKind.ERROR) } }
        if (!group.archived)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosButton(
                        { navigate("money_edit/GROUP_EXPENSE/$id") },
                        Modifier.weight(1f),
                    ) {
                        Text("Expense")
                    }
                    TraxosButton(
                        { navigate("money_edit/GROUP_SETTLE/$id") },
                        Modifier.weight(1f),
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Settle")
                    }
                }
            }
        item {
            SectionHeader("Members", if (!group.archived) "Add" else null) {
                member = null
                memberEditor = true
            }
        }
        items(members, key = { it.id }) { m ->
            TraxosCard {
                SettingsRow(
                    m.name + (if (m.id == group.selfId) " · You" else ""),
                    if (m.archived) "Archived" else "Local member",
                ) {
                    if (!group.archived) {
                        member = m
                        memberEditor = true
                    }
                }
                MoneyText(balances[m.id] ?: 0, group.currency, hidden = hidden)
            }
        }
        item { SectionHeader("History") }
        if (events.isEmpty())
            item {
                Text(
                    "No expenses or settlements recorded.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        items(events.take(100), key = { it.id }) { e ->
            SettingsRow(
                e.title.ifBlank { e.kind.lowercase() },
                "${LocalDate.ofEpochDay(e.day)} · ${e.kind.lowercase()}${if(events.any{it.reversalOf==e.id})" · reversed" else ""}",
            ) {
                detail = e.id
            }
        }
        item {
            Text(
                "Latest 100 events shown. All saved history is retained.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            TraxosTextButton(
                { vm.run { repo.archive(id, group.revision, !group.archived) } },
                Modifier.fillMaxWidth(),
                !busy && (group.archived || balances.values.all { it == 0L }),
            ) {
                Text(if (group.archived) "Restore" else "Archive")
            }
        }
    }
}

@Composable
private fun GroupMemberSheet(
    groupId: String,
    old: GroupMember?,
    dismiss: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(key = "member_editor"),
) {
    var name by rememberSaveable(old?.id) { mutableStateOf(old?.name.orEmpty()) }
    var archived by rememberSaveable(old?.id) { mutableStateOf(old?.archived ?: false) }
    val operation = rememberSaveable(old?.id) { UUID.randomUUID().toString() }
    val memberId = rememberSaveable(old?.id) { old?.id ?: UUID.randomUUID().toString() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val done by vm.completed.collectAsStateWithLifecycle()
    val initial = remember { done }
    LaunchedEffect(done) { if (done > initial) dismiss() }
    TraxosSheet(if (old == null) "Add member" else "Edit member", { if (!busy) dismiss() }) {
        Text(
            "This adds a local person, not an accepted online invitation.",
            style = MaterialTheme.typography.bodySmall,
        )
        TraxosInput("Name", name, { name = it.take(80) }, enabled = !busy, id = "group_member")
        if (old != null)
            TraxosCheckbox(archived, { archived = it }, "Archive member", enabled = !busy)
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraxosTextButton(dismiss, Modifier.weight(1f), !busy) { Text("Cancel") }
            TraxosButton(
                {
                    vm.run {
                        vm.app.groups.member(
                            GroupMember(memberId, groupId, name, archived, operationId = operation),
                            old?.revision ?: 0,
                        )
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

@Composable
internal fun GroupEditor(
    kind: String,
    id: String,
    currencyDefault: String,
    close: () -> Unit,
    vm: MoneyToolsViewModel = viewModel(),
) {
    val repo = vm.app.groups
    val snapshot by remember(id) { repo.db.groups().snapshot(id) }.collectAsStateWithLifecycle(null)
    val group = snapshot?.group
    val members =
        snapshot
            ?.members
            .orEmpty()
            .sortedWith(compareBy<GroupMember> { it.archived }.thenBy { it.name })
    val events = snapshot?.eventRows.orEmpty().map { it.event }
    val shares = snapshot?.eventRows.orEmpty().flatMap { it.shares }
    val records by repo.db.groups().linkCandidates().collectAsStateWithLifecycle(emptyList())
    val accounts by repo.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val entity = rememberSaveable { UUID.randomUUID().toString() }
    val operation = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf("") }
    var selfName by rememberSaveable { mutableStateOf("You") }
    var currency by rememberSaveable { mutableStateOf(currencyDefault) }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var payer by rememberSaveable { mutableStateOf("") }
    var receiver by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("other-expense") }
    val categories by repo.db.ledger().categories().collectAsStateWithLifecycle(emptyList())
    var method by rememberSaveable { mutableStateOf("EQUAL") }
    var selected by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var link by rememberSaveable { mutableStateOf("") }
    var addMember by remember { mutableStateOf(false) }
    val creating = kind == "GROUP"
    val settling = kind == "GROUP_SETTLE"
    val unit = group?.currency ?: currency
    LaunchedEffect(group?.selfId) { if (payer.isBlank()) payer = group?.selfId.orEmpty() }
    LaunchedEffect(members) {
        if (selected.isEmpty() && members.isNotEmpty())
            selected = members.filter { !it.archived }.associate { it.id to "1" }
    }
    if (addMember) GroupMemberSheet(id, null, { addMember = false })
    MoneyEditor(
        if (creating) "New group" else if (settling) "Settle group" else "Group expense",
        vm,
        close,
        {
            vm.run {
                when {
                    creating -> repo.create(entity, name, currency, selfName, operation)
                    settling ->
                        repo.settle(
                            id,
                            payer,
                            receiver,
                            Money.parse(amount, unit),
                            LocalDate.parse(date).toEpochDay(),
                            name,
                            link.takeIf { it.isNotBlank() },
                            operation,
                        )
                    else ->
                        repo.expense(
                            id,
                            payer,
                            Money.parse(amount, unit),
                            method,
                            selected,
                            LocalDate.parse(date).toEpochDay(),
                            name,
                            link.takeIf { it.isNotBlank() },
                            operation,
                            category,
                        )
                }
            }
        },
    ) {
        if (creating) {
            TraxosInput(
                "Group name",
                name,
                { name = it.take(80) },
                enabled = !busy,
                id = "group_name",
            )
            TraxosInput("Your name", selfName, { selfName = it.take(80) }, enabled = !busy)
            TraxosSelector("Currency", currency, Money.currencies.map { it to it }, !busy) {
                currency = it
            }
            TraxosNotice(
                "Members and expenses stay on this phone until a shared backend is configured.",
                NoticeKind.INFO,
            )
        } else if (group == null) Text("Loading group…")
        else {
            TraxosNotice(
                "Your share counts in personal spending. Link the existing payment to prevent duplicate spending. Settlements are not income or expenses. Group records do not move bank money.",
                NoticeKind.INFO,
            )
            TraxosInput(
                if (settling) "Note · optional" else "Description",
                name,
                { name = it.take(120) },
                enabled = !busy,
                id = "group_title",
            )
            TraxosInput(
                "Amount · $unit",
                amount,
                { amount = it },
                enabled = !busy,
                number = true,
                id = "group_amount",
            )
            TraxosDateField("Date", date, !busy) { date = it }
            TraxosSelector(
                if (settling) "From" else "Paid by",
                payer,
                members.filter { !it.archived }.map { it.id to it.name },
                !busy,
            ) {
                payer = it
                link = ""
            }
            if (settling) {
                TraxosSelector(
                    "To",
                    receiver,
                    members.filter { !it.archived && it.id != payer }.map { it.id to it.name },
                    !busy,
                ) {
                    receiver = it
                }
                val balance = GroupMath.balances(members, events, shares)
                Text(
                    "From owes ${Money.format(maxOf(0,-(balance[payer]?:0)),unit)} · To is owed ${Money.format(maxOf(0,balance[receiver]?:0),unit)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                CategoryPicker(vm.app.repository, category, categories, "EXPENSE", !busy) {
                    category = it
                }
                TraxosSelector(
                    "Split",
                    method,
                    listOf(
                        "EQUAL" to "Equal",
                        "EXACT" to "Exact amounts",
                        "PERCENT" to "Percentages",
                        "SHARES" to "Shares",
                    ),
                    !busy,
                ) {
                    method = it
                    selected = selected.mapValues { if (it.value == "1") "" else it.value }
                }
                members
                    .filter { !it.archived }
                    .forEach { m ->
                        TraxosCheckbox(
                            m.id in selected,
                            { checked ->
                                selected =
                                    if (checked)
                                        selected +
                                            (m.id to
                                                if (method == "EQUAL" || method == "SHARES") "1"
                                                else "")
                                    else selected - m.id
                            },
                            m.name,
                            enabled = !busy,
                        )
                        if (m.id in selected && method != "EQUAL")
                            TraxosInput(
                                if (method == "EXACT") "${m.name} · $unit"
                                else if (method == "PERCENT") "${m.name} · %"
                                else "${m.name} · shares",
                                selected[m.id].orEmpty(),
                                { selected = selected + (m.id to it) },
                                enabled = !busy,
                                number = true,
                            )
                    }
                TraxosTextButton({ addMember = true }, Modifier.fillMaxWidth(), !busy) {
                    Text("Add member")
                }
            }
            val candidates =
                records.filter { r ->
                    accounts.any { it.account.id == r.accountId && it.account.currency == unit } &&
                        r.type ==
                            (if (settling && receiver == group.selfId) "INCOME" else "EXPENSE")
                }
            if (payer == group.selfId || settling && receiver == group.selfId)
                TraxosSelector(
                    "Link transaction",
                    link,
                    listOf("" to "Not linked · no account changes") +
                        candidates.map {
                            it.id to
                                "${it.note.ifBlank{it.type}} · ${Money.format(it.amountMinor,unit)} · ${LocalDate.ofEpochDay(it.day)}"
                        },
                    !busy,
                ) { selectedId ->
                    link = selectedId
                    candidates
                        .firstOrNull { it.id == selectedId }
                        ?.let {
                            category = it.categoryId
                            amount = Money.editable(it.amountMinor, unit)
                            date = LocalDate.ofEpochDay(it.day).toString()
                        }
                }
        }
    }
}
