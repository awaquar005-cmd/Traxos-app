package app.traxos.finance.entry

import app.traxos.finance.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class TrendFilters(
    val from: String = LocalDate.now().withDayOfMonth(1).toString(),
    val through: String = LocalDate.now().toString(),
    val account: String = "",
    val category: String = "",
    val merchant: String = "",
    val currency: String = "",
    val minimum: String = "",
    val maximum: String = "",
    val kind: String = "ALL",
    val origin: String = "ALL",
    val receipt: String = "ALL",
    val split: String = "ALL",
    val comparison: String = "PERIOD",
) {
    val start
        get() = LocalDate.parse(from).toEpochDay()

    val end
        get() = LocalDate.parse(through).toEpochDay()

    val minMinor
        get() = if (minimum.isBlank()) 0 else Money.parse(minimum, currency, true)

    val maxMinor
        get() = if (maximum.isBlank()) Money.MAX_MINOR else Money.parse(maximum, currency, true)

    fun previous(): LongRange {
        val a = LocalDate.parse(from)
        val b = LocalDate.parse(through)
        return when (comparison) {
            "MONTH" -> a.minusMonths(1).toEpochDay()..b.minusMonths(1).toEpochDay()
            "YEAR" -> a.minusYears(1).toEpochDay()..b.minusYears(1).toEpochDay()
            else -> (start - (end - start + 1))..(start - 1)
        }
    }

    fun validate(): TrendFilters {
        require(start <= end && end - start < 366) { "Choose an inclusive period of 1–366 days" }
        require(
            start >= LocalDate.of(1900, 1, 1).toEpochDay() &&
                end <= LocalDate.of(2200, 12, 31).toEpochDay()
        )
        require(currency.isBlank() || currency in Money.currencies)
        require((minimum.isBlank() && maximum.isBlank()) || currency.isNotBlank()) {
            "Choose one currency before using amount bounds"
        }
        require(minMinor <= maxMinor)
        require(
            kind in listOf("ALL", "EXPENSE", "INCOME") &&
                origin in listOf("ALL", "LEDGER", "GROUP") &&
                receipt in listOf("ALL", "HAS", "NONE") &&
                split in listOf("ALL", "SPLIT", "SINGLE") &&
                comparison in listOf("PERIOD", "MONTH", "YEAR")
        )
        return this
    }

    fun saved() =
        arrayListOf(
            from,
            through,
            account,
            category,
            merchant,
            currency,
            minimum,
            maximum,
            kind,
            origin,
            receipt,
            split,
            comparison,
        )

    companion object {
        fun read(v: List<String>) =
            if (v.size == 13)
                TrendFilters(
                        v[0],
                        v[1],
                        v[2],
                        v[3],
                        v[4],
                        v[5],
                        v[6],
                        v[7],
                        v[8],
                        v[9],
                        v[10],
                        v[11],
                        v[12],
                    )
                    .validate()
            else TrendFilters()

        fun change(current: Long, previous: Long): String? =
            if (previous <= 0) null
            else
                BigDecimal.valueOf(current - previous)
                    .multiply(BigDecimal(100))
                    .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_EVEN)
                    .toPlainString() + "%"
    }
}
