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
import kotlinx.coroutines.launch

@Composable
fun TraxosApp(vm: MainViewModel) {
    val app = LocalContext.current.applicationContext as app.traxos.finance.TraxosApplication
    val gifts: GiftViewModel =
        viewModel(factory = viewModelFactory { initializer { GiftViewModel(app.gifts) } })
    val incoming by GiftInbox.pending.collectAsState()
    val nav = rememberNavController()
    LaunchedEffect(incoming) {
        when (incoming) {
            GiftIncoming.Open -> {
                nav.navigate("gifts") { launchSingleTop = true }
                GiftInbox.pending.value = null
            }
            null -> Unit
            else -> nav.navigate("gift_import") { launchSingleTop = true }
        }
    }
    val snackbarGiftEvents = remember {
        kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    }
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
            route.startsWith("gift_edit/")
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
        if (nav.currentDestination?.route != target) {
            if (target == "settings" && nav.currentDestination?.route == "navigation_settings")
                nav.popBackStack("settings", false)
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
                                "navigation_settings" -> "Navigation"
                                "gifts" -> "Gift Cards"
                                "gift_import" -> "Smart import"
                                "gift_settings" -> "Gift settings"
                                "gift/{id}" -> "Gift card"
                                "more" -> "More"
                                else -> "Dashboard"
                            },
                            back =
                                if (
                                    route == "settings" ||
                                        route == "navigation_settings" ||
                                        route.startsWith("gift")
                                ) {
                                    {
                                        nav.popBackStack()
                                        Unit
                                    }
                                } else null,
                            actions =
                                if (
                                    prefs.centerAction != "ADD_TRANSACTION" ||
                                        "settings" !in slots ||
                                        "more" !in slots
                                ) {
                                    {
                                        TraxosActionMenu(
                                            buildList {
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
            if (!form)
                TraxosBottomBar(
                    if (route == "navigation_settings") "settings" else route,
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
            composable("settings") {
                RoutePage("settings") {
                    SettingsScreen(
                        vm,
                        { nav.navigate("navigation_settings") { launchSingleTop = true } },
                        { nav.navigate("gift_settings") { launchSingleTop = true } },
                    )
                }
            }
            composable("navigation_settings") {
                RoutePage("navigation_settings") { NavigationSettingsScreen(vm) }
            }
            composable("more") { RoutePage("more") { MoreScreen(mainNav) } }
            composable("gifts") {
                RoutePage("gifts") {
                    GiftWalletScreen(
                        gifts,
                        { nav.navigate("gift/$it") { launchSingleTop = true } },
                        {
                            gifts.begin()
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
