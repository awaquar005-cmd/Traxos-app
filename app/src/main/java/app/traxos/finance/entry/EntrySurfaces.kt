package app.traxos.finance.entry

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.work.*
import app.traxos.finance.MainActivity
import app.traxos.finance.R
import app.traxos.finance.TraxosApplication
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.*

/** Only approved local preset IDs/action names cross an Android entry surface. Never amounts. */
data class EntryRequest(val action: String, val presetId: String? = null)

object EntryInbox {
    val pending = MutableStateFlow<EntryRequest?>(null)
    const val PREFIX = "app.traxos.finance.entry."

    fun receive(intent: Intent?): Boolean {
        val action =
            intent?.action?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return false
        if (action !in listOf("EXPENSE", "INCOME", "VOICE", "SMART", "HOME", "PRESET")) return false
        val id =
            if (action == "PRESET")
                intent.getStringExtra("preset")?.takeIf {
                    runCatching { UUID.fromString(it) }.isSuccess
                } ?: return false
            else null
        pending.value = EntryRequest(action, id)
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra("preset")
        return true
    }

    fun intent(context: Context, selection: String): Intent {
        val preset = selection.takeIf { it.startsWith("PRESET:") }?.substringAfter(':')
        val action = if (preset != null) "PRESET" else selection
        require(action in listOf("EXPENSE", "INCOME", "VOICE", "SMART", "HOME", "PRESET"))
        preset?.let { UUID.fromString(it) }
        return Intent(context, MainActivity::class.java)
            .setAction(PREFIX + action)
            .setData(Uri.parse("traxos-entry://$action/${preset.orEmpty()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { preset?.let { putExtra("preset", it) } }
    }

    fun label(selection: String) =
        when (selection) {
            "EXPENSE" -> "Expense"
            "INCOME" -> "Income"
            "VOICE" -> "Voice"
            "SMART" -> "Review"
            "HOME" -> "Home"
            else -> "Favorite"
        }

    fun pending(context: Context, selection: String) =
        PendingIntent.getActivity(
            context,
            selection.hashCode(),
            intent(context, selection),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class EntryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        EntrySurfaceWork.kick(context)
    }

    override fun onEnabled(context: Context) {
        EntrySurfaceWork.kick(context)
    }
}

object EntrySurfaceWork {
    fun kick(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "entry-surfaces",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<EntrySurfaceWorker>().build(),
            )
    }
}

class EntrySurfaceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as TraxosApplication
            val dao = app.database.extras()
            val settings = dao.settings().first() ?: FastEntrySettings()
            val presets = dao.presets().first()
            val available =
                presets
                    .filter { !it.archived && it.favorite }
                    .filter {
                        try {
                            app.transactionExtras.validatePreset(it)
                            true
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: IllegalArgumentException) {
                            false
                        }
                    }
            val manager = AppWidgetManager.getInstance(app)
            val views = RemoteViews(app.packageName, R.layout.traxos_entry_widget)
            listOf(settings.widget1, settings.widget2, settings.widget3).forEachIndexed {
                index,
                selection ->
                val viewId =
                    listOf(R.id.widget_entry1, R.id.widget_entry2, R.id.widget_entry3)[index]
                val p =
                    selection
                        .takeIf { it.startsWith("PRESET:") }
                        ?.substringAfter(':')
                        ?.let { id -> available.firstOrNull { it.id == id } }
                val label =
                    if (p != null && settings.publicLabels) p.name.take(18)
                    else EntryInbox.label(selection)
                views.setTextViewText(viewId, label)
                views.setOnClickPendingIntent(viewId, EntryInbox.pending(app, selection))
            }
            views.setOnClickPendingIntent(R.id.widget_mark, EntryInbox.pending(app, "HOME"))
            manager.updateAppWidget(ComponentName(app, EntryWidget::class.java), views)
            val shortcuts = app.getSystemService(ShortcutManager::class.java)
            val official = ComponentName(app, app.packageName + ".OfficialLauncher")
            val finance = ComponentName(app, app.packageName + ".FinanceLauncher")
            val activity =
                if (
                    app.packageManager.getComponentEnabledSetting(finance) ==
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                )
                    finance
                else official
            val oldPinned = shortcuts.pinnedShortcuts.filter { it.id.startsWith("traxos-preset-") }
            val relabel =
                oldPinned.mapNotNull { old ->
                    val id = old.id.removePrefix("traxos-preset-")
                    val row = available.firstOrNull { it.id == id }
                    val label =
                        if (settings.publicLabels && row != null) row.name.take(18) else "Favorite"
                    if (old.shortLabel.toString() == label && old.activity == activity) null
                    else
                        ShortcutInfo.Builder(app, old.id)
                            .setShortLabel(label)
                            .setActivity(activity)
                            .setIcon(Icon.createWithResource(app, R.drawable.traxos_mark))
                            .setIntent(EntryInbox.intent(app, "PRESET:$id"))
                            .build()
                }
            if (relabel.isNotEmpty())
                check(shortcuts.updateShortcuts(relabel)) { "Shortcut label refresh rate-limited" }
            oldPinned.forEach { old ->
                val id = old.id.removePrefix("traxos-preset-")
                if (available.none { it.id == id })
                    shortcuts.disableShortcuts(
                        listOf(old.id),
                        "Favorite unavailable. Review saved favorites in Traxos.",
                    )
                else shortcuts.enableShortcuts(listOf(old.id))
            }
            val desired =
                if (settings.shortcuts)
                    listOf("EXPENSE", "INCOME", "VOICE", "SMART").map { selection ->
                        ShortcutInfo.Builder(app, "traxos-$selection")
                            .setShortLabel(EntryInbox.label(selection))
                            .setLongLabel("Traxos ${EntryInbox.label(selection)}")
                            .setIcon(Icon.createWithResource(app, R.drawable.traxos_mark))
                            .setActivity(activity)
                            .setIntent(EntryInbox.intent(app, selection))
                            .build()
                    } +
                        available.take(maxOf(0, shortcuts.maxShortcutCountPerActivity - 4)).map { p
                            ->
                            ShortcutInfo.Builder(app, "traxos-preset-${p.id}")
                                .setShortLabel(
                                    if (settings.publicLabels) p.name.take(18) else "Favorite"
                                )
                                .setActivity(activity)
                                .setIcon(Icon.createWithResource(app, R.drawable.traxos_mark))
                                .setIntent(EntryInbox.intent(app, "PRESET:${p.id}"))
                                .build()
                        }
                else emptyList()
            val limited = desired.take(shortcuts.maxShortcutCountPerActivity)
            fun signature(rows: List<ShortcutInfo>) =
                rows
                    .map {
                        listOf(
                            it.id,
                            it.shortLabel.toString(),
                            it.activity?.flattenToString(),
                            it.intent?.action,
                            it.intent?.getStringExtra("preset"),
                        )
                    }
                    .sortedBy { it[0] }
            if (signature(shortcuts.dynamicShortcuts) != signature(limited))
                check(shortcuts.setDynamicShortcuts(limited)) { "Shortcut update rate-limited" }
            app.getSharedPreferences("entry_tile", 0)
                .edit()
                .putBoolean("enabled", settings.tile)
                .apply()
            TileService.requestListeningState(app, ComponentName(app, EntryTile::class.java))
            // Old decrypted viewer copies are temporary; no financial records are removed.
            File(app.cacheDir, "receipt-view")
                .listFiles()
                .orEmpty()
                .filter { System.currentTimeMillis() - it.lastModified() > 15 * 60_000 }
                .forEach { file ->
                    runCatching {
                        val uri =
                            androidx.core.content.FileProvider.getUriForFile(
                                app,
                                app.packageName + ".capture",
                                file,
                            )
                        app.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    file.delete()
                }
            app.getSharedPreferences("entry_surface_health", 0)
                .edit()
                .putBoolean("failed", false)
                .apply()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            applicationContext
                .getSharedPreferences("entry_surface_health", 0)
                .edit()
                .putBoolean("failed", true)
                .apply()
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

class EntryTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val enabled = getSharedPreferences("entry_tile", 0).getBoolean("enabled", true)
        qsTile?.apply {
            label = "Traxos Add"
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (!getSharedPreferences("entry_tile", 0).getBoolean("enabled", true)) return
        val launch = {
            val next = EntryInbox.intent(this, "EXPENSE")
            if (Build.VERSION.SDK_INT >= 34)
                startActivityAndCollapse(EntryInbox.pending(this, "EXPENSE"))
            else launchLegacy(next)
        }
        if (isLocked) unlockAndRun { launch() } else launch()
    }

    // Android26–33 has no PendingIntent overload. Never call the legacy API on34+.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchLegacy(intent: Intent) {
        check(Build.VERSION.SDK_INT < 34)
        startActivityAndCollapse(intent)
    }
}

object EntryPinning {
    fun preset(context: Context, row: EntryPreset, publicLabel: Boolean): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java)
        require(manager.isRequestPinShortcutSupported) {
            "This launcher does not support pinned shortcuts"
        }
        val finance = ComponentName(context, context.packageName + ".FinanceLauncher")
        val activity =
            if (
                context.packageManager.getComponentEnabledSetting(finance) ==
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            )
                finance
            else ComponentName(context, context.packageName + ".OfficialLauncher")
        val info =
            ShortcutInfo.Builder(context, "traxos-preset-${row.id}")
                .setActivity(activity)
                .setShortLabel(if (publicLabel) row.name.take(18) else "Favorite")
                .setIcon(Icon.createWithResource(context, R.drawable.traxos_mark))
                .setIntent(EntryInbox.intent(context, "PRESET:${row.id}"))
                .build()
        return manager.requestPinShortcut(info, null)
    }
}
