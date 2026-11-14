package app.traxos.finance.regional

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.util.AtomicFile
import java.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfilePhoto(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "local-profile.png"))

    val version = kotlinx.coroutines.flow.MutableStateFlow(file.baseFile.lastModified())

    suspend fun bitmap(): Bitmap? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists()) null
            else file.openRead().use { BitmapFactory.decodeStream(it) }
        }

    suspend fun remove(): Unit =
        withContext(Dispatchers.IO) {
            file.delete()
            version.value++
        }

    suspend fun import(uri: Uri): Unit =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content") { "Choose a photo using Android Files" }
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 12 * 1024 * 1024) { "Choose an image under12MB" }
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                } ?: error("Photo could not be read")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(
                bounds.outWidth > 0 &&
                    bounds.outHeight > 0 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 60_000_000
            ) {
                "Choose a supported image under60 megapixels"
            }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            val decoded =
                requireNotNull(
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                ) {
                    "Photo format is unsupported"
                }
            val orientation =
                try {
                    androidx.exifinterface.media
                        .ExifInterface(ByteArrayInputStream(bytes))
                        .getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                        )
                } catch (e: IOException) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                }
            val raw = app.traxos.finance.gifts.BarcodeImageImport.orient(decoded, orientation)
            if (raw !== decoded) decoded.recycle()
            val side = minOf(raw.width, raw.height)
            val crop =
                Bitmap.createBitmap(
                    raw,
                    (raw.width - side) / 2,
                    (raw.height - side) / 2,
                    side,
                    side,
                )
            val scaled = Bitmap.createScaledBitmap(crop, minOf(512, side), minOf(512, side), true)
            var stream: FileOutputStream? = null
            try {
                stream = file.startWrite()
                check(scaled.compress(Bitmap.CompressFormat.PNG, 100, stream))
                file.finishWrite(stream)
                version.value++
            } catch (e: Exception) {
                stream?.let { file.failWrite(it) }
                throw e
            } finally {
                if (scaled !== crop) scaled.recycle()
                if (crop !== raw) crop.recycle()
                raw.recycle()
                bytes.fill(0)
            }
        }

    suspend fun rotate(): Unit =
        withContext(Dispatchers.IO) {
            val raw = requireNotNull(bitmap()) { "Choose a photo first" }
            val rotated =
                Bitmap.createBitmap(
                    raw,
                    0,
                    0,
                    raw.width,
                    raw.height,
                    Matrix().apply { postRotate(90f) },
                    true,
                )
            var output: FileOutputStream? = null
            try {
                output = file.startWrite()
                check(rotated.compress(Bitmap.CompressFormat.PNG, 100, output))
                file.finishWrite(output)
                version.value++
            } catch (e: Exception) {
                output?.let { file.failWrite(it) }
                throw e
            } finally {
                if (rotated !== raw) rotated.recycle()
                raw.recycle()
            }
        }
}

object LauncherNames {
    fun select(context: Context, name: String) {
        require(name in listOf("TRAXOS", "FINANCE"))
        val pm = context.packageManager
        val official = ComponentName(context, context.packageName + ".OfficialLauncher")
        val finance = ComponentName(context, context.packageName + ".FinanceLauncher")
        val chosen = if (name == "TRAXOS") official else finance
        val other = if (name == "TRAXOS") finance else official
        // Enable the new entry first so a failed switch never leaves no launcher entry.
        pm.setComponentEnabledSetting(
            chosen,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            other,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
