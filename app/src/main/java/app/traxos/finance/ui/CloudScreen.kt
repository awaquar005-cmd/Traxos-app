package app.traxos.finance.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.R
import app.traxos.finance.cloud.DriveSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CloudScreen(openLocal: () -> Unit, vm: CloudViewModel = viewModel()) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by vm.account.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val failed by vm.error.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val verified by vm.verified.collectAsStateWithLifecycle()
    val resolution by vm.resolution.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val candidate by vm.candidate.collectAsStateWithLifecycle()
    val driveBinding by vm.driveBinding.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("SIGN_IN") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudConfirmation by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var backupConfirmation by remember { mutableStateOf("") }
    var keepLocal by rememberSaveable { mutableStateOf(true) }
    var createFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("TRAXOS Backups") }
    var confirmLink by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DriveSnapshot?>(null) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            result ->
            vm.completeAuthorization(context, result.data, result.resultCode == Activity.RESULT_OK)
        }
    LaunchedEffect(resolution) {
        resolution?.let {
            try {
                vm.consumeResolution()
                chooser.launch(it)
            } catch (_: Exception) {
                vm.launchFailed()
            }
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) vm.cancelGoogle()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.cancelGoogle()
        }
    }
    LaunchedEffect(state.binding?.email, driveBinding?.email) {
        (state.binding?.email ?: driveBinding?.email)?.let {
            email = it
            password = ""
            confirmation = ""
        }
    }
    LaunchedEffect(state.binding?.uid) { if (state.binding != null) mode = "SIGN_IN" }
    val isSignup = mode == "SIGN_UP" && state.binding == null
    val configPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                                    input ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(4096)
                                    while (true) {
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        require(output.size() + n <= 256_000)
                                        output.write(buffer, 0, n)
                                    }
                                    output.toString("UTF-8")
                                }
                            }
                        vm.importConfig(text)
                    } catch (_: Exception) {
                        vm.status.value =
                            "Could not read Firebase Android configuration. Choose google-services.json for app.traxos.finance."
                        vm.error.value = true
                    }
                }
        }
    candidate?.let { selected ->
        TraxosDialog(
            "Link Google Drive?",
            "This phone's existing local ledger will use ${selected.email} for backups. This Google account cannot be replaced by another account. Records stay local until you explicitly choose Backup.",
            "Link",
            vm::confirmDrive,
            vm::rejectDrive,
        )
    }
    if (confirmLink)
        TraxosDialog(
            "Link this phone?",
            "This attaches the existing local profile to ${state.profile?.email}. Financial records and the app PIN stay on this phone. Only this account can reconnect; no upload starts automatically.",
            "Link",
            {
                confirmLink = false
                vm.bind()
            },
            { confirmLink = false },
        )
    if (confirmOut)
        TraxosDialog(
            "Sign out?",
            "Your local records and PIN remain. The original account binding is retained. Sign in with that same account to resume Drive backups.",
            "Sign out",
            {
                confirmOut = false
                password = ""
                vm.signout(context)
            },
            { confirmOut = false },
        )
    if (confirmDiscard)
        TraxosDialog(
            "Discard pending snapshot?",
            "Only the unfinished local upload copy is removed. Your ledger and completed local/Drive backups remain.",
            "Discard",
            {
                confirmDiscard = false
                vm.discard()
            },
            { confirmDiscard = false },
            destructive = true,
        )
    if (createFolder)
        TraxosSheet("Drive folder", { if (!busy) createFolder = false }) {
            TraxosInput("Folder name", folderName, { folderName = it.take(80) }, enabled = !busy)
            TraxosButton(
                {
                    vm.createFolder(folderName)
                    createFolder = false
                },
                Modifier.fillMaxWidth(),
                !busy && folderName.isNotBlank(),
            ) {
                Text("Create")
            }
        }
    selected?.let { snapshot ->
        TraxosSheet(
            "Review Drive backup",
            {
                if (!busy) {
                    selected = null
                    backupPassword = ""
                }
            },
        ) {
            Text(
                snapshot.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Enter the archive password. Download and validation finish before any restore can start.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosInput(
                "Backup password",
                backupPassword,
                { backupPassword = it.take(128) },
                enabled = !busy,
                secret = true,
            )
            TraxosButton(
                {
                    val key = backupPassword.toCharArray()
                    backupPassword = ""
                    selected = null
                    vm.inspect(snapshot, key)
                },
                Modifier.fillMaxWidth(),
                !busy && backupPassword.length >= 12,
            ) {
                Text("Review")
            }
        }
    }
    preview?.let { staged ->
        TraxosSheet("Restore verified snapshot", vm::cancelPreview, scrollable = true) {
            Text(
                "${staged.counts["accounts"] ?: 0} accounts · ${staged.counts["transactions"] ?: 0} transactions · ${staged.counts["gift_cards"] ?: 0} gift cards"
            )
            Text(
                "Missing records are added. Identical records are skipped. Any conflicting edit cancels the whole restore. Card vault, PIN, account binding and device preferences are excluded.",
                style = MaterialTheme.typography.bodySmall,
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
        Text("Your account. Your backups.", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Keep using Traxos offline. Online sign-in is optional and does not replace the app PIN.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TraxosNotice(
            status,
            if (failed) NoticeKind.ERROR else if (busy) NoticeKind.INFO else NoticeKind.INFO,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (busy)
            Text(
                progress?.let { "Uploading · $it%" } ?: "Working…",
                style = MaterialTheme.typography.labelLarge,
            )
        if (!config.authReady || !config.googleReady)
            SettingsGroup("ACCOUNT SETUP") {
                Text(
                    "Use Firebase Authentication in your existing Google project. Enable Email/Password and Google, register app.traxos.finance, then download its public google-services.json. No Firestore database or private key is needed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!config.googleReady)
                    Text(
                        "Google sign-in needs the Web OAuth client ID included after Google provider setup. An Android OAuth client ID alone cannot sign in.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                TraxosButton(
                    { configPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    Modifier.fillMaxWidth(),
                    !busy,
                ) {
                    Text("Import")
                }
            }
        val profile = state.profile
        SettingsGroup("ACCOUNT") {
            if (state.cacheError)
                TraxosNotice(
                    "Saved cloud credentials could not be opened. Local records are intact. Sign in again; no ledger data is reset.",
                    NoticeKind.WARNING,
                )
            if (profile == null) {
                if (state.binding == null)
                    TraxosSegment(
                        listOf("SIGN_IN" to "Sign in", "SIGN_UP" to "Sign up"),
                        mode,
                        !busy,
                    ) {
                        mode = it
                        password = ""
                        confirmation = ""
                    }
                state.binding?.let {
                    Text("Linked to ${it.email}", style = MaterialTheme.typography.labelLarge)
                }
                TraxosInput(
                    "Email",
                    email,
                    { email = it.take(254) },
                    enabled = !busy && state.binding == null && driveBinding == null,
                )
                TraxosInput(
                    "Password",
                    password,
                    { password = it.take(128) },
                    enabled = !busy,
                    secret = true,
                )
                if (isSignup)
                    TraxosInput(
                        "Confirm password",
                        confirmation,
                        { confirmation = it.take(128) },
                        enabled = !busy,
                        secret = true,
                    )
                TraxosButton(
                    {
                        val secret = password
                        password = ""
                        confirmation = ""
                        if (isSignup) vm.signup(email, secret) else vm.signin(email, secret)
                    },
                    Modifier.fillMaxWidth(),
                    !busy &&
                        config.authReady &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        (!isSignup || password == confirmation && password.length >= 12),
                    busy = busy,
                ) {
                    Text(if (isSignup) "Sign up" else "Sign in")
                }
                TraxosTextButton(
                    { vm.reset(email) },
                    Modifier.fillMaxWidth(),
                    !busy && config.authReady && email.isNotBlank(),
                ) {
                    Text("Reset password")
                }
            } else {
                Text(
                    profile.email,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (profile.verified) "Email verified"
                    else "Verify your email before linking this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!profile.verified)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TraxosButton(vm::verifyEmail, Modifier.weight(1f), !busy) { Text("Verify") }
                        TraxosTextButton(vm::refresh, Modifier.weight(1f), !busy) {
                            Text("Refresh")
                        }
                    }
                if (state.binding == null)
                    TraxosButton(
                        { confirmLink = true },
                        Modifier.fillMaxWidth(),
                        !busy && profile.verified,
                    ) {
                        Text("Link")
                    }
                else
                    Text(
                        "This local profile stays bound to this account. A different Google account cannot replace it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (profile.googleSubject.isNotBlank())
                    Text(
                        "Google linked · ${profile.googleEmail}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                else
                    Text(
                        "Use the same verified email when linking Google. Sign in again first if your session is older than five minutes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                TraxosTextButton({ showPassword = !showPassword }, Modifier.fillMaxWidth(), !busy) {
                    Text(if (showPassword) "Less" else "Account password")
                }
                if (showPassword) {
                    Text(
                        "To add/change a password, authenticate again first: sign out and sign in with your current password, or continue with your linked Google account.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TraxosInput(
                        "New password",
                        cloudPassword,
                        { cloudPassword = it.take(128) },
                        enabled = !busy,
                        secret = true,
                    )
                    TraxosInput(
                        "Confirm new password",
                        cloudConfirmation,
                        { cloudConfirmation = it.take(128) },
                        enabled = !busy,
                        secret = true,
                    )
                    TraxosButton(
                        {
                            val secret = cloudPassword
                            cloudPassword = ""
                            cloudConfirmation = ""
                            vm.password(secret)
                        },
                        Modifier.fillMaxWidth(),
                        !busy && cloudPassword.length >= 12 && cloudPassword == cloudConfirmation,
                    ) {
                        Text("Save")
                    }
                }
            }
            GoogleAccountButton({ vm.google(context) }, !busy && config.googleReady)
            if (profile != null)
                TraxosTextButton({ confirmOut = true }, Modifier.fillMaxWidth(), !busy) {
                    Text("Sign out")
                }
        }
        SettingsGroup("LOCAL FOLDER") {
            SettingsRow("Backup folder", "Choose and test a writable local folder") {
                if (!busy) openLocal()
            }
            Text(
                "Local files and Drive snapshots are separate copies. Keep the backup password safely.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsGroup("GOOGLE DRIVE") {
            driveBinding?.let {
                Text(
                    "Linked Google account: ${it.email}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (!config.authReady)
                Text(
                    "Drive can connect using your existing Android OAuth registration. Firebase setup is only needed for email/password and account sign-in.",
                    style = MaterialTheme.typography.bodySmall,
                )
            Text(
                "Grant access only to app-created files. Nothing uploads until you choose Backup. Card vault, PIN and unapproved Smart Add are never included.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosButton({ vm.authorize(context) }, Modifier.fillMaxWidth(), !busy) {
                Text(if (connected) "Reconnect" else "Authorize")
            }
            if (connected) {
                if (folders.isNotEmpty())
                    TraxosSelector(
                        "Drive folder",
                        folder?.id.orEmpty(),
                        folders.map { it.id to it.name },
                        !busy && !pending,
                        onCreate = { createFolder = true },
                    ) {
                        vm.selectFolder(it)
                    }
                else
                    TraxosButton(
                        { createFolder = true },
                        Modifier.fillMaxWidth(),
                        !busy,
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Create folder")
                    }
                if (verified.isNotBlank())
                    Text(
                        "Last Drive verification: $verified. Newer changes may not be backed up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                TraxosInput(
                    "Archive password",
                    backupPassword,
                    { backupPassword = it.take(128) },
                    enabled = !busy,
                    secret = true,
                )
                if (!pending)
                    TraxosInput(
                        "Confirm archive password",
                        backupConfirmation,
                        { backupConfirmation = it.take(128) },
                        enabled = !busy,
                        secret = true,
                    )
                TraxosCheckbox(
                    keepLocal,
                    { keepLocal = it },
                    "Keep a local copy too",
                    enabled = !busy,
                )
                TraxosButton(
                    {
                        val key = backupPassword.toCharArray()
                        backupPassword = ""
                        backupConfirmation = ""
                        vm.backup(key, keepLocal, pending)
                    },
                    Modifier.fillMaxWidth(),
                    !busy &&
                        backupPassword.length in 12..128 &&
                        (pending || backupPassword == backupConfirmation && folder != null),
                    busy = busy,
                ) {
                    Text(if (pending) "Resume" else "Backup")
                }
                if (pending)
                    Text(
                        "This pending snapshot keeps its original Drive folder. Resume it or discard it before choosing another folder.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (pending)
                    TraxosTextButton({ confirmDiscard = true }, Modifier.fillMaxWidth(), !busy) {
                        Text("Discard")
                    }
                TraxosTextButton({ vm.list() }, Modifier.fillMaxWidth(), !busy && folder != null) {
                    Text("Refresh backups")
                }
                if (folder != null && snapshots.isEmpty() && !busy)
                    Text(
                        "No app-created backups in this folder yet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                snapshots.forEach { snapshot ->
                    SettingsRow(
                        snapshot.name,
                        "${snapshot.modified.take(10)} · ${(snapshot.size+1023)/1024} KiB · Review to restore",
                    ) {
                        if (!busy) {
                            backupPassword = ""
                            selected = snapshot
                        }
                    }
                }
                TraxosTextButton(vm::disconnect, Modifier.fillMaxWidth(), !busy) {
                    Text("Disconnect")
                }
                if (next.isNotBlank())
                    TraxosTextButton({ vm.list(true) }, Modifier.fillMaxWidth(), !busy) {
                        Text("More")
                    }
            }
        }
    }
}

@Composable
private fun GoogleAccountButton(click: () -> Unit, enabled: Boolean) {
    Surface(
        shape = Radius.control,
        color = Color.White,
        border = BorderStroke(.5.dp, Color(0xFF747775)),
    ) {
        TraxosButton(click, Modifier.fillMaxWidth(), enabled, tone = ButtonTone.GHOST) {
            Image(painterResource(R.drawable.google_g), null, Modifier.size(20.dp))
            Text(
                "Continue with Google",
                color = Color(0xFF1F1F1F),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
