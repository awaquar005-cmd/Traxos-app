package app.traxos.finance.gifts

import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.Locale

/**
 * Stored-value cards are assets outside the cash ledger; discount vouchers have no monetary
 * balance.
 */
object GiftRules {
    val kinds = listOf("STORED_VALUE", "SINGLE_USE", "DISCOUNT")
    val sorts = listOf("EXPIRY", "MERCHANT", "NEWEST", "BALANCE")

    fun normalize(code: String): String =
        code.trim().uppercase(Locale.ROOT).filterNot { it.isWhitespace() || it == '-' }

    fun validateCode(code: String) {
        require(code.trim().length in 3..512 && code.none { it.isISOControl() }) {
            "Enter a code of 3–512 printable characters"
        }
        require(normalize(code).isNotBlank()) { "Enter a valid code" }
    }

    fun state(card: GiftCard, today: Long = LocalDate.now().toEpochDay()): String =
        when {
            card.deleted -> "DELETED"
            card.archived -> "ARCHIVED"
            card.redeemed || card.remainingMinor == 0L -> "REDEEMED"
            card.manuallyExpired -> "EXPIRED"
            card.expiryDay != null && card.expiryDay < today -> "EXPIRED"
            card.expiryDay != null && card.expiryDay <= today + 30 -> "EXPIRING"
            else -> "ACTIVE"
        }

    fun redeem(card: GiftCard, amount: Long?): Long? {
        require(!card.archived && !card.deleted && !card.redeemed) {
            "This card is not available for redemption"
        }
        require(!card.manuallyExpired) { "This card was marked expired" }
        require(card.expiryDay == null || card.expiryDay >= LocalDate.now().toEpochDay()) {
            "This card has expired"
        }
        if (card.kind == "DISCOUNT") {
            require(amount == null)
            return null
        }
        val balance = requireNotNull(card.remainingMinor) { "Confirm the remaining balance first" }
        val used = requireNotNull(amount) { "Enter the redeemed value" }
        require(used in 1..balance) {
            "Redemption must be greater than zero and within the remaining balance"
        }
        require(card.partialAllowed || used == balance) {
            "Partial redemption is not enabled for this card"
        }
        require(card.kind != "SINGLE_USE" || used == balance) {
            "Single-use vouchers must be redeemed in full"
        }
        return balance - used
    }

    fun intervals(text: String): List<Int> {
        val values =
            text.split(',').map {
                it.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Use days separated by commas")
            }
        require(
            values.size in 1..8 &&
                values.distinct().size == values.size &&
                values.all { it in 0..365 }
        ) {
            "Choose up to eight different intervals from 0 to 365 days"
        }
        return values.sortedDescending()
    }

    fun amount(text: String, currency: String): Long? =
        if (text.isBlank()) null else Money.parse(text, currency, true)
}

data class GiftExtraction(
    val merchant: String = "",
    val code: String = "",
    val amount: String = "",
    val currency: String = "",
    val expiry: String = "",
    val amountIsBalance: Boolean = false,
)

/**
 * Deliberately conservative: labeled codes/amounts only, unambiguous dates only. Never follows
 * links.
 */
object GiftParser {
    fun parse(text: String): GiftExtraction {
        val input = text.take(16000)
        fun match(pattern: String) =
            Regex(pattern, RegexOption.IGNORE_CASE)
                .find(input)
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()
        val merchant =
            listOf("Amazon", "Flipkart", "Myntra", "IKEA", "Starbucks").firstOrNull {
                Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(input)
            } ?: match("(?:merchant|store)\\s*[:=]\\s*([^\\r\\n]{1,80})")
        val code =
            match(
                "(?:gift\\s*card\\s*(?:number|code)|voucher\\s*code|promo\\s*code|code|card\\s*number)\\s*(?:[:=]|\\bis\\b)\\s*([A-Za-z0-9][A-Za-z0-9_-]{2,511})(?=\\s|$)"
            )
        val money =
            Regex(
                    "(?:value|amount|balance|worth)\\s*[:=]?\\s*(INR|USD|EUR|JPY|KWD|₹|\\$|€|¥)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)(?=\\s|$|[;!?])",
                    RegexOption.IGNORE_CASE,
                )
                .find(input)
        val currency =
            when (money?.groupValues?.get(1)?.uppercase()) {
                "₹",
                "INR" -> "INR"
                "USD" -> "USD"
                "€",
                "EUR" -> "EUR"
                "JPY" -> "JPY"
                "KWD" -> "KWD"
                else -> ""
            }
        // Bare $ / ¥ are ambiguous; do not assume their currency.
        val amount =
            if (currency.isEmpty()) "" else money?.groupValues?.get(2)?.replace(",", "").orEmpty()
        val rawDate =
            match(
                "(?:expires?|expiry|valid\\s*(?:until|till))(?:\\s+(?:on|date))?\\s*[:=]?\\s*(\\d{4}-\\d{2}-\\d{2})"
            )
        val expiry = runCatching { LocalDate.parse(rawDate).toString() }.getOrDefault("")
        return GiftExtraction(
            merchant,
            code,
            amount,
            currency,
            expiry,
            money?.value?.startsWith("balance", ignoreCase = true) == true,
        )
    }
}
