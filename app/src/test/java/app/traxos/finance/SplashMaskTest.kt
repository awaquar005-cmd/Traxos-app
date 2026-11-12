package app.traxos.finance

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashMaskTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private fun render(id: Int, size: Int, mask: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (mask)
            canvas.clipPath(
                Path().apply { addCircle(size / 2f, size / 2f, size / 3f, Path.Direction.CW) }
            )
        val drawable = ContextCompat.getDrawable(context, id)!!
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun pixels(bitmap: Bitmap) =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    private fun components(bitmap: Bitmap): List<Pair<Int, Int>> {
        val points = pixels(bitmap)
        val seen = BooleanArray(points.size)
        val result = mutableListOf<Pair<Int, Int>>()
        for (start in points.indices) {
            if (seen[start] || Color.alpha(points[start]) < 128) continue
            val queue = java.util.ArrayDeque<Int>()
            queue.add(start)
            seen[start] = true
            var x0 = bitmap.width
            var x1 = 0
            var y0 = bitmap.height
            var y1 = 0
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                val x = p % bitmap.width
                val y = p / bitmap.width
                x0 = minOf(x0, x)
                x1 = maxOf(x1, x)
                y0 = minOf(y0, y)
                y1 = maxOf(y1, y)
                for (next in
                    listOf(
                        if (x > 0) p - 1 else -1,
                        if (x < bitmap.width - 1) p + 1 else -1,
                        if (y > 0) p - bitmap.width else -1,
                        if (y < bitmap.height - 1) p + bitmap.width else -1,
                    )) {
                    if (next >= 0 && !seen[next] && Color.alpha(points[next]) >= 128) {
                        seen[next] = true
                        queue.add(next)
                    }
                }
            }
            result += (x1 - x0 + 1) to (y1 - y0 + 1)
        }
        return result
    }

    @Test
    fun fiveUnboxedSquaresFitAndroidSplashMaskAtMaximumAnimationScale() {
        val full = render(R.drawable.traxos_splash_icon, 288)
        val masked = render(R.drawable.traxos_splash_icon, 288, true)
        assertArrayEquals(
            "Android's 192dp circular safe area must not remove any logo pixel",
            pixels(full),
            pixels(masked),
        )
        val squares = components(masked)
        assertEquals(5, squares.size)
        assertTrue(squares.all { kotlin.math.abs(it.first - it.second) <= 1 && it.first in 23..27 })
        assertEquals(0, Color.alpha(full.getPixel(0, 0)))
        val legacy = render(R.drawable.traxos_splash_mark, 64)
        assertEquals(5, components(legacy).size)
        val parser = context.resources.getXml(R.drawable.traxos_splash_animated)
        parser.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) {}
            assertEquals(
                R.drawable.traxos_splash_icon,
                it.getAttributeResourceValue(
                    "http://schemas.android.com/apk/res/android",
                    "drawable",
                    0,
                ),
            )
        }
        val preview = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
        Canvas(preview).apply {
            drawColor(Color.rgb(17, 30, 55))
            drawBitmap(masked, 0f, 0f, Paint())
        }
        val file = File("build/reports/offline-tools/splash-mask-simulation.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        full.recycle()
        masked.recycle()
        legacy.recycle()
        preview.recycle()
    }
}
