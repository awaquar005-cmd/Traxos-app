package app.traxos.finance.entry

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*

class ReceiptStore(
    private val context: Context,
    private val db: TraxosDatabase,
    private val cipher: GiftCipher = KeystoreGiftCipher("traxos.receipts"),
) {
    companion object {
        const val MAX_BYTES = 1_000_000

        fun validate(mime: String, bytes: ByteArray) {
            require(bytes.size in 1..MAX_BYTES) { "Receipt exceeds 1 MB" }
            require(mime in listOf("image/jpeg", "application/pdf"))
            if (mime == "application/pdf")
                require(
                    bytes.size >= 5 &&
                        bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-"
                ) {
                    "Not a readable PDF"
                }
            else
                require(
                    bytes.size >= 3 &&
                        (bytes[0].toInt() and 255) == 255 &&
                        (bytes[1].toInt() and 255) == 216
                ) {
                    "Not a readable receipt image"
                }
            if (mime == "image/jpeg") {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(
                    bounds.outWidth > 0 &&
                        bounds.outHeight > 0 &&
                        bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000 &&
                        maxOf(bounds.outWidth, bounds.outHeight) <= 2000
                ) {
                    "Receipt image dimensions are unsupported"
                }
            }
        }
    }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    suspend fun add(owner: String, uri: Uri): String =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content") { "Choose a receipt using Android Files" }
            val input =
                requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Receipt could not be opened"
                }
            val raw =
                input.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        require(out.size() + n <= 12 * 1024 * 1024) {
                            "Choose an image under 12 MB or a PDF under 1 MB"
                        }
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            val pdf = raw.size >= 5 && raw.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-"
            val mime = if (pdf) "application/pdf" else "image/jpeg"
            val payload =
                if (pdf) raw
                else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                    require(
                        bounds.outWidth > 0 &&
                            bounds.outHeight > 0 &&
                            bounds.outWidth.toLong() * bounds.outHeight <= 60_000_000
                    ) {
                        "Choose a supported receipt image under 60 megapixels"
                    }
                    var sample = 1
                    while (
                        (maxOf(bounds.outWidth, bounds.outHeight) + sample - 1) / sample > 2000
                    ) sample *= 2
                    val decoded =
                        requireNotNull(
                            BitmapFactory.decodeByteArray(
                                raw,
                                0,
                                raw.size,
                                BitmapFactory.Options().apply { inSampleSize = sample },
                            )
                        ) {
                            "Receipt image could not be decoded"
                        }
                    val orientation =
                        runCatching {
                                ExifInterface(ByteArrayInputStream(raw))
                                    .getAttributeInt(
                                        ExifInterface.TAG_ORIENTATION,
                                        ExifInterface.ORIENTATION_NORMAL,
                                    )
                            }
                            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    val bitmap = BarcodeImageImport.orient(decoded, orientation)
                    if (bitmap !== decoded) decoded.recycle()
                    try {
                        val out = ByteArrayOutputStream()
                        var quality = 85
                        do {
                            out.reset()
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out))
                            quality -= 15
                        } while (out.size() > 700_000 && quality >= 40)
                        require(out.size() <= 700_000) { "Choose a smaller receipt image" }
                        out.toByteArray()
                    } finally {
                        bitmap.recycle()
                        raw.fill(0)
                    }
                }
            try {
                validate(mime, payload)
                val name =
                    runCatching {
                            context.contentResolver
                                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        }
                        .getOrNull()
                        ?.replace(Regex("[\\p{Cc}/\\\\]"), " ")
                        ?.take(100)
                        ?.takeIf { it.isNotBlank() } ?: if (pdf) "Receipt.pdf" else "Receipt.jpg"
                val hash = digest(payload)
                val id = UUID.randomUUID().toString()
                val sealed = cipher.seal(id, payload)
                db.withTransaction {
                    val dao = db.extras()
                    val record = db.ledger().record(owner)
                    require(record == null || !record.deleted) {
                        "Restore the transaction before attaching a receipt"
                    }
                    require(dao.allReceiptCount() < 10000) { "Supported receipt limit reached" }
                    if (record == null) {
                        UUID.fromString(owner)
                        require(dao.draftCount() < 100) {
                            "Discard unused receipt drafts from Settings first"
                        }
                        require(dao.draftRows(owner).size < 5) {
                            "Up to 5 receipts per transaction"
                        }
                        require(dao.duplicateDraft(owner, hash) == 0) {
                            "This receipt is already in this draft"
                        }
                        dao.addDraft(
                            ReceiptDraft(
                                id,
                                owner,
                                name,
                                mime,
                                hash,
                                sealed,
                                System.currentTimeMillis(),
                            )
                        )
                    } else {
                        require(dao.receiptCount(owner) < 5) {
                            "Up to 5 receipts per transaction, including deleted history"
                        }
                        require(dao.duplicateReceipt(owner, hash) == 0) {
                            "This receipt is already attached; restore it if deleted"
                        }
                        dao.addReceipt(
                            ReceiptAttachment(
                                id,
                                owner,
                                name,
                                mime,
                                hash,
                                sealed,
                                System.currentTimeMillis(),
                            )
                        )
                    }
                }
                id
            } finally {
                payload.fill(0)
            }
        }

    suspend fun bytes(id: String): Pair<String, ByteArray> =
        withContext(Dispatchers.IO) {
            val saved = db.extras().receipt(id)
            val draft = if (saved == null) db.extras().draft(id) else null
            require(saved != null || draft != null) { "Receipt no longer exists" }
            val mime = saved?.mime ?: draft!!.mime
            val bytes = cipher.open(id, saved?.secret ?: draft!!.secret)
            validate(mime, bytes)
            mime to bytes
        }

    suspend fun delete(id: String, revision: Long, deleted: Boolean) =
        withContext(Dispatchers.IO) {
            require(db.extras().receiptDeleted(id, revision, deleted) == 1) {
                "Receipt changed; reopen it"
            }
        }

    suspend fun external(id: String): Pair<Uri, String> =
        withContext(Dispatchers.IO) {
            val (mime, bytes) = bytes(id)
            try {
                val dir = File(context.cacheDir, "receipt-view")
                check(dir.isDirectory || dir.mkdirs())
                val file =
                    File(dir, "${UUID.randomUUID()}.${if(mime=="application/pdf")"pdf"else"jpg"}")
                file.outputStream().use { it.write(bytes) }
                FileProvider.getUriForFile(context, context.packageName + ".capture", file) to mime
            } finally {
                bytes.fill(0)
            }
        }

    suspend fun clearCache() =
        withContext(Dispatchers.IO) {
            File(context.cacheDir, "receipt-view").listFiles().orEmpty().forEach { file ->
                runCatching {
                    val uri =
                        FileProvider.getUriForFile(context, context.packageName + ".capture", file)
                    context.revokeUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                check(file.delete() || !file.exists()) {
                    "Some opened receipt files could not be removed"
                }
            }
        }
}
