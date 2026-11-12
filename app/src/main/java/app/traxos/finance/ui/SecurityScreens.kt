package app.traxos.finance.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.traxos.finance.security.*
import javax.crypto.Cipher

internal val LocalAppInteractionAllowed = staticCompositionLocalOf { true }
internal val LocalLockController = staticCompositionLocalOf<LockViewModel?> { null }
internal val LocalDevicePrompt = staticCompositionLocalOf<(Cipher) -> Unit> { {} }

@Composable
internal fun SecurityGate(vm: LockViewModel, content: @Composable () -> Unit) {
    // Critical gate state is collected even while Activity is stopped, before its next frame.
    val ready by vm.ready.collectAsState()
    val locked by vm.locked.collectAsState()
    val shield by vm.shielded.collectAsState()
    val fatal by vm.fatal.collectAsState()
    var rendered by remember { mutableStateOf(false) }
    if (ready && !locked && !fatal) rendered = true
    val hidden = !ready || locked || shield || fatal
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(hidden) {
        if (hidden) {
            focus.clearFocus(true)
            keyboard?.hide()
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (rendered)
            CompositionLocalProvider(LocalAppInteractionAllowed provides !hidden) {
                Box(
                    Modifier.fillMaxSize()
                        .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)
                ) {
                    content()
                }
            }
        if (hidden)
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!ready || (shield && !locked && !fatal))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BrandMark()
                        Text("TRAXOS", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (!ready) "Checking security…" else "Private view",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                else LockScreen(vm)
            }
    }
}

