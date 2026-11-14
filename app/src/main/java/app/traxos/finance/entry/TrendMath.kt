package app.traxos.finance.entry

import java.time.LocalDate
import java.time.YearMonth

data class TrendBucket(val label: String, val spending: Long, val income: Long)

object TrendMath {
    fun buckets(rows: List<TrendPoint>, start: Long, end: Long): List<TrendBucket> {
        require(start <= end && end - start < 366)
        require(rows.map { it.currency }.distinct().size <= 1) {
            "Chart currencies must remain separate"
        }
        val monthly = end - start > 62
        if (!monthly) {
            val byDay = rows.groupBy { it.day }
            return (start..end).map { day ->
                val values = byDay[day].orEmpty()
                TrendBucket(
                    LocalDate.ofEpochDay(day).toString(),
                    values.sumOf { it.expenseMinor - it.refundMinor },
                    values.sumOf { it.incomeMinor },
                )
            }
        }
        val byMonth = rows.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }
        val last = YearMonth.from(LocalDate.ofEpochDay(end))
        var current = YearMonth.from(LocalDate.ofEpochDay(start))
        val output = mutableListOf<TrendBucket>()
        while (current <= last) {
            val values = byMonth[current].orEmpty()
            output +=
                TrendBucket(
                    current.toString(),
                    values.sumOf { it.expenseMinor - it.refundMinor },
                    values.sumOf { it.incomeMinor },
                )
            current = current.plusMonths(1)
        }
        return output
    }
}
