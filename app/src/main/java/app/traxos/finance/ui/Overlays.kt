@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.traxos.finance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun TraxosNotice(text: String, kind: NoticeKind, modifier: Modifier = Modifier) {
    val accent =
        when (kind) {
            NoticeKind.ERROR -> MaterialTheme.colorScheme.error
            NoticeKind.WARNING ->
                if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFFFC475)
                else Color(0xFF885B13)
            NoticeKind.SUCCESS,
            NoticeKind.UNDO -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.control)
            .background(accent.copy(alpha = .07f))
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kind == NoticeKind.PROGRESS) BusyMark(Modifier.size(18.dp), accent)
        else
            Text(
                when (kind) {
                    NoticeKind.SUCCESS -> "✓"
                    NoticeKind.ERROR,
                    NoticeKind.WARNING -> "!"
                    NoticeKind.UNDO -> "↶"
                    else -> "i"
                },
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

data class TraxosSnackbarVisuals(
    override val message: String,
    val kind: NoticeKind = NoticeKind.INFO,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
internal fun TraxosSnackbar(data: SnackbarData) {
    val kind = (data.visuals as? TraxosSnackbarVisuals)?.kind ?: NoticeKind.INFO
    val accent =
        when (kind) {
            NoticeKind.ERROR -> MaterialTheme.colorScheme.error
            NoticeKind.WARNING ->
                if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFFFC475)
                else Color(0xFF885B13)
            else -> MaterialTheme.colorScheme.primary
        }
    Surface(
        shape = Radius.control,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(12.dp).fillMaxWidth().testTag("traxos_feedback"),
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (kind == NoticeKind.PROGRESS) BusyMark(Modifier.size(18.dp), accent)
            else
                Text(
                    when (kind) {
                        NoticeKind.SUCCESS -> "✓"
                        NoticeKind.UNDO -> "↶"
                        NoticeKind.ERROR,
                        NoticeKind.WARNING -> "!"
                        else -> "i"
                    },
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                )
            Text(
                data.visuals.message,
                Modifier.weight(1f).padding(vertical = 12.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            data.visuals.actionLabel?.let { label ->
                TraxosTextButton(onClick = { data.performAction() }) { Text(label) }
            }
            if (data.visuals.withDismissAction)
                TraxosIconButton(onClick = { data.dismiss() }) {
                    Text(
                        "×",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { contentDescription = "Dismiss message" },
                    )
                }
        }
    }
}

@Composable
internal fun TraxosDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    if (!LocalAppInteractionAllowed.current) return

    val keyboard = LocalSoftwareKeyboardController.current
    val parentFocus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        keyboard?.hide()
        parentFocus.clearFocus()
    }
    val maximumHeight = overlayMaxHeight()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier =
                Modifier.padding(20.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maximumHeight)
                    .semantics { paneTitle = title }
                    .testTag("traxos_dialog"),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (destructive) "PLEASE REVIEW" else "TRAXOS",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TraxosButton(
                    onConfirm,
                    Modifier.fillMaxWidth().testTag("dialog_confirm"),
                    enabled,
                    tone = if (destructive) ButtonTone.DESTRUCTIVE else ButtonTone.PRIMARY,
                ) {
                    Text(confirmLabel)
                }
                TraxosTextButton(onDismiss, Modifier.fillMaxWidth(), enabled) { Text(cancelLabel) }
            }
        }
    }
}

@Composable
internal fun TraxosSheet(
    title: String,
    onDismiss: () -> Unit,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalAppInteractionAllowed.current) return

    val maximumHeight = overlayMaxHeight()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = maximumHeight)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .semantics { paneTitle = title }
                .testTag("traxos_sheet")
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TraxosIconButton(onDismiss) {
                    Text(
                        "×",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { contentDescription = "Close picker" },
                    )
                }
            }
            if (scrollable)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            else content()
        }
    }
}

private class FittingPopup(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            (if (layoutDirection == LayoutDirection.Rtl) anchorBounds.right - popupContentSize.width
                else anchorBounds.left)
                .coerceIn(
                    margin,
                    (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin),
                )
        val below = anchorBounds.bottom + margin
        val y =
            (if (below + popupContentSize.height <= windowSize.height - margin) below
                else anchorBounds.top - popupContentSize.height - margin)
                .coerceIn(
                    margin,
                    (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin),
                )
        return IntOffset(x, y)
    }
}

