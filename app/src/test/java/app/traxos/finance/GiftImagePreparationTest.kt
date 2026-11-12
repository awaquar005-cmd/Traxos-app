package app.traxos.finance

import android.app.Application
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import app.traxos.finance.gifts.BarcodeImageImport
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
        assertEquals(1, BarcodeImageImport.sampleSize(1080, 1920))
        assertEquals(1, BarcodeImageImport.sampleSize(1080, 5000))
        val sample = BarcodeImageImport.sampleSize(8000, 6000)
        assertTrue(8000L / sample * (6000L / sample) <= BarcodeImageImport.MAX_PIXELS)
    }

    @Test
    fun exifRotationAndCropKeepCorrectDimensions() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val rotated = BarcodeImageImport.orient(source, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(200, rotated.width)
        assertEquals(400, rotated.height)
        val cropped = BarcodeImageImport.crop(rotated, 0f, .25f, 1f, .75f)
        assertEquals(200, cropped.width)
        assertEquals(200, cropped.height)
        assertThrows(IllegalArgumentException::class.java) {
            BarcodeImageImport.crop(source, .9f, 0f, .1f, 1f)
        }
        cropped.recycle()
        rotated.recycle()
        source.recycle()
    }
}
