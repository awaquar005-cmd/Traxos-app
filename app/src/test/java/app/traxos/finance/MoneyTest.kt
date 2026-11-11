package app.traxos.finance

import app.traxos.finance.domain.Money
import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test
    fun exactPaise() {
        assertEquals(1001L, Money.parse("10.01", "INR"))
        assertEquals(10L, Money.parse("0.10", "INR"))
    }

    @Test
    fun scalesFollowCurrency() {
        assertEquals(1250L, Money.parse("1.250", "KWD"))
        assertEquals(42L, Money.parse("42", "JPY"))
    }

    @Test
    fun rejectsRounding() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1.001", "INR") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1.5", "JPY") }
    }

    @Test
    fun rejectsInvalidInputs() {
        listOf("0", "-1", "NaN", "1e3", "1,000", "", " 10 ").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Money.parse(value, "INR") }
        }
    }

    @Test
    fun rejectsOverflow() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.parse("9223372036854775808", "INR")
        }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1000000000", "INR") }
    }

    @Test
    fun zeroOpeningIsExplicit() {
        assertEquals(0L, Money.parse("0", "INR", true))
    }

    @Test
    fun ledgerSigns() {
        assertEquals(-100L, Money.effect("EXPENSE", 100))
        assertEquals(100L, Money.effect("INCOME", 100))
        assertThrows(IllegalStateException::class.java) { Money.effect("TRANSFER", 100) }
    }

    @Test
    fun editableRoundtrip() {
        Money.currencies.forEach { c ->
            listOf(1L, 100L, Money.MAX_MINOR).forEach { amount ->
                assertEquals(amount, Money.parse(Money.editable(amount, c), c))
            }
        }
    }
}
