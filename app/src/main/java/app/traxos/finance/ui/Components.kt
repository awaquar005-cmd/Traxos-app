@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.traxos.finance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.traxos.finance.R

object Radius {
    val card = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(10.dp)
    val small = RoundedCornerShape(8.dp)
}

object Ink {
    val navy = Color(0xFF111E37)
    val cobalt = Color(0xFF3565DC)
    val green = Color(0xFF007C67)
    val coral = Color(0xFFAE4936)
}

// One consistent, compact line-icon language. Native vector paths, no extra icon SDK.
object TraxosIcons {
    private fun line(
        name: String,
        draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathBuilder = draw,
                )
            }
            .build()

    val report =
        line("Reports") {
            moveTo(4f, 3f)
            lineTo(4f, 21f)
            lineTo(21f, 21f)
            moveTo(8f, 17f)
            lineTo(8f, 12f)
            moveTo(13f, 17f)
            lineTo(13f, 8f)
            moveTo(18f, 17f)
            lineTo(18f, 4f)
        }
    val history =
        line("Recovery") {
            moveTo(3f, 10f)
            curveTo(4f, 4f, 10f, 2f, 15f, 4f)
            curveTo(22f, 7f, 22f, 17f, 15f, 20f)
            curveTo(10f, 22f, 5f, 19f, 4f, 16f)
            moveTo(3f, 4f)
            lineTo(3f, 10f)
            lineTo(9f, 10f)
            moveTo(12f, 7f)
            lineTo(12f, 12f)
            lineTo(16f, 14f)
        }
    val tag =
        line("Category") {
            moveTo(3f, 3f)
            lineTo(12f, 3f)
            lineTo(21f, 12f)
            lineTo(12f, 21f)
            lineTo(3f, 12f)
            close()
            moveTo(7f, 7f)
            lineTo(8f, 7f)
        }
    val food =
        line("Food") {
            moveTo(5f, 3f)
            verticalLineTo(10f)
            curveTo(5f, 14f, 11f, 14f, 11f, 10f)
            verticalLineTo(3f)
            moveTo(8f, 3f)
            verticalLineTo(21f)
            moveTo(18f, 3f)
            verticalLineTo(21f)
            moveTo(18f, 3f)
            curveTo(13f, 4f, 13f, 11f, 18f, 12f)
        }
    val travel =
        line("Transport") {
            moveTo(5f, 4f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 12f)
            horizontalLineTo(19f)
            moveTo(8f, 19f)
            verticalLineTo(22f)
            moveTo(16f, 19f)
            verticalLineTo(22f)
            moveTo(8f, 16f)
            horizontalLineTo(9f)
            moveTo(15f, 16f)
            horizontalLineTo(16f)
        }
    val work =
        line("Work") {
            moveTo(3f, 7f)
            horizontalLineTo(21f)
            verticalLineTo(20f)
            horizontalLineTo(3f)
            close()
            moveTo(8f, 7f)
            verticalLineTo(3f)
            horizontalLineTo(16f)
            verticalLineTo(7f)
            moveTo(3f, 12f)
            lineTo(12f, 15f)
            lineTo(21f, 12f)
        }
    val gift =
        line("Gift") {
            moveTo(3f, 8f)
            horizontalLineTo(21f)
            verticalLineTo(12f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 12f)
            verticalLineTo(21f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            moveTo(12f, 8f)
            verticalLineTo(21f)
            moveTo(12f, 8f)
            curveTo(0f, 8f, 6f, -2f, 12f, 8f)
            curveTo(18f, -2f, 24f, 8f, 12f, 8f)
        }
    val health =
        line("Health") {
            moveTo(9f, 3f)
            horizontalLineTo(15f)
            verticalLineTo(9f)
            horizontalLineTo(21f)
            verticalLineTo(15f)
            horizontalLineTo(15f)
            verticalLineTo(21f)
            horizontalLineTo(9f)
            verticalLineTo(15f)
            horizontalLineTo(3f)
            verticalLineTo(9f)
            horizontalLineTo(9f)
            close()
        }
    val tools =
        line("More tools") {
            for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                moveTo(x, y)
                horizontalLineTo(x + 7f)
                verticalLineTo(y + 7f)
                horizontalLineTo(x)
                close()
            }
        }
    val spark =
        line("Smart Add") {
            moveTo(12f, 2f)
            lineTo(14.5f, 9.5f)
            lineTo(22f, 12f)
            lineTo(14.5f, 14.5f)
            lineTo(12f, 22f)
            lineTo(9.5f, 14.5f)
            lineTo(2f, 12f)
            lineTo(9.5f, 9.5f)
            close()
        }
    val mic =
        line("Microphone") {
            moveTo(9f, 5f)
            curveTo(9f, 1f, 15f, 1f, 15f, 5f)
            verticalLineTo(11f)
            curveTo(15f, 15f, 9f, 15f, 9f, 11f)
            close()
            moveTo(5f, 10f)
            verticalLineTo(12f)
            curveTo(5f, 21f, 19f, 21f, 19f, 12f)
            verticalLineTo(10f)
            moveTo(12f, 19f)
            verticalLineTo(23f)
            moveTo(8f, 23f)
            horizontalLineTo(16f)
        }
    val transfer =
        line("Transfer") {
            moveTo(3f, 7f)
            horizontalLineTo(20f)
            moveTo(16f, 3f)
            lineTo(20f, 7f)
            lineTo(16f, 11f)
            moveTo(21f, 17f)
            horizontalLineTo(4f)
            moveTo(8f, 13f)
            lineTo(4f, 17f)
            lineTo(8f, 21f)
        }
    val people =
        line("People") {
            moveTo(8f, 4f)
            curveTo(2f, 4f, 2f, 12f, 8f, 12f)
            curveTo(14f, 12f, 14f, 4f, 8f, 4f)
            moveTo(2f, 21f)
            curveTo(2f, 12f, 14f, 12f, 14f, 21f)
            moveTo(17f, 6f)
            curveTo(23f, 6f, 23f, 14f, 17f, 14f)
            moveTo(17f, 16f)
            curveTo(21f, 16f, 23f, 18f, 23f, 21f)
        }
    val target =
        line("Goal") {
            moveTo(12f, 3f)
            curveTo(0f, 3f, 0f, 21f, 12f, 21f)
            curveTo(24f, 21f, 24f, 3f, 12f, 3f)
            moveTo(12f, 8f)
            curveTo(6f, 8f, 6f, 16f, 12f, 16f)
            curveTo(18f, 16f, 18f, 8f, 12f, 8f)
        }
    val clock =
        line("Recurring") {
            moveTo(12f, 3f)
            curveTo(0f, 3f, 0f, 21f, 12f, 21f)
            curveTo(24f, 21f, 24f, 3f, 12f, 3f)
            moveTo(12f, 7f)
            verticalLineTo(12f)
            lineTo(16f, 14f)
        }
    val shield =
        line("Security") {
            moveTo(12f, 2f)
            lineTo(21f, 6f)
            verticalLineTo(13f)
            curveTo(21f, 18f, 15f, 21f, 12f, 23f)
            curveTo(9f, 21f, 3f, 18f, 3f, 13f)
            verticalLineTo(6f)
            close()
            moveTo(8f, 12f)
            lineTo(11f, 15f)
            lineTo(16f, 9f)
        }
    val cloud =
        line("Data and backup") {
            moveTo(5f, 19f)
            curveTo(-2f, 18f, 0f, 9f, 6f, 9f)
            curveTo(8f, 0f, 20f, 2f, 20f, 10f)
            curveTo(26f, 11f, 24f, 19f, 19f, 19f)
            horizontalLineTo(5f)
        }
    val home =
        line("Home") {
            moveTo(3f, 10f)
            lineTo(12f, 3f)
            lineTo(21f, 10f)
            moveTo(5f, 9f)
            verticalLineTo(21f)
            horizontalLineTo(10f)
            verticalLineTo(15f)
            horizontalLineTo(14f)
            verticalLineTo(21f)
            horizontalLineTo(19f)
            verticalLineTo(9f)
        }
    val ledger =
        line("Activity") {
            moveTo(6f, 3f)
            horizontalLineTo(19f)
            verticalLineTo(21f)
            horizontalLineTo(5f)
            verticalLineTo(3f)
            moveTo(9f, 8f)
            horizontalLineTo(15f)
            moveTo(9f, 12f)
            horizontalLineTo(16f)
            moveTo(9f, 16f)
            horizontalLineTo(13f)
        }
    val wallet =
        line("Accounts") {
            moveTo(4f, 6f)
            lineTo(17f, 3f)
            verticalLineTo(6f)
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            close()
            moveTo(20f, 11f)
            horizontalLineTo(14f)
            verticalLineTo(16f)
            horizontalLineTo(20f)
            moveTo(16.5f, 13.5f)
            horizontalLineTo(17f)
        }
    val plus =
        line("Add") {
            moveTo(12f, 5f)
            verticalLineTo(19f)
            moveTo(5f, 12f)
            horizontalLineTo(19f)
        }
    val search =
        line("Search") {
            moveTo(10f, 3f)
            curveTo(0.7f, 3f, 0.7f, 17f, 10f, 17f)
            curveTo(19.3f, 17f, 19.3f, 3f, 10f, 3f)
            moveTo(15f, 15f)
            lineTo(21f, 21f)
        }
    val eye =
        line("Visibility") {
            moveTo(2f, 12f)
            curveTo(7f, 3f, 17f, 3f, 22f, 12f)
            curveTo(17f, 21f, 7f, 21f, 2f, 12f)
            moveTo(12f, 9f)
            curveTo(8f, 9f, 8f, 15f, 12f, 15f)
            curveTo(16f, 15f, 16f, 9f, 12f, 9f)
        }
    val eyeOff =
        line("Hidden") {
            moveTo(3f, 3f)
            lineTo(21f, 21f)
            moveTo(2f, 12f)
            curveTo(6f, 5f, 9f, 5f, 12f, 5f)
            curveTo(17f, 5f, 20f, 9f, 22f, 12f)
            moveTo(3f, 13f)
            curveTo(7f, 20f, 15f, 21f, 19f, 17f)
        }
    val settings =
        line("Settings") {
            moveTo(4f, 7f)
            horizontalLineTo(20f)
            moveTo(4f, 17f)
            horizontalLineTo(20f)
            moveTo(8f, 4f)
            verticalLineTo(10f)
            moveTo(16f, 14f)
            verticalLineTo(20f)
        }
    val arrow =
        line("Open") {
            moveTo(5f, 12f)
            horizontalLineTo(19f)
            moveTo(14f, 7f)
            lineTo(19f, 12f)
            lineTo(14f, 17f)
        }
    val back =
        line("Back") {
            moveTo(19f, 12f)
            horizontalLineTo(5f)
            moveTo(10f, 7f)
            lineTo(5f, 12f)
            lineTo(10f, 17f)
        }
    val income =
        line("Inflow") {
            moveTo(17f, 5f)
            lineTo(6f, 16f)
            moveTo(6f, 7f)
            verticalLineTo(16f)
            horizontalLineTo(15f)
        }
    val expense =
        line("Outflow") {
            moveTo(6f, 17f)
            lineTo(17f, 6f)
            moveTo(8f, 6f)
            horizontalLineTo(17f)
            verticalLineTo(15f)
        }
}

