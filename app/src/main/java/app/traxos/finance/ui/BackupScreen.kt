package app.traxos.finance.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.TraxosApplication
import app.traxos.finance.backup.*
import app.traxos.finance.gifts.KeystoreGiftCipher
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TraxosApplication
    private val store = app.getSharedPreferences("local_folder", 0)
    private val service = PortableBackup(app, app.database, KeystoreGiftCipher())
    val folder = MutableStateFlow(store.getString("tree", "").orEmpty())
    val status =
        MutableStateFlow(
            store
                .getString(
                    "verified",
                    "Choose your own folder. Backups run only when you request them.",
                )
                .orEmpty()
        )
    val busy = MutableStateFlow(false)
    val preview = MutableStateFlow<PortableBackup.Preview?>(null)
    private var job: Job? = null

    fun folder(uri: Uri, flags: Int) = run {
        try {
            val selected = FolderStorage(app.contentResolver).select(uri, flags)
            folder.value = selected.uri.toString()
            store.edit {
                putString("tree", folder.value)
                putString("folder_name", selected.name)
            }
            status.value =
                "${selected.name} selected and tested. Create a backup to save your records."
        } catch (e: SecurityException) {
            status.value =
                "Android did not grant lasting write access. Use the system Files picker and choose a writable subfolder."
        } catch (e: IllegalArgumentException) {
            status.value = e.message ?: "Choose a writable local subfolder."
        } catch (e: Exception) {
            status.value =
                "The folder could not create, read and remove a test file. Choose a writable local folder and retry."
        }
    }

    fun folderFailure(message: String) {
        status.value = message
    }

    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        job =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    status.value =
                        "Operation failed. Check the password, folder access and free space. Conflicting or incompatible backups leave your existing records unchanged."
                } finally {
                    busy.value = false
                }
            }
    }

    private fun create(extension: String, mime: String): Uri {
        require(folder.value.isNotBlank())
        val tree = folder.value.toUri()
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
        return requireNotNull(
            DocumentsContract.createDocument(
                app.contentResolver,
                parent,
                mime,
                "TRAXOS-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$extension",
            )
        )
    }

    fun backup(password: CharArray) {
        if (busy.value) {
            password.fill('\u0000')
            return
        }
        run {
            var uri: Uri? = null
            try {
                uri = create("traxos", "application/octet-stream")
                app.contentResolver.openOutputStream(uri, "w").use {
                    service.write(requireNotNull(it), password)
                }
                app.contentResolver
                    .openInputStream(uri)
                    .use { service.inspect(requireNotNull(it), password) }
                    .use {}
                val text =
                    "Snapshot verified ${java.time.LocalDateTime.now().withNano(0)}. Newer changes may not be included."
                store.edit { putString("verified", text) }
                status.value = text
            } catch (e: Exception) {
                uri?.let { partial ->
                    try {
                        DocumentsContract.deleteDocument(app.contentResolver, partial)
                    } catch (_: Exception) {
                        status.value =
                            "An incomplete file may remain in the selected folder. Existing backups were not changed."
                    }
                }
                throw e
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun inspect(uri: Uri, password: CharArray) {
        if (busy.value) {
            password.fill('\u0000')
            return
        }
        run {
            try {
                preview.value?.close()
                preview.value = null
                preview.value =
                    app.contentResolver.openInputStream(uri).use {
                        service.inspect(requireNotNull(it), password)
                    }
                status.value = "Password and archive verified. Review the counts before restoring."
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun cancelPreview() {
        if (!busy.value) {
            preview.value?.close()
            preview.value = null
        }
    }

    fun restore() = run {
        val pending = requireNotNull(preview.value)
        try {
            val count = service.restore(pending)
            app.giftScheduler.start()
            app.recurringScheduler.start()
            status.value = "Restored $count new records. Identical existing records were kept."
        } finally {
            pending.close()
            preview.value = null
        }
    }

    fun csv() = run {
        val uri = create("csv", "text/csv")
        try {
            LedgerCsv.write(
                app.database,
                requireNotNull(app.contentResolver.openOutputStream(uri, "w")),
            )
            status.value =
                "CSV exported to the selected folder. This readable report is not a backup and contains no card-vault or gift codes."
        } catch (e: Exception) {
            try {
                DocumentsContract.deleteDocument(app.contentResolver, uri)
            } catch (_: Exception) {
                /* Report failure; never claim a completed export. */
            }
            throw e
        }
    }

    @Volatile private var cleared = false

    override fun onCleared() {
        cleared = true
        if (!busy.value) {
            preview.value?.close()
            preview.value = null
        }
    }
}

@Composable
internal fun BackupScreen(vm: BackupViewModel = viewModel()) {
    val folder by vm.folder.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var csv by remember { mutableStateOf(false) }
    val tree =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val uri = data?.data
                if (uri != null) vm.folder(uri, data.flags)
                else
                    vm.folderFailure(
                        "The picker did not return a folder. Try the system Files app."
                    )
            }
        }
    val file =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected = it }
    if (csv)
        TraxosDialog(
            "Export readable report?",
            "CSV contains transaction names, amounts and notes in plaintext. Keep it private. Transfers and deleted records are labelled; currencies stay separate. It cannot restore the app.",
            "Export",
            {
                csv = false
                vm.csv()
            },
            { csv = false },
        )
    if (selected != null)
        TraxosSheet(
            "Open backup",
            {
                if (!busy) {
                    selected = null
                    password = ""
                }
            },
        ) {
            Text(
                "Enter its password. Existing financial records are never silently overwritten.",
                style = MaterialTheme.typography.bodySmall,
            )
            PasswordInput(password, { password = it }, !busy)
            TraxosButton(
                {
                    val key = password.toCharArray()
                    password = ""
                    vm.inspect(selected!!, key)
                    selected = null
                },
                Modifier.fillMaxWidth(),
                enabled = password.length in 12..128 && !busy,
            ) {
                Text("Review")
            }
        }
    preview?.let { pending ->
        TraxosSheet("Restore verified snapshot", { vm.cancelPreview() }, scrollable = true) {
            Text(
                "${pending.counts["accounts"] ?: 0} accounts · ${pending.counts["transactions"] ?: 0} transactions · ${pending.counts["gift_cards"] ?: 0} gift cards",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Missing records will be added. Identical records are skipped. Any same-ID disagreement cancels the whole restore. Card vault, app lock, pending SMS and device settings are excluded."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton(vm::cancelPreview, Modifier.weight(1f), !busy) { Text("Cancel") }
                TraxosButton(vm::restore, Modifier.weight(1f), !busy, busy = busy) {
                    Text("Restore")
                }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your folder. Your copy.", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your records stay in the app. This folder holds backup files and exports; it does not move live data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsGroup("LOCAL STORAGE") {
            Text(
                "Choose a subfolder inside Documents or Downloads. Android can disable selection of storage roots; create a TRAXOS folder there first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsRow(
                "Backup folder",
                if (folder.isBlank()) "Not selected" else Uri.decode(folder.substringAfterLast('/')),
            ) {
                if (!busy)
                    try {
                        tree.launch(
                            FolderStorage.picker(folder.takeIf { it.isNotBlank() }?.toUri())
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        vm.folderFailure(
                            "Android's folder picker is unavailable. Enable the system Files/Documents app, then retry."
                        )
                    } catch (_: SecurityException) {
                        vm.folderFailure(
                            "Android blocked folder access. Choose a different folder."
                        )
                    }
            }
            TraxosNotice(status, NoticeKind.INFO)
        }
        SettingsGroup("ENCRYPTED BACKUP") {
            Text(
                "Includes ledger, accounts, categories, merchants, recurring rules, Plans and gift cards with images. Excludes the bank/card vault, PIN, device preferences and unapproved Smart Add suggestions.",
                style = MaterialTheme.typography.bodySmall,
            )
            PasswordInput(password, { password = it }, !busy)
            PasswordInput(confirm, { confirm = it }, !busy, "Confirm password")
            if (confirm.isNotBlank() && password != confirm)
                Text(
                    "Passwords do not match",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            Text(
                "Use 12–128 characters. Keep this password safely: Traxos cannot recover it. Restore needs it, even on a new phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TraxosButton(
                {
                    val key = password.toCharArray()
                    password = ""
                    confirm = ""
                    vm.backup(key)
                },
                Modifier.fillMaxWidth(),
                enabled =
                    !busy &&
                        folder.isNotBlank() &&
                        password.length in 12..128 &&
                        password == confirm,
                busy = busy,
            ) {
                Text("Backup")
            }
            TraxosTextButton(
                {
                    password = ""
                    confirm = ""
                    file.launch(arrayOf("application/octet-stream", "*/*"))
                },
                Modifier.fillMaxWidth(),
                !busy,
            ) {
                Text("Restore")
            }
        }
        SettingsGroup("EXPORT") {
            SettingsRow(
                "Transaction CSV",
                "Readable report · no gift secrets or card-vault details",
            ) {
                if (!busy && folder.isNotBlank()) csv = true
                else vm.status.value = "Select a folder before exporting."
            }
        }
    }
}

@Composable
private fun PasswordInput(
    value: String,
    change: (String) -> Unit,
    enabled: Boolean,
    label: String = "Backup password",
) {
    // Reuse the existing secret field so passwords never enter saveable UI state.
    TraxosInput(label, value, { change(it.take(128)) }, enabled = enabled, secret = true)
}
