@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.traxos.finance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.traxos.finance.BuildConfig
import app.traxos.finance.R
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val feedback: (Feedback) -> Unit = { f ->
        if (f.close) {
            vm.refresh()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            nav.popBackStack()
        }
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
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!form)
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.traxos_mark),
                                contentDescription = "Traxos",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (route == "settings") "Settings" else "TRAXOS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    navigationIcon = {
                        if (route == "settings")
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                    },
                    actions = {
                        if (route != "settings")
                            IconButton(
                                onClick = { nav.navigate("settings") { launchSingleTop = true } }
                            ) {
                                Icon(Icons.Default.Settings, "Settings")
                            }
                    },
                )
        },
        bottomBar = {
            if (!form && route != "settings")
                NavigationBar {
                    listOf(
                            Triple("home", "Home", Icons.Default.Home),
                            Triple("transactions", "Transactions", Icons.AutoMirrored.Filled.List),
                            Triple("accounts", "Accounts", Icons.Default.AccountCircle),
                        )
                        .forEach { (key, label, icon) ->
                            NavigationBarItem(
                                selected = route == key,
                                onClick = {
                                    nav.navigate(key) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, label) },
                                label = { Text(label) },
                            )
                        }
                }
        },
        floatingActionButton = {
            if (!form && route != "settings")
                FloatingActionButton(
                    onClick = { nav.navigate("transaction/new") { launchSingleTop = true } },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Add, "Add transaction")
                }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("home") {
                WithReadState(reads, vm::retry) {
                    HomeScreen(
                        it,
                        { nav.navigate("account/new") { launchSingleTop = true } },
                        { nav.navigate("transaction/$it") { launchSingleTop = true } },
                    )
                }
            }
            composable("transactions") {
                TransactionsScreen(
                    vm,
                    { nav.navigate("transaction/$it") { launchSingleTop = true } },
                )
            }
            composable("accounts") {
                WithReadState(reads, vm::retry) {
                    AccountsScreen(
                        it.accounts,
                        { nav.navigate("account/$it") { launchSingleTop = true } },
                    )
                }
            }
            composable("settings") { SettingsScreen(vm) }
            composable("transaction/{id}") { FormDestination(vm, false, nav, reads, feedback) }
            composable("account/{id}") { FormDestination(vm, true, nav, reads, feedback) }
        }
    }
}

@Composable
private fun WithReadState(
    state: ReadState,
    retry: () -> Unit,
    content: @Composable (ReadState.Ready) -> Unit,
) {
    when (state) {
        ReadState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        ReadState.Failed ->
            Message(
                "Could not open local records",
                "Your records have not been changed.",
                "Retry",
                retry,
            )
        is ReadState.Ready -> content(state)
    }
}

