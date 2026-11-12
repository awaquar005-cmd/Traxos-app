package app.traxos.finance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.traxos.finance.data.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

internal class CardDetailsViewModel(val repository: CardVaultRepository, val id: String) :
    ViewModel() {
    val fields = MutableStateFlow(CardSecret())
    val loading = MutableStateFlow(true)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    var revision = 0L
        private set

    private var original = CardSecret()
    private val operation = UUID.randomUUID().toString()
    val events = Channel<String>(Channel.BUFFERED)

    init {
        load()
    }

    fun update(change: (CardSecret) -> CardSecret) {
        fields.update(change)
        error.value = null
    }

    fun dirty() = fields.value != original

    fun load() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                require(repository.db.ledger().account(id)?.type in setOf("CREDIT_CARD", "BANK")) {
                    "Card account unavailable"
                }
                val result = repository.read(id)
                original = result.first
                fields.value = result.first
                revision = result.second
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value =
                    "Could not unlock card details. Keep existing data and check device security."
            } finally {
                loading.value = false
            }
        }
    }

    fun save(remove: Boolean = false) {
        if (busy.value || loading.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                if (remove) repository.remove(id, revision)
                else repository.save(id, fields.value, revision, operation)
                events.send(
                    if (remove) "Stored card details removed" else "Card details saved securely"
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value =
                    if (e is IllegalArgumentException) e.message
                    else "Could not save securely. Your inputs are kept."
                busy.value = false
            }
        }
    }
}

@Composable
internal fun CardDetailsScreen(
    repository: CardVaultRepository,
    id: String,
    back: () -> Unit,
    saved: (String) -> Unit,
) {
    val vm: CardDetailsViewModel =
        viewModel(
            factory = viewModelFactory { initializer { CardDetailsViewModel(repository, id) } }
        )
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    val fields by vm.fields.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val accounts by repository.db.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val account = accounts.firstOrNull { it.account.id == id }
    var discard by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!busy) {
            if (vm.dirty()) discard = true else back()
        }
    }
    BackHandler(onBack = leave)
    LaunchedEffect(vm) { for (message in vm.events) saved(message) }
    if (discard)
        TraxosDialog(
            "Discard card changes?",
            "Stored details and financial records stay unchanged.",
            "Discard",
            {
                discard = false
                back()
            },
            { discard = false },
            destructive = true,
        )
    if (remove)
        TraxosDialog(
            "Remove stored card details?",
            "Only the encrypted number, CVV and expiry are removed. Your account and transactions remain.",
            "Remove details",
            {
                remove = false
                vm.save(true)
            },
            { remove = false },
            destructive = true,
        )
    TraxosFormPanel {
        Column(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("card_details_form")
        ) {
            TraxosToolbar("Card details", back = leave)
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                account?.let {
                    Text(it.account.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (it.account.type == "BANK") "Account balance"
                        else if (it.balanceMinor < 0) "Amount owed" else "Credit balance",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    MoneyText(
                        if (it.account.type == "BANK") it.balanceMinor
                        else kotlin.math.abs(it.balanceMinor),
                        it.account.currency,
                    )
                }
                Text(
                    "Optional local reference. Details are encrypted and masked; this does not make payments or provide backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) repeat(3) { RowSkeleton() }
                else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TraxosInput(
                            "Card number",
                            fields.number,
                            { v -> vm.update { it.copy(number = v.take(24)) } },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            number = true,
                            secret = true,
                            id = "card_number",
                        )
                        TraxosTextButton(
                            {
                                copied =
                                    if (copyCardDetail(context, fields.number))
                                        "Copied number · clipboard clear requested after 60 seconds"
                                    else "Clipboard unavailable; try again while Traxos is open"
                            },
                            enabled = !busy && fields.number.isNotBlank(),
                        ) {
                            Text("Copy number")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TraxosInput(
                            "Expiry · MM/YY",
                            fields.expiry,
                            { v -> vm.update { it.copy(expiry = v.take(7)) } },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            id = "card_expiry",
                        )
                        TraxosTextButton(
                            {
                                copied =
                                    if (copyCardDetail(context, fields.expiry))
                                        "Copied expiry · clipboard clear requested after 60 seconds"
                                    else "Clipboard unavailable; try again while Traxos is open"
                            },
                            enabled = !busy && fields.expiry.isNotBlank(),
                        ) {
                            Text("Copy expiry")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TraxosInput(
                            "CVV (optional)",
                            fields.cvv,
                            { v -> vm.update { it.copy(cvv = v.take(4)) } },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            number = true,
                            secret = true,
                            id = "card_cvv",
                        )
                        TraxosTextButton(
                            {
                                copied =
                                    if (copyCardDetail(context, fields.cvv))
                                        "Copied CVV · clipboard clear requested after 60 seconds"
                                    else "Clipboard unavailable; try again while Traxos is open"
                            },
                            enabled = !busy && fields.cvv.isNotBlank(),
                        ) {
                            Text("Copy CVV")
                        }
                    }
                    if (
                        vm.revision > 0 &&
                            runCatching { fields.validated().expired() }.getOrDefault(false)
                    )
                        TraxosNotice(
                            "This card has expired. Historical transactions are retained.",
                            NoticeKind.WARNING,
                        )
                    if (vm.revision > 0)
                        TraxosTextButton({ remove = true }, enabled = !busy) {
                            Text("Remove stored details", color = MaterialTheme.colorScheme.error)
                        }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (copied != null)
                    TraxosNotice(
                        copied!!,
                        if (copied!!.startsWith("Copied")) NoticeKind.SUCCESS else NoticeKind.ERROR,
                    )
                if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
                TraxosButton(
                    { vm.save() },
                    Modifier.fillMaxWidth().testTag("save_card_details"),
                    !busy && !loading,
                    busy,
                ) {
                    Text("Save")
                }
            }
        }
    }
}
