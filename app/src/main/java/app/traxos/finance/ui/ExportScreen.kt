package app.traxos.finance.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.TraxosApplication
import app.traxos.finance.backup.*
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.KeystoreGiftCipher
import app.traxos.finance.regional.DisplayOptions
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal data class ExportRequest(
    val format: String,
    val scope: ExportScope,
    val secrets: Boolean,
    val display: DisplayOptions,
    val conversion: String,
)

internal class ExportViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as TraxosApplication
    val busy = MutableStateFlow(false)
    val selecting = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val failed = MutableStateFlow(false)
    val saved = MutableStateFlow<Uri?>(null)
    val savedMime = MutableStateFlow("application/octet-stream")
    var request: ExportRequest? = null
    private var job: Job? = null

    fun prepare(value: ExportRequest): Boolean {
        if (busy.value || selecting.value) return false
        require(value.format in listOf("PDF", "CSV", "JSON"))
        require(value.conversion.isBlank() || value.conversion in Money.currencies)
        value.scope.validate()
        request = value
        selecting.value = true
        saved.value = null
        return true
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    fun result(uri: Uri?) {
        selecting.value = false
        val next = request
        request = null
        if (uri == null) {
            status.value = "Cancelled. No export was written."
            return
        }
        if (next == null) {
            status.value =
                "The export was interrupted. Choose Export again; remove any empty file left by the provider."
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
            }
            return
        }
        if (busy.value) return
        busy.value = true
        failed.value = false
        status.value = "Preparing export…"
        job =
            // Enter try/finally even if Cancel arrives before the IO dispatcher starts.
            viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
                val directory = File(app.cacheDir, "readable-exports")
                val temp = File(directory, UUID.randomUUID().toString())
                try {
                    check(directory.isDirectory || directory.mkdirs()) {
                        "Could not create export staging folder"
                    }
                    val service = ReadableExports(app.database, KeystoreGiftCipher())
                    temp.outputStream().use { out ->
                        when (next.format) {
                            "PDF" -> service.pdf(out, next.scope, next.display, next.conversion)
                            "CSV" -> service.csv(out, next.scope)
                            "JSON" -> service.json(out, next.secrets)
                            else -> error("Unknown format")
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    requireNotNull(app.contentResolver.openOutputStream(uri, "w")).use { out ->
                        temp.inputStream().use { input ->
                            val buffer = ByteArray(32768)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                            }
                            out.flush()
                        }
                    }
                    savedMime.value =
                        when (next.format) {
                            "PDF" -> "application/pdf"
                            "CSV" -> "text/csv"
                            else -> "application/json"
                        }
                    saved.value = uri
                    status.value =
                        "Export saved. This file is not encrypted; share it only with people you choose."
                } catch (e: CancellationException) {
                    val removed =
                        runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                            .getOrDefault(false)
                    status.value =
                        if (removed) "Cancelled; incomplete file removed."
                        else "Cancelled. Remove any incomplete file from your chosen location."
                    throw e
                } catch (e: Exception) {
                    val removed =
                        runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                            .getOrDefault(false)
                    failed.value = true
                    status.value =
                        (if (e is IllegalArgumentException) e.message
                            else "Export failed. Check folder access and free space.")
                            .orEmpty() +
                            if (removed) " No financial records changed."
                            else
                                " Remove any incomplete destination file; your records are unchanged."
                } finally {
                    temp.delete()
                    busy.value = false
                }
            }
    }

    fun cancel() {
        job?.cancel()
    }
}

