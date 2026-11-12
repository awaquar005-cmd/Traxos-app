package app.traxos.finance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

enum class ButtonTone {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
    GHOST,
}

enum class NoticeKind {
    SUCCESS,
    ERROR,
    WARNING,
    UNDO,
    PROGRESS,
    INFO,
}

internal data class ButtonPalette(val foreground: Color, val background: Color, val pressed: Color)

internal fun buttonPalette(tone: ButtonTone, colors: ColorScheme): ButtonPalette {
    val foreground =
        when (tone) {
            ButtonTone.PRIMARY,
            ButtonTone.DESTRUCTIVE -> Color.White
            ButtonTone.GHOST -> colors.primary
            else -> colors.onSurface
        }
    val base =
        when (tone) {
            ButtonTone.PRIMARY -> Ink.cobalt
            ButtonTone.DESTRUCTIVE -> Ink.coral
            ButtonTone.SECONDARY -> colors.surfaceContainerLowest
            ButtonTone.GHOST -> Color.Transparent
        }
    val pressed =
        when (tone) {
            ButtonTone.GHOST -> colors.primary.copy(alpha = .08f)
            ButtonTone.PRIMARY,
            ButtonTone.DESTRUCTIVE -> androidx.compose.ui.graphics.lerp(base, Color.Black, .12f)
            ButtonTone.SECONDARY -> androidx.compose.ui.graphics.lerp(base, colors.onSurface, .06f)
        }
    return ButtonPalette(foreground, base, pressed)
}

@Composable
internal fun TraxosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    tone: ButtonTone = ButtonTone.PRIMARY,
    shape: Shape = Radius.control,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val palette = buttonPalette(tone, MaterialTheme.colorScheme)
    val foreground = palette.foreground
    val background = if (pressed) palette.pressed else palette.background
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            modifier
                .heightIn(min = 48.dp)
                .alpha(if (enabled) 1f else .65f)
                .clip(shape)
                .background(background)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled && !busy && LocalAppInteractionAllowed.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (busy) BusyMark(Modifier.size(20.dp), foreground)
            ProvideTextStyle(
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun TraxosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    TraxosButton(
        onClick,
        modifier,
        enabled,
        tone = ButtonTone.GHOST,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun TraxosIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(Radius.control)
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                else Color.Transparent
            )
            .alpha(if (enabled) 1f else .65f)
            .clickable(source, null, enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun BusyMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(
        modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            contentDescription = "In progress"
        }
    ) {
        repeat(3) { i ->
            drawLine(
                color.copy(alpha = .4f + i * .3f),
                androidx.compose.ui.geometry.Offset(
                    size.width * (.15f + i * .32f),
                    size.height * .25f,
                ),
                androidx.compose.ui.geometry.Offset(
                    size.width * (.15f + i * .32f),
                    size.height * .75f,
                ),
                strokeWidth = size.width * .13f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun TraxosInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    multiline: Boolean = false,
    number: Boolean = false,
    focus: Boolean = false,
    error: String? = null,
    id: String = label,
    secret: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, secret) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) revealed = false
            }
        if (secret) owner.lifecycle.addObserver(observer)
        onDispose { if (secret) owner.lifecycle.removeObserver(observer) }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focus) { if (focus) requester.requestFocus() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (secret)
                TraxosIconButton({ revealed = !revealed }, enabled = enabled) {
                    Icon(
                        if (revealed) TraxosIcons.eyeOff else TraxosIcons.eye,
                        if (revealed) "Hide $label" else "Reveal $label",
                        Modifier.size(20.dp),
                    )
                }
        }
        BasicTextField(
            visualTransformation =
                if (secret && !revealed)
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            value = value,
            onValueChange = onChange,
            enabled = enabled && LocalAppInteractionAllowed.current,
            singleLine = !multiline,
            minLines = if (multiline) 2 else 1,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        if (secret && number) KeyboardType.NumberPassword
                        else if (secret) KeyboardType.Password
                        else if (number) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier =
                Modifier.fillMaxWidth()
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused }
                    .testTag("input_$id")
                    .semantics {
                        contentDescription = label
                        if (error != null) this.error(error)
                    }
                    .clip(Radius.control)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        if (focused || error != null) 1.dp else .5.dp,
                        when {
                            error != null -> MaterialTheme.colorScheme.error
                            focused -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        Radius.control,
                    )
                    .alpha(if (enabled) 1f else .5f)
                    .padding(14.dp),
            decorationBox = { field ->
                Box(Modifier.heightIn(min = 24.dp)) {
                    if (value.isEmpty())
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    field()
                }
            },
        )
        if (error != null)
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
    }
}

@Composable
internal fun TraxosAmountInput(
    value: String,
    currency: String,
    enabled: Boolean,
    focus: Boolean,
    onChange: (String) -> Unit,
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focus) { if (focus) requester.requestFocus() }
    Surface(
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "AMOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    currency,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFeatureSettings = "tnum",
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .focusRequester(requester)
                        .onFocusChanged { focused = it.isFocused }
                        .testTag("input_amount")
                        .semantics { contentDescription = "Amount" },
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty())
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        field()
                    }
                },
            )
            Box(
                Modifier.fillMaxWidth()
                    .height(if (focused) 2.dp else 1.dp)
                    .background(
                        if (focused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
internal fun TraxosChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("choice_$label")
            .selectable(selected, enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(20.dp)
                .border(
                    if (selected) 5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    androidx.compose.foundation.shape.CircleShape,
                )
        )
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .65f),
        )
    }
}

@Composable
internal fun TraxosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Toggle",
) {
    Box(
        modifier
            .sizeIn(minWidth = 54.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else .65f)
            .toggleable(checked, enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp, 26.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                .background(if (checked) Ink.cobalt else MaterialTheme.colorScheme.outlineVariant)
                .padding(3.dp)
        ) {
            Box(
                Modifier.size(20.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
internal fun TraxosCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            Modifier.size(21.dp)
                .clip(Radius.small)
                .background(
                    if (checked) Ink.cobalt else MaterialTheme.colorScheme.surfaceContainerLowest
                )
                .border(
                    1.dp,
                    if (checked) Ink.cobalt else MaterialTheme.colorScheme.outline,
                    Radius.small,
                )
        ) {
            if (checked) {
                drawLine(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(size.width * .23f, size.height * .5f),
                    androidx.compose.ui.geometry.Offset(size.width * .43f, size.height * .7f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(size.width * .43f, size.height * .7f),
                    androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .3f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .65f),
        )
    }
}

@Composable
internal fun TraxosSegment(
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(Radius.control)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (id, label) ->
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(Radius.small)
                    .background(
                        if (selected == id) MaterialTheme.colorScheme.surfaceContainerLowest
                        else Color.Transparent
                    )
                    .selectable(
                        selected == id,
                        enabled,
                        role = Role.Tab,
                        onClick = { onSelect(id) },
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (selected == id) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
