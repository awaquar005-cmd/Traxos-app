package app.traxos.finance.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.TraxosApplication
import app.traxos.finance.domain.Money
import app.traxos.finance.regional.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class RefinementViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as TraxosApplication
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val failed = MutableStateFlow(false)
    val stamp = MutableStateFlow(0L)

    fun run(message: String = "Saved", block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        status.value = ""
        failed.value = false
        viewModelScope.launch {
            try {
                block()
                stamp.value++
                status.value = message
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                failed.value = true
                status.value = e.message ?: "Check the values"
            } catch (e: Exception) {
                failed.value = true
                status.value = "Could not complete this action. Existing data is kept; retry."
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
private fun RefinementStatus(vm: RefinementViewModel) {
    val message by vm.status.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    if (message.isNotBlank())
        TraxosNotice(message, if (failed) NoticeKind.ERROR else NoticeKind.SUCCESS)
}

@Composable
internal fun RegionalSettingsScreen(main: MainViewModel, vm: RefinementViewModel = viewModel()) {
    val display by vm.app.deviceOptions.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val rates by vm.app.regional.dao.rates().collectAsStateWithLifecycle(null)
    var edit by rememberSaveable { mutableStateOf("") }
    val old = rates?.firstOrNull { it.id == edit }
    if (edit.isNotBlank() && (edit == "new" || old != null))
        key(edit) {
            var source by rememberSaveable { mutableStateOf(old?.source ?: "USD") }
            var target by rememberSaveable { mutableStateOf(old?.target ?: "INR") }
            var amount by rememberSaveable { mutableStateOf(old?.rate ?: "") }
            var date by rememberSaveable {
                mutableStateOf(
                    LocalDate.ofEpochDay(old?.day ?: LocalDate.now().toEpochDay()).toString()
                )
            }
            val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            var saving by remember { mutableStateOf(false) }
            TraxosSheet("Manual exchange rate", { if (!saving) edit = "" }, true) {
                Text(
                    "1 source unit = rate target units. No online lookup or automatic triangulation.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TraxosSelector("From", source, Money.currencies.map { it to it }, !saving) {
                    source = it
                }
                TraxosSelector("To", target, Money.currencies.map { it to it }, !saving) {
                    target = it
                }
                TraxosInput(
                    "Rate",
                    amount,
                    { amount = it },
                    enabled = !saving,
                    number = true,
                    id = "manual_rate",
                )
                TraxosDateField("Effective date", date, !saving) { date = it }
                error?.let { TraxosNotice(it, NoticeKind.ERROR) }
                TraxosButton(
                    {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                try {
                                    vm.app.regional.save(
                                        ManualRate(
                                            id,
                                            source,
                                            target,
                                            LocalDate.parse(date).toEpochDay(),
                                            amount,
                                        ),
                                        old?.revision ?: 0,
                                    )
                                    edit = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error =
                                        if (e is IllegalArgumentException) e.message
                                        else "Could not save. This pair/date may already exist."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    !saving,
                    saving,
                ) {
                    Text("Save")
                }
            }
        }
    var remove by remember { mutableStateOf<ManualRate?>(null) }
    remove?.let { row ->
        TraxosDialog(
            "Delete rate?",
            "Original financial records stay unchanged. Future conversion estimates will use another applicable rate or remain unconverted.",
            "Delete",
            {
                remove = null
                vm.run { vm.app.regional.delete(row) }
            },
            { remove = null },
            destructive = true,
            enabled = !busy,
        )
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("regional_settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Currency & region", style = MaterialTheme.typography.titleLarge)
        val core by main.preferences.collectAsStateWithLifecycle()
        val coreBusy by main.busy.collectAsStateWithLifecycle()
        core?.let { p ->
            TraxosSelector(
                "Default currency",
                p.defaultCurrency,
                Money.currencies.map { it to it },
                !coreBusy,
            ) {
                main.currency(it)
            }
        }
        Text(
            "Original amounts and accounting dates never change. Formatting languages affect numbers and dates; interface text remains English.",
            style = MaterialTheme.typography.bodySmall,
        )
        TraxosSelector(
            "Formatting language / region",
            display.locale,
            DisplayOptions.locales,
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(display.copy(locale = it)) }
        }
        TraxosSelector("Date format", display.date, DisplayOptions.dates.map { it to it }, !busy) {
            vm.run { vm.app.deviceOptions.save(display.copy(date = it)) }
        }
        var zoneSearch by rememberSaveable { mutableStateOf("") }
        TraxosInput("Find timezone", zoneSearch, { zoneSearch = it }, id = "zone_search")
        val zones =
            remember(zoneSearch, display.zone) {
                (listOf("SYSTEM", display.zone, "Asia/Kolkata", "UTC") +
                        ZoneId.getAvailableZoneIds()
                            .filter { zoneSearch.isNotBlank() && it.contains(zoneSearch, true) }
                            .sorted()
                            .take(100))
                    .distinct()
                    .map { it to if (it == "SYSTEM") "Device timezone" else it }
            }
        TraxosSelector("Display timezone", display.zone, zones, !busy) {
            vm.run { vm.app.deviceOptions.save(display.copy(zone = it)) }
        }
        TraxosSelector(
            "Clock",
            display.clock,
            listOf("24" to "24-hour", "12" to "12-hour"),
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(display.copy(clock = it)) }
        }
        SettingsToggle(
            "Currency codes",
            "Show INR/USD labels instead of ambiguous symbols",
            display.currencyCode,
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(display.copy(currencyCode = it)) }
        }
        Text(
            "Preview: ${display.date(LocalDate.now().toEpochDay())} · ${display.time(java.time.Instant.now())} · ${Money.format(123456,"INR",display.localeValue(),display.currencyCode)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Only displayed timestamps use this timezone. Historical transaction days and recurring schedules keep their original accounting dates.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsToggle(
            "Allow inverse rates",
            "Use 1/rate only when no direct rate exists; never chain rates",
            display.inverse,
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(display.copy(inverse = it)) }
        }
        TraxosSelector(
            "Conversion rounding",
            display.rounding,
            listOf("HALF_EVEN" to "Half to even", "HALF_UP" to "Half up"),
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(display.copy(rounding = it)) }
        }
        var input by rememberSaveable { mutableStateOf("") }
        var from by rememberSaveable { mutableStateOf("USD") }
        var to by rememberSaveable { mutableStateOf("INR") }
        var result by rememberSaveable { mutableStateOf("") }
        TraxosCard {
            Text("Conversion preview", style = MaterialTheme.typography.titleMedium)
            TraxosInput(
                "Amount",
                input,
                {
                    input = it
                    result = ""
                },
                number = true,
                id = "conversion_amount",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    TraxosSelector("Source", from, Money.currencies.map { it to it }) {
                        from = it
                        result = ""
                    }
                }
                Box(Modifier.weight(1f)) {
                    TraxosSelector("Target", to, Money.currencies.map { it to it }) {
                        to = it
                        result = ""
                    }
                }
            }
            TraxosButton(
                {
                    result =
                        try {
                            val minor = Money.parse(input, from, true)
                            if (from == to)
                                Money.format(minor, to, display.localeValue(), display.currencyCode)
                            else
                                ExchangeRules.convert(
                                        minor,
                                        from,
                                        to,
                                        LocalDate.now().toEpochDay(),
                                        rates.orEmpty(),
                                        display.inverse,
                                        display.rounding,
                                    )
                                    ?.let {
                                        "Estimate ${Money.format(it.minor,to,display.localeValue(),true)} · ${if(it.inverse)"inverse "else""}rate dated ${display.date(it.rate.day)}${if(LocalDate.now().toEpochDay()-it.rate.day>30)" · older than30 days"else""}"
                                    } ?: "No applicable rate. Add a manual rate."
                        } catch (e: IllegalArgumentException) {
                            e.message ?: "Check the amount"
                        }
                },
                enabled = rates != null,
            ) {
                Text("Convert")
            }
            if (result.isNotBlank()) Text(result)
        }
        SectionHeader("Manual rates", "Add") { edit = "new" }
        if (rates == null) RowSkeleton()
        else if (rates!!.isEmpty()) Text("No rates saved. Currencies stay separate.")
        var rateSearch by rememberSaveable { mutableStateOf("") }
        if (rates.orEmpty().isNotEmpty())
            TraxosInput(
                "Find rates · pair or ISO date",
                rateSearch,
                { rateSearch = it },
                id = "rate_search",
            )
        val matchingRates =
            rates.orEmpty().filter {
                rateSearch.isBlank() ||
                    "${it.source} ${it.target} ${LocalDate.ofEpochDay(it.day)} ${it.rate}"
                        .contains(rateSearch, true)
            }
        if (matchingRates.size > 100)
            Text(
                "Latest100 matches shown. Refine the pair or ISO date to find older rates; all rates remain stored.",
                style = MaterialTheme.typography.bodySmall,
            )
        matchingRates.take(100).forEach { r ->
            TraxosCard {
                Text(
                    "1 ${r.source} = ${r.rate} ${r.target}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(display.date(r.day), style = MaterialTheme.typography.bodySmall)
                Row {
                    TraxosTextButton({ edit = r.id }, Modifier.weight(1f), !busy) { Text("Edit") }
                    TraxosTextButton({ remove = r }, Modifier.weight(1f), !busy) { Text("Delete") }
                }
            }
        }
        RefinementStatus(vm)
    }
}

