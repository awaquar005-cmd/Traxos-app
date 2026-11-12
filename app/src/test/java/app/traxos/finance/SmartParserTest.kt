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
    fun smsPermissionRevocationOrPlayFlavorAlwaysStopsIngestion() {
        assertTrue(SmsAccess.allowed(true, true, true, true))
        assertFalse(SmsAccess.allowed(false, true, true, true))
        assertFalse(SmsAccess.allowed(true, false, true, true))
        assertFalse(SmsAccess.allowed(true, true, false, true))
        assertFalse(SmsAccess.allowed(true, true, true, false))
    }

    @Test
    fun cardHintsAndUnambiguousMessageDatesAreExtracted() {
        val d =
            SmartParser.parse(
                "Your credit card XX4821 spent INR 500 at TEST STORE on 31-Dec-2025 Ref TEST12345"
            )!!
        assertEquals("TRANSACTION", d.kind)
        assertTrue(d.cardRequired)
        assertEquals("4821", d.accountHint)
        assertEquals("2025-12-31", d.day)
        assertEquals("", SmartParser.parse("Bank a/c XX1234 debited INR 100 on 03/04/2025")!!.day)
        assertEquals(
            "2025-12-30",
            SmartParser.parse("Bank a/c XX1234 debited INR 100", "2025-12-30")!!.day,
        )
        assertNotNull(
            SmartParser.parse("UPI payment INR 100 to TEST STORE successful Ref TEST12345")
        )
        assertNull(SmartParser.parse("UPI payment of INR 100 is due tomorrow"))
    }

    @Test
    fun receivedMoneyIsIncomeAndFailedPaymentsCannotBePosted() {
        assertEquals(
            "INCOME",
            SmartParser.parse("Bank account received INR 500 on 2025-12-31")!!.type,
        )
        assertEquals("INCOME", SmartParser.parse("Bank account deposited INR 500")!!.type)
        assertEquals("EXPENSE", SmartParser.parse("Bank account deducted INR 500")!!.type)
        assertEquals(
            "REVIEW_ONLY",
            SmartParser.parse("UPI payment INR 500 failed; bank account debited pending reversal")!!
                .kind,
        )
        assertEquals(
            "REVIEW_ONLY",
            SmartParser.parse("UPI payment request received for INR 500")!!.kind,
        )
    }
}
