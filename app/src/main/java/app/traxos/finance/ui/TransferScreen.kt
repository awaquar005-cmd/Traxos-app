package app.traxos.finance.ui

import androidx.activity.compose.BackHandler
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
import app.traxos.finance.data.LedgerRepository
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TransferScreen(
    repository: LedgerRepository,
    back: () -> Unit,
    saved: (String) -> Unit,
) {
    val rows by repository.dao.accounts().collectAsStateWithLifecycle(emptyList())
    var source by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val op = rememberSaveable { UUID.randomUUID().toString() }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val currency = rows.firstOrNull { it.account.id == source }?.account?.currency
    var discard by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (!busy) {
            if (
                amount.isNotBlank() ||
                    source.isNotBlank() ||
                    destination.isNotBlank() ||
                    day != LocalDate.now().toString()
            )
                discard = true
            else back()
        }
    }
    if (discard)
        TraxosDialog(
            "Discard transfer draft?",
            "Your saved account balances stay unchanged.",
            "Discard",
            {
                discard = false
                back()
            },
            { discard = false },
            cancelLabel = "Keep editing",
            destructive = true,
        )
    LaunchedEffect(op) {
        if (busy) {
            if (repository.dao.operation(op) != null) saved(op) else busy = false
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Move money, keep spending accurate", style = MaterialTheme.typography.titleLarge)
            TraxosNotice(
                "Between your own accounts. Neither side is income or an expense. Credit card bills use Pay card.",
                NoticeKind.INFO,
            )
            AccountPicker(repository, source, rows, !busy, "From account", allowCard = false) {
                source = it
                if (destination == it) destination = ""
            }
            AccountPicker(
                repository,
                destination,
                rows.filter { it.account.id != source },
                !busy,
                "To account",
                currency,
                false,
            ) {
                destination = it
            }
            TraxosAmountInput(amount, currency ?: "Choose source account", !busy, false) {
                amount = it.take(24)
            }
            TraxosDateField("Transfer date", day, !busy) { day = it }
            Text(
                "Both account balances update together. No currency conversion is inferred.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    if (!busy) {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                repository.transfer(
                                    op,
                                    source,
                                    destination,
                                    Money.parse(amount, currency.orEmpty()),
                                    LocalDate.parse(day).toEpochDay(),
                                )
                                saved(op)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error =
                                    if (
                                        e is IllegalArgumentException ||
                                            e is java.time.DateTimeException
                                    )
                                        e.message
                                    else "Could not record transfer. Your inputs are kept."
                                busy = false
                            }
                        }
                    }
                },
                Modifier.fillMaxWidth().testTag("transfer_save"),
                !busy && currency != null && destination.isNotBlank(),
                busy,
            ) {
                Text("Record transfer")
            }
        }
    }
}
