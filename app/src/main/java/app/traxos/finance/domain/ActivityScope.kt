package app.traxos.finance.domain

import java.time.LocalDate

/** Inclusive local ledger dates. Only primitive strings enter SavedStateHandle. */
data class ActivityScope(
    val from: String = "",
    val through: String = "",
    val accountId: String = "",
    val categoryId: String = "",
    val currency: String = "",
) {
    val startDay: Long
        get() = if (from.isBlank()) Long.MIN_VALUE else LocalDate.parse(from).toEpochDay()

    val endDay: Long
        get() = if (through.isBlank()) Long.MAX_VALUE else LocalDate.parse(through).toEpochDay()

    val active: Boolean
        get() = saved().any { it.isNotBlank() }

    fun validate(): ActivityScope {
        require(startDay <= endDay) { "End date must be on or after the start date" }
        require(currency.isBlank() || currency in Money.currencies) {
            "Choose a supported currency"
        }
        return this
    }

    fun saved() = arrayListOf(from, through, accountId, categoryId, currency)

    companion object {
        fun read(values: List<String>): ActivityScope =
            if (values.size == 5)
                ActivityScope(values[0], values[1], values[2], values[3], values[4]).validate()
            else ActivityScope()
    }
}
