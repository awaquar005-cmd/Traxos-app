package app.traxos.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun accountTypeLabel(type: String) =
    when (type) {
        "CASH" -> "Cash"
        "BANK" -> "Bank"
        "WALLET" -> "Wallet"
        "CREDIT_CARD" -> "Credit card"
        else -> type
    }

internal fun catalogIcon(name: String): ImageVector =
    when (name) {
        "food" -> TraxosIcons.food
        "travel" -> TraxosIcons.travel
        "home" -> TraxosIcons.home
        "work" -> TraxosIcons.work
        "gift" -> TraxosIcons.gift
        "health" -> TraxosIcons.health
        "wallet" -> TraxosIcons.wallet
        else -> TraxosIcons.tag
    }

@Composable
internal fun catalogColor(name: String): Color {
    val dark = MaterialTheme.colorScheme.background == Color(0xFF0C1423)
    return when (name) {
        "GREEN" -> if (dark) Color(0xFF8DDBC0) else Color(0xFF17634C)
        "VIOLET" -> if (dark) Color(0xFFD4BBFF) else Color(0xFF63409B)
        "AMBER" -> if (dark) Color(0xFFF2CB83) else Color(0xFF805211)
        "ROSE" -> if (dark) Color(0xFFFFB8CA) else Color(0xFF9E3151)
        else -> MaterialTheme.colorScheme.primary
    }
}

/** Reuses the existing picker; inline editor never navigates away from its parent form. */
@Composable
internal fun AccountPicker(
    repository: LedgerRepository,
    selected: String,
    accounts: List<AccountBalance>,
    enabled: Boolean = true,
    label: String = "Account",
    currency: String? = null,
    allowCard: Boolean = true,
    flat: Boolean = false,
    onSelect: (String) -> Unit,
) {
    var create by rememberSaveable { mutableStateOf(false) }
    val choices =
        accounts.filter {
            (!it.account.archived || it.account.id == selected) &&
                (currency == null || it.account.currency == currency) &&
                (allowCard || it.account.type != "CREDIT_CARD")
        }
    TraxosSelector(
        label,
        selected,
        choices.map {
            it.account.id to
                "${it.account.name} · ${it.account.currency}${if(it.account.archived) " · Archived" else ""}"
        },
        enabled,
        onCreate = { create = true },
        flat = flat,
    ) {
        onSelect(it)
    }
    if (create)
        CatalogEditor(
            repository,
            "ACCOUNT",
            currency = currency,
            allowCard = allowCard,
            onDismiss = { create = false },
        ) {
            onSelect(it)
            create = false
        }
}

@Composable
internal fun CategoryPicker(
    repository: LedgerRepository,
    selected: String,
    categories: List<Category>,
    type: String,
    enabled: Boolean = true,
    label: String = "Category",
    flat: Boolean = false,
    onSelect: (String) -> Unit,
) {
    var create by rememberSaveable { mutableStateOf(false) }
    val categoryType = if (type == "REFUND") "EXPENSE" else type
    val choices =
        categories.filter { it.type == categoryType && (!it.archived || it.id == selected) }
    TraxosSelector(
        label,
        selected,
        choices.map { it.id to it.name },
        enabled,
        onCreate = { create = true },
        flat = flat,
    ) {
        onSelect(it)
    }
    if (create)
        CatalogEditor(repository, "CATEGORY", type = categoryType, onDismiss = { create = false }) {
            onSelect(it)
            create = false
        }
}

@Composable
internal fun SavedLabelPicker(
    repository: LedgerRepository,
    kind: String,
    onSelect: (EntryLabel) -> Unit,
) {
    val labels by repository.dao.labels().collectAsStateWithLifecycle(emptyList())
    var create by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    val title = if (kind == "MERCHANT") "Saved merchants" else "Saved descriptions"
    TraxosSelector(
        title,
        "",
        labels.filter { it.kind == kind && !it.archived }.map { it.id to it.name },
        onCreate = { create = true },
    ) { id ->
        labels.firstOrNull { it.id == id }?.let(onSelect)
    }
    if (create)
        CatalogEditor(repository, kind, onDismiss = { create = false }) { id ->
            // Saved callback has the durable value even before Room's next observation emission.
            create = false
            // Read through repository-backed observation below rather than selecting a stale list.
            pending = id
        }
    LaunchedEffect(pending, labels) {
        labels
            .firstOrNull { it.id == pending }
            ?.let {
                onSelect(it)
                pending = null
            }
    }
}

