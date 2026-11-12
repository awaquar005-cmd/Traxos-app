package app.traxos.finance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val reviewStates =
    listOf(
        "Sample",
        "Empty",
        "Loading",
        "Error",
        "Offline",
        "Permission denied",
        "Locked",
        "Access revoked",
        "Conflict",
        "Success",
        "Long text",
    )

/**
 * An isolated UI sandbox: it has no finance repository, lock controller, file or network actions.
 */
@Composable
internal fun DesignReviewScreen(start: String = "catalog", close: () -> Unit, home: () -> Unit) {
    var path by
        rememberSaveable(start) {
            mutableStateOf(if (start == "catalog") listOf("catalog") else listOf("catalog", start))
        }
    var state by rememberSaveable { mutableStateOf("Sample") }
    val id = path.last()
    val page = reviewPages.firstOrNull { it.id == id }
    val back: () -> Unit = {
        if (path.size > 1) {
            path = path.dropLast(1)
            state = "Sample"
        } else close()
    }
    val navigate: (String) -> Unit = { target ->
        if (target != path.last()) {
            path = path + target
            state = "Sample"
        }
    }
    BackHandler(onBack = back)
    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("design_review")
    ) {
        TraxosToolbar(
            page?.title ?: "Full UI review",
            back = back,
            actions = {
                TraxosActionMenu(
                    listOf(
                        "catalog" to "All preview screens",
                        "home" to "Real dashboard",
                        "close" to "Close preview",
                    )
                ) {
                    when (it) {
                        "catalog" -> {
                            path = listOf("catalog")
                            state = "Sample"
                        }
                        "home" -> home()
                        else -> close()
                    }
                }
            },
        )
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "UI PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                " · Sample only · Nothing is saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (page == null) {
            ReviewCatalog(navigate)
        } else
            key(page.id) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${page.group} / ${page.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TraxosSelector(
                        "UI state",
                        state,
                        reviewStates.map { it to it },
                        sheet = false,
                    ) {
                        state = it
                    }
                }
                ReviewPageContent(page, state, navigate, { state = "Sample" }, Modifier.weight(1f))
            }
    }
}

