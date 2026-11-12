package app.traxos.finance.gifts

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

/**
 * Images stay local. Recognition gets readable pixels; attachment compression happens afterwards.
 */
object GiftImageImport {
    data class Result(
        val extraction: GiftExtraction,
        val image: ByteArray,
        val recognizedCharacters: Int = 0,
    )

    const val MAX_PIXELS = 6_000_000L

    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Choose a readable image" }
        var sample = 1
        while (
            width.toLong() / sample * (height.toLong() / sample) > MAX_PIXELS ||
                maxOf(width, height) / sample > 6000
        ) sample *= 2
        return sample
    }

    fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun load(context: Context, uri: Uri): Bitmap =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content") { "Choose a local image; links are not supported" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                }
            val orientation =
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    runCatching {
                            ExifInterface(stream)
                                .getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL,
                                )
                        }
                        .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            val bitmap =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: throw IllegalArgumentException("Could not open image")
            val corrected = orient(bitmap, orientation)
            if (corrected !== bitmap) bitmap.recycle()
            corrected
        }

    fun crop(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        require(
            left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom
        ) {
            "Choose a non-empty crop"
        }
        val x = (left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        return Bitmap.createBitmap(
            bitmap,
            x,
            y,
            ((right - left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x),
            ((bottom - top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y),
        )
    }

    private suspend fun recognize(
        bitmap: Bitmap,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
    ): String = suspendCancellableCoroutine { cont ->
        recognizer
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (cont.isActive) {
                    val lines =
                        result.textBlocks
                            .flatMap { it.lines }
                            .mapNotNull { line ->
                                line.boundingBox?.let { box ->
                                    OcrReadingOrder.Line(
                                        line.text,
                                        box.left,
                                        box.top,
                                        box.right,
                                        box.bottom,
                                    )
                                }
                            }
                    cont.resume(if (lines.isEmpty()) result.text else OcrReadingOrder.text(lines))
                }
            }
            .addOnFailureListener {
                if (cont.isActive)
                    cont.resumeWithException(
                        IllegalArgumentException(
                            "Offline text recognition could not read this image. Crop, rotate or rescan a clearer photo.",
                            it,
                        )
                    )
            }
    }

    /** OCR tiles overlap so multiline labels/codes are not lost at a tile edge. */
    suspend fun detect(bitmap: Bitmap, ocr: Boolean = true): Result =
        withContext(Dispatchers.Default) {
            val scanner = BarcodeScanning.getClient()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val codes =
                    suspendCancellableCoroutine<List<String>> { cont ->
                        scanner
                            .process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { r ->
                                if (cont.isActive)
                                    cont.resume(
                                        r.mapNotNull { it.rawValue }
                                            .filterNot(ImportTextSafety::isLink)
                                            .distinct()
                                    )
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resumeWithException(it)
                            }
                    }
                val pieces = mutableListOf<String>()
                if (ocr) {
                    if (bitmap.height > 2400 && bitmap.height > bitmap.width * 2) {
                        var y = 0
                        while (y < bitmap.height) {
                            val tile =
                                Bitmap.createBitmap(
                                    bitmap,
                                    0,
                                    y,
                                    bitmap.width,
                                    minOf(2200, bitmap.height - y),
                                )
                            try {
                                pieces += recognize(tile, recognizer)
                            } finally {
                                if (tile !== bitmap) tile.recycle()
                            }
                            if (y + 2200 >= bitmap.height) break
                            y += 2000
                        }
                    } else pieces += recognize(bitmap, recognizer)
                }
                var text = pieces.joinToString("\n")
                var parsed = GiftParser.parse(text)
                // One bounded contrast/grayscale retry for faint text; never sharpen by guessing
                // characters.
                if (ocr && (parsed.code.isBlank() || parsed.amount.isBlank())) {
                    val enhanced = createBitmap(bitmap.width, bitmap.height)
                    val contrast =
                        ColorMatrix(
                            floatArrayOf(
                                1.15f,
                                0f,
                                0f,
                                0f,
                                -12f,
                                0f,
                                1.15f,
                                0f,
                                0f,
                                -12f,
                                0f,
                                0f,
                                1.15f,
                                0f,
                                -12f,
                                0f,
                                0f,
                                0f,
                                1f,
                                0f,
                            )
                        )
                    val gray =
                        ColorMatrix().apply {
                            setSaturation(0f)
                            postConcat(contrast)
                        }
                    Canvas(enhanced)
                        .drawBitmap(
                            bitmap,
                            0f,
                            0f,
                            Paint().apply { colorFilter = ColorMatrixColorFilter(gray) },
                        )
                    try {
                        val secondText = recognize(enhanced, recognizer)
                        val second = GiftParser.parse(secondText)
                        if (second.confidence.values.sum() > parsed.confidence.values.sum()) {
                            parsed = second
                            text = secondText
                        }
                    } finally {
                        enhanced.recycle()
                    }
                }
                val barcode =
                    codes.singleOrNull()?.takeIf {
                        it.length in 3..512 && !ImportTextSafety.isOtp(it)
                    }
                if (barcode != null && parsed.code.isBlank())
                    parsed =
                        parsed.copy(
                            code = barcode,
                            confidence = parsed.confidence + ("code" to 65),
                            warnings = parsed.warnings + "Barcode value needs confirmation",
                        )
                if (codes.size > 1)
                    parsed =
                        parsed.copy(
                            warnings =
                                parsed.warnings +
                                    "Several barcodes found; crop to one card or choose a code"
                        )
                if (ocr && parsed.code.isBlank())
                    parsed =
                        parsed.copy(
                            warnings =
                                parsed.warnings + "Code not read: crop/rescan or enter it yourself"
                        )
                if (ocr && parsed.amount.isBlank())
                    parsed =
                        parsed.copy(
                            warnings =
                                parsed.warnings + "Amount is unconfirmed; leave it blank if unknown"
                        )
                val ratio = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
                val small =
                    if (ratio < 1f)
                        bitmap.scale(
                            (bitmap.width * ratio).toInt().coerceAtLeast(1),
                            (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        )
                    else bitmap
                val bytes =
                    try {
                        ByteArrayOutputStream().use { out ->
                            small.compress(Bitmap.CompressFormat.JPEG, 65, out)
                            out.toByteArray()
                        }
                    } finally {
                        if (small !== bitmap) small.recycle()
                    }
                require(bytes.size <= 700_000) { "Crop the image to reduce its size before saving" }
                Result(parsed, bytes, text.length)
            } finally {
                scanner.close()
                recognizer.close()
            }
        }
}
