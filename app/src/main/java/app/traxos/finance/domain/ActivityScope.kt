package app.traxos.finance.domain

import java.time.LocalDate

/** Inclusive accounting dates and primitive, backwards-compatible saved filters. */
data class ActivityScope(
    val from: String = "",
    val through: String = "",
    val accountId: String = "",
    val categoryId: String = "",
    val currency: String = "",
    val merchantId: String = "",
    val minimum: String = "",
    val maximum: String = "",
    val receipt: String = "ALL",
    val split: String = "ALL",
    val origin: String = "ALL",
) {
    val startDay
        get() = if (from.isBlank()) Long.MIN_VALUE else LocalDate.parse(from).toEpochDay()

    val endDay
        get() = if (through.isBlank()) Long.MAX_VALUE else LocalDate.parse(through).toEpochDay()

    val minimumMinor
        get() = if (minimum.isBlank()) 0 else Money.parse(minimum, currency, true)

    val maximumMinor
        get() = if (maximum.isBlank()) Money.MAX_MINOR else Money.parse(maximum, currency, true)

    val active
        get() =
            listOf(from, through, accountId, categoryId, currency, merchantId, minimum, maximum)
                .any { it.isNotBlank() } || receipt != "ALL" || split != "ALL" || origin != "ALL"

    fun validate(): ActivityScope {
        require(startDay <= endDay) { "End date must be on or after the start date" }
        require(currency.isBlank() || currency in Money.currencies) {
            "Choose a supported currency"
        }
        require(
            receipt in listOf("ALL", "HAS", "NONE") &&
                split in listOf("ALL", "SPLIT", "SINGLE") &&
                origin in listOf("ALL", "LEDGER", "GROUP")
        )
        require((minimum.isBlank() && maximum.isBlank()) || currency.isNotBlank()) {
            "Choose one currency before using amount limits"
        }
        require(minimumMinor <= maximumMinor) { "Maximum amount must not be below minimum" }
        return this
    }

    fun saved() =
        arrayListOf(
            from,
            through,
            accountId,
            categoryId,
            currency,
            merchantId,
            minimum,
            maximum,
            receipt,
            split,
            origin,
        )

    companion object {
        fun read(v: List<String>): ActivityScope =
            when (v.size) {
                11 ->
                    ActivityScope(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10])
                10 -> ActivityScope(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9])
                5 -> ActivityScope(v[0], v[1], v[2], v[3], v[4])
                else -> ActivityScope()
            }.validate()
    }
}