@Composable
internal fun CatalogEditor(
    repository: LedgerRepository,
    kind: String,
    type: String = "EXPENSE",
    category: Category? = null,
    account: Account? = null,
    label: EntryLabel? = null,
    currency: String? = null,
    allowCard: Boolean = true,
    onDismiss: () -> Unit,
    saved: (String) -> Unit,
) {
    val id = rememberSaveable {
        category?.id ?: account?.id ?: label?.id ?: UUID.randomUUID().toString()
    }
    val op = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable {
        mutableStateOf(category?.name ?: account?.name ?: label?.name ?: "")
    }
    var icon by rememberSaveable { mutableStateOf(category?.icon ?: account?.icon ?: "tag") }
    var color by rememberSaveable { mutableStateOf(category?.color ?: account?.color ?: "BLUE") }
    var archived by rememberSaveable {
        mutableStateOf(category?.archived ?: account?.archived ?: label?.archived ?: false)
    }
    var accountType by rememberSaveable { mutableStateOf(account?.type ?: "CASH") }
    var unit by rememberSaveable { mutableStateOf(currency ?: account?.currency ?: "INR") }
    val preferences by repository.dao.preferences().collectAsStateWithLifecycle(null)
    var unitInitialized by rememberSaveable { mutableStateOf(currency != null || account != null) }
    LaunchedEffect(preferences?.defaultCurrency) {
        if (!unitInitialized && preferences != null) {
            unit = preferences!!.defaultCurrency
            unitInitialized = true
        }
    }
    var opening by rememberSaveable { mutableStateOf("0") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var details by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val edit = category != null || account != null || label != null
    val title =
        when (kind) {
            "CATEGORY" -> "category"
            "ACCOUNT" -> "account"
            "MERCHANT" -> "merchant"
            else -> "description"
        }
    TraxosSheet(
        "${if(edit) "Edit" else "New"} $title",
        { if (!busy) onDismiss() },
        scrollable = true,
    ) {
        TraxosInput(
            "Name",
            name,
            { name = it.take(if (kind == "CATEGORY" || kind == "ACCOUNT") 80 else 120) },
            enabled = !busy,
            id = "catalog_name",
        )
        if (kind == "ACCOUNT" && !edit) {
            TraxosSelector(
                "Account type",
                accountType,
                LedgerRepository.accountTypes
                    .filter { allowCard || it != "CREDIT_CARD" }
                    .map { it to accountTypeLabel(it) },
                !busy,
            ) {
                accountType = it
            }
            TraxosInput(
                if (accountType == "CREDIT_CARD") "Opening amount owed"
                else "Known opening balance",
                opening,
                { opening = it.take(24) },
                enabled = !busy,
                number = true,
                id = "catalog_opening",
            )
            if (accountType == "CREDIT_CARD")
                Text(
                    "Enter debt, never a credit limit. Purchases add debt; use Pay card for repayments.",
                    style = MaterialTheme.typography.bodySmall,
                )
        }
        TraxosTextButton({ details = !details }, enabled = !busy) {
            Text(if (details) "Less details" else "More details")
        }
        if (details) {
            if (kind == "CATEGORY" || kind == "ACCOUNT") {
                TraxosSelector(
                    "Icon",
                    icon,
                    LedgerRepository.icons.map { it to it.replaceFirstChar(Char::uppercase) },
                    !busy,
                ) {
                    icon = it
                }
                TraxosSelector(
                    "Color",
                    color,
                    LedgerRepository.colors.map {
                        it to it.lowercase().replaceFirstChar(Char::uppercase)
                    },
                    !busy,
                ) {
                    color = it
                }
                Icon(catalogIcon(icon), "Selected icon", tint = catalogColor(color))
            }
            if (kind == "ACCOUNT" && !edit) {
                TraxosSelector(
                    "Currency",
                    unit,
                    Money.currencies.map { it to it },
                    !busy && currency == null,
                ) {
                    unit = it
                }
                TraxosDateField("Opening date", day, !busy) { day = it }
            }
        }
        if (edit)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Archived", Modifier.weight(1f))
                TraxosSwitch(archived, { archived = it }, enabled = !busy, label = "Archive option")
            }
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        TraxosButton(
            {
                if (!busy) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            when (kind) {
                                "ACCOUNT" ->
                                    repository.saveAccount(
                                        op,
                                        id,
                                        name,
                                        accountType,
                                        unit,
                                        Money.parse(opening, unit, true),
                                        account?.revision,
                                        archived,
                                        LocalDate.parse(day).toEpochDay(),
                                        icon,
                                        color,
                                    )
                                "CATEGORY" ->
                                    repository.saveCategory(
                                        op,
                                        id,
                                        name,
                                        type,
                                        icon,
                                        color,
                                        archived,
                                        category?.revision,
                                    )
                                else ->
                                    repository.saveLabel(
                                        op,
                                        id,
                                        name,
                                        kind,
                                        archived,
                                        label?.revision,
                                    )
                            }
                            saved(id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error =
                                if (e is IllegalArgumentException) e.message
                                else "Could not save. Your entries are kept."
                            busy = false
                        }
                    }
                }
            },
            Modifier.fillMaxWidth().testTag("catalog_save"),
            !busy,
            busy,
        ) {
            Text(if (edit) "Save changes" else "Create and select")
        }
    }
}

