package app.traxos.finance.gifts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object GiftImageImport {
    data class Result(val extraction: GiftExtraction, val image: ByteArray)

    suspend fun load(context: Context, uri: Uri): Bitmap =
        withContext(Dispatchers.IO) {
            // Bounds decode before allocating. Imports are user-selected only; no media permission.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable image" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: throw IllegalArgumentException("Could not open image")
        }

    suspend fun detect(bitmap: Bitmap, ocr: Boolean): Result =
        withContext(Dispatchers.Default) {
            val scaled =
                if (maxOf(bitmap.width, bitmap.height) > 1600) {
                    val ratio = 1600f / maxOf(bitmap.width, bitmap.height)
                    bitmap.scale(
                        (bitmap.width * ratio).toInt(),
                        (bitmap.height * ratio).toInt(),
                        true,
                    )
                } else bitmap
            val input = InputImage.fromBitmap(scaled, 0)
            val scanner = BarcodeScanning.getClient()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val codes =
                    suspendCancellableCoroutine<List<String>> { cont ->
                        scanner
                            .process(input)
                            .addOnSuccessListener { values ->
                                if (cont.isActive)
                                    cont.resume(values.mapNotNull { it.rawValue }.distinct())
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resumeWithException(it)
                            }
                    }
                require(codes.size <= 1) {
                    "Several codes were found. Crop the image to one gift card and try again"
                }
                val text =
                    if (ocr)
                        suspendCancellableCoroutine<String> { cont ->
                            recognizer
                                .process(input)
                                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                                .addOnFailureListener {
                                    if (cont.isActive) cont.resumeWithException(it)
                                }
                        }
                    else ""
                val parsed = GiftParser.parse(text)
                // URLs are not followed or mistaken for gift codes. User can paste a labeled code
                // if needed.
                val raw =
                    codes
                        .singleOrNull()
                        ?.takeIf { !it.contains("://") && it.length in 3..512 }
                        .orEmpty()
                val bytes =
                    ByteArrayOutputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        out.toByteArray()
                    }
                require(bytes.size <= 700_000) { "Image is too large. Crop it and try again" }
                Result(parsed.copy(code = raw.ifBlank { parsed.code }), bytes)
            } finally {
                scanner.close()
                recognizer.close()
                if (scaled !== bitmap) scaled.recycle()
            }
        }
}