@Composable
internal fun TraxosSelector(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    sheet: Boolean = true,
    onCreate: (() -> Unit)? = null,
    onSelect: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val parentFocus = LocalFocusManager.current
    var open by rememberSaveable { mutableStateOf(false) }
    var width by remember { mutableStateOf(240.dp) }
    val density = LocalDensity.current
    val value = options.firstOrNull { it.first == selected }?.second ?: "Choose $label"
    Box(
        Modifier.fillMaxWidth().onGloballyPositioned {
            width = with(density) { it.size.width.toDp() }
        }
    ) {
        TraxosButton(
            {
                keyboard?.hide()
                parentFocus.clearFocus()
                open = true
            },
            Modifier.fillMaxWidth().testTag("selector_$label"),
            enabled && (options.isNotEmpty() || onCreate != null),
            tone = ButtonTone.SECONDARY,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(TraxosIcons.arrow, null, Modifier.size(18.dp))
        }
        if (open && !sheet && LocalAppInteractionAllowed.current)
            Popup(
                popupPositionProvider = FittingPopup(with(density) { 8.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = Radius.control,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp,
                    modifier = Modifier.width(width).heightIn(max = 300.dp).testTag("traxos_menu"),
                ) {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        items(options, key = { it.first }) { (id, name) ->
                            TraxosChoiceRow(name, id == selected) {
                                open = false
                                onSelect(id)
                            }
                        }
                    }
                }
            }
    }
    if (open && sheet)
        TraxosSheet(label, { open = false }) {
            if (onCreate != null)
                TraxosTextButton(
                    {
                        open = false
                        onCreate()
                    },
                    Modifier.fillMaxWidth().testTag("add_new_$label"),
                ) {
                    Text("+ Add new")
                }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(options, key = { it.first }) { (id, name) ->
                    TraxosChoiceRow(name, id == selected) {
                        open = false
                        onSelect(id)
                    }
                }
            }
        }
}

@Composable
internal fun TraxosDateField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val parentFocus = LocalFocusManager.current
    var open by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    TraxosButton(
        {
            keyboard?.hide()
            parentFocus.clearFocus()
            open = true
        },
        Modifier.fillMaxWidth().testTag("date_selector"),
        enabled,
        tone = ButtonTone.SECONDARY,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                date?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(TraxosIcons.ledger, null, Modifier.size(18.dp))
    }
    if (open && LocalAppInteractionAllowed.current) {
        var draft by rememberSaveable { mutableStateOf(value) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        TraxosSheet("Choose date", { open = false }) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Choose a quick date or enter any date below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraxosButton(
                        {
                            draft = LocalDate.now().toString()
                            error = null
                        },
                        Modifier.weight(1f),
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Today")
                    }
                    TraxosButton(
                        {
                            draft = LocalDate.now().minusDays(1).toString()
                            error = null
                        },
                        Modifier.weight(1f),
                        tone = ButtonTone.SECONDARY,
                    ) {
                        Text("Yesterday")
                    }
                }
                TraxosInput(
                    "Date · YYYY-MM-DD",
                    draft,
                    {
                        draft = it.take(10)
                        error = null
                    },
                    error = error,
                    id = "date",
                )
                Spacer(Modifier.height(8.dp))
            }
            TraxosButton(
                {
                    try {
                        LocalDate.parse(draft)
                        onChange(draft)
                        open = false
                    } catch (_: java.time.DateTimeException) {
                        error = "Enter a valid date as YYYY-MM-DD"
                    }
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("Use date")
            }
        }
    }
}

@Composable
internal fun TraxosActionMenu(options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val parentFocus = LocalFocusManager.current
    var open by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        TraxosIconButton({
            keyboard?.hide()
            parentFocus.clearFocus()
            open = true
        }) {
            Text(
                "⋮",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { contentDescription = "More actions" },
            )
        }
        if (open && LocalAppInteractionAllowed.current)
            Popup(
                popupPositionProvider = FittingPopup(with(density) { 8.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = Radius.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 4.dp,
                    modifier = Modifier.width(208.dp).testTag("traxos_menu"),
                ) {
                    Column(Modifier.padding(4.dp)) {
                        options.forEach { (id, label) ->
                            TraxosTextButton(
                                {
                                    open = false
                                    onSelect(id)
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        when (id) {
                                            "add" -> TraxosIcons.plus
                                            "settings" -> TraxosIcons.settings
                                            "reports" -> TraxosIcons.report
                                            "deleted" -> TraxosIcons.history
                                            else -> TraxosIcons.ledger
                                        },
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow =
                                            androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun overlayMaxHeight(): Dp {
    val size = LocalWindowInfo.current.containerSize
    return (with(LocalDensity.current) { size.height.toDp() } * .8f).coerceAtLeast(180.dp)
}