@Composable
internal fun ProfilePhotoControls(vm: RefinementViewModel = viewModel()) {
    val stamp by vm.app.profilePhoto.version.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, stamp) { value = vm.app.profilePhoto.bitmap() }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { vm.run { vm.app.profilePhoto.import(it) } }
        }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), "Local profile photo", Modifier.size(64.dp)) }
                ?: BrandMark()
        }
        TraxosTextButton(
            {
                vm.status.value = ""
                try {
                    picker.launch("image/*")
                } catch (e: Exception) {
                    vm.failed.value = true
                    vm.status.value =
                        "Android photo picker is unavailable. Your existing photo is kept."
                }
            },
            Modifier.weight(1f),
            !busy,
        ) {
            Text("Photo")
        }
        TraxosTextButton(
            { vm.run { vm.app.profilePhoto.remove() } },
            Modifier.weight(1f),
            !busy && bitmap != null,
        ) {
            Text("Remove")
        }
    }
    TraxosTextButton(
        { vm.run { vm.app.profilePhoto.rotate() } },
        enabled = !busy && bitmap != null,
    ) {
        Text("Rotate")
    }
    Text(
        "Stored on this phone only. Re-encoded without metadata; center-cropped. Not uploaded or included in exports/backups.",
        style = MaterialTheme.typography.bodySmall,
    )
    RefinementStatus(vm)
}

