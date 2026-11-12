package app.traxos.finance.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.traxos.finance.data.Preferences

@Composable
internal fun SmsSettings(prefs: Preferences, vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
        Text(
            "This device does not advertise SMS support. Share or paste messages into Smart Add instead.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    var disclosure by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                    granted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS,
                        ) == PackageManager.PERMISSION_GRANTED
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            attempted = true
            granted = allowed
            vm.smsOptIn(allowed)
        }
    if (disclosure)
        TraxosDialog(
            "Allow incoming financial SMS?",
            "With your Android permission, Traxos will inspect incoming financial messages locally and keep encrypted suggestions for review. Personal conversations, OTPs and links are ignored. No historical SMS is read, nothing is uploaded, and no wallet or transaction entry is created automatically.",
            "Continue",
            {
                disclosure = false
                permission.launch(Manifest.permission.RECEIVE_SMS)
            },
            { disclosure = false },
        )
    Text("Personal APK · Incoming messages only", style = MaterialTheme.typography.labelLarge)
    Text(
        "Android or your installer may block SMS access even after consent. Share/paste remains available. No historical inbox access.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (attempted && !granted) {
        TraxosNotice(
            "SMS access was not granted. Traxos cannot override Android or installer restrictions. Share or paste messages into Smart Add instead.",
            NoticeKind.WARNING,
        )
        TraxosTextButton({
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }) {
            Text("Open app permission settings")
        }
    }
    SettingsToggle(
        "Incoming financial SMS",
        "Review encrypted suggestions; access stops if permission is revoked",
        prefs.smsOptIn && granted,
        !busy,
    ) {
        if (it) disclosure = true else vm.smsOptIn(false)
    }
}
