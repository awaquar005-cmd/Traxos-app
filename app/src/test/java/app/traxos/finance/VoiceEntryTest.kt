package app.traxos.finance

import app.traxos.finance.voice.*
import org.junit.Assert.*
import org.junit.Test

class VoiceEntryTest {
    private class Engine(override val available: Boolean = true) : OfflineSpeechEngine {
        var starts = 0
        var stops = 0
        var cancels = 0
        var result: ((String) -> Unit)? = null
        var error: ((String) -> Unit)? = null

        override fun start(result: (String) -> Unit, failure: (String) -> Unit) {
            starts++
            this.result = result
            error = failure
        }

        override fun stop() {
            stops++
        }

        override fun cancel() {
            cancels++
        }

        override fun destroy() {}
    }

    @Test
    fun localParserUsesExactCurrencyAndRejectsAmbiguousOrNonExpenseFlows() {
        assertEquals("150", VoiceParser.parse("Lunch 150 rupaye UPI se", "INR").amount)
        assertEquals("INCOME", VoiceParser.parse("Received salary 35000", "INR").type)
        assertEquals("INR", VoiceParser.parse("Paid 150 rupees", "USD").currency)
        assertEquals("USD", VoiceParser.parse("Spent USD 12.34 coffee", "INR").currency)
        assertEquals("1500.50", VoiceParser.parse("Paid 1,500.50 for food", "INR").amount)
        for (raw in
            listOf(
                "Lunch ten rupees",
                "Lunch 150 and taxi 200",
                "Transfer 500 to own bank",
                "Received refund 200",
                "OTP 123456",
                "Spent INR 100 USD 5",
                "Paid 1,23 for coffee",
                "Received loan 500",
                "Paid -150 coffee",
                "Payment failed 150",
                "Paid credit card 500",
                "Credit card payment 500",
                "Paid $20 coffee",
                "Paid 20 dollars",
            )) assertThrows(IllegalArgumentException::class.java) { VoiceParser.parse(raw, "INR") }
    }

    @Test
    fun deniedOrUnsupportedDevicesNeverStartSpeechService() {
        val engine = Engine()
        val vm = VoiceViewModel(engine)
        vm.start(false)
        assertEquals(0, engine.starts)
        assertEquals("EDIT", vm.state.value.phase)
        val unavailable = Engine(false)
        VoiceViewModel(unavailable).start(true)
        assertEquals(0, unavailable.starts)
    }

    @Test
    fun holdReleaseDoesNotSubmitAndReviewIsConsumedOnlyOnce() {
        val engine = Engine()
        val vm = VoiceViewModel(engine)
        vm.start(true)
        vm.start(true)
        assertEquals(1, engine.starts)
        vm.stop()
        vm.stop()
        assertEquals(1, engine.stops)
        assertNull(vm.takeDraft())
        engine.result!!("Lunch 150 cash")
        assertEquals("EDIT", vm.state.value.phase)
        assertNull(vm.takeDraft())
        vm.approve("INR")
        assertThrows(IllegalArgumentException::class.java) { vm.approve("INR") }
        assertEquals("150", vm.takeDraft()!!.amount)
        assertNull(vm.takeDraft())
    }

    @Test
    fun failedRecognitionPreservesTypedWordsForManualFallback() {
        val engine = Engine()
        val vm = VoiceViewModel(engine)
        vm.text("Paid 50 coffee")
        vm.start(true)
        engine.error!!("No speech recognized")
        assertEquals("Paid 50 coffee", vm.state.value.text)
        assertEquals("EDIT", vm.state.value.phase)
    }

    @Test
    fun leavingAppOrCancellingInvalidatesLateRecognizerCallbacks() {
        val engine = Engine()
        val vm = VoiceViewModel(engine)
        vm.start(true)
        val late = engine.result!!
        vm.interrupt()
        late("Received salary 9000")
        assertEquals("", vm.state.value.text)
        assertEquals("EDIT", vm.state.value.phase)
        vm.text("Paid 50 coffee")
        vm.dismiss()
        assertEquals("", vm.state.value.text)
        late("Paid 99 late")
        assertEquals("", vm.state.value.text)
    }
}
