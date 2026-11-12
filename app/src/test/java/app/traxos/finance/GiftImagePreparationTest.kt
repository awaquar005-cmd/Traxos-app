package app.traxos.finance

import android.app.Application
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import app.traxos.finance.gifts.GiftImageImport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GiftImagePreparationTest {
    @Test
    fun screenshotsPreserveSmallTextAndCameraAllocationsAreBounded() {
        assertEquals(1, GiftImageImport.sampleSize(1080, 1920))
        assertEquals(1, GiftImageImport.sampleSize(1080, 5000))
        val sample = GiftImageImport.sampleSize(8000, 6000)
        assertTrue(8000L / sample * (6000L / sample) <= GiftImageImport.MAX_PIXELS)
    }

    @Test
    fun exifRotationAndCropKeepCorrectDimensions() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val rotated = GiftImageImport.orient(source, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(200, rotated.width)
        assertEquals(400, rotated.height)
        val cropped = GiftImageImport.crop(rotated, 0f, .25f, 1f, .75f)
        assertEquals(200, cropped.width)
        assertEquals(200, cropped.height)
        assertThrows(IllegalArgumentException::class.java) {
            GiftImageImport.crop(source, .9f, 0f, .1f, 1f)
        }
        cropped.recycle()
        rotated.recycle()
        source.recycle()
    }

    @Test
    fun labelAndValueColumnsAreJoinedByPositionNotBlockOrder() {
        val text =
            app.traxos.finance.gifts.OcrReadingOrder.text(
                listOf(
                    app.traxos.finance.gifts.OcrReadingOrder.Line("PIN:", 0, 60, 60, 80),
                    app.traxos.finance.gifts.OcrReadingOrder.Line("TEST-1234", 150, 20, 300, 40),
                    app.traxos.finance.gifts.OcrReadingOrder.Line(
                        "Gift card code:",
                        0,
                        20,
                        140,
                        40,
                    ),
                    app.traxos.finance.gifts.OcrReadingOrder.Line("9876", 150, 60, 240, 80),
                )
            )
        assertEquals("Gift card code: TEST-1234\nPIN: 9876", text)
        val result = app.traxos.finance.gifts.GiftParser.parse("Amazon Gift Card\n$text")
        assertEquals("TEST-1234", result.code)
        assertEquals("9876", result.pin)
    }
}