@Composable
private fun Message(
    title: String,
    detail: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(Space.large),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun MoneyText(minor: Long, currency: String, large: Boolean = false, prefix: String = "") {
    Text(
        prefix + Money.format(minor, currency),
        style =
            (if (large) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.titleMedium)
                .copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
    )
}

@Composable
internal fun HomeScreen(
    state: ReadState.Ready,
    createAccount: () -> Unit,
    openRecord: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.small,
                bottom = 96.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(Space.gutter),
    ) {
        item {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                "Your money, in view.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Text(
                "On this phone only · Not backed up",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.accounts.isEmpty())
            item {
                Message(
                    "Start with an account",
                    "Add cash or a bank account with your known opening balance. No sign-in needed.",
                    "Create account",
                    createAccount,
                )
            }
        else {
            item {
                Text(
                    "SPENT THIS MONTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.summary.isEmpty()) item { Text("No spending or income recorded this month.") }
            items(state.summary, key = { "summary:${it.currency}" }) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    MoneyText(s.expenseMinor, s.currency, true)
                    Text(
                        "Income · " + Money.format(s.incomeMinor, s.currency),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                HorizontalDivider()
                Text(
                    "Account balances",
                    modifier = Modifier.padding(top = Space.gutter),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(
                state.accounts.groupBy { it.account.currency }.toList(),
                key = { "balance:${it.first}" },
            ) { (currency, accounts) ->
                Column {
                    MoneyText(accounts.sumOf { it.balanceMinor }, currency)
                    Text(
                        "${accounts.size} account(s), including archived",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Text(
                "Recent transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.recent.isEmpty())
            item {
                Text(
                    "Nothing recorded yet. Tap + to add an expense or income.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(state.recent, key = { "recent:${it.record.id}" }) {
            RecordItem(it) { openRecord(it.record.id) }
        }
        item {
            Text(
                "Uninstalling or clearing app storage removes local records. Encrypted backups are planned for a later milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordItem(row: RecordRow, click: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.tiny),
    ) {
        // Stacked amount remains fully readable at narrow widths and large font scales.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (row.record.type == "INCOME") "+" else "−",
                Modifier.padding(end = Space.medium),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    row.record.note.ifBlank { row.categoryName },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${row.accountName} · ${row.categoryName}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(
            row.record.amountMinor,
            row.currency,
            prefix = if (row.record.type == "INCOME") "+ " else "− ",
        )
        Text(
            LocalDate.ofEpochDay(row.record.day).format(DateTimeFormatter.ofPattern("d MMM yyyy")) +
                " · " +
                (if (row.record.type == "INCOME") "Income" else "Expense"),
            style = MaterialTheme.typography.labelSmall,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TransactionsScreen(vm: MainViewModel, open: (String) -> Unit) {
    val search by vm.search.collectAsStateWithLifecycle()
    val list = vm.pages.collectAsLazyPagingItems()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = vm::search,
            label = { Text("Search notes, categories, accounts") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
            singleLine = true,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.small,
                    bottom = 96.dp,
                ),
        ) {
            if (list.loadState.refresh is LoadState.Loading)
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (list.loadState.refresh is LoadState.Error)
                item {
                    Message(
                        "Could not load transactions",
                        "Local records have not been changed.",
                        "Retry",
                        list::retry,
                    )
                }
            if (list.itemCount == 0 && list.loadState.refresh is LoadState.NotLoading)
                item {
                    Message(
                        if (search.isBlank()) "Your ledger starts here"
                        else "No matching transactions",
                        if (search.isBlank())
                            "Tap + to record your first expense or income. Create an account first if needed."
                        else "Try a different note, account or category.",
                    )
                }
            items(list.itemCount, key = list.itemKey { it.record.id }) { i ->
                list[i]?.let { row -> RecordItem(row) { open(row.record.id) } }
            }
            if (list.loadState.append is LoadState.Loading)
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (list.loadState.append is LoadState.Error)
                item { TextButton(onClick = list::retry) { Text("Retry loading more") } }
        }
    }
}

@Composable
private fun AccountsScreen(accounts: List<AccountBalance>, open: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.gutter, Space.small, Space.gutter, 96.dp),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Accounts",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                TextButton(onClick = { open("new") }) { Text("Add account") }
            }
        }
        if (accounts.isEmpty())
            item { Text("No accounts yet. Add cash or a bank account to begin.") }
        items(accounts, key = { it.account.id }) { a ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable { open(a.account.id) }
                    .padding(vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    a.account.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    a.account.type.lowercase().replaceFirstChar { it.uppercase() } +
                        if (a.account.archived) " · Archived" else " · Active",
                    style = MaterialTheme.typography.labelMedium,
                )
                MoneyText(a.balanceMinor, a.account.currency)
                Text(
                    "Tap to rename or ${if(a.account.archived) "restore" else "archive"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        item {
            Text(
                "Opening balances are known as of the date you choose. Each currency stays separate. Archived accounts retain history and balances.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        listOf("SYSTEM" to "Follow system", "LIGHT" to "Light", "DARK" to "Dark").forEach {
            (key, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(
                        selected = theme == key,
                        enabled = !busy,
                        role = Role.RadioButton,
                        onClick = { vm.theme(key) },
                    )
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = theme == key, onClick = null, enabled = !busy)
                Text(label, Modifier.padding(start = Space.medium))
            }
        }
        HorizontalDivider()
        Text("TRAXOS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text("Native Android · Offline personal finance")
        Text("Development milestone 1", style = MaterialTheme.typography.labelLarge)
        Text(
            "Saved on this phone only. Uninstalling, clearing storage or losing this phone can permanently remove your records."
        )
        Text(
            "Not yet implemented: encrypted backup/restore, PIN/biometrics, encrypted database, transfers, cards, plans and shared groups. This build uses app-private Room storage and is not ready for public release.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "No advertising, analytics or cloud SDKs in this build.",
            style = MaterialTheme.typography.bodySmall,
        )
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
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Text(
                    if (account) if (vm.isNew) "New account" else "Edit account"
                    else if (vm.isNew) "Add transaction" else "Edit transaction"
                )
            },
            navigationIcon = {
                IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.medium),
        ) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
