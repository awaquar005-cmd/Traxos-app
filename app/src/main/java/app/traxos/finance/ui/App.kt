@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.traxos.finance.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import app.traxos.finance.data.*
import app.traxos.finance.domain.NavigationLayout
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
import kotlinx.coroutines.launch

@Composable
fun TraxosApp(vm: MainViewModel) {
    val appContext = LocalContext.current
    val app = LocalContext.current.applicationContext as app.traxos.finance.TraxosApplication
    val gifts: GiftViewModel =
        viewModel(factory = viewModelFactory { initializer { GiftViewModel(app.gifts) } })
    val smart: SmartViewModel =
        viewModel(
            factory = viewModelFactory { initializer { SmartViewModel(app.smart, app.repository) } }
        )
    val incoming by GiftInbox.pending.collectAsState()
    val nav = rememberNavController()
    LaunchedEffect(incoming) {
        when (incoming) {
            GiftIncoming.SmartOpen -> {
                nav.navigate("smart") { launchSingleTop = true }
                GiftInbox.pending.value = null
            }
            GiftIncoming.Open -> {
                nav.navigate("gifts") { launchSingleTop = true }
                GiftInbox.pending.value = null
            }
            is GiftIncoming.Text -> nav.navigate("smart") { launchSingleTop = true }
            null -> Unit
            else -> nav.navigate("gift_import") { launchSingleTop = true }
        }
    }
    val snackbarGiftEvents = remember {
        kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    }
    LaunchedEffect(smart) { for (message in smart.events) snackbarGiftEvents.send(message) }
    LaunchedEffect(gifts) {
        for (message in gifts.events) {
            snackbarGiftEvents.send(message)
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val form =
        route.startsWith("transaction/") ||
            route.startsWith("account/") ||
            route.startsWith("gift_edit/") ||
            route.startsWith("ui_review") ||
            route.startsWith("card_details/")
    val transferEvents = remember {
        kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    }
    var transferCloseRequested by remember { mutableStateOf(false) }
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    var recurringOpen by rememberSaveable { mutableStateOf(false) }
    if (transferOpen)
        TraxosSheet("Transfer between accounts", { transferCloseRequested = true }) {
            TransferScreen(
                vm.repository,
                back = { transferOpen = false },
                closeRequested = transferCloseRequested,
                closeHandled = { transferCloseRequested = false },
            ) { id ->
                transferOpen = false
                transferEvents.trySend(id)
            }
        }
    if (recurringOpen)
        RecurringPanel(app.recurring, { recurringOpen = false }, app.recurringScheduler::kick)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        for (message in snackbarGiftEvents) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(TraxosSnackbarVisuals(message, NoticeKind.SUCCESS))
        }
    }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reads by vm.reads.collectAsStateWithLifecycle()
    val loadedPreferences by vm.preferences.collectAsStateWithLifecycle()
    val prefs = loadedPreferences ?: Preferences(balancesHidden = true)
    val visibleReads = if (loadedPreferences == null) ReadState.Loading else reads
    val slots = NavigationLayout.decode(prefs.navigationSlots)
    val preferenceBusy by vm.busy.collectAsStateWithLifecycle()
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
                app.giftScheduler.reconcile()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val mainNav: (String) -> Unit = { target ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val current = nav.currentDestination?.route.orEmpty()
        if (target == "settings") {
            val inSettings =
                current == "settings" ||
                    current.startsWith("settings/") ||
                    current == "navigation_settings" ||
                    current == "categories" ||
                    current.startsWith("labels/")
            if (current != "settings" && (!inSettings || !nav.popBackStack("settings", false))) {
                nav.clearBackStack("settings")
                nav.navigate("settings") {
                    launchSingleTop = true
                    restoreState = false
                }
            }
        } else if (target == "home") {
            // Never restore Home's saved child stack: this action means the dashboard itself.
            if (current != "home" && !nav.popBackStack("home", false))
                nav.navigate("home") {
                    launchSingleTop = true
                    restoreState = false
                }
        } else if (target in NavigationLayout.implemented) {
            if (current != target)
                nav.navigate(target) {
                    popUpTo("home") { saveState = false }
                    launchSingleTop = true
                    restoreState = false
                }
        } else
            nav.navigate(target) {
                launchSingleTop = true
                restoreState = false
            }
    }
    val add: () -> Unit = { nav.navigate("transaction/new") { launchSingleTop = true } }
    val search: () -> Unit = {
        focusSearch = true
        mainNav("transactions")
    }
    val feedback: (Feedback) -> Unit = { f ->
        if (f.close) {
            snackbar.currentSnackbarData?.dismiss()
            vm.refresh()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            nav.popBackStack()
        }
        if (f.preferenceChange) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        else
            scope.launch {
                val result =
                    snackbar.showSnackbar(
                        TraxosSnackbarVisuals(
                            f.text,
                            if (f.undoId != null) NoticeKind.UNDO else f.kind,
                            actionLabel = if (f.undoId != null) "Undo" else null,
                        )
                    )
                if (result == SnackbarResult.ActionPerformed) vm.undo(f)
            }
    }
    LaunchedEffect(vm) { for (event in vm.events) feedback(event) }
    LaunchedEffect(transferEvents) {
        for (id in transferEvents) {
            vm.refresh()
            feedback(
                Feedback("Transfer recorded between your accounts", undoId = id, undoRevision = 1)
            )
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (!form) SnackbarHost(snackbar) { TraxosSnackbar(it) } },
        topBar = {
            if (!form)
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.statusBarsPadding()) {
                        TraxosToolbar(
                            when (route) {
                                "transactions" -> "Transactions"
                                "accounts" -> "Accounts"
                                "settings" -> "Settings"
                                "settings/{section}" ->
                                    settingSections
                                        .firstOrNull {
                                            it.first == entry?.arguments?.getString("section")
                                        }
                                        ?.second ?: "Settings"
                                "navigation_settings" -> "Navigation"
                                "gifts" -> "Gift Cards"
                                "gift_import" -> "Smart import"
                                "gift_settings" -> "Gift settings"
                                "gift/{id}" -> "Gift card"
                                "more" -> "More"
                                "smart" -> "Smart Add"
                                "reports" -> "Monthly reports"
                                "deleted" -> "Deleted transactions"
                                "transfer" -> "Own-account transfer"
                                "categories" -> "Categories"
                                "labels/{kind}" -> "Saved entry options"
                                "card_payment/{id}" -> "Pay card"
                                else -> "Dashboard"
                            },
                            back =
                                if (
                                    route == "transfer" ||
                                        route == "deleted" ||
                                        (route == "transactions" &&
                                            nav.previousBackStackEntry?.destination?.route ==
                                                "reports") ||
                                        (route == "reports" &&
                                            nav.previousBackStackEntry
                                                ?.destination
                                                ?.route
                                                ?.startsWith("settings") == true) ||
                                        route == "categories" ||
                                        route.startsWith("labels/") ||
                                        route.startsWith("card_payment/") ||
                                        route == "settings" ||
                                        route.startsWith("settings/") ||
                                        route == "navigation_settings" ||
                                        route.startsWith("gift")
                                ) {
                                    {
                                        if (route == "transfer")
                                            (appContext as? androidx.activity.ComponentActivity)
                                                ?.onBackPressedDispatcher
                                                ?.onBackPressed()
                                        else nav.popBackStack()
                                        Unit
                                    }
                                } else null,
                            home = { mainNav("home") },
                            actions =
                                if (
                                    route != "transfer" &&
                                        (route == "transactions" ||
                                            prefs.centerAction != "ADD_TRANSACTION" ||
                                            "settings" !in slots ||
                                            "home" !in slots ||
                                            "more" !in slots)
                                ) {
                                    {
                                        TraxosActionMenu(
                                            buildList {
                                                if (route == "transactions") {
                                                    add("reports" to "Monthly reports")
                                                    add("deleted" to "Deleted transactions")
                                                }
                                                if ("home" !in slots) add("home" to "Home")
                                                if (prefs.centerAction != "ADD_TRANSACTION")
                                                    add("add" to "Add transaction")
                                                if ("settings" !in slots)
                                                    add("settings" to "Settings")
                                                if ("more" !in slots) add("more" to "More tools")
                                            }
                                        ) { id ->
                                            if (id == "add") add() else mainNav(id)
                                        }
                                    }
                                } else null,
                        )
                    }
                }
        },
        bottomBar = {
            if (!form && route != "transfer")
                TraxosBottomBar(
                    if (route == "navigation_settings" || route.startsWith("settings/")) "settings"
                    else route,
                    CenterShortcut.from(prefs.centerAction),
                    mainNav,
                    loadedPreferences != null,
                    slots,
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
                        smart.pending.collectAsStateWithLifecycle().value,
                        { nav.navigate("smart") { launchSingleTop = true } },
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
                            { id -> nav.navigate("card_details/$id") { launchSingleTop = true } },
                            { transferOpen = true },
                            { recurringOpen = true },
                        )
                    }
                }
            }
            composable("settings") {
                RoutePage("settings") {
                    SettingsScreen(vm) { section ->
                        val destination =
                            when (section) {
                                "review" -> "ui_review"
                                "navigation" -> "navigation_settings"
                                "deleted" -> "deleted"
                                "reports" -> "reports"
                                else -> "settings/$section"
                            }
                        nav.navigate(destination) {
                            popUpTo("settings")
                            launchSingleTop = true
                        }
                    }
                }
            }
            composable("settings/{section}") { e ->
                RoutePage("settings_detail") {
                    SettingsDetailScreen(
                        e.arguments?.getString("section").orEmpty(),
                        vm,
                        gifts,
                        { nav.navigate("navigation_settings") { launchSingleTop = true } },
                        { mainNav("accounts") },
                        {
                            nav.navigate("settings/data") {
                                popUpTo("settings")
                                launchSingleTop = true
                            }
                        },
                        { destination -> nav.navigate(destination) { launchSingleTop = true } },
                    )
                }
            }
            composable("navigation_settings") {
                RoutePage("navigation_settings") { NavigationSettingsScreen(vm) }
            }
            composable("reports") {
                RoutePage("reports") {
                    MonthlyReportsScreen(vm, prefs.balancesHidden) {
                        nav.navigate("transactions") { launchSingleTop = true }
                    }
                }
            }
            composable("deleted") {
                RoutePage("deleted") { DeletedTransactionsScreen(vm, prefs.balancesHidden) }
            }
            composable("more") { RoutePage("more") { MoreScreen(mainNav) } }
            composable("smart") {
                RoutePage("smart") {
                    SmartInboxScreen(
                        smart,
                        (incoming as? GiftIncoming.Text)?.value,
                        { GiftInbox.pending.value = null },
                    )
                }
            }
            composable("gifts") {
                RoutePage("gifts") {
                    GiftWalletScreen(
                        gifts,
                        { nav.navigate("gift/$it") { launchSingleTop = true } },
                        {
                            gifts.begin(defaultCurrency = prefs.defaultCurrency)
                            nav.navigate("gift_edit/new") { launchSingleTop = true }
                        },
                        { nav.navigate("gift_import") { launchSingleTop = true } },
                        { nav.navigate("gift_settings") { launchSingleTop = true } },
                        hidden = prefs.balancesHidden,
                    )
                }
            }
            composable("gift_settings") { RoutePage("gift_settings") { GiftSettingsScreen(gifts) } }
            composable("gift_import") {
                RoutePage("gift_import") {
                    GiftImportScreen(
                        gifts,
                        incoming,
                        { GiftInbox.pending.value = null },
                        { nav.popBackStack() },
                        { nav.navigate("gift_edit/new") { launchSingleTop = true } },
                        saved = { id ->
                            nav.popBackStack()
                            if (nav.currentDestination?.route == "gift_edit/{id}")
                                nav.popBackStack()
                            nav.navigate("gift/$id") { launchSingleTop = true }
                        },
                    )
                }
            }
            composable("gift_edit/{id}") { e ->
                RoutePage("gift_form") {
                    GiftFormScreen(
                        gifts,
                        e.arguments?.getString("id") ?: "new",
                        { nav.popBackStack() },
                        { id ->
                            nav.popBackStack()
                            if (nav.currentDestination?.route == "gift_import") nav.popBackStack()
                            if (nav.currentDestination?.route != "gift/{id}")
                                nav.navigate("gift/$id") { launchSingleTop = true }
                        },
                        scan = { nav.navigate("gift_import") { launchSingleTop = true } },
                    )
                }
            }
            composable("gift/{id}") { e ->
                RoutePage("gift_detail") {
                    val id = e.arguments?.getString("id") ?: ""
                    GiftDetailScreen(
                        gifts,
                        id,
                        {
                            gifts.draft.value = null
                            nav.navigate("gift_edit/$id") { launchSingleTop = true }
                        },
                        { nav.popBackStack() },
                    )
                }
            }
            composable("categories") {
                RoutePage("categories") { CatalogScreen(vm.repository, "CATEGORY") }
            }
            composable("labels/{kind}") { e ->
                RoutePage("labels") {
                    CatalogScreen(vm.repository, e.arguments?.getString("kind") ?: "MERCHANT")
                }
            }
            composable("card_details/{id}") { e ->
                RoutePage("card_details") {
                    CardDetailsScreen(
                        app.cardVault,
                        e.arguments?.getString("id").orEmpty(),
                        { nav.popBackStack() },
                    ) { message ->
                        feedback(Feedback(message, close = true))
                    }
                }
            }
            composable("card_payment/{id}") { e ->
                RoutePage("card_payment") {
                    CardPaymentScreen(vm.repository, e.arguments?.getString("id").orEmpty()) {
                        nav.popBackStack()
                    }
                }
            }
            composable(
                "ui_review?screen={screen}",
                arguments =
                    listOf(androidx.navigation.navArgument("screen") { defaultValue = "catalog" }),
            ) { e ->
                RoutePage("ui_review") {
                    DesignReviewScreen(
                        e.arguments?.getString("screen") ?: "catalog",
                        { nav.popBackStack() },
                        { mainNav("home") },
                    )
                }
            }
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
