package app.traxos.finance

import app.traxos.finance.gifts.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic, non-redeemable regression examples. Not a measurement of ML Kit image accuracy. */
class GiftContextParserTest {
    @Test
    fun unrelatedBalanceDoesNotChangeSelectedGiftValueMeaning() {
        val p =
            GiftParser.parse(
                "Amazon gift card\nGift card value INR 1000\nOrder balance due INR 20\nCode: DEMO1234"
            )
        assertEquals("1000", p.amount)
        assertFalse(p.amountIsBalance)
        assertTrue(
            GiftParser.parse("Gift card\nRemaining balance INR 700\nCode: DEMO1234").amountIsBalance
        )
    }

    @Test
    fun multilineLabelsCurrencyAndPinAreContextualAcrossMerchants() {
        for (merchant in listOf("Amazon", "Flipkart", "Myntra", "Custom store")) {
            val p =
                GiftParser.parse(
                    "Merchant: $merchant\nGift card\nGift Card Number:\nTEST-AB12-CD34\nPIN:\n9876\nGift card value\n₹1,250.50\nValid until 31 December 2030\nOrder ID: 123456789012\nPhone: 9876543210\nShipping ₹50"
                )
            assertEquals(merchant, p.merchant)
            assertEquals("TEST-AB12-CD34", p.code)
            assertEquals("9876", p.pin)
            assertEquals("1250.50", p.amount)
            assertEquals("INR", p.currency)
            assertEquals("2030-12-31", p.expiry)
        }
    }

    @Test
    fun wrappedCodeAndIndianGroupingNeverBecomeOrderAmounts() {
        val p =
            GiftParser.parse(
                "Amazon gift voucher\nClaim code:\nDEMO-AB\n1234-CD\nWorth INR 1,00,000.00\nOrder total INR 400\nExpiry 31/12/2030"
            )
        assertEquals("DEMO-AB1234-CD", p.code)
        assertEquals("100000.00", p.amount)
        assertEquals("2030-12-31", p.expiry)
    }

    @Test
    fun tiedCandidatesStayBlankAndRemainReviewable() {
        val p =
            GiftParser.parse(
                "Myntra gift card\nValue ₹1000\nValue ₹2000\nCode: DEMO1234\nCode: DEMO5678"
            )
        assertEquals("", p.amount)
        assertEquals("", p.code)
        assertTrue(p.candidates.count { it.field == "amount" } == 2)
        assertTrue(p.warnings.any { it.contains("Several amount") })
        assertEquals("", p.expiry)
    }

    @Test
    fun ambiguousDatesLinksAndOtpAreNeverImported() {
        val p =
            GiftParser.parse(
                "Amazon gift card\nCode: https://example.invalid/1234\nOTP: 654321\nValid till 03/04/2030\nOrder ID: 1234567890"
            )
        assertEquals("", p.code)
        assertEquals("", p.pin)
        assertEquals("", p.expiry)
        assertEquals("", GiftParser.parse("OTP 123456 for login, gift card value INR 1000").amount)
        assertEquals("", GiftParser.parse("Hi, please call me on 9876543210").code)
    }

    @Test
    fun missingFieldsAndBadGroupingAreNotInvented() {
        val p = GiftParser.parse("Flipkart Gift Card")
        assertEquals("Flipkart", p.merchant)
        assertEquals("", p.code)
        assertEquals("", p.amount)
        assertEquals("", p.pin)
        assertEquals("", GiftParser.parse("Gift card value INR 1,2,3").amount)
    }
}
