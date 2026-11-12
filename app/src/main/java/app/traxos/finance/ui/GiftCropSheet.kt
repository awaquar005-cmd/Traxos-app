package app.traxos.finance.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.traxos.finance.gifts.BarcodeImageImport

/** Fixed ratio crops plus explicit edge entry remain accessible without precision drag gestures. */
@Composable
internal fun GiftCropSheet(
    bitmap: Bitmap,
    busy: Boolean,
    close: () -> Unit,
    scan: (Bitmap) -> Unit,
) {
    var rotation by remember { mutableIntStateOf(0) }
    var left by remember { mutableStateOf("0") }
    var top by remember { mutableStateOf("0") }
    var right by remember { mutableStateOf("100") }
    var bottom by remember { mutableStateOf("100") }
    var preset by remember { mutableStateOf("FULL") }
    var error by remember { mutableStateOf<String?>(null) }
    val preview =
        remember(bitmap, rotation) {
            if (rotation == 0) bitmap
            else
                android.graphics.Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    android.graphics.Matrix().apply { postRotate(rotation.toFloat()) },
                    true,
                )
        }
    TraxosSheet("Crop, rotate and rescan", close, scrollable = true) {
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            Image(preview.asImageBitmap(), "Gift card crop preview", Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val scale = minOf(size.width / preview.width, size.height / preview.height)
                val w = preview.width * scale
                val h = preview.height * scale
                val x = (size.width - w) / 2
                val y = (size.height - h) / 2
                val l = (left.toFloatOrNull() ?: 0f).coerceIn(0f, 100f) / 100
                val t = (top.toFloatOrNull() ?: 0f).coerceIn(0f, 100f) / 100
                val r = (right.toFloatOrNull() ?: 100f).coerceIn(0f, 100f) / 100
                val bot = (bottom.toFloatOrNull() ?: 100f).coerceIn(0f, 100f) / 100
                if (r > l && bot > t)
                    drawRect(
                        Color(0xFF3E78FF),
                        Offset(x + l * w, y + t * h),
                        Size((r - l) * w, (bot - t) * h),
                        style = Stroke(2.dp.toPx()),
                    )
            }
        }
        TraxosTextButton({ rotation = (rotation + 90) % 360 }, enabled = !busy) {
            Text("Rotate 90°")
        }
        TraxosSelector(
            "Crop area",
            preset,
            listOf(
                "FULL" to "Whole image",
                "CENTER" to "Center of image",
                "TOP" to "Top half",
                "BOTTOM" to "Bottom half",
                "CUSTOM" to "Custom edges",
            ),
            !busy,
        ) { value ->
            preset = value
            when (value) {
                "FULL" -> {
                    left = "0"
                    top = "0"
                    right = "100"
                    bottom = "100"
                }
                "CENTER" -> {
                    left = "10"
                    top = "10"
                    right = "90"
                    bottom = "90"
                }
                "TOP" -> {
                    left = "0"
                    top = "0"
                    right = "100"
                    bottom = "50"
                }
                "BOTTOM" -> {
                    left = "0"
                    top = "50"
                    right = "100"
                    bottom = "100"
                }
            }
        }
        if (preset == "CUSTOM") {
            Text("Edges as percentages · keep merchant, amount and code inside the blue box")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosInput("Left %", left, { left = it }, Modifier.weight(1f), number = true)
                TraxosInput("Top %", top, { top = it }, Modifier.weight(1f), number = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TraxosInput("Right %", right, { right = it }, Modifier.weight(1f), number = true)
                TraxosInput("Bottom %", bottom, { bottom = it }, Modifier.weight(1f), number = true)
            }
        }
        if (error != null) TraxosNotice(error!!, NoticeKind.ERROR)
        TraxosButton(
            {
                try {
                    val crop =
                        BarcodeImageImport.crop(
                            preview,
                            left.toFloat() / 100,
                            top.toFloat() / 100,
                            right.toFloat() / 100,
                            bottom.toFloat() / 100,
                        )
                    // Caller owns this processing frame; a failed scan must not recycle the visible
                    // preview.
                    scan(
                        if (crop === preview)
                            requireNotNull(preview.copy(Bitmap.Config.ARGB_8888, false))
                        else crop
                    )
                } catch (_: IllegalArgumentException) {
                    error = "Use edges from 0–100 with a non-empty crop"
                }
            },
            Modifier.fillMaxWidth(),
            !busy,
            busy,
        ) {
            Text("Scan this image")
        }
    }
}
