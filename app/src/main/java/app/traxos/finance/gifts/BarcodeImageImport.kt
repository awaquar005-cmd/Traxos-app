package app.traxos.finance.gifts

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

/** Local QR/barcode capture and bounded attachment decoding. No image text recognition. */
object BarcodeImageImport {
    data class Result(val extraction: GiftExtraction, val image: ByteArray)

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

    /** Barcode values are untrusted text; no text recognition or link resolution. */
    suspend fun detect(bitmap: Bitmap): Result =
        withContext(Dispatchers.Default) {
            val scanner = BarcodeScanning.getClient()
            try {
                val codes =
                    suspendCancellableCoroutine<List<String>> { cont ->
                        scanner
                            .process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { values ->
                                if (cont.isActive)
                                    cont.resume(
                                        values
                                            .mapNotNull { it.rawValue }
                                            .filterNot {
                                                ImportTextSafety.isLink(it) ||
                                                    ImportTextSafety.isOtp(it)
                                            }
                                            .distinct()
                                    )
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resumeWithException(it)
                            }
                    }
                require(codes.size == 1) {
                    if (codes.isEmpty())
                        "No safe QR/barcode found. Paste the code or enter it manually."
                    else "Several barcodes found. Photograph one card at a time."
                }
                val code = codes.single()
                GiftRules.validateCode(code)
                val parsed =
                    GiftExtraction(
                        code = code,
                        confidence = mapOf("code" to 65),
                        warnings =
                            listOf(
                                "Review this barcode code. Merchant and value must be entered manually."
                            ),
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
                Result(parsed, bytes)
            } finally {
                scanner.close()
            }
        }
}