@Composable
private fun ReviewCatalog(navigate: (String) -> Unit) {
    val groups = reviewPages.groupBy { it.group }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("review_catalog"),
    ) {
        item {
            Text("Review the whole product", style = MaterialTheme.typography.titleLarge)
            Text(
                "${reviewPages.size} pages · linked journeys · shared Traxos components. Live features stay outside this sandbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        groups.forEach { (group, pages) ->
            item(key = "heading:$group") {
                Text(
                    group.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(pages, key = { it.id }) { page ->
                ToolRow(
                    ToolEntry(page.id, page.title, page.subtitle, reviewIcon(page.group)),
                    true,
                    navigate,
                )
            }
        }
    }
}

private fun reviewIcon(group: String) =
    when (group) {
        "Start & authentication",
        "Settings & privacy" -> TraxosIcons.shield
        "Accounts & cards" -> TraxosIcons.wallet
        "Plans & goals" -> TraxosIcons.target
        "People & groups",
        "Debts & contacts" -> TraxosIcons.people
        "Data & recovery" -> TraxosIcons.cloud
        "Reminders" -> TraxosIcons.clock
        "Smart entry" -> TraxosIcons.spark
        else -> TraxosIcons.tools
    }

@Composable
internal fun ReviewPageContent(
    page: ReviewPage,
    state: String,
    navigate: (String) -> Unit,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var values by remember(page.id) { mutableStateOf(emptyMap<String, String>()) }
    var notice by rememberSaveable(page.id) { mutableStateOf<String?>(null) }
    var validation by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    fun action() {
        validation = null
        notice =
            "Preview interaction only. No financial record, account, message, backup or service was changed."
    }
    if (confirm)
        TraxosDialog(
            "Review sample action",
            "This is an isolated design preview. Your real records and settings are unchanged.",
            "Preview confirmation",
            {
                confirm = false
                action()
            },
            { confirm = false },
            destructive = true,
        )
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                page.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("review_page_${page.id}"),
            )
            Text(
                page.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                "Empty" ->
                    Message(
                        "Nothing here yet · preview",
                        "This first-use state contains no real records.",
                        "Preview first action",
                    ) {
                        action()
                    }
                "Loading" -> {
                    Text(
                        "Loading layout example · no job is running",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    repeat(3) { RowSkeleton() }
                }
                "Error" -> {
                    TraxosNotice(
                        "Sample recoverable error. Preserve inputs and offer a clear retry.",
                        NoticeKind.ERROR,
                    )
                    TraxosButton(retry) { Text("Retry preview") }
                }
                "Permission denied" -> {
                    TraxosNotice(
                        "Sample permission-denied state. Manual entry remains available; no permission is requested here.",
                        NoticeKind.WARNING,
                    )
                    TraxosButton(
                        { navigate("permission_dashboard") },
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Review permission layout")
                    }
                }
                "Locked" -> {
                    Icon(
                        TraxosIcons.shield,
                        "Content locked",
                        Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Sample locked content. No private ledger is loaded.")
                    TraxosButton(retry) { Text("Preview unlock transition") }
                }
                "Access revoked" -> {
                    TraxosNotice(
                        "Sample revoked access. Keep a personal draft; don't claim it was shared.",
                        NoticeKind.WARNING,
                    )
                    TraxosButton({ navigate("group_revoked") }, tone = ButtonTone.SECONDARY) {
                        Text("Review access-change flow")
                    }
                }
                else -> {
                    if (state == "Offline")
                        TraxosNotice(
                            "Offline is normal. This sample layout keeps personal tracking available.",
                            NoticeKind.INFO,
                        )
                    if (state == "Conflict") {
                        ReviewPair("This phone · sample", "₹250 · original retained")
                        ReviewPair("Other device · sample", "₹300 · needs deliberate review")
                        TraxosButton({ navigate("conflict_detail") }, tone = ButtonTone.SECONDARY) {
                            Text("Compare versions")
                        }
                    }
                    if (state == "Success")
                        TraxosNotice(
                            "Success-state preview only · nothing was actually saved or sent",
                            NoticeKind.SUCCESS,
                        )
                    if (page.layout == "WELCOME") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BrandMark(Modifier.size(44.dp))
                        }
                        Text(
                            "Your money. Your pace.",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        TraxosButton({ navigate("tracking_mode") }, Modifier.fillMaxWidth()) {
                            Text("Start offline · preview")
                        }
                        TraxosButton(
                            { navigate("link") },
                            Modifier.fillMaxWidth(),
                            tone = ButtonTone.SECONDARY,
                        ) {
                            Text("Optional account link · preview")
                        }
                    }
                    if (page.layout == "DASHBOARD") {
                        Surface(
                            shape = Radius.card,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "SPENT THIS MONTH · SAMPLE",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text("₹14,580.00", style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    "Inflow ₹30,000 · Net change ₹15,420",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        ReviewPair("Budget rail · sample", "₹8,400 of ₹15,000")
                        LinearProgressIndicator(
                            progress = { .56f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                        ToolRow(
                            ToolEntry(
                                "smart_inbox",
                                "2 suggestions ready to review",
                                "Fictional candidates, not your inbox",
                                TraxosIcons.spark,
                            ),
                            true,
                            navigate,
                        )
                        Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                        ReviewPair("Sample cafe", "₹250 · Food & drink")
                        ReviewPair("Sample salary", "₹30,000 · Income")
                    }
                    if (page.layout == "BUDGET" || page.layout == "GOAL") {
                        Text(
                            if (page.layout == "GOAL") "₹12,000 / ₹50,000 · sample"
                            else "₹8,400 / ₹15,000 · sample",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        LinearProgressIndicator(
                            progress = { if (page.layout == "GOAL") .24f else .56f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (page.layout == "NAVIGATION") {
                        TraxosBottomBar(
                            "home",
                            CenterShortcut.ADD_TRANSACTION,
                            {},
                            slots = listOf("home", "transactions", "accounts", "more"),
                            systemInsets = false,
                        ) {
                            action()
                        }
                        Text(
                            "This sample bar does not replace your actual navigation preferences.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    page.rows.forEach { (label, value) ->
                        ReviewPair(
                            if (state == "Long text")
                                "$label · A very long multilingual sample label हिन्दी " +
                                    label.repeat(5)
                            else label,
                            value,
                        )
                    }
                    if (page.fields.isNotEmpty()) {
                        val essential =
                            page.fields.take(if (page.layout == "SPLIT") page.fields.size else 4)
                        (essential + if (details) page.fields.drop(4) else emptyList()).forEach {
                            field ->
                            val value = values[field.label] ?: field.options.firstOrNull() ?: ""
                            val change: (String) -> Unit = {
                                values = values + (field.label to it)
                                validation = null
                                notice = null
                            }
                            when (field.kind) {
                                "CHOICE" ->
                                    TraxosSelector(
                                        field.label,
                                        value,
                                        field.options.map { it to it },
                                        onSelect = change,
                                    )
                                "TOGGLE" ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            field.label,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        TraxosSwitch(
                                            value == "true",
                                            { change(it.toString()) },
                                            label = field.label,
                                        )
                                    }
                                "DATE" ->
                                    TraxosDateField(
                                        field.label,
                                        value.ifBlank { "2030-12-31" },
                                        true,
                                        change,
                                    )
                                else ->
                                    TraxosInput(
                                        field.label,
                                        value,
                                        change,
                                        number =
                                            field.kind == "AMOUNT" ||
                                                (field.kind == "SECRET" &&
                                                    field.label.contains("PIN")),
                                        secret = field.kind == "SECRET",
                                        multiline =
                                            field.label.contains("draft") ||
                                                field.label.contains("happened"),
                                        id = "review_${field.label}",
                                    )
                            }
                        }
                        if (page.fields.size > 4 && page.layout != "SPLIT")
                            TraxosTextButton({ details = !details }) {
                                Text(if (details) "Less details" else "More details")
                            }
                    }
                    if (page.layout == "LIST")
                        TraxosButton(
                            { notice = "Sample filter selected · no real ledger query was run" },
                            Modifier.fillMaxWidth(),
                            tone = ButtonTone.SECONDARY,
                        ) {
                            Icon(TraxosIcons.search, null)
                            Text("Search / filter preview")
                        }
                    if (page.layout == "CONFLICT")
                        TraxosSegment(
                            listOf(
                                "LOCAL" to "This phone",
                                "REMOTE" to "Other device",
                                "LATER" to "Later",
                            ),
                            values["resolution"] ?: "LATER",
                        ) {
                            values = values + ("resolution" to it)
                        }
                    if (page.layout == "DESTRUCTIVE")
                        TraxosNotice(
                            "Sample destructive flow only. The actual app does not delete data from this screen.",
                            NoticeKind.WARNING,
                        )
                }
            }
            if (validation != null) TraxosNotice(validation!!, NoticeKind.ERROR)
            if (notice != null)
                TraxosNotice(notice!!, NoticeKind.INFO, Modifier.testTag("review_feedback"))
            if (page.links.isNotEmpty()) {
                Text(
                    "NEXT IN THIS JOURNEY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                page.links.forEach { id ->
                    reviewPages
                        .firstOrNull { it.id == id }
                        ?.let { next ->
                            ToolRow(
                                ToolEntry(id, next.title, next.subtitle, reviewIcon(next.group)),
                                true,
                                navigate,
                            )
                        }
                }
            }
            Text(
                "Prototype only · no external services or financial writes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state !in listOf("Loading", "Locked", "Permission denied", "Access revoked"))
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TraxosButton(
                        {
                            if (page.layout == "DESTRUCTIVE") confirm = true
                            else if (
                                page.fields.any {
                                    it.kind == "AMOUNT" && !it.label.contains("optional", true)
                                } &&
                                    values[
                                            page.fields
                                                .first {
                                                    it.kind == "AMOUNT" &&
                                                        !it.label.contains("optional", true)
                                                }
                                                .label]
                                        .isNullOrBlank()
                            )
                                validation = "Enter a sample amount to review this form state"
                            else action()
                        },
                        Modifier.weight(1f).testTag("review_primary"),
                        tone =
                            if (page.layout == "DESTRUCTIVE") ButtonTone.DESTRUCTIVE
                            else ButtonTone.PRIMARY,
                    ) {
                        Text(
                            if (page.layout == "DESTRUCTIVE") "Preview confirmation"
                            else "Preview action"
                        )
                    }
                }
            }
    }
}

@Composable
private fun ReviewPair(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}
