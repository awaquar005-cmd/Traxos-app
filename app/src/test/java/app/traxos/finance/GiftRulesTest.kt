package app.traxos.finance

import app.traxos.finance.gifts.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GiftRulesTest {
    private val today = LocalDate.now().toEpochDay()

    private fun card() =
        GiftCard(
            "id",
            "Merchant",
            "Gift",
            "STORED_VALUE",
            byteArrayOf(),
            "hash",
            100000,
            100000,
            "INR",
            today,
            null,
            partialAllowed = true,
            createdAt = 1,
            source = "RECEIVED",
        )

    @Test
    fun exactPartialAndFullRedemption() {
        assertEquals(70000L, GiftRules.redeem(card(), 30000))
        assertEquals(0L, GiftRules.redeem(card(), 100000))
    }

    @Test
    fun neverAssumesPartialRedemptionSupport() {
        assertThrows(IllegalArgumentException::class.java) {
            GiftRules.redeem(card().copy(partialAllowed = false), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftRules.redeem(card().copy(kind = "SINGLE_USE"), 1)
        }
        assertEquals(0L, GiftRules.redeem(card().copy(kind = "SINGLE_USE"), 100000))
    }

    @Test
    fun unknownBalanceAndExpiredCardsCannotBeSpent() {
        assertThrows(IllegalArgumentException::class.java) {
            GiftRules.redeem(card().copy(remainingMinor = null), 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftRules.redeem(card().copy(expiryDay = today - 1), 100)
        }
        assertThrows(IllegalArgumentException::class.java) { GiftRules.redeem(card(), 100001) }
        assertThrows(IllegalArgumentException::class.java) { GiftRules.redeem(card(), 0) }
        assertThrows(IllegalArgumentException::class.java) { GiftRules.redeem(card(), -1) }
    }

    @Test
    fun discountVoucherHasNoCashValue() {
        assertNull(
            GiftRules.redeem(
                card().copy(kind = "DISCOUNT", remainingMinor = null, originalMinor = null),
                null,
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            GiftRules.redeem(card().copy(kind = "DISCOUNT"), 100)
        }
    }

    @Test
    fun statusesAndExpiryBoundaries() {
        assertEquals("ACTIVE", GiftRules.state(card(), today))
        assertEquals("EXPIRING", GiftRules.state(card().copy(expiryDay = today), today))
        assertEquals("EXPIRED", GiftRules.state(card().copy(expiryDay = today - 1), today))
        assertEquals("REDEEMED", GiftRules.state(card().copy(remainingMinor = 0), today))
        assertEquals("ARCHIVED", GiftRules.state(card().copy(archived = true), today))
    }

    @Test
    fun codeNormalizationAndValidation() {
        assertEquals("ABC123", GiftRules.normalize(" ab-c 123 "))
        listOf("", "--", "xx", "a\nb").forEach {
            assertThrows(IllegalArgumentException::class.java) { GiftRules.validateCode(it) }
        }
        GiftRules.validateCode("CODE-1234")
    }

    @Test
    fun amountPrecisionAndBlankUnknown() {
        assertNull(GiftRules.amount("", "INR"))
        assertEquals(30000L, GiftRules.amount("300.00", "INR"))
        assertThrows(IllegalArgumentException::class.java) { GiftRules.amount("1.001", "INR") }
    }

    @Test
    fun reminderIntervalsValidated() {
        assertEquals(listOf(30, 7, 1, 0), GiftRules.intervals("0, 1,7,30"))
        listOf("", "1,1", "-1", "366", "tomorrow").forEach {
            assertThrows(IllegalArgumentException::class.java) { GiftRules.intervals(it) }
        }
    }

    @Test
    fun conservativeTextParserKeepsUncertaintyBlank() {
        val p =
            GiftParser.parse(
                "Amazon gift card code: ABCD-1234\nValue INR 1000.00\nExpiry: 2027-12-31"
            )
        assertEquals("Amazon", p.merchant)
        assertEquals("ABCD-1234", p.code)
        assertEquals("1000.00", p.amount)
        assertEquals("INR", p.currency)
        assertEquals("2027-12-31", p.expiry)
        val uncertain = GiftParser.parse("Your reward: 12345. Worth $50. Valid until 03/04/27")
        assertEquals("", uncertain.code)
        assertEquals("", uncertain.currency)
        assertEquals("", uncertain.expiry)
        assertEquals("", GiftParser.parse("Expiry: 2027-02-30").expiry)
        assertEquals("", GiftParser.parse("Code: https://example.com").code)
        assertTrue(GiftParser.parse("Balance INR 300").amountIsBalance)
        assertEquals("ABC123", GiftParser.parse("Gift card code is ABC123").code)
        assertEquals("100000", GiftParser.parse("Value INR 1,00,000").amount)
    }

    @Test
    fun privateReminderPlanHandlesEditsNoExpiryAndLongAbsence() {
        val c = card().copy(expiryDay = today + 30)
        assertEquals(4, GiftReminderPlan.entries(c, GiftSettings(), today).size)
        assertTrue(GiftReminderPlan.eligible(c, GiftSettings(), today + 30, 30, today))
        assertFalse(
            GiftReminderPlan.eligible(
                c.copy(expiryDay = today + 31),
                GiftSettings(),
                today + 30,
                30,
                today,
            )
        )
        assertTrue(GiftReminderPlan.entries(card(), GiftSettings(), today).isEmpty())
        assertTrue(
            GiftReminderPlan.entries(c.copy(redeemed = true), GiftSettings(), today).isEmpty()
        )
        assertTrue(
            GiftReminderPlan.entries(c.copy(archived = true), GiftSettings(), today).isEmpty()
        )
        assertTrue(GiftReminderPlan.entries(c, GiftSettings(reminders = false), today).isEmpty())
        val overdue =
            GiftReminderPlan.entries(c.copy(expiryDay = today - 30), GiftSettings(), today)
        assertEquals(listOf(0), overdue.map { it.offset })
        assertEquals(today, overdue.single().day)
    }
}
