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
    var addCardAfterSave by rememberSaveable { mutableStateOf(false) }
    val wantsCard by rememberUpdatedState(addCardAfterSave)
    LaunchedEffect(vm) {
        for (event in vm.events) {
            if (
                account &&
                    vm.isNew &&
                    wantsCard &&
                    event.close &&
                    vm.fields.value["type"] in setOf("CREDIT_CARD", "BANK")
            ) {
                latestFeedback(event.copy(close = false))
                nav.navigate("card_details/${vm.entityId}") {
                    popUpTo("account/new") { inclusive = true }
                    launchSingleTop = true
                }
            } else latestFeedback(event)
        }
    }
    val preferences by main.preferences.collectAsStateWithLifecycle()
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
        !loading &&
            preferences != null &&
            fields.isNotEmpty() &&
            selectionsReady &&
            (account || reads is ReadState.Ready)
    TraxosFormPanel {
        Column(Modifier.fillMaxWidth()) {
            TraxosToolbar(
                if (account) if (vm.isNew) "New account" else "Edit account"
                else if (vm.isNew) "Add transaction" else "Edit transaction",
                back = leave,
            )
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (loading) repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(60.dp)) }
                else if (!fields["linkId"].isNullOrBlank()) {
                    TraxosNotice(
                        "Linked movement. Delete / Undo changes both accounts together. To correct the amount, delete and record a new movement.",
                        NoticeKind.INFO,
                    )
                    Text(fields["amount"].orEmpty(), style = MaterialTheme.typography.titleLarge)
                } else if (fields.isNotEmpty()) {
                    if (account) {
                        AccountFormFields(fields, vm.isNew, !busy, vm::set)
                        if (vm.isNew && fields["type"] in setOf("CREDIT_CARD", "BANK"))
                            TraxosCheckbox(
                                addCardAfterSave,
                                { addCardAfterSave = it },
                                "Add card number, CVV & expiry after saving",
                                enabled = !busy,
                            )
                        if (!vm.isNew && fields["type"] in setOf("CREDIT_CARD", "BANK"))
                            TraxosButton(
                                {
                                    nav.navigate(
                                        "card_details/${nav.currentBackStackEntry?.arguments?.getString("id")}"
                                    ) {
                                        launchSingleTop = true
                                    }
                                },
                                Modifier.fillMaxWidth(),
                                !busy,
                                tone = ButtonTone.SECONDARY,
                            ) {
                                Text("Card number, expiry & CVV")
                            }
                        if (!vm.isNew && fields["type"] == "CREDIT_CARD")
                            TraxosButton(
                                {
                                    nav.navigate(
                                        "card_payment/${nav.currentBackStackEntry?.arguments?.getString("id")}"
                                    )
                                },
                                Modifier.fillMaxWidth(),
                                !busy,
                                tone = ButtonTone.SECONDARY,
                            ) {
                                Text("Pay card")
                            }
                    } else
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
                                    preferences ?: Preferences(),
                                    vm::default,
                                    main.repository,
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
                    Modifier.fillMaxWidth().padding(12.dp),
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
                        enabled = ready && fields["linkId"].isNullOrBlank(),
                        busy = busy,
                    ) {
                        Text(
                            if (busy) "Saving…"
                            else if (account) "Save account" else "Save transaction"
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
}

@Composable
private fun TransactionFormFields(
    f: Map<String, String>,
    state: ReadState.Ready,
    enabled: Boolean,
    isNew: Boolean,
    set: (String, String) -> Unit,
    preferences: Preferences,
    initial: (String, String) -> Unit,
    repository: LedgerRepository,
    addAccount: () -> Unit,
) {
    val accounts = state.accounts.filter { !it.account.archived || it.account.id == f["accountId"] }
    val categoryType = if (f["type"] == "REFUND") "EXPENSE" else f["type"]
    LaunchedEffect(accounts.map { it.account.id }, f["type"]) {
        if (f["accountId"].isNullOrEmpty() && accounts.isNotEmpty()) {
            val recent =
                state.recent
                    .firstOrNull { r -> accounts.any { it.account.id == r.record.accountId } }
                    ?.record
                    ?.accountId
            initial(
                "accountId",
                preferences.defaultAccountId.takeIf { id -> accounts.any { it.account.id == id } }
                    ?: recent
                    ?: accounts.first().account.id,
            )
        }
        if (f["categoryId"].isNullOrEmpty()) {
            val category =
                preferences.defaultExpenseCategoryId.takeIf { id ->
                    f["type"] == "EXPENSE" &&
                        state.categories.any { it.id == id && it.type == "EXPENSE" && !it.archived }
                }
                    ?: state.recent
                        .firstOrNull {
                            it.record.type == f["type"] &&
                                state.categories.any { c ->
                                    c.id == it.record.categoryId && !c.archived
                                }
                        }
                        ?.record
                        ?.categoryId
                    ?: state.categories.firstOrNull { it.type == categoryType && !it.archived }?.id
            if (category != null) initial("categoryId", category)
        }
    }
    val account = accounts.firstOrNull { it.account.id == f["accountId"] }?.account
    LaunchedEffect(account?.id, account?.type) {
        if (
            (account?.type == "CREDIT_CARD" && f["type"] == "INCOME") ||
                (account?.type != "CREDIT_CARD" && f["type"] == "REFUND")
        ) {
            set("type", "EXPENSE")
            set("categoryId", "")
        }
    }
    TraxosSegment(
        if (account?.type == "CREDIT_CARD") listOf("EXPENSE" to "Purchase", "REFUND" to "Refund")
        else listOf("EXPENSE" to "Expense", "INCOME" to "Income"),
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
    AccountPicker(repository, f["accountId"].orEmpty(), accounts, enabled) { set("accountId", it) }
    CategoryPicker(
        repository,
        f["categoryId"].orEmpty(),
        state.categories,
        f["type"].orEmpty(),
        enabled,
    ) {
        set("categoryId", it)
    }
    if (account?.type == "CREDIT_CARD")
        Text(
            "Purchases increase debt. Refunds reduce expense and debt; repayments belong in Pay card.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    TraxosDateField("Transaction date", f["day"].orEmpty(), enabled) { set("day", it) }
    var details by rememberSaveable { mutableStateOf(!isNew || !f["note"].isNullOrBlank()) }
    TraxosTextButton({ details = !details }, Modifier.testTag("transaction_details"), enabled) {
        Text(if (details) "Less details" else "More details")
    }
    if (details) {
        val labels by repository.dao.labels().collectAsStateWithLifecycle(emptyList())
        SavedLabelPicker(repository, "MERCHANT") { set("merchantId", it.id) }
        labels.firstOrNull { it.id == f["merchantId"] }?.let { Text("Merchant: ${it.name}") }
        if (!f["merchantId"].isNullOrBlank())
            TraxosTextButton({ set("merchantId", "") }) { Text("Clear merchant") }
        SavedLabelPicker(repository, "DESCRIPTION") { set("note", it.name) }
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
}

@Composable
internal fun AccountFormFields(
    f: Map<String, String>,
    isNew: Boolean,
    enabled: Boolean,
    set: (String, String) -> Unit,
) {
    if (isNew)
        TraxosSelector(
            "Account type",
            f["type"].orEmpty(),
            LedgerRepository.accountTypes.map { it to accountTypeLabel(it) },
            enabled,
        ) {
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
            if (f["type"] == "CREDIT_CARD") "Opening amount owed" else "Known opening balance",
            f["opening"].orEmpty(),
            { set("opening", it.take(24)) },
            enabled = enabled,
            number = true,
            id = "opening_amount",
        )
        if (f["type"] == "CREDIT_CARD")
            Text(
                "Enter outstanding debt, never available credit. Use Pay card for repayments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        var details by rememberSaveable { mutableStateOf(false) }
        TraxosTextButton({ details = !details }, enabled = enabled) {
            Text(if (details) "Less details" else "More details")
        }
        if (details) TraxosDateField("Opening date", f["day"].orEmpty(), enabled) { set("day", it) }
        Text(
            "Use your known balance as of this date. Zero is an explicit starting balance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        TraxosCard {
            Text(
                "${accountTypeLabel(f["type"].orEmpty())} · ${f["currency"]}",
                style = MaterialTheme.typography.labelLarge,
            )
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
    var styleOpen by rememberSaveable { mutableStateOf(false) }
    TraxosTextButton({ styleOpen = !styleOpen }, enabled = enabled) {
        Text(if (styleOpen) "Hide icon and color" else "Icon and color")
    }
    if (styleOpen) {
        TraxosSelector(
            "Icon",
            f["icon"] ?: "wallet",
            LedgerRepository.icons.map { it to it },
            enabled,
        ) {
            set("icon", it)
        }
        TraxosSelector(
            "Color",
            f["color"] ?: "BLUE",
            LedgerRepository.colors.map { it to it },
            enabled,
        ) {
            set("color", it)
        }
    }
}
