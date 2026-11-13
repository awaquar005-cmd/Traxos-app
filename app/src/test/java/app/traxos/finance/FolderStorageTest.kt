package app.traxos.finance

import android.app.Application
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.FolderStorage
import java.io.File
import java.io.FileNotFoundException
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

private class FixtureFolders : DocumentsProvider() {
    var creates = 0
    var deletes = 0
    var writable = true
    var failWrite = false
    var corrupt = false
    lateinit var storage: File

    override fun onCreate(): Boolean {
        storage = File(context!!.cacheDir, "folder-provider-fixture")
        storage.mkdirs()
        return true
    }

    override fun queryRoots(projection: Array<out String>?) =
        MatrixCursor(arrayOf("root_id", "document_id", "title", "flags")).apply {
            addRow(arrayOf<Any?>("root", "root", "Fixture", 0))
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?) =
        MatrixCursor(projection ?: arrayOf("document_id", "_display_name", "mime_type", "flags"))
            .apply {
                addRow(
                    columnNames
                        .map {
                            when (it) {
                                "document_id" -> documentId
                                "_display_name" -> "Fixture folder"
                                "mime_type" -> DocumentsContract.Document.MIME_TYPE_DIR
                                "flags" ->
                                    if (writable)
                                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                                    else 0
                                else -> null
                            }
                        }
                        .toTypedArray<Any?>()
                )
            }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ) = MatrixCursor(arrayOf("document_id", "_display_name"))

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        parentDocumentId == "root"

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        creates++
        return "probe"
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (failWrite && mode.contains('w')) throw FileNotFoundException("Fixture write denied")
        val file = File(storage, "probe.bin")
        if (mode == "r" && corrupt) file.writeText("different")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun deleteDocument(documentId: String) {
        deletes++
        File(storage, "probe.bin").delete()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FolderStorageTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var provider: FixtureFolders
    private val uri = DocumentsContract.buildTreeDocumentUri("fixture.folders", "root")
    private val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Before
    fun setup() {
        provider = FixtureFolders()
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = "fixture.folders"
                name = FixtureFolders::class.java.name
                exported = true
                grantUriPermissions = true
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = "android.permission.MANAGE_DOCUMENTS"
            },
        )
        ShadowContentResolver.registerProviderInternal("fixture.folders", provider)
    }

    @After
    fun cleanup() {
        if (::provider.isInitialized) runCatching { provider.storage.deleteRecursively() }
    }

    @Test
    fun selectionActuallyCreatesReadsAndRemovesProbeBeforeReturning() {
        var grant = 0
        val storage = FolderStorage(context.contentResolver) { _, flags -> grant = flags }
        val selected = storage.select(uri, rw or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(rw, grant)
        assertEquals("Fixture folder", selected.name)
        assertEquals(uri, selected.uri)
        assertEquals(1, provider.creates)
        assertEquals(1, provider.deletes)
        assertFalse(File(provider.storage, "probe.bin").exists())
    }

    @Test
    fun readOnlyPermissionOrProviderIsRejectedWithoutClaimingFolderSelection() {
        val storage = FolderStorage(context.contentResolver) { _, _ -> }
        assertThrows(IllegalArgumentException::class.java) {
            storage.select(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        provider.writable = false
        assertThrows(IllegalArgumentException::class.java) { storage.select(uri, rw) }
        assertEquals(0, provider.creates)
    }

    @Test
    fun failedWriteAndIncorrectReadBackAreNotReportedAsSuccess() {
        val storage = FolderStorage(context.contentResolver) { _, _ -> }
        provider.failWrite = true
        assertThrows(Exception::class.java) { storage.select(uri, rw) }
        provider.failWrite = false
        provider.corrupt = true
        assertThrows(IllegalArgumentException::class.java) { storage.select(uri, rw) }
        assertTrue(provider.deletes >= 2)
    }

    @Test
    fun pickerRequestsTheActualPersistableReadWriteTreeGrant() {
        val intent = FolderStorage.picker(uri)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertEquals(rw, intent.flags and rw)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
    }
}
