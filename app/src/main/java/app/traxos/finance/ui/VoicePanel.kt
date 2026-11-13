package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.voice.*

@Composable
internal fun VoicePanel(
    vm: VoiceViewModel,
    currency: String,
    record: () -> Unit,
    dismiss: () -> Unit,
    use: () -> Unit,
) {
    TraxosSheet("Voice entry", dismiss, scrollable = true) {
        VoiceFields(vm, currency, record, use)
    }
}

@Composable
internal fun VoiceEntryScreen(
    vm: VoiceViewModel,
    currency: String,
    record: () -> Unit,
    use: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VoiceFields(vm, currency, record, use)
    }
}

@Composable
private fun ColumnScope.VoiceFields(
    vm: VoiceViewModel,
    currency: String,
    record: () -> Unit,
    use: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    TraxosNotice(
        "On-device recognition only. Hold Plus and release to finish, or tap Record. Review the draft; nothing is saved automatically.",
        NoticeKind.INFO,
    )
    if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
    TraxosInput(
        "Transaction phrase",
        state.text,
        {
            vm.text(it)
            error = null
        },
        multiline = true,
        enabled = state.phase !in setOf("LISTENING", "PROCESSING"),
        id = "voice_phrase",
        placeholder = "Lunch 150 rupaye UPI se",
    )
    val parsed = runCatching { VoiceParser.parse(state.text, currency) }.getOrNull()
    if (parsed != null)
        Text(
            "${parsed.type.lowercase()} · ${parsed.currency} ${parsed.amount} · review account/category next",
            style = MaterialTheme.typography.bodyMedium,
        )
    if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TraxosButton(
            { if (state.phase == "LISTENING") vm.stop() else record() },
            Modifier.weight(1f),
            state.phase != "PROCESSING",
            tone = ButtonTone.SECONDARY,
        ) {
            Icon(TraxosIcons.mic, null, Modifier.size(18.dp))
            Text(if (state.phase == "LISTENING") "Stop" else "Record")
        }
        TraxosButton(
            {
                try {
                    vm.approve(currency)
                    use()
                } catch (e: IllegalArgumentException) {
                    error = e.message
                }
            },
            Modifier.weight(1f),
            state.text.isNotBlank() && state.phase !in setOf("LISTENING", "PROCESSING"),
        ) {
            Text("Use")
        }
    }
    Text(
        "Examples: Petrol 500 cash · Received salary 35000. If words or amounts are uncertain, edit them. Transfer, repayments and refunds use their existing matching flows.",
        style = MaterialTheme.typography.bodySmall,
    )
}
