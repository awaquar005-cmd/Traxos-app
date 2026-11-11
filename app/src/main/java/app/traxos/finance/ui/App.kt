@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.traxos.finance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import kotlinx.coroutines.launch

@Composable
fun TraxosApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val form = route.startsWith("transaction/") || route.startsWith("account/")
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reads by vm.reads.collectAsStateWithLifecycle()
    val loadedPreferences by vm.preferences.collectAsStateWithLifecycle()
    val prefs = loadedPreferences ?: Preferences(balancesHidden = true)
    val visibleReads = if (loadedPreferences == null) ReadState.Loading else reads
    val preferenceBusy by vm.busy.collectAsStateWithLifecycle()
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val mainNav: (String) -> Unit = { target ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (nav.currentDestination?.route != target) {
            if (target == "settings") nav.navigate(target) { launchSingleTop = true }
            else
                nav.navigate(target) {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
        }
    }
    val add: () -> Unit = { nav.navigate("transaction/new") { launchSingleTop = true } }
    val search: () -> Unit = {
        focusSearch = true
        mainNav("transactions")
    }
    val feedback: (Feedback) -> Unit = { f ->
        if (f.close) {
            vm.refresh()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            nav.popBackStack()
        }
        if (f.preferenceChange) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        else
            scope.launch {
                val result =
                    snackbar.showSnackbar(
                        f.text,
                        actionLabel = if (f.undoId != null) "Undo" else null,
                        withDismissAction = true,
                    )
                if (result == SnackbarResult.ActionPerformed) vm.undo(f)
            }
    }
    LaunchedEffect(vm) { for (event in vm.events) feedback(event) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!form)
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.statusBarsPadding()) {
                        TraxosToolbar(
                            when (route) {
                                "transactions" -> "Transactions"
                                "accounts" -> "Accounts"
                                "settings" -> "Settings"
                                else -> "Dashboard"
                            },
                            back =
                                if (route == "settings") {
                                    {
                                        nav.popBackStack()
                                        Unit
                                    }
                                } else null,
                            settings =
                                if (route != "settings") {
                                    { mainNav("settings") }
                                } else null,
                            add = if (route != "settings") add else null,
                        )
                    }
                }
        },
        bottomBar = {
            if (!form && route != "settings")
                TraxosBottomBar(
                    route,
                    CenterShortcut.from(prefs.centerAction),
                    mainNav,
                    loadedPreferences != null,
                ) {
                    when (CenterShortcut.from(prefs.centerAction)) {
                        CenterShortcut.ADD_TRANSACTION -> add()
                        CenterShortcut.HOME -> mainNav("home")
                        CenterShortcut.ACCOUNTS -> mainNav("accounts")
                        CenterShortcut.TRANSACTIONS -> mainNav("transactions")
                    }
                }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = "home",
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("home") {
                RoutePage("home") {
                    WithReadState(visibleReads, vm::retry) {
                        HomeScreen(
                            it,
                            { nav.navigate("account/new") { launchSingleTop = true } },
                            { id -> nav.navigate("transaction/$id") { launchSingleTop = true } },
                            prefs.balancesHidden,
                            { vm.balancesHidden(!prefs.balancesHidden) },
                            search,
                            { mainNav("accounts") },
                            { id -> nav.navigate("account/$id") { launchSingleTop = true } },
                            !preferenceBusy && loadedPreferences != null,
                        )
                    }
                }
            }
            composable("transactions") {
                RoutePage("transactions") {
                    TransactionsScreen(
                        vm,
                        { nav.navigate("transaction/$it") { launchSingleTop = true } },
                        prefs.balancesHidden,
                        focusSearch,
                        { focusSearch = false },
                        add,
                    )
                }
            }
            composable("accounts") {
                RoutePage("accounts") {
                    WithReadState(visibleReads, vm::retry, "accounts") {
                        AccountsScreen(
                            it.accounts,
                            { id -> nav.navigate("account/$id") { launchSingleTop = true } },
                            prefs.balancesHidden,
                        )
                    }
                }
            }
            composable("settings") { RoutePage("settings") { SettingsScreen(vm) } }
            composable("transaction/{id}") {
                RoutePage("transaction_form") { FormDestination(vm, false, nav, reads, feedback) }
            }
            composable("account/{id}") {
                RoutePage("account_form") { FormDestination(vm, true, nav, reads, feedback) }
            }
        }
    }
}

@Composable
private fun RoutePage(name: String, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("page_$name")
    ) {
        content()
    }
}

@Composable
internal fun WithReadState(
    state: ReadState,
    retry: () -> Unit,
    kind: String = "home",
    content: @Composable (ReadState.Ready) -> Unit,
) {
    when (state) {
        ReadState.Loading -> ScreenSkeleton(kind)
        ReadState.Failed ->
            Box(Modifier.padding(16.dp)) {
                Message(
                    "Could not open local records",
                    "Your records are unchanged.",
                    "Retry",
                    retry,
                )
            }
        is ReadState.Ready -> content(state)
    }
}