@Composable
internal fun AccessibilitySettingsScreen(vm: RefinementViewModel = viewModel()) {
    val p by vm.app.deviceOptions.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("accessibility_settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Accessibility & display", style = MaterialTheme.typography.titleLarge)
        TraxosSelector(
            "Additional text size",
            p.textScale,
            listOf("1.0" to "Follow Android", "1.1" to "+10%", "1.2" to "+20%", "1.3" to "+30%"),
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(p.copy(textScale = it)) }
        }
        Text(
            "Adds to Android font scaling; never reduces your system font size. Forms and lists retain accessible touch targets.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsToggle(
            "High-contrast text",
            "Stronger primary and secondary text throughout the app",
            p.contrast,
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(p.copy(contrast = it)) }
        }
        SettingsToggle(
            "Haptic feedback",
            "App navigation, PIN and long-press feedback",
            p.haptics,
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(p.copy(haptics = it)) }
        }
        SettingsToggle(
            "Reduce logo motion",
            "Show the lock mark without entrance movement",
            p.reduceMotion,
            !busy,
        ) {
            vm.run { vm.app.deviceOptions.save(p.copy(reduceMotion = it)) }
        }
        SettingsRow(
            "Android accessibility",
            "Screen reader, magnification and system font controls",
        ) {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                vm.status.value = "Android accessibility settings are unavailable"
                vm.failed.value = true
            }
        }
        Text("Preview heading", style = MaterialTheme.typography.titleLarge)
        Text(
            "Readable secondary text and exact amounts: ${displayMoney(123456,"INR")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RefinementStatus(vm)
    }
}

@Composable
internal fun LauncherSettingsScreen(vm: RefinementViewModel = viewModel()) {
    val p by vm.app.deviceOptions.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandMark()
        Text("App identity", style = MaterialTheme.typography.titleLarge)
        Text(
            "Keep the Traxos five-square mark. Change only the launcher name. Android launchers may take a moment to refresh or keep old pinned shortcuts."
        )
        TraxosSelector(
            "Launcher name",
            p.launcher,
            listOf("TRAXOS" to "TRAXOS", "FINANCE" to "Traxos Finance"),
            !busy,
        ) { name ->
            vm.run {
                LauncherNames.select(vm.app, name)
                vm.app.deviceOptions.save(p.copy(launcher = name))
            }
        }
        RefinementStatus(vm)
    }
}

@Composable
internal fun ProfilePhotoPreview() {
    val app = LocalContext.current.applicationContext as? TraxosApplication ?: return
    val stamp by app.profilePhoto.version.collectAsStateWithLifecycle()
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, stamp) { value = app.profilePhoto.bitmap() }
    bitmap?.let {
        Image(it.asImageBitmap(), "Local profile photo", Modifier.padding(end = 8.dp).size(40.dp))
    }
}
