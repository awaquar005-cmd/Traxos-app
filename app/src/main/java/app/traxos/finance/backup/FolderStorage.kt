package app.traxos.finance.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.UUID

/** Grant and test the actual returned SAF capability before replacing a saved destination. */
class FolderStorage(
    private val resolver: ContentResolver,
    private val persist: (Uri, Int) -> Unit = { uri, flags ->
        resolver.takePersistableUriPermission(uri, flags)
    },
) {
    data class Selection(val uri: Uri, val name: String)

    fun select(uri: Uri, returnedFlags: Int): Selection {
        require(uri.scheme == "content") { "Choose a folder from Android's folder picker" }
        val permissions =
            returnedFlags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        require(
            permissions and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 &&
                permissions and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
        ) {
            "This folder is read-only. Choose a writable folder such as Documents/TRAXOS."
        }
        persist(uri, permissions)
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
        val name =
            resolver
                .query(
                    parent,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_FLAGS,
                    ),
                    android.os.Bundle(),
                    null,
                )
                ?.use { cursor ->
                    require(cursor.moveToFirst()) { "Folder is unavailable" }
                    require(
                        cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE !=
                            0
                    ) {
                        "This provider cannot create files in this folder"
                    }
                    cursor.getString(0)
                } ?: throw IOException("Folder provider is unavailable")
        var probe: Uri? = null
        try {
            probe =
                DocumentsContract.createDocument(
                    resolver,
                    parent,
                    "application/octet-stream",
                    ".traxos-check-${UUID.randomUUID()}.tmp",
                ) ?: throw IOException("Could not create a file in this folder")
            val bytes = "TRAXOS folder access check".toByteArray(Charsets.UTF_8)
            requireNotNull(resolver.openOutputStream(probe, "w")).use { it.write(bytes) }
            requireNotNull(resolver.openInputStream(probe)).use { input ->
                val returned = ByteArray(bytes.size + 1)
                var count = 0
                while (count < returned.size) {
                    val n = input.read(returned, count, returned.size - count)
                    if (n < 0) break
                    count += n
                }
                require(count == bytes.size && returned.copyOf(count).contentEquals(bytes)) {
                    "Folder could not read back the test file"
                }
            }
            require(DocumentsContract.deleteDocument(resolver, probe)) {
                "This provider cannot remove incomplete files. Choose another folder."
            }
            probe = null
            return Selection(uri, name)
        } finally {
            probe?.let {
                try {
                    DocumentsContract.deleteDocument(resolver, it)
                } catch (_: Exception) {
                    /* Non-sensitive probe only; selection remains unverified. */
                }
            }
        }
    }

    companion object {
        fun picker(initial: Uri? = null) =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
                if (initial != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
    }
}
