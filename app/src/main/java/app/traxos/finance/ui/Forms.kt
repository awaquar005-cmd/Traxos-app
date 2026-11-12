package app.traxos.finance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money

@Composable
internal fun FormDestination(
    main: MainViewModel,
    account: Boolean,
    nav: NavHostController,
    reads: ReadState,
    feedback: (Feedback) -> Unit,
) {
    val vm: FormViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        FormViewModel(main.repository, createSavedStateHandle(), account)
                    }
                }
        )
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val latestFeedback by rememberUpdatedState(feedback)
    LaunchedEffect(vm) { for (event in vm.events) latestFeedback(event) }
    val fields by vm.fields.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var discard by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!busy) {
            if (vm.dirty()) discard = true else nav.popBackStack()
        }
    }
    BackHandler(onBack = leave)
    if (discard)
        TraxosDialog(
            "Discard unsaved changes?",
            "Your saved records will stay unchanged.",
            "Discard",
            {
                discard = false
                nav.popBackStack()
            },
            { discard = false },
            cancelLabel = "Keep editing",
            destructive = true,
        )
    if (delete)
        TraxosDialog(
            "Delete this transaction?",
            "The account balance will update. History is retained and Undo will be available after deletion.",
            "Delete transaction",
            {
                delete = false
                vm.delete()
            },
            { delete = false },
            destructive = true,
            enabled = !busy,
        )
    val selectionsReady =
        account || (!fields["accountId"].isNullOrBlank() && !fields["categoryId"].isNullOrBlank())
    val ready =
        !loading && fields.isNotEmpty() && selectionsReady && (account || reads is ReadState.Ready)
    Column(Modifier.fillMaxSize().imePadding()) {
        TraxosToolbar(
            if (account) if (vm.isNew) "New account" else "Edit account"
            else if (vm.isNew) "Add transaction" else "Edit transaction",
            back = leave,
        )
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading) repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(60.dp)) }
            else if (fields.isNotEmpty()) {
                if (account) AccountFormFields(fields, vm.isNew, !busy, vm::set)
                else
                    when (reads) {
                        ReadState.Loading ->
                            repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(60.dp)) }
                        ReadState.Failed -> {
                            TraxosNotice(
                                "Could not read accounts and categories.",
                                NoticeKind.ERROR,
                            )
                            TraxosTextButton(main::retry) { Text("Retry local records") }
                        }
                        is ReadState.Ready ->
                            TransactionFormFields(
                                fields,
                                reads,
                                !busy,
                                vm.isNew,
                                vm::set,
                                vm::default,
                            ) {
                                nav.navigate("account/new") { launchSingleTop = true }
                            }
                    }
            }
            if (!account && !vm.isNew)
                TraxosButton(
                    { delete = true },
                    Modifier.fillMaxWidth().testTag("form_delete"),
                    !busy && !loading && fields["revision"] != null,
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    TraxosNotice(error!!, NoticeKind.ERROR, Modifier.testTag("form_error"))
                    if (fields.isEmpty())
                        TraxosTextButton(vm::load) { Text("Retry opening record") }
                }
                TraxosButton(
                    {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        vm.save()
                    },
                    Modifier.fillMaxWidth().testTag("form_save"),
                    enabled = ready,
                    busy = busy,
                ) {
                    Text(
                        if (busy) "Saving…" else if (account) "Save account" else "Save transaction"
                    )
                }
                Text(
                    "Records stay on this phone · No cloud backup",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TransactionFormFields(
    f: Map<String, String>,
    state: ReadState.Ready,
    enabled: Boolean,
    isNew: Boolean,
    set: (String, String) -> Unit,
    initial: (String, String) -> Unit,
    addAccount: () -> Unit,
) {
    val accounts = state.accounts.filter { !it.account.archived || it.account.id == f["accountId"] }
    LaunchedEffect(accounts.map { it.account.id }, f["type"]) {
        if (f["accountId"].isNullOrEmpty() && accounts.isNotEmpty()) {
            val recent =
                state.recent
                    .firstOrNull { r -> accounts.any { it.account.id == r.record.accountId } }
                    ?.record
                    ?.accountId
            initial("accountId", recent ?: accounts.first().account.id)
        }
        if (f["categoryId"].isNullOrEmpty()) {
            val category =
                state.recent.firstOrNull { it.record.type == f["type"] }?.record?.categoryId
                    ?: state.categories.firstOrNull { it.type == f["type"] }?.id
            if (category != null) initial("categoryId", category)
        }
    }
    val account = accounts.firstOrNull { it.account.id == f["accountId"] }?.account
    TraxosSegment(
        listOf("EXPENSE" to "Expense", "INCOME" to "Income"),
        f["type"].orEmpty(),
        enabled,
    ) {
        set("type", it)
        set("categoryId", "")
    }
    TraxosAmountInput(
        f["amount"].orEmpty(),
        account?.currency ?: "Choose account",
        enabled,
        isNew,
    ) {
        set("amount", it.take(24))
    }
    if (accounts.isEmpty()) {
        TraxosNotice("Create an account before saving a transaction.", NoticeKind.INFO)
        TraxosButton(addAccount, Modifier.fillMaxWidth(), enabled, tone = ButtonTone.SECONDARY) {
            Text("Create account")
        }
    }
    TraxosSelector(
        "Account",
        f["accountId"].orEmpty(),
        accounts.map {
            it.account.id to
                (it.account.name +
                    " · " +
                    it.account.currency +
                    if (it.account.archived) " · Archived" else "")
        },
        enabled,
    ) {
        set("accountId", it)
    }
    TraxosSelector(
        "Category",
        f["categoryId"].orEmpty(),
        state.categories.filter { it.type == f["type"] }.map { it.id to it.name },
        enabled,
    ) {
        set("categoryId", it)
    }
    TraxosDateField("Transaction date", f["day"].orEmpty(), enabled) { set("day", it) }
    TraxosInput(
        "Note (optional)",
        f["note"].orEmpty(),
        { set("note", it.take(500)) },
        enabled = enabled,
        placeholder = "What was this for?",
        multiline = true,
        id = "note",
    )
}

@Composable
private fun AccountFormFields(
    f: Map<String, String>,
    isNew: Boolean,
    enabled: Boolean,
    set: (String, String) -> Unit,
) {
    if (isNew)
        TraxosSegment(listOf("CASH" to "Cash", "BANK" to "Bank"), f["type"].orEmpty(), enabled) {
            set("type", it)
        }
    TraxosInput(
        "Account name",
        f["name"].orEmpty(),
        { set("name", it.take(80)) },
        enabled = enabled,
        placeholder = "Give this account a name",
        id = "account_name",
    )
    if (isNew) {
        TraxosSelector(
            "Currency",
            f["currency"].orEmpty(),
            Money.currencies.map { it to it },
            enabled,
            sheet = false,
        ) {
            set("currency", it)
        }
        TraxosInput(
            "Known opening balance",
            f["opening"].orEmpty(),
            { set("opening", it.take(24)) },
            enabled = enabled,
            number = true,
            id = "opening_amount",
        )
        TraxosDateField("Opening date", f["day"].orEmpty(), enabled) { set("day", it) }
        Text(
            "Use your known balance as of this date. Zero is an explicit starting balance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        TraxosCard {
            Text("${f["type"]} · ${f["currency"]}", style = MaterialTheme.typography.labelLarge)
            Text(
                "Opened ${f["day"]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Archive account",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TraxosSwitch(
                    f["archived"].toBoolean(),
                    { set("archived", it.toString()) },
                    enabled = enabled,
                    label = "Archive account",
                )
            }
        }
        if (f["archived"].toBoolean())
            TraxosNotice(
                "Archived accounts keep all history and balances, and block new entries. Save to apply your changes.",
                NoticeKind.WARNING,
            )
    }
}