@Composable
private fun FormDestination(
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
    LaunchedEffect(vm) { for (event in vm.events) feedback(event) }
    val fields by vm.fields.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var discard by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (busy) feedback(Feedback("Saving on this phone…"))
        else if (vm.dirty()) discard = true else nav.popBackStack()
    }
    BackHandler(onBack = leave)
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your saved records will stay unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        nav.popBackStack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete this transaction?") },
            text = {
                Text(
                    "The account balance will update. The audit history is retained, and you can Undo after deletion."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        vm.delete()
                    }
                ) {
                    Text("Delete transaction")
                }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } },
        )
    Column(Modifier.fillMaxSize()) {
        TraxosToolbar(
            if (account) if (vm.isNew) "New account" else "Edit account"
            else if (vm.isNew) "Add transaction" else "Edit transaction",
            back = leave,
        )
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.medium),
        ) {
            if (loading) {
                repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(52.dp)) }
            }
            if (fields.isNotEmpty()) {
                if (account) AccountFields(fields, vm.isNew, !busy, vm::set)
                else
                    WithReadState(reads, main::retry) { state ->
                        TransactionFields(
                            fields,
                            state,
                            !busy,
                            vm.isNew,
                            vm::set,
                            vm::default,
                            { nav.navigate("account/new") { launchSingleTop = true } },
                        )
                    }
            }
            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (fields.isEmpty()) TextButton(onClick = vm::load) { Text("Retry") }
            }
            Button(
                shape = Radius.control,
                onClick = vm::save,
                enabled =
                    !busy &&
                        !loading &&
                        fields.isNotEmpty() &&
                        (account || reads is ReadState.Ready),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.small))
                }
                Text(if (busy) "Saving…" else if (account) "Save account" else "Save transaction")
            }
            if (!vm.isNew && !account)
                OutlinedButton(
                    onClick = { delete = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete transaction")
                }
            Text(
                "Stored on this phone · No cloud backup",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.large))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    enabled: Boolean = true,
    number: Boolean = false,
    multiline: Boolean = false,
    focus: Boolean = false,
    onChange: (String) -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) requester.requestFocus() }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusRequester(requester),
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        shape = Radius.control,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        keyboardOptions =
            KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
private fun Picker(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var show by rememberSaveable { mutableStateOf(false) }
    val value = options.firstOrNull { it.first == selected }?.second ?: "Choose $label"
    OutlinedButton(
        shape = Radius.control,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        onClick = { show = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.tiny)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (show)
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(label) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { it.first }) { (id, name) ->
                        TextButton(
                            onClick = {
                                show = false
                                onSelect(id)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
        )
}

@Composable
private fun AccountFields(
    f: Map<String, String>,
    isNew: Boolean,
    enabled: Boolean,
    set: (String, String) -> Unit,
) {
    Field("Account name", f["name"].orEmpty(), enabled) { set("name", it.take(80)) }
    if (isNew) {
        Picker(
            "Account type",
            f["type"].orEmpty(),
            listOf("CASH" to "Cash", "BANK" to "Bank"),
            enabled,
        ) {
            set("type", it)
        }
        Picker("Currency", f["currency"].orEmpty(), Money.currencies.map { it to it }, enabled) {
            set("currency", it)
        }
        Field("Known opening balance", f["opening"].orEmpty(), enabled, true) {
            set("opening", it.take(24))
        }
        Field("Opening date · YYYY-MM-DD", f["day"].orEmpty(), enabled) { set("day", it.take(10)) }
        Text(
            "Use your known balance as of this date; transactions cannot predate it. Zero is an explicit starting balance.",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(
            "${f["type"]} · ${f["currency"]} · Opening date ${f["day"]}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Archive account", Modifier.weight(1f))
            Switch(
                checked = f["archived"].toBoolean(),
                onCheckedChange = { set("archived", it.toString()) },
                enabled = enabled,
            )
        }
        Text(
            "Archived accounts keep history and balances, but cannot receive new entries.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TransactionFields(
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
            val recentAccount =
                state.recent
                    .firstOrNull { r -> accounts.any { it.account.id == r.record.accountId } }
                    ?.record
                    ?.accountId
            initial("accountId", recentAccount ?: accounts.first().account.id)
        }
        if (f["categoryId"].isNullOrEmpty()) {
            val recentCategory =
                state.recent.firstOrNull { it.record.type == f["type"] }?.record?.categoryId
            val category =
                recentCategory ?: state.categories.firstOrNull { it.type == f["type"] }?.id
            if (category != null) initial("categoryId", category)
        }
    }
    val account = accounts.firstOrNull { it.account.id == f["accountId"] }?.account
    Text(
        "${if(f["type"]=="INCOME") "Money received" else "Money spent"}",
        style = MaterialTheme.typography.titleLarge,
    )
    Field(
        "Amount · ${account?.currency ?: "choose account"}",
        f["amount"].orEmpty(),
        enabled,
        true,
        focus = isNew,
    ) {
        set("amount", it.take(24))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
        listOf("EXPENSE" to "Expense", "INCOME" to "Income").forEach { (key, label) ->
            FilterChip(
                selected = f["type"] == key,
                onClick = {
                    set("type", key)
                    set("categoryId", "")
                },
                label = { Text(label) },
                enabled = enabled,
            )
        }
    }
    if (accounts.isEmpty()) {
        Text("Create an account before saving a transaction.")
        TextButton(onClick = addAccount, enabled = enabled) { Text("Create account") }
    }
    Picker(
        "Account",
        f["accountId"].orEmpty(),
        accounts.map { it.account.id to "${it.account.name} · ${it.account.currency}" },
        enabled && accounts.isNotEmpty(),
    ) {
        set("accountId", it)
    }
    Picker(
        "Category",
        f["categoryId"].orEmpty(),
        state.categories.filter { it.type == f["type"] }.map { it.id to it.name },
        enabled,
    ) {
        set("categoryId", it)
    }
    Field("Date · YYYY-MM-DD", f["day"].orEmpty(), enabled) { set("day", it.take(10)) }
    Field("Note (optional)", f["note"].orEmpty(), enabled, multiline = true) {
        set("note", it.take(500))
    }
    Text(
        "Expenses reduce this account; income increases it. Transfers, refunds and card payments have separate semantics and are not available yet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
