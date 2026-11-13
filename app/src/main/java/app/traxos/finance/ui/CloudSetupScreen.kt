package app.traxos.finance.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
internal fun CloudSetupScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sync & recovery", style = MaterialTheme.typography.titleLarge)
        TraxosNotice(
            "Not configured. Your current ledger works offline. No cloud account is linked and no backup or sync has run.",
            NoticeKind.INFO,
        )
        SettingsGroup("GOOGLE SIGN-IN & DRIVE") {
            Text(
                "Create a Google Cloud project, configure OAuth consent, enable Drive API and register an Android OAuth client. The repository guide lists the exact package and current signing fingerprints.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Package: app.traxos.finance", style = MaterialTheme.typography.labelLarge)
        }
        SettingsGroup("SUPABASE") {
            Text(
                "Create a Supabase project and keep its project URL and publishable key. The service-role key stays server-side. Auth redirects, row-level security, memberships and conflict-safe sync must be configured before this app can connect.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsGroup("SEPARATE RECOVERY LAYERS") {
            Text(
                "Google Drive snapshots, chosen-folder encrypted backups and backend sync are separate features. A local save is not a verified backup. Protected card-vault data must remain excluded from portable copies.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TraxosButton(
            {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/awaquar005-cmd/Traxos-app/blob/main/docs/CLOUD_SETUP.md"
                            .toUri(),
                    )
                )
            },
            Modifier.fillMaxWidth(),
        ) {
            Text("Guide")
        }
        Text(
            "This page is setup guidance, not a simulated login or a working cloud transport. Existing PIN, biometrics and local data are preserved.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
