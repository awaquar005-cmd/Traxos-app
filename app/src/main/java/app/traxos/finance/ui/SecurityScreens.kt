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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val episode by vm.episode.collectAsState()
    val prefs by vm.preferences.collectAsState()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current
    val prompt = LocalDevicePrompt.current
    LaunchedEffect(
        ready,
        locked,
        shield,
        fatal,
        episode,
        prefs.deviceAuth,
        prefs.remainingMillis == 0L,
        lifecycleState,
    ) {
        if (
            lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                vm.claimAutomaticDevicePrompt()
        )
            vm.requestDevice(context, false, show = prompt)
    }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var wasLocked by remember { mutableStateOf(locked) }
    LaunchedEffect(locked) {
        if (wasLocked && !locked)
            haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
            )
        wasLocked = locked
    }
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
    val promptPending by vm.promptPending.collectAsState()
    val error by vm.error.collectAsState()
    val fatal by vm.fatal.collectAsState()
    var pin by remember { mutableStateOf("") }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    DisposableEffect(owner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) pin = ""
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(error) {
        if (error == "Incorrect PIN")
            haptic.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
            )
    }
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    var recovery by remember { mutableStateOf(false) }
    if (recovery)
        TraxosSheet("Recovery information", { recovery = false }, scrollable = true) {
            Text(
                "There is no reset that bypasses your lock. Enabled device authentication can unlock; changing or disabling the PIN still requires the current PIN. Do not clear app data.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    Column(
        Modifier.widthIn(max = 440.dp)
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("app_lock")
    ) {
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LockBrand()
                Text(
                    "TRAXOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (fatal) "Security needs attention" else "Welcome back",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Unlock your private ledger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
            if (!fatal) {
                TraxosInput(
                    "Traxos PIN",
                    pin,
                    { pin = it.filter { c -> c in '0'..'9' }.take(12) },
                    secret = true,
                    number = true,
                    enabled = !busy && !promptPending,
                    id = "unlock_pin",
                )
                if (
                    WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) ==
                        0
                )
                    PinPad(!busy && !promptPending && prefs.remainingMillis == 0L) { key ->
                        pin =
                            when (key) {
                                "Clear" -> ""
                                "⌫" -> pin.dropLast(1)
                                else -> (pin + key).take(12)
                            }
                    }
                if (prefs.remainingMillis > 0)
                    Text(
                        "Try again in ${(prefs.remainingMillis+999)/1000} seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                TraxosTextButton({ recovery = true }) { Text("Recovery information") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (fatal)
                    TraxosButton(
                        vm::reload,
                        Modifier.fillMaxWidth(),
                        !busy,
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Retry security check")
                    }
                else
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TraxosButton(
                            {
                                val value = pin
                                pin = ""
                                vm.unlock(value)
                            },
                            Modifier.weight(1f).testTag("unlock_submit"),
                            !busy &&
                                !promptPending &&
                                pin.length >= 6 &&
                                prefs.remainingMillis == 0L,
                            busy,
                        ) {
                            Text(if (busy) "Checking…" else "Unlock")
                        }
                        if (prefs.deviceAuth)
                            TraxosButton(
                                { vm.requestDevice(context, false, show = prompt) },
                                Modifier.weight(1f).testTag("unlock_device_retry"),
                                !busy && !promptPending,
                                tone = ButtonTone.SECONDARY,
                            ) {
                                Text(
                                    "Retry device",
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription =
                                                "Retry fingerprint, face or device authentication"
                                        },
                                )
                            }
                    }
            }
        }
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

/**
 * Short entrance only; no artificial loading hold and Android reduced-motion setting is honored.
 */
@Composable
private fun LockBrand() {
    var entered by remember {
        mutableStateOf(!android.animation.ValueAnimator.areAnimatorsEnabled())
    }
    val scale by
        androidx.compose.animation.core.animateFloatAsState(
            if (entered) 1f else .94f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "Traxos mark entrance",
        )
    LaunchedEffect(Unit) { entered = true }
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(app.traxos.finance.R.drawable.traxos_mark),
        "Traxos five-square mark",
        Modifier.size(42.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PinPad(enabled: Boolean, onChange: (String) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Clear", "0", "⌫"),
            )
            .forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        TraxosButton(
                            {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType
                                        .TextHandleMove
                                )
                                onChange(key)
                            },
                            Modifier.weight(1f).testTag("pin_key_$key"),
                            enabled,
                            tone = ButtonTone.SECONDARY,
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                key,
                                style =
                                    if (key.length == 1) MaterialTheme.typography.titleLarge
                                    else MaterialTheme.typography.labelMedium,
                                modifier =
                                    Modifier.semantics {
                                        contentDescription =
                                            if (key == "⌫") "Delete last digit" else key
                                    },
                            )
                        }
                    }
                }
            }
    }
}
