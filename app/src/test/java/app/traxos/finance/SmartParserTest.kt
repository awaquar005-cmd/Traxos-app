package app.traxos.finance

import app.traxos.finance.smart.*
import org.junit.Assert.*
import org.junit.Test

class SmartParserTest {
    @Test
    fun directionAndAvailableBalanceAreDistinct() {
        val d =
            SmartParser.parse(
                "Bank account credited INR 1500 salary from Employer on 2030-01-01. Avl balance INR 9000"
            )!!
        assertEquals("INCOME", d.type)
        assertEquals("1500", d.amount)
        assertEquals("INR", d.currency)
    }

    @Test
    fun voucherAndUnrelatedMessagesDoNotBecomeExpenses() {
        assertEquals("GIFT", SmartParser.parse("Myntra coupon code: DEMO1234")!!.kind)
        assertEquals("DISCOUNT", SmartParser.parse("Myntra coupon code: DEMO1234")!!.giftKind)
        assertNull(SmartParser.parse("Happy birthday, call 9876543210"))
    }

    @Test
    fun unlabelledNumbersAndUrlsDoNotBecomeMoney() {
        assertNull(SmartParser.parse("https://example.com/pay/1000 OTP 123456"))
        assertEquals(
            "",
            SmartParser.parse("Bank account debited; ref 123456789 on 2030-01-01")!!.amount,
        )
    }

    @Test
    fun smsPermissionRevocationOrMissingPolicyAlwaysStopsIngestion() {
        assertTrue(SmsAccess.allowed(true, true, true, true))
        assertFalse(SmsAccess.allowed(false, true, true, true))
        assertFalse(SmsAccess.allowed(true, false, true, true))
        assertFalse(SmsAccess.allowed(true, true, false, true))
        assertFalse(SmsAccess.allowed(true, true, true, false))
    }
}
