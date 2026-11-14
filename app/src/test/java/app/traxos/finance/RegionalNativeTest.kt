package app.traxos.finance

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.regional.*
import java.io.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RegionalNativeTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private fun id() = UUID.randomUUID().toString()

    @After fun cleanup() = runBlocking { ProfilePhoto(context).remove() }

    @Test
    fun profilePhotoIsBoundedStrippedAndDurableAndRejectsBadReplacement() = runBlocking {
        val photo = ProfilePhoto(context)
        val image =
            Bitmap.createBitmap(1800, 1200, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
        val bytes =
            ByteArrayOutputStream()
                .also { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                .toByteArray()
        image.recycle()
        val uri = Uri.parse("content://fixture/photo")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(bytes))
        photo.import(uri)
        val loaded = ProfilePhoto(context).bitmap()!!
        assertTrue(loaded.width <= 512)
        assertEquals(loaded.width, loaded.height)
        assertTrue(Color.blue(loaded.getPixel(10, 10)) > 240)
        assertTrue(Color.red(loaded.getPixel(10, 10)) < 10)
        loaded.recycle()
        val saved = File(context.filesDir, "local-profile.png").readBytes()
        val bad = Uri.parse("content://fixture/bad")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(bad, ByteArrayInputStream("not an image".toByteArray()))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { photo.import(bad) } }
        assertArrayEquals(saved, File(context.filesDir, "local-profile.png").readBytes())
        photo.rotate()
        assertNotNull(photo.bitmap())
        photo.remove()
        assertNull(photo.bitmap())
    }

    @Test
    fun oversizedAndExternalImageInputsCannotReplaceExistingPhoto() = runBlocking {
        val p = ProfilePhoto(context)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { p.import(Uri.parse("https://example.com/private.png")) }
        }
        val uri = Uri.parse("content://fixture/huge")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(ByteArray(12 * 1024 * 1024 + 1)))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.import(uri) } }
        assertNull(p.bitmap())
    }

    @Test
    fun launcherAliasesKeepExactlyOneBrandedEntryAndActivityReachable() {
        val official = ComponentName(context, context.packageName + ".OfficialLauncher")
        val finance = ComponentName(context, context.packageName + ".FinanceLauncher")
        LauncherNames.select(context, "FINANCE")
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            context.packageManager.getComponentEnabledSetting(finance),
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            context.packageManager.getComponentEnabledSetting(official),
        )
        LauncherNames.select(context, "TRAXOS")
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            context.packageManager.getComponentEnabledSetting(official),
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            context.packageManager.getComponentEnabledSetting(finance),
        )
        assertNotNull(
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            LauncherNames.select(context, "Other")
        }
    }

    @Test
    fun reportLayoutPaginatesAndRendersUsingNativeCanvasWithoutChangingLedger() = runBlocking {
        val name = "pdf-${id()}.db"
        val db = TraxosDatabase.open(context, name)
        val path = File(context.cacheDir, "fixture.pdf")
        try {
            val today = LocalDate.now().toEpochDay()
            val ledger = LedgerRepository(db)
            ledger.saveAccount(
                id(),
                "bank",
                "Fixture bank",
                "BANK",
                "INR",
                10000000,
                openingDate = today - 1,
            )
            repeat(40) {
                ledger.saveRecord(
                    id(),
                    id(),
                    "bank",
                    "food",
                    "EXPENSE",
                    1000,
                    today,
                    "Fixture entry $it · " + "readable note ".repeat(20),
                )
            }
            val pages = mutableListOf<Bitmap>()
            var written = false
            var closed = false
            val backend =
                object : PdfPages {
                    override fun startPage(number: Int): Canvas {
                        assertEquals(pages.size + 1, number)
                        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        pages += bitmap
                        return Canvas(bitmap)
                    }

                    override fun finishPage() {}

                    override fun writeTo(output: OutputStream) {
                        written = true
                    }

                    override fun close() {
                        closed = true
                    }
                }
            // Robolectric4.14 does not implement PdfDocument JNI. Test the actual report
            // pagination/drawing on native Canvas; device-native PDF serialization is a phone gate.
            ReadableExports(db, TestGiftCipher(), pdfFactory = { backend })
                .pdf(ByteArrayOutputStream(), ExportScope(), DisplayOptions(locale = "en-IN"))
            assertTrue(written)
            assertTrue(closed)
            assertTrue(pages.size > 1)
            val first = pages.first()
            var marked = false
            for (y in 20 until 200) for (x in 20 until 560) if (first.getPixel(x, y) != Color.WHITE)
                marked = true
            assertTrue(marked)
            val preview =
                File("build/reports/regional/pdf-layout-page.png").apply { parentFile!!.mkdirs() }
            preview.outputStream().use { first.compress(Bitmap.CompressFormat.PNG, 100, it) }
            pages.forEach { it.recycle() }
            assertEquals(40L, db.ledger().recordCount())
        } finally {
            db.close()
            context.deleteDatabase(name)
            path.delete()
        }
    }
}
