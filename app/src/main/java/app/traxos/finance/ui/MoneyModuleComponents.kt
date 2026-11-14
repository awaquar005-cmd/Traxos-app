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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun MoneyEditor(
    title: String,
    vm: MoneyToolsViewModel,
    close: () -> Unit,
    save: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    var consumed by rememberSaveable { mutableStateOf(completed) }
    var discard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(completed) {
        if (completed > consumed) {
            consumed = completed
            close()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val leave = { if (!busy) discard = true }
    BackHandler { leave() }
    if (discard)
        TraxosDialog(
            "Leave this form?",
            "Unsaved changes will be discarded.",
            "Leave",
            {
                discard = false
                close()
            },
            { discard = false },
            enabled = !busy,
        )
    TraxosFormPanel {
        Column(Modifier.fillMaxSize()) {
            TraxosToolbar(title, back = leave)
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TraxosTextButton(leave, Modifier.weight(1f), !busy) { Text("Cancel") }
                        TraxosButton(
                            {
                                keyboard?.hide()
                                focus.clearFocus()
                                save()
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
        }
    }
}
