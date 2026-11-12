package app.traxos.finance.smart

import app.traxos.finance.gifts.*
import java.time.LocalDate
import java.util.Locale

data class SmartDraft(
    val kind: String,
    val merchant: String = "",
    val amount: String = "",
    val currency: String = "",
    val type: String = "EXPENSE",
    val day: String = LocalDate.now().toString(),
    val code: String = "",
    val pin: String = "",
    val expiry: String = "",
    val amountIsBalance: Boolean = false,
    val reference: String = "",
    val accountHint: String = "",
    val note: String = "",
    val warning: String = "",
    val originalText: String = "",
    val giftKind: String = "STORED_VALUE",
    val cardRequired: Boolean = false,
) {
    fun gift() =
        GiftExtraction(
            merchant,
            code,
            amount,
            currency,
            expiry,
            amountIsBalance,
            pin,
            giftKind = giftKind,
        )
}

/** Contextual classification is deliberately separate from approval and accounting. */
object SmartParser {
    private val giftContext =
        Regex(
            "(?i)\\b(?:gift\\s*(?:card|voucher)|pay\\s+code|voucher|claim code|promo code|coupon code|redemption code)\\b"
        )
    private val bankContext = Regex("(?i)\\b(?:bank|a/c|acct?|account|upi|card|imps|neft|rtgs)\\b")
    private val direction =
        Regex("(?i)\\b(?:debited|credited|paid|spent|received|withdrawn|deposited|deducted)\\b")

