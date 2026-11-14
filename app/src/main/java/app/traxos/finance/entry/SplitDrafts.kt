package app.traxos.finance.entry

import app.traxos.finance.domain.Money
import org.json.JSONArray
import org.json.JSONObject

object SplitDrafts {
    data class Row(val categoryId: String, val amount: String)

    fun read(value: String): List<Row> {
        if (value.isBlank()) return emptyList()
        val array = JSONArray(value)
        require(array.length() <= 20)
        return (0 until array.length()).map {
            val row = array.getJSONObject(it)
            Row(row.getString("categoryId"), row.getString("amount"))
        }
    }

    fun write(rows: List<Row>) =
        if (rows.isEmpty()) ""
        else
            JSONArray(
                    rows.map {
                        JSONObject().put("categoryId", it.categoryId).put("amount", it.amount)
                    }
                )
                .toString()

    fun parts(value: String, currency: String) =
        read(value).map { SplitPart(it.categoryId, Money.parse(it.amount, currency)) }
}
