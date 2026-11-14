package app.traxos.finance.domain

/** Stable IDs only for screens that exist. UI routes/icons are registered separately. */
object NavigationLayout {
    val implemented =
        listOf(
            "home",
            "transactions",
            "accounts",
            "settings",
            "gifts",
            "more",
            "smart",
            "reports",
            "plans",
            "voice",
            "debts",
        )
    // Default positions remain stable when new implemented screens join the registry.
    val defaults = listOf("home", "transactions", "accounts", "settings")
    const val SLOT_COUNT = 4

    fun validate(slots: List<String>): List<String> {
        require(slots.size == SLOT_COUNT) { "Choose four navigation screens" }
        require(slots.distinct().size == SLOT_COUNT) { "A screen can appear only once" }
        require(slots.all { it in implemented }) { "Choose an implemented screen" }
        return slots.toList()
    }

    fun encode(slots: List<String>) = validate(slots).joinToString(",")

    fun decode(value: String): List<String> =
        try {
            validate(value.split(','))
        } catch (_: IllegalArgumentException) {
            defaults
        }

    fun replace(slots: List<String>, index: Int, destination: String): List<String> {
        validate(slots)
        require(index in slots.indices)
        require(destination in implemented)
        val result = slots.toMutableList()
        val other = result.indexOf(destination)
        if (other >= 0) result[other] = result[index]
        result[index] = destination
        return validate(result)
    }

    fun move(slots: List<String>, from: Int, to: Int): List<String> {
        validate(slots)
        require(from in slots.indices && to in slots.indices)
        val result = slots.toMutableList()
        val entry = result.removeAt(from)
        result.add(to, entry)
        return validate(result)
    }
}
