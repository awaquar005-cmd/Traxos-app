package app.traxos.finance.ui

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.traxos.finance.TraxosApplication
import app.traxos.finance.domain.ActivityScope
import app.traxos.finance.domain.Money
import app.traxos.finance.entry.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal sealed interface TrendsState {
    data object Loading : TrendsState

    data object Failed : TrendsState

    data class Ready(
        val filter: TrendFilters,
        val current: List<TrendPoint>,
        val previous: List<TrendPoint>,
        val categories: List<TrendCategory>,
    ) : TrendsState
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class TrendsViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    val app = application as TraxosApplication
    private val retry = MutableStateFlow(0)
    val filters = saved.getStateFlow("trends", TrendFilters().saved())
    val state =
        combine(filters, retry) { values, _ ->
                runCatching { TrendFilters.read(values) }.getOrDefault(TrendFilters())
            }
            .flatMapLatest { f ->
                val p = f.previous()
                fun points(start: Long, end: Long) =
                    app.database
                        .trends()
                        .points(
                            start,
                            end,
                            f.account,
                            f.category,
                            f.merchant,
                            f.currency,
                            f.minMinor,
                            f.maxMinor,
                            f.kind,
                            f.origin,
                            f.receipt,
                            f.split,
                        )
                combine(
                        points(f.start, f.end),
                        points(p.first, p.last),
                        app.database
                            .trends()
                            .categories(
                                f.start,
                                f.end,
                                f.account,
                                f.category,
                                f.merchant,
                                f.currency,
                                f.minMinor,
                                f.maxMinor,
                                f.kind,
                                f.origin,
                                f.receipt,
                                f.split,
                            ),
                    ) { current, previous, categories ->
                        TrendsState.Ready(f, current, previous, categories) as TrendsState
                    }
                    .onStart { emit(TrendsState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(TrendsState.Failed)
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrendsState.Loading)

    fun apply(f: TrendFilters) {
        saved["trends"] = f.validate().saved()
    }

    fun retry() {
        retry.value++
    }
}

@Composable
internal fun TrendsScreen(
    drill: (ActivityScope, String) -> Unit,
    vm: TrendsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val values by vm.filters.collectAsStateWithLifecycle()
    val f = runCatching { TrendFilters.read(values) }.getOrDefault(TrendFilters())
    var editing by rememberSaveable { mutableStateOf(false) }
    val accounts by vm.app.repository.dao.accounts().collectAsStateWithLifecycle(emptyList())
    val categories by vm.app.repository.dao.categories().collectAsStateWithLifecycle(emptyList())
    val merchants by vm.app.repository.dao.labels().collectAsStateWithLifecycle(emptyList())
    if (editing) {
        var from by rememberSaveable { mutableStateOf(f.from) }
        var to by rememberSaveable { mutableStateOf(f.through) }
        var account by rememberSaveable { mutableStateOf(f.account) }
        var category by rememberSaveable { mutableStateOf(f.category) }
        var merchant by rememberSaveable { mutableStateOf(f.merchant) }
        var currency by rememberSaveable { mutableStateOf(f.currency) }
        var minimum by rememberSaveable { mutableStateOf(f.minimum) }
        var maximum by rememberSaveable { mutableStateOf(f.maximum) }
        var kind by rememberSaveable { mutableStateOf(f.kind) }
        var origin by rememberSaveable { mutableStateOf(f.origin) }
        var receipt by rememberSaveable { mutableStateOf(f.receipt) }
        var split by rememberSaveable { mutableStateOf(f.split) }
        var comparison by rememberSaveable { mutableStateOf(f.comparison) }
        var error by remember { mutableStateOf<String?>(null) }
        TraxosSheet("Report filters", { editing = false }, true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton(
                    {
                        from = LocalDate.now().withDayOfMonth(1).toString()
                        to = LocalDate.now().toString()
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Month")
                }
                TraxosTextButton(
                    {
                        from = LocalDate.now().minusDays(6).toString()
                        to = LocalDate.now().toString()
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Week")
                }
                TraxosTextButton(
                    {
                        from = YearMonth.now().minusMonths(11).atDay(1).toString()
                        to = LocalDate.now().toString()
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Year")
                }
            }
            TraxosDateField("From", from, true) { from = it }
            TraxosDateField("Through · inclusive", to, true) { to = it }
            TraxosSelector(
                "Account",
                account,
                listOf("" to "All accounts") + accounts.map { it.account.id to it.account.name },
            ) {
                account = it
            }
            TraxosSelector(
                "Category",
                category,
                listOf("" to "All categories") + categories.map { it.id to it.name },
            ) {
                category = it
            }
            TraxosSelector(
                "Merchant",
                merchant,
                listOf("" to "All merchants") +
                    merchants.filter { it.kind == "MERCHANT" }.map { it.id to it.name },
            ) {
                merchant = it
            }
            TraxosSelector(
                "Currency",
                currency,
                listOf("" to "Currencies kept separate") + Money.currencies.map { it to it },
            ) {
                currency = it
            }
            TraxosInput(
                "Minimum · whole payment",
                minimum,
                { minimum = it },
                number = true,
                id = "trend_minimum",
            )
            TraxosInput(
                "Maximum · whole payment",
                maximum,
                { maximum = it },
                number = true,
                id = "trend_maximum",
            )
            TraxosSelector(
                "Type",
                kind,
                listOf(
                    "ALL" to "Income & spending",
                    "EXPENSE" to "Spending & refunds",
                    "INCOME" to "Income",
                ),
            ) {
                kind = it
            }
            TraxosSelector(
                "Source",
                origin,
                listOf(
                    "ALL" to "All personal allocations",
                    "LEDGER" to "Personal ledger allocations",
                    "GROUP" to "Group self shares",
                ),
            ) {
                origin = it
            }
            TraxosSelector(
                "Receipts",
                receipt,
                listOf(
                    "ALL" to "Any receipt state",
                    "HAS" to "Has receipts",
                    "NONE" to "No receipts",
                ),
            ) {
                receipt = it
            }
            TraxosSelector(
                "Categories",
                split,
                listOf(
                    "ALL" to "All allocations",
                    "SPLIT" to "Split transactions",
                    "SINGLE" to "Single-category entries",
                ),
            ) {
                split = it
            }
            TraxosSelector(
                "Compare",
                comparison,
                listOf(
                    "PERIOD" to "Equal preceding period",
                    "MONTH" to "Dates one month earlier",
                    "YEAR" to "Dates one year earlier",
                ),
            ) {
                comparison = it
            }
            Text(
                "Up to 366 days. Amount bounds use the original whole payment/group event and require one currency. Category totals use allocations. Unlinked group shares have no bank account/merchant.",
                style = MaterialTheme.typography.bodySmall,
            )
            error?.let { TraxosNotice(it, NoticeKind.ERROR) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosTextButton(
                    {
                        vm.apply(TrendFilters())
                        editing = false
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Reset")
                }
                TraxosButton(
                    {
                        try {
                            vm.apply(
                                TrendFilters(
                                    from,
                                    to,
                                    account,
                                    category,
                                    merchant,
                                    currency,
                                    minimum,
                                    maximum,
                                    kind,
                                    origin,
                                    receipt,
                                    split,
                                    comparison,
                                )
                            )
                            editing = false
                        } catch (e: Exception) {
                            error =
                                if (e is IllegalArgumentException) e.message
                                else "Choose valid dates and amounts"
                        }
                    },
                    Modifier.weight(1f).testTag("trends_apply"),
                ) {
                    Text("Apply")
                }
            }
        }
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("trends_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Trends & comparisons", "Filter") { editing = true }
        Text(
            "${displayDate(f.start)} – ${displayDate(f.end)}",
            style = MaterialTheme.typography.bodySmall,
        )
        val previous = f.previous()
        Text(
            "Compared with ${displayDate(previous.first)} – ${displayDate(previous.last)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Personal income/spending, not cash-movement totals. Transfers, card settlements and loan principal are excluded. Group self shares replace matched payments; currencies are never summed together.",
            style = MaterialTheme.typography.bodySmall,
        )
        when (val s = state) {
            TrendsState.Loading -> repeat(3) { RowSkeleton() }
            TrendsState.Failed -> {
                TraxosNotice(
                    "Could not load local reports. Your records are unchanged.",
                    NoticeKind.ERROR,
                )
                TraxosTextButton(vm::retry) { Text("Retry") }
            }
            is TrendsState.Ready -> {
                val currencies =
                    (s.current.map { it.currency } + s.previous.map { it.currency })
                        .distinct()
                        .sorted()
                if (currencies.isEmpty())
                    Text(
                        "No matching activity in either period. Change filters or record a transaction."
                    )
                currencies.forEach { unit ->
                    val current = s.current.filter { it.currency == unit }
                    val old = s.previous.filter { it.currency == unit }
                    val spent = current.sumOf { it.expenseMinor - it.refundMinor }
                    val income = current.sumOf { it.incomeMinor }
                    val previousSpent = old.sumOf { it.expenseMinor - it.refundMinor }
                    val previousIncome = old.sumOf { it.incomeMinor }
                    TraxosCard {
                        Text(unit, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Net spending", style = MaterialTheme.typography.labelSmall)
                                MoneyText(spent, unit)
                                Text(
                                    "Previous ${displayMoney(previousSpent,unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    TrendFilters.change(spent, previousSpent)?.let { "Change $it" }
                                        ?: "No positive previous baseline",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Income", style = MaterialTheme.typography.labelSmall)
                                MoneyText(income, unit)
                                Text(
                                    "Previous ${displayMoney(previousIncome,unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    TrendFilters.change(income, previousIncome)?.let {
                                        "Change $it"
                                    } ?: "No positive previous baseline",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(
                            "${current.sumOf{it.entries}} matching daily entries · ${f.end-f.start+1} days",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val monthly = f.end - f.start > 62
                        val buckets = TrendMath.buckets(current, f.start, f.end)
                        TrendBars(buckets, unit)
                        Text(
                            "Spending and income share the scale. Net refunds extend below zero. Empty dates are zero, not invented transactions.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        var details by rememberSaveable(unit) { mutableStateOf(false) }
                        TraxosTextButton({ details = !details }) {
                            Text(
                                if (details) "Hide values"
                                else if (monthly) "Monthly values" else "Daily values"
                            )
                        }
                        if (details)
                            buckets.forEach { bucket ->
                                Text(
                                    "${bucket.label} · Net ${displayMoney(bucket.spending,unit)} · Income ${displayMoney(bucket.income,unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        val top = s.categories.filter { it.currency == unit }.take(20)
                        Text(
                            "Top ${top.size} category allocations",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        top.forEach { row ->
                            TraxosTextButton(
                                {
                                    drill(
                                        ActivityScope(
                                            f.from,
                                            f.through,
                                            f.account,
                                            row.categoryId,
                                            unit,
                                            f.merchant,
                                            f.minimum,
                                            f.maximum,
                                            f.receipt,
                                            f.split,
                                            f.origin,
                                        ),
                                        if (row.incomeMinor != 0L) "INCOME" else "EXPENSES",
                                    )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    row.categoryName +
                                        if (row.incomeMinor != 0L) " · Income" else " · Spending",
                                    Modifier.weight(1f),
                                    maxLines = 2,
                                )
                                MoneyText(
                                    if (row.incomeMinor != 0L) row.incomeMinor
                                    else row.expenseMinor - row.refundMinor,
                                    unit,
                                )
                            }
                        }
                    }
                }
                Text(
                    "Activity drill-down shows whole transactions matching a category split. Unlinked group shares remain in group history rather than being fabricated as bank transactions.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrendBars(values: List<TrendBucket>, currency: String) {
    val high =
        values
            .maxOfOrNull { maxOf(kotlin.math.abs(it.spending), kotlin.math.abs(it.income)) }
            ?.coerceAtLeast(1) ?: 1
    val negativeValues = values.any { it.spending < 0 }
    val spending = MaterialTheme.colorScheme.primary
    val income = MaterialTheme.colorScheme.secondary
    val refund = MaterialTheme.colorScheme.error
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f)
    val description =
        values.joinToString("; ") {
            "${it.label}: spending ${Money.editable(it.spending,currency)}, income ${Money.editable(it.income,currency)} $currency"
        }
    Canvas(
        Modifier.fillMaxWidth().height(90.dp).semantics {
            contentDescription = "Spending and income trend. $description"
        }
    ) {
        val base = if (negativeValues) size.height * .5f else size.height * .95f
        val maxHeight = if (negativeValues) size.height * .45f else size.height * .9f
        drawLine(
            axis,
            androidx.compose.ui.geometry.Offset(0f, base),
            androidx.compose.ui.geometry.Offset(size.width, base),
            1f,
        )
        if (values.isNotEmpty()) {
            val width = size.width / values.size
            fun bar(index: Int, value: Long, offset: Float, color: Color) {
                val ratio =
                    BigDecimal.valueOf(kotlin.math.abs(value))
                        .divide(BigDecimal.valueOf(high), 6, java.math.RoundingMode.HALF_EVEN)
                        .toFloat()
                val height = maxHeight * ratio
                drawRect(
                    color,
                    androidx.compose.ui.geometry.Offset(
                        index * width + offset * width,
                        if (value < 0) base else base - height,
                    ),
                    androidx.compose.ui.geometry.Size(maxOf(.5f, width * .32f), height),
                )
            }
            values.forEachIndexed { index, row ->
                bar(index, row.spending, .05f, if (row.spending < 0) refund else spending)
                bar(index, row.income, .48f, income)
            }
        }
    }
}
