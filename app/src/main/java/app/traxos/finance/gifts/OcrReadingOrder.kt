package app.traxos.finance.gifts

/**
 * Reading order joins geometrically aligned label/value columns, without changing recognized
 * characters.
 */
object OcrReadingOrder {
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun text(lines: List<Line>): String {
        val rows = mutableListOf<MutableList<Line>>()
        for (line in lines.sortedWith(compareBy<Line> { it.top }.thenBy { it.left })) {
            val row =
                rows.lastOrNull { group ->
                    val ref = group.first()
                    val overlap = minOf(ref.bottom, line.bottom) - maxOf(ref.top, line.top)
                    overlap > 0 &&
                        overlap * 2 >=
                            minOf(ref.bottom - ref.top, line.bottom - line.top).coerceAtLeast(1)
                }
            if (row == null) rows.add(mutableListOf(line)) else row.add(line)
        }
        return rows.joinToString("\n") {
            it.sortedBy { line -> line.left }.joinToString(" ") { line -> line.text }
        }
    }
}