@Composable
private fun LockScreen(vm: LockViewModel) {
    val context = LocalContext.current
    val prompt = LocalDevicePrompt.current
    val prefs by vm.preferences.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val fatal by vm.fatal.collectAsState()
    var pin by remember { mutableStateOf("") }
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    Column(
        Modifier.fillMaxWidth()
            .widthIn(max = 440.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("app_lock"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandMark()
        Text("TRAXOS", style = MaterialTheme.typography.labelLarge)
        Text(
            if (fatal) "Security needs attention" else "Unlock your space",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        if (fatal)
            TraxosButton(vm::reload, Modifier.fillMaxWidth(), !busy, tone = ButtonTone.SECONDARY) {
                Text("Retry security check")
            }
        else {
            TraxosInput(
                "Traxos PIN",
                pin,
                { pin = it.filter { c -> c in '0'..'9' }.take(12) },
                secret = true,
                number = true,
                enabled = !busy,
                id = "unlock_pin",
            )
            if (prefs.remainingMillis > 0)
                Text(
                    "Try again in ${(prefs.remainingMillis+999)/1000} seconds",
                    style = MaterialTheme.typography.bodySmall,
                )
            TraxosButton(
                {
                    val value = pin
                    pin = ""
                    vm.unlock(value)
                },
                Modifier.fillMaxWidth().testTag("unlock_submit"),
                !busy && pin.length >= 6 && prefs.remainingMillis == 0L,
                busy,
            ) {
                Text("Unlock")
            }
            if (prefs.deviceAuth)
                TraxosButton(
                    { vm.requestDevice(context, false, show = prompt) },
                    Modifier.fillMaxWidth(),
                    !busy,
                    tone = ButtonTone.SECONDARY,
                ) {
                    Text("Use fingerprint / face / device lock")
                }
            Text(
                "Forgot PIN? There is no reset that bypasses your lock. Enabled device authentication can unlock; changing or disabling the PIN still requires your current PIN. Do not clear app data.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TraxosTextButton({ (context as? Activity)?.moveTaskToBack(true) }) { Text("Back to phone") }
    }
}

@Composable
internal fun SecuritySettingsScreen(vm: LockViewModel) {
    val prefs by vm.preferences.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    val prompt = LocalDevicePrompt.current
    var mode by remember { mutableStateOf("") }
    var old by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    old = ""
                    pin = ""
                    confirm = ""
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun close() {
        if (!busy) {
            mode = ""
            old = ""
            pin = ""
            confirm = ""
            vm.error.value = null
        }
    }
    if (mode.isNotBlank())
        TraxosSheet(
            when (mode) {
                "PIN" -> if (prefs.enabled) "Change Traxos PIN" else "Set Traxos PIN"
                "DISABLE" -> "Disable app lock"
                else -> "Device authentication"
            },
            { close() },
            scrollable = true,
        ) {
            if (prefs.enabled)
                TraxosInput(
                    "Current PIN",
                    old,
                    { old = it.filter { c -> c in '0'..'9' }.take(12) },
                    secret = true,
                    number = true,
                    enabled = !busy,
                    id = "current_pin",
                )
            if (mode == "PIN") {
                TraxosInput(
                    "New PIN · 6–12 digits",
                    pin,
                    { pin = it.filter { c -> c in '0'..'9' }.take(12) },
                    secret = true,
                    number = true,
                    enabled = !busy,
                    id = "new_pin",
                )
                TraxosInput(
                    "Confirm PIN",
                    confirm,
                    { confirm = it.filter { c -> c in '0'..'9' }.take(12) },
                    secret = true,
                    number = true,
                    enabled = !busy,
                    id = "confirm_pin",
                )
            }
            Text(
                if (mode == "DISABLE")
                    "Anyone using this unlocked phone will be able to open Traxos. Your financial records and encrypted gift data will be kept."
                else
                    "PIN changes require the current PIN. Keep it safe: no portable recovery is available yet.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            TraxosButton(
                {
                    when (mode) {
                        "PIN" ->
                            vm.setPin(old, pin, confirm) {
                                mode = ""
                                old = ""
                                pin = ""
                                confirm = ""
                            }
                        "DISABLE" ->
                            vm.disable(old) {
                                mode = ""
                                old = ""
                            }
                        "DEVICE_OFF" ->
                            vm.disableDevice(old) {
                                mode = ""
                                old = ""
                            }
                        else ->
                            vm.requestDevice(context, true, old) { cipher ->
                                mode = ""
                                old = ""
                                prompt(cipher)
                            }
                    }
                },
                Modifier.fillMaxWidth(),
                !busy,
                busy,
                tone = if (mode == "DISABLE") ButtonTone.DESTRUCTIVE else ButtonTone.PRIMARY,
            ) {
                Text(if (mode == "DISABLE") "Verify PIN and disable" else "Confirm")
            }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Security & Privacy", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (prefs.enabled) "App lock is on" else "Protect access with an offline Traxos PIN",
            style = MaterialTheme.typography.bodyMedium,
        )
        TraxosButton(
            {
                mode = "PIN"
                vm.error.value = null
            },
            Modifier.fillMaxWidth(),
            !busy,
        ) {
            Text(if (prefs.enabled) "Change PIN" else "Set up PIN")
        }
        if (prefs.enabled) {
            TraxosButton(
                {
                    mode = if (prefs.deviceAuth) "DEVICE_OFF" else "DEVICE_ON"
                    vm.error.value = null
                },
                Modifier.fillMaxWidth(),
                !busy,
                tone = ButtonTone.SECONDARY,
            ) {
                Text(
                    if (prefs.deviceAuth) "Disable device authentication"
                    else "Enable fingerprint / face / device lock"
                )
            }
            Text(
                "Android manages eligible strong fingerprint/face authentication. Device credential fallback is offered on Android 11+. Without eligible enrollment, use your Traxos PIN.",
                style = MaterialTheme.typography.bodySmall,
            )
            TraxosSelector(
                "Lock after background",
                prefs.timeoutSeconds.toString(),
                listOf(
                    "0" to "Immediately",
                    "30" to "30 seconds",
                    "60" to "1 minute",
                    "300" to "5 minutes",
                ),
                !busy,
            ) {
                vm.timeout(it.toInt())
            }
            TraxosButton(
                {
                    mode = "DISABLE"
                    vm.error.value = null
                },
                Modifier.fillMaxWidth(),
                !busy,
                tone = ButtonTone.GHOST,
            ) {
                Text("Disable PIN lock")
            }
        }
        TraxosTextButton({ context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
            Text("Open phone security / enrollment")
        }
        if (error != null && mode.isBlank()) TraxosNotice(error!!, NoticeKind.ERROR)
        Text(
            "Recent-app previews and screenshots are protected. App lock gates access; it does not encrypt the existing cash ledger. Gift secrets and security state use separate Android Keystore keys. Losing keys or data has no recovery shortcut.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