@Composable
internal fun BrandMark(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(painterResource(R.drawable.traxos_mark), "Traxos", modifier.size(20.dp), tint = tint)
}

@Composable
internal fun TraxosToolbar(
    title: String,
    back: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    home: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) TraxosIconButton(onClick = back) { Icon(TraxosIcons.back, "Back") }
        else {
            if (home != null)
                TraxosIconButton(home, Modifier.testTag("toolbar_home")) { BrandMark() }
            else BrandMark()
            Spacer(Modifier.width(if (home != null) 2.dp else 10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "TRAXOS",
                style =
                    MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                style =
                    MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions?.invoke()
    }
}

@Composable
internal fun TraxosCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun MoneyText(
    minor: Long,
    currency: String,
    large: Boolean = false,
    prefix: String = "",
    hidden: Boolean = false,
    color: Color = Color.Unspecified,
) {
    Text(
        if (hidden) "••••" else prefix + displayMoney(minor, currency),
        style =
            (if (large) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.titleMedium)
                .copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier =
            if (hidden) Modifier.semantics { contentDescription = "Amount hidden" } else Modifier,
    )
}

@Composable
internal fun SectionHeader(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (action != null)
            TraxosTextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(action, style = MaterialTheme.typography.labelMedium)
            }
    }
}

