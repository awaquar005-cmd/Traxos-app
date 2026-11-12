package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.ui.DesignExperimentStore
import app.traxos.finance.ui.experimentCaption
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DesignExperimentTest {
    @Test
    fun comparisonChoicePersistsAndCaptionsKeepCurrencyAndBrandIdentity() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "experiment-${UUID.randomUUID()}"
        val store = DesignExperimentStore(context, name)
        assertTrue(store.enabled.value)
        store.setEnabled(false)
        assertFalse(store.enabled.value)
        assertFalse(DesignExperimentStore(context, name).enabled.value)
        store.setEnabled(true)
        assertTrue(DesignExperimentStore(context, name).enabled.value)
        assertEquals("Security & smart tracking", experimentCaption("SECURITY & SMART TRACKING"))
        assertEquals("Full V3 UI review", experimentCaption("FULL V3 UI REVIEW"))
        assertEquals("INR", experimentCaption("INR"))
        assertEquals("TRAXOS", experimentCaption("TRAXOS"))
        context.deleteSharedPreferences(name)
    }
}