@Composable
internal fun CatalogScreen(repository: LedgerRepository, kind: String) {
    val categories by repository.dao.categories().collectAsStateWithLifecycle(emptyList())
    val labels by repository.dao.labels().collectAsStateWithLifecycle(emptyList())
    var type by rememberSaveable { mutableStateOf("EXPENSE") }
    var open by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<Category?>(null) }
    var label by remember { mutableStateOf<EntryLabel?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (kind == "CATEGORY")
            TraxosSegment(listOf("EXPENSE" to "Expense", "INCOME" to "Income"), type) { type = it }
        TraxosButton(
            {
                category = null
                label = null
                open = true
            },
            Modifier.fillMaxWidth(),
        ) {
            Text("+ Add new")
        }
        if (kind == "CATEGORY")
            categories
                .filter { it.type == type }
                .forEach { value ->
                    TraxosButton(
                        {
                            category = value
                            open = true
                        },
                        Modifier.fillMaxWidth(),
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Icon(catalogIcon(value.icon), null, tint = catalogColor(value.color))
                        Text(
                            value.name + if (value.archived) " · Archived" else "",
                            Modifier.weight(1f),
                        )
                    }
                }
        else
            labels
                .filter { it.kind == kind }
                .forEach { value ->
                    TraxosButton(
                        {
                            label = value
                            open = true
                        },
                        Modifier.fillMaxWidth(),
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text(value.name + if (value.archived) " · Archived" else "")
                    }
                }
        Text(
            "Renaming keeps historical references. Archived options stay in history and can be restored here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open)
        CatalogEditor(
            repository,
            kind,
            type,
            category = category,
            label = label,
            onDismiss = { open = false },
        ) {
            open = false
        }
}

@Composable
internal fun CardPaymentScreen(repository: LedgerRepository, cardId: String, back: () -> Unit) {
    val accounts by repository.dao.accounts().collectAsStateWithLifecycle(emptyList())
    val card = accounts.firstOrNull { it.account.id == cardId }
    var source by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val op = rememberSaveable { UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(card?.account?.name ?: "Card payment", style = MaterialTheme.typography.titleLarge)
        card?.let {
            Text("Recorded debt: ${Money.format(maxOf(0,-it.balanceMinor),it.account.currency)}")
        }
        Text(
            "Moves money from your account to the card. Does not create a second expense.",
            style = MaterialTheme.typography.bodySmall,
        )
        AccountPicker(
            repository,
            source,
            accounts,
            !busy,
            "Pay from",
            card?.account?.currency,
            false,
        ) {
            source = it
        }
        TraxosAmountInput(amount, card?.account?.currency ?: "INR", !busy, false) {
            amount = it.take(24)
        }
        TraxosDateField("Payment date", day, !busy) { day = it }
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        TraxosButton(
            {
                if (!busy && card != null) {
                    busy = true
                    scope.launch {
                        try {
                            repository.payCard(
                                op,
                                cardId,
                                source,
                                Money.parse(amount, card.account.currency),
                                LocalDate.parse(day).toEpochDay(),
                            )
                            back()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save payment"
                            busy = false
                        }
                    }
                }
            },
            Modifier.fillMaxWidth().testTag("card_payment_save"),
            !busy && card != null,
            busy,
        ) {
            Text("Record card payment")
        }
    }
}
