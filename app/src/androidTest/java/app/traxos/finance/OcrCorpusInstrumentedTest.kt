package app.traxos.finance

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.traxos.finance.gifts.GiftImageImport
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone only. Fixture labels must contain test/redacted values; mismatch logs never include
 * secrets.
 */
@RunWith(AndroidJUnit4::class)
class OcrCorpusInstrumentedTest {
    @Test
    fun measureRedactedImageCorpusOffline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val fixtures = assets.list("local-ocr-fixtures").orEmpty().filter { it.endsWith(".json") }
        assumeTrue(
            "No real redacted image corpus supplied; native OCR accuracy is unmeasured",
            fixtures.isNotEmpty(),
        )
        val context = instrumentation.targetContext
        var fields = 0
        var matches = 0
        for (name in fixtures) {
            val label =
                JSONObject(
                    assets.open("local-ocr-fixtures/$name").bufferedReader().use { it.readText() }
                )
            val folder = File(context.cacheDir, "gift-capture").apply { mkdirs() }
            val file = File(folder, "qa-${System.nanoTime()}.image")
            try {
                assets.open("local-ocr-fixtures/${label.getString("image")}").use { source ->
                    file.outputStream().use { source.copyTo(it) }
                }
                val bitmap =
                    GiftImageImport.load(
                        context,
                        FileProvider.getUriForFile(context, "${context.packageName}.capture", file),
                    )
                val result =
                    try {
                        GiftImageImport.detect(bitmap, true).extraction
                    } finally {
                        bitmap.recycle()
                    }
                val actual =
                    mapOf(
                        "merchant" to result.merchant,
                        "amount" to result.amount,
                        "currency" to result.currency,
                        "code" to result.code,
                        "pin" to result.pin,
                        "expiry" to result.expiry,
                    )
                val expected = label.getJSONObject("expected")
                for (field in actual.keys) if (expected.has(field)) {
                    fields++
                    if (expected.getString(field) == actual[field]) matches++
                }
            } finally {
                file.delete()
            }
        }
        val metrics =
            JSONObject()
                .put("images", fixtures.size)
                .put("fields", fields)
                .put("matched", matches)
                .put("offlineModel", "bundled ML Kit Latin")
        File(context.filesDir, "ocr-corpus-metrics.json").writeText(metrics.toString(2))
        assertTrue(
            "OCR corpus field mismatches; inspect redacted fixtures locally. Counts are in ocr-corpus-metrics.json",
            fields > 0 && matches == fields,
        )
    }
}