    fun parse(raw: String, receivedDay: String? = null): SmartDraft? {
        val text = ImportTextSafety.clean(raw)
        if (text.isBlank()) return null
        if (giftContext.containsMatchIn(text)) {
            val e = GiftParser.parse(text)
            return SmartDraft(
                "GIFT",
                e.merchant,
                e.amount,
                e.currency,
                code = e.code,
                pin = e.pin,
                expiry = e.expiry,
                amountIsBalance = e.amountIsBalance,
                warning = e.warnings.joinToString(" · "),
                originalText = text,
                giftKind = e.giftKind,
            )
        }
        val confirmation =
            Regex(
                "(?i)\\b(?:payment|purchase|txn|transaction)\\b[\\s\\S]{0,140}\\b(?:successful|successfully|completed)\\b|\\b(?:successful|completed)\\b[\\s\\S]{0,80}\\b(?:payment|purchase|txn)\\b"
            )
        if (
            !bankContext.containsMatchIn(text) ||
                (!direction.containsMatchIn(text) && !confirmation.containsMatchIn(text))
        )
            return null
        val ownTransfer =
            Regex("(?i)\\b(?:self[ -]transfer|own accounts?|between (?:my|your) accounts?)\\b")
                .containsMatchIn(text)
        val blocked =
            when {
                Regex("(?i)\\b(?:failed|declined|unsuccessful|pending|request(?:ed)?)\\b")
                    .containsMatchIn(text) ->
                    "This is not a confirmed completed payment. Review it manually; no ordinary expense or income will be posted."
                Regex(
                        "(?i)credit\\s*card.*(?:bill|payment|settle)|(?:bill|payment).*credit\\s*card|payment.*towards.*card"
                    )
                    .containsMatchIn(text) ->
                    "Card settlement is not an expense. Use Pay card from the card account to record both sides; no ordinary expense will be posted."
                Regex("(?i)\\b(?:own account|self transfer|transfer(?:red)?|neft|imps|rtgs)\\b")
                    .containsMatchIn(text) ->
                    "This may be a transfer. Review both owned accounts in Transfer between accounts; no ordinary expense or income will be posted."
                Regex("(?i)\\b(?:refund|reversal|reversed|cashback)\\b").containsMatchIn(text) ->
                    "Refunds/reversals must be matched to their original purchase; they are not earned income. No record will be posted."
                else -> ""
            }
        val values =
            Regex("(?i)(?:₹|INR|Rs\\.?|USD|EUR|JPY|KWD)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")
                .findAll(text)
                .mapNotNull { m ->
                    val n = m.groupValues[1]
                    if (
                        !n.matches(
                            Regex(
                                "(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]+)?"
                            )
                        )
                    )
                        null
                    else {
                        val before = text.substring(maxOf(0, m.range.first - 28), m.range.first)
                        val after =
                            text.substring(m.range.last + 1, minOf(text.length, m.range.last + 36))
                        val score =
                            if (Regex("(?i)balance|limit|avl|avail").containsMatchIn(before)) 0
                            else if (direction.containsMatchIn(before + after)) 90 else 50
                        val currency =
                            Regex("(?i)^(₹|INR|Rs\\.?|USD|EUR|JPY|KWD)")
                                .find(m.value)!!
                                .value
                                .uppercase(Locale.ROOT)
                                .let { if (it in listOf("₹", "INR", "RS", "RS.")) "INR" else it }
                        Triple(n.replace(",", ""), currency, score)
                    }
                }
                .filter { it.third > 0 }
                .sortedByDescending { it.third }
                .distinctBy { it.first to it.second }
                .toList()
        val best =
            values.firstOrNull()?.takeIf { values.size == 1 || it.third - values[1].third >= 15 }
        val incoming =
            Regex("(?i)\\b(?:credited|received|deposited)\\b").containsMatchIn(text) &&
                !Regex("(?i)\\b(?:debited|spent|paid|withdrawn|deducted)\\b").containsMatchIn(text)
        val merchant =
            Regex(
                    "(?i)\\b(?:at|to|from)\\s+([A-Za-z][A-Za-z0-9 &'._-]{1,60}?)(?=\\s+(?:on|ref|upi|a/c|acct|account|for|via)|[.;\\n]|$)"
                )
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()
        val reference =
            Regex(
                    "(?i)\\b(?:ref(?:erence)?(?:\\s*(?:no|id))?|utr|upi ref(?: no)?)\\s*[:#.-]?\\s*([A-Za-z0-9]{6,32})\\b"
                )
                .find(text)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val account =
            Regex("(?i)(?:a/c|acct?|account|card)\\s*[:.-]?\\s*[xX*]*(\\d{3,6})\\b")
                .find(text)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val date =
            messageDate(text)
                ?: receivedDay?.takeIf { value ->
                    runCatching {
                            val day = LocalDate.parse(value)
                            !day.isAfter(LocalDate.now()) && day.year >= 1980
                        }
                        .getOrDefault(false)
                }
        return SmartDraft(
            if (
                ownTransfer &&
                    !Regex(
                            "(?i)failed|declined|unsuccessful|pending|reversal|reversed|refund|cashback|request|credit\\s*card"
                        )
                        .containsMatchIn(text)
            )
                "TRANSFER"
            else if (blocked.isBlank()) "TRANSACTION" else "REVIEW_ONLY",
            merchant,
            best?.first.orEmpty(),
            best?.second.orEmpty(),
            if (incoming) "INCOME" else "EXPENSE",
            date.orEmpty(),
            reference = reference,
            accountHint = account,
            note = merchant.take(120),
            warning =
                blocked.ifBlank {
                    if (best == null)
                        "Choose the transaction amount; several or no usable amounts were found"
                    else "Check account, category, date and amount before approving"
                },
            originalText = text,
            cardRequired = Regex("(?i)credit\\s*card").containsMatchIn(text),
        )
    }

    private fun messageDate(text: String): String? {
        val iso = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(text)?.value
        if (iso != null) return runCatching { LocalDate.parse(iso).toString() }.getOrNull()
        val named = Regex("(?i)\\b(\\d{1,2})[- /]([A-Za-z]{3,9})[- /](\\d{2}|\\d{4})\\b").find(text)
        if (named != null)
            return runCatching {
                    val month =
                        java.time.Month.values().first {
                            it.name.startsWith(named.groupValues[2].take(3), true)
                        }
                    val y = named.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                    LocalDate.of(y, month, named.groupValues[1].toInt()).toString()
                }
                .getOrNull()
        val numeric =
            Regex("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2}|\\d{4})\\b").find(text) ?: return null
        val a = numeric.groupValues[1].toInt()
        val c = numeric.groupValues[2].toInt()
        if (a <= 12 && c <= 12 && a != c) return null
        val day = if (a > 12) a else c
        val month = if (a > 12) c else a
        val y = numeric.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        return runCatching { LocalDate.of(y, month, day).toString() }.getOrNull()
    }
}
