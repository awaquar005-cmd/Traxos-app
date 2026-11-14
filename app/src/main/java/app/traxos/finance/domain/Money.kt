package app.traxos.finance.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    val currencies = listOf("INR", "USD", "EUR", "JPY", "KWD")
    const val MAX_MINOR = 99_999_999_999L
    const val MAX_TRANSACTIONS = 1_000_000

    fun parse(text: String, currency: String, allowZero: Boolean = false): Long {
        require(currency in currencies) { "Choose a supported currency" }
        require(text.matches(Regex("[0-9]+(\\.[0-9]+)?"))) {
            "Enter an amount using digits and a decimal point"
        }
        val scale = Currency.getInstance(currency).defaultFractionDigits
        val minor =
            try {
                BigDecimal(text)
                    .setScale(scale, RoundingMode.UNNECESSARY)
                    .movePointRight(scale)
                    .longValueExact()
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("Amount has too many decimals or is too large")
            }
        require(minor in (if (allowZero) 0L else 1L)..MAX_MINOR) {
            "Amount is outside the supported range"
        }
        return minor
    }

    fun editable(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, Currency.getInstance(currency).defaultFractionDigits)
            .toPlainString()

    fun format(minor: Long, currency: String): String {
        val unit: Currency = Currency.getInstance(currency)
        return NumberFormat.getCurrencyInstance(Locale.getDefault())
            .apply {
                this.currency = unit
                minimumFractionDigits = unit.defaultFractionDigits
                maximumFractionDigits = unit.defaultFractionDigits
            }
            .format(BigDecimal.valueOf(minor, unit.defaultFractionDigits))
    }

    fun effect(type: String, amount: Long): Long {
        require(amount in 1..MAX_MINOR)
        return when (type) {
            "EXPENSE",
            "PAYMENT_OUT",
            "TRANSFER_OUT",
            "DEBT_OUT" -> -amount
            "INCOME",
            "REFUND",
            "PAYMENT_IN",
            "TRANSFER_IN",
            "DEBT_IN" -> amount
            else -> error("Unsupported ledger type")
        }
    }
}
