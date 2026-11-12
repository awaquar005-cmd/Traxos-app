package app.traxos.finance.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

/** A per-field explicit copy; never combines credentials or puts them in feedback. */
internal fun copyCardDetail(context: Context, value: String): Boolean {
    if (value.isBlank()) return false
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val label = "TRAXOS card ${UUID.randomUUID()}"
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras =
        PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    try {
        clipboard.setPrimaryClip(clip)
    } catch (e: SecurityException) {
        return false
    }
    Handler(Looper.getMainLooper())
        .postDelayed(
            {
                try {
                    if (clipboard.primaryClipDescription?.label?.toString() == label) {
                        if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                        else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (e: SecurityException) {
                    // Android may deny background clipboard access. Timed clearing is best-effort.
                }
            },
            60_000,
        )
    return true
}