@Composable
internal fun ExportScreen(vm: ExportViewModel = viewModel()) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val message by vm.status.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val accounts by vm.app.database.ledger().accounts().collectAsStateWithLifecycle(emptyList())
    val display by vm.app.deviceOptions.state.collectAsStateWithLifecycle()
    var format by rememberSaveable { mutableStateOf("PDF") }
    var all by rememberSaveable { mutableStateOf(true) }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var account by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("") }
    var deleted by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(true) }
    var secrets by rememberSaveable { mutableStateOf(false) }
    var conversion by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pdf =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf"),
            vm::result,
        )
    val csv =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
            vm::result,
        )
    val json =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
            vm::result,
        )
    if (confirm)
        TraxosDialog(
            "Export unencrypted data?",
            if (format == "JSON" && secrets)
                "This JSON includes gift codes, PINs and saved gift images in readable portable encoding. The card vault, login and app PIN are excluded. Anyone with the file can read it. Use encrypted Backup for recovery."
            else
                "This file contains private financial history and is not encrypted. JSON preserves complete selected financial data; PDF/CSV are reports. Card-vault details and login credentials are never included. Use encrypted Backup for recovery.",
            "Export",
            {
                confirm = false
                try {
                    val scope =
                        if (format == "JSON") ExportScope()
                        else
                            ExportScope(
                                if (all) ExportScope().start
                                else LocalDate.parse(from).toEpochDay(),
                                if (all) ExportScope().end else LocalDate.parse(to).toEpochDay(),
                                account,
                                currency,
                                deleted,
                                notes,
                            )
                    if (vm.prepare(ExportRequest(format, scope, secrets, display, conversion))) {
                        val name =
                            "TRAXOS-${LocalDate.now()}-${UUID.randomUUID().toString().take(8)}.${format.lowercase()}"
                        when (format) {
                            "PDF" -> pdf.launch(name)
                            "CSV" -> csv.launch(name)
                            else -> json.launch(name)
                        }
                    }
                } catch (e: Exception) {
                    vm.selecting.value = false
                    error =
                        if (e is IllegalArgumentException) e.message
                        else "The Android file picker is unavailable. Try again."
                }
            },
            { confirm = false },
        )
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("export_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Export your records", style = MaterialTheme.typography.titleLarge)
        TraxosSelector(
            "Format",
            format,
            listOf(
                "PDF" to "PDF · readable activity report",
                "CSV" to "CSV · spreadsheet activity",
                "JSON" to "JSON · lossless typed financial data",
            ),
            !busy && !selecting,
        ) {
            format = it
        }
        if (format == "JSON") {
            Text(
                "Complete financial tables, IDs, revisions, deleted/archived history and manual rates. Integers are exact decimal strings with type tags. No scope filters are applied. JSON export is not an import/restore bypass.",
                style = MaterialTheme.typography.bodySmall,
            )
            SettingsToggle(
                "Include gift secrets",
                "Also export gift codes, PINs and saved images as unencrypted portable data",
                secrets,
                !busy && !selecting,
            ) {
                secrets = it
            }
            Text(
                "Without this option, financial metadata remains exact but secret gift payloads are explicitly omitted. Card vault, app PIN, online tokens, pending SMS, device preferences and local profile photo are always excluded.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            TraxosCheckbox(all, { all = it }, "All dates", enabled = !busy && !selecting)
            if (!all) {
                TraxosDateField("From", from, !busy && !selecting) { from = it }
                TraxosDateField("Through · inclusive", to, !busy && !selecting) { to = it }
            }
            TraxosSelector(
                "Account",
                account,
                listOf("" to "All accounts") +
                    accounts.map { it.account.id to "${it.account.name} · ${it.account.currency}" },
                !busy && !selecting,
            ) {
                account = it
            }
            TraxosSelector(
                "Currency",
                currency,
                listOf("" to "All currencies · separate amounts") +
                    Money.currencies.map { it to it },
                !busy && !selecting,
            ) {
                currency = it
            }
            SettingsToggle(
                "Include deleted entries",
                "Rows are explicitly marked deleted",
                deleted,
                !busy && !selecting,
            ) {
                deleted = it
            }
            SettingsToggle(
                "Include notes",
                "Notes can contain private information",
                notes,
                !busy && !selecting,
            ) {
                notes = it
            }
            if (format == "PDF")
                TraxosSelector(
                    "Conversion estimates",
                    conversion,
                    listOf("" to "Original currencies only") +
                        Money.currencies.map { it to "Estimate in $it" },
                    !busy && !selecting,
                ) {
                    conversion = it
                }
            Text(
                if (format == "PDF")
                    "Paginated native PDF. Up to5,000 filtered entries; choose CSV/JSON for larger histories. Estimates use dated manual rates and never replace original amounts."
                else
                    "UTF-8 CSV with stable IDs, paired-transfer IDs and formula-safe text. Dates and decimals use a stable machine-readable format.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let { TraxosNotice(it, NoticeKind.ERROR) }
        TraxosButton(
            {
                error = null
                confirm = true
            },
            Modifier.fillMaxWidth().testTag("export_start"),
            !busy && !selecting,
            busy,
        ) {
            Text(if (selecting) "Choosing…" else "Export")
        }
        if (busy) TraxosTextButton(vm::cancel) { Text("Cancel") }
        if (message.isNotBlank())
            TraxosNotice(
                message,
                if (failed) NoticeKind.ERROR else if (busy) NoticeKind.PROGRESS else NoticeKind.INFO,
            )
        saved?.let { uri ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mime by vm.savedMime.collectAsStateWithLifecycle()
                TraxosTextButton(
                    {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        } catch (e: Exception) {
                            error =
                                "No app could open this file. It is saved in your chosen location."
                        }
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Open")
                }
                TraxosTextButton(
                    {
                        try {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType(mime)
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    "Share Traxos export",
                                )
                            )
                        } catch (e: Exception) {
                            error =
                                "Sharing is unavailable. The saved file remains in your chosen location."
                        }
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Share")
                }
            }
        }
    }
}
