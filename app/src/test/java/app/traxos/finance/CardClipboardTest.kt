package app.traxos.finance

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.ui.copyCardDetail
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CardClipboardTest {
    @Test
    fun sensitiveCopyClearsOnlyItsOwnClipAfterTimeout() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        assertTrue(copyCardDetail(app, "SYNTHETIC-CARD-REFERENCE"))
        assertEquals(
            "SYNTHETIC-CARD-REFERENCE",
            clipboard.primaryClip!!.getItemAt(0).text.toString(),
        )
        assertTrue(
            clipboard.primaryClipDescription!!
                .extras!!
                .getBoolean("android.content.extra.IS_SENSITIVE")
        )
        ShadowLooper.idleMainLooper(61, TimeUnit.SECONDS)
        assertFalse(clipboard.hasPrimaryClip())
    }

    @Test
    fun laterClipboardContentsAreNeverRemovedByOldTimer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        copyCardDetail(app, "SYNTHETIC")
        clipboard.setPrimaryClip(ClipData.newPlainText("Another app", "Keep this"))
        ShadowLooper.idleMainLooper(61, TimeUnit.SECONDS)
        assertEquals("Keep this", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }
}