@Composable
internal fun Message(
    title: String,
    detail: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    TraxosCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), Radius.control),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TraxosIcons.ledger, null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null)
            TraxosButton(onClick = onAction, shape = Radius.control) { Text(action) }
    }
}

@Composable
internal fun BrandWatermark(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = size.minDimension / 3.25f
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 1 to 2).forEach { (x, y) ->
            drawRoundRect(
                Color.White.copy(alpha = .055f),
                Offset(x * unit * 1.08f, y * unit * 1.08f),
                Size(unit * .9f, unit * .9f),
                CornerRadius(unit * .18f),
            )
        }
    }
}

@Composable
internal fun SearchShortcut(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = Radius.control,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                TraxosIcons.search,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Search your transactions",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                TraxosIcons.arrow,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LedgerSearch(value: String, onChange: (String) -> Unit, requester: FocusRequester) {
    Surface(
        shape = Radius.control,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 50.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                TraxosIcons.search,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier =
                    Modifier.weight(1f)
                        .padding(vertical = 14.dp)
                        .focusRequester(requester)
                        .semantics { contentDescription = "Search transactions" },
                decorationBox = { field ->
                    Box {
                        if (value.isBlank())
                            Text(
                                "Search activity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        field()
                    }
                },
            )
            if (value.isNotEmpty())
                TraxosTextButton(onClick = { onChange("") }, contentPadding = PaddingValues(4.dp)) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
        }
    }
}

@Composable
internal fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Radius.small)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
            .clearAndSetSemantics {}
    )
}

@Composable
internal fun RowSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(Modifier.size(38.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(.7f).height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(.45f).height(10.dp))
        }
        SkeletonBlock(Modifier.width(64.dp).height(16.dp))
    }
}

@Composable
internal fun ScreenSkeleton(kind: String = "home") {
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics {
            contentDescription = "Loading local records"
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (kind == "home") {
            TraxosCard {
                SkeletonBlock(Modifier.width(100.dp).height(12.dp))
                SkeletonBlock(Modifier.fillMaxWidth(.65f).height(38.dp))
                SkeletonBlock(Modifier.fillMaxWidth(.4f).height(12.dp))
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(50.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { SkeletonBlock(Modifier.weight(1f).height(86.dp)) }
            }
        }
        if (kind == "accounts")
            repeat(2) {
                TraxosCard {
                    SkeletonBlock(Modifier.width(120.dp).height(16.dp))
                    SkeletonBlock(Modifier.width(150.dp).height(24.dp))
                    SkeletonBlock(Modifier.width(80.dp).height(12.dp))
                }
            }
        else repeat(4) { RowSkeleton() }
    }
}
