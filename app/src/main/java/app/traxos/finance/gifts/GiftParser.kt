package app.traxos.finance.gifts

import java.time.LocalDate
import java.time.format.ResolverStyle
import java.util.Locale

/** No URL resolution, no network, no inferred balances. Scores describe message context only. */
object ImportTextSafety {
    private val links =
        Regex(
            "(?i)(?:[a-z][a-z0-9+.-]*://|www\\.|(?:mailto|tel|sms|intent|javascript):)\\S+|\\b[a-z0-9.-]+\\.(?:com|in|org|net)\\b\\S*"
        )
    private val otp =
        Regex("(?i)\\b(?:otp|one[ -]time (?:password|code)|verification code|login code)\\b")

    fun clean(text: String): String =
        text
            .take(24000)
            .lines()
            .filterNot { otp.containsMatchIn(it) }
            .joinToString("\n")
            .replace(links, " ")
            .replace('\u00a0', ' ')

    fun isLink(text: String) = links.containsMatchIn(text)

    fun isOtp(text: String) = otp.containsMatchIn(text)
}

object GiftParser {
    private val gift =
        Regex(
            "(?i)\\b(?:gift\\s*(?:card|voucher)|voucher|e-?gift|claim code|redemption code|promo(?:tional)? code|coupon)\\b"
        )
    private val codeLabel =
        Regex(
            "(?i)\\b(?:gift\\s*card\\s*(?:number|no\\.?|code)|claim\\s*code|redemption\\s*code|voucher\\s*(?:number|no\\.?|code)|promo\\s*code|coupon\\s*code|card\\s*(?:number|no\\.?)|code)\\b\\s*(?::|=|is|-)??\\s*$"
        )
    private val badContext =
        Regex(
            "(?i)\\b(?:order|invoice|transaction|reference|ref|mobile|phone|contact|customer id|otp|balance due|mrp|discount price|selling price)\\b"
        )
    private val amountLabel =
        Regex(
            "(?i)\\b(?:gift\\s*(?:card|voucher)\\s*(?:value|amount|balance)?|(?:card|voucher) value|face value|denomination|worth|value|amount|remaining balance|balance)\\b"
        )
    private val currencyNumber =
        Regex(
            "(?i)(₹|INR|Rs\\.?|USD|EUR|JPY|KWD|€|\\$|¥)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(?![0-9.,])"
        )
    private val validNumber =
        Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]+)?")

    fun parse(raw: String): GiftExtraction {
        val text = ImportTextSafety.clean(raw)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = mutableListOf<ImportCandidate>()
        val warnings = mutableListOf<String>()
        val isGift = gift.containsMatchIn(text)
        val declaredCurrency =
            Regex("(?im)^currency\\s*[:=]\\s*(INR|USD|EUR|JPY|KWD)\\b")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.uppercase(Locale.ROOT)
                .orEmpty()
        val merchant =
            listOf(
                    "Amazon",
                    "Flipkart",
                    "Myntra",
                    "IKEA",
                    "Starbucks",
                    "Swiggy",
                    "Zomato",
                    "BigBasket",
                    "Croma",
                    "Reliance",
                    "Tata CLiQ",
                    "Nykaa",
                    "BookMyShow",
                    "Ajio",
                )
                .firstOrNull {
                    Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE)
                        .containsMatchIn(text)
                }
                ?: Regex("(?im)^(?:merchant|store|brand)\\s*[:=]\\s*([^\\r\\n]{1,80})")
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    .orEmpty()
        if (merchant.isNotEmpty()) candidates += ImportCandidate("merchant", merchant, 90)
        for ((index, line) in lines.withIndex()) {
            val prev = lines.getOrNull(index - 1).orEmpty()
            val context = "$prev $line"
            // Explicit labels may be split onto preceding lines. Unknown standalone numbers are
            // never codes.
            val labels =
                Regex(
                        "(?i)(?:gift\\s*card\\s*(?:number|no\\.?|code)|claim\\s*code|redemption\\s*code|voucher\\s*(?:number|no\\.?|code)|promo\\s*code|coupon\\s*code|card\\s*number|code)\\s*(?::|=|\\bis\\b|-)?\\s*(.*)"
                    )
                    .find(line)
            val labeled = labels != null || codeLabel.containsMatchIn(prev)
            if (labeled && !badContext.containsMatchIn(line.substringBefore(':').take(35))) {
                var value = labels?.groupValues?.get(1)?.trim().orEmpty()
                if (value.isEmpty())
                    value = if (labels == null) line else lines.getOrNull(index + 1).orEmpty()
                // At most one subsequent token line, only when code fragments are separated at
                // whitespace/hyphens.
                val nextIndex =
                    if (labels != null && labels.groupValues[1].isBlank()) index + 2 else index + 1
                val next = lines.getOrNull(nextIndex).orEmpty()
                val token = Regex("^[A-Za-z0-9][A-Za-z0-9 -]{2,63}$")
                if (
                    token.matches(value) &&
                        token.matches(next) &&
                        value.length < 16 &&
                        next.any(Char::isDigit) &&
                        next.none(Char::isWhitespace) &&
                        !next.matches(Regex("\\d{1,4}"))
                )
                    value += " $next"
                value =
                    value
                        .split(
                            Regex(
                                "(?i)\\s+(?=(?:PIN|valid|expiry|expires|value|amount|phone|contact|order|purchase|received)\\b)"
                            ),
                            limit = 2,
                        )
                        .first()
                val compact = value.trim().trimEnd('.', ',', ';').replace(Regex("\\s+"), "")
                if (
                    compact.length in 3..64 &&
                        compact.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]+")) &&
                        compact.any(Char::isDigit) &&
                        !ImportTextSafety.isLink(value) &&
                        !Regex("(?i)^(?:order|http|otp)").containsMatchIn(compact)
                ) {
                    if (isGift || labels?.value?.contains("gift", true) == true)
                        candidates +=
                            ImportCandidate(
                                "code",
                                compact,
                                if (labels != null || isGift) 90 else 60,
                            )
                }
            }
            val pin =
                Regex("(?i)\\b(?:gift\\s*card\\s*)?pin\\s*(?::|=|is)?\\s*(\\d{4,8})\\b").find(line)
                    ?: if (Regex("(?i)^pin\\s*[:=]?$").matches(prev))
                        Regex("^(\\d{4,8})$").find(line)
                    else null
            if (pin != null && isGift) candidates += ImportCandidate("pin", pin.groupValues[1], 90)
            for (m in currencyNumber.findAll(line)) {
                val number = m.groupValues[2]
                if (!validNumber.matches(number)) continue
                val currency =
                    when (m.groupValues[1].uppercase(Locale.ROOT)) {
                        "₹",
                        "INR",
                        "RS",
                        "RS." -> "INR"
                        "USD",
                        "$" -> "USD"
                        "EUR",
                        "€" -> "EUR"
                        "JPY",
                        "¥" -> "JPY"
                        "KWD" -> "KWD"
                        else -> ""
                    }
                if (currency.isEmpty()) continue
                val score = amountScore(line, prev, isGift, m.range.first, m.range.last + 1)
                if (score >= 50)
                    candidates +=
                        ImportCandidate(
                            "amount",
                            number.replace(",", "") + "|" + currency,
                            score,
                            Regex("(?i)remaining|balance")
                                .containsMatchIn(
                                    if (amountLabel.containsMatchIn(line)) line else context
                                ),
                        )
            }
            // Also accept currency suffixes and clearly labeled amounts without a currency.
            val suffix = Regex("(?i)([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(INR|USD|EUR|JPY|KWD)\\b")
            for (m in suffix.findAll(line)) {
                if (
                    validNumber.matches(m.groupValues[1]) &&
                        amountScore(line, prev, isGift, m.range.first, m.range.last + 1) >= 50
                )
                    candidates +=
                        ImportCandidate(
                            "amount",
                            m.groupValues[1].replace(",", "") +
                                "|" +
                                m.groupValues[2].uppercase(Locale.ROOT),
                            amountScore(line, prev, isGift, m.range.first, m.range.last + 1),
                            Regex("(?i)remaining|balance").containsMatchIn(line),
                        )
            }
            if (
                !currencyNumber.containsMatchIn(line) &&
                    !suffix.containsMatchIn(line) &&
                    isGift &&
                    !badContext.containsMatchIn(line)
            ) {
                val labeledAmount =
                    Regex(
                            "(?i)(?:face value|card value|voucher value|value|amount|denomination|worth|remaining balance|balance)\\s*[:=]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(?![0-9.,])"
                        )
                        .find(line)
                        ?: if (amountLabel.matches(prev))
                            Regex("^([0-9][0-9,]*(?:\\.[0-9]+)?)$").find(line)
                        else null
                labeledAmount
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { validNumber.matches(it) }
                    ?.let {
                        candidates +=
                            ImportCandidate(
                                "amount",
                                it.replace(",", "") + "|" + declaredCurrency,
                                80,
                                Regex("(?i)remaining|balance").containsMatchIn(context),
                            )
                        if (declaredCurrency.isEmpty())
                            warnings +=
                                "No currency stated; review the suggested application currency"
                    }
            }
            if (
                Regex(
                        "(?i)\\b(?:expir(?:y|es|ation)|valid\\s*(?:till|until|through|upto|up to))\\b"
                    )
                    .containsMatchIn(context)
            ) {
                val dates =
                    Regex(
                        "(?i)\\b(?:\\d{4}[-/]\\d{2}[-/]\\d{2}|\\d{1,2}[-/ .]\\d{1,2}[-/ .]\\d{4}|\\d{1,2}[ -](?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[ ,/-]+\\d{4})\\b"
                    )
                for (d in dates.findAll(line)) {
                    val parsed = parseDate(d.value)
                    if (parsed != null)
                        candidates += ImportCandidate("expiry", parsed.first, parsed.second)
                    else warnings += "Expiry is ambiguous or unreadable; choose it yourself"
                }
            }
        }
        fun choose(field: String): ImportCandidate? {
            val sorted =
                candidates
                    .filter { it.field == field }
                    .sortedByDescending { it.score }
                    .distinctBy { it.value }
            if (sorted.size > 1 && sorted[0].score - sorted[1].score < 15) {
                warnings += "Several $field values found; select the correct one"
                return null
            }
            val best = sorted.firstOrNull()
            if (best != null && best.score < 80)
                warnings += "Check the detected $field before saving"
            return best
        }
        val code = choose("code")
        val amount = choose("amount")
        val expiry = choose("expiry")
        val pin = choose("pin")
        val money = amount?.value?.split('|')
        return GiftExtraction(
            merchant,
            code?.value.orEmpty(),
            money?.get(0).orEmpty(),
            money?.get(1).orEmpty(),
            expiry?.value.orEmpty(),
            amount?.isBalance == true,
            pin?.value.orEmpty(),
            mapOf(
                "merchant" to if (merchant.isBlank()) 0 else 90,
                "code" to (code?.score ?: 0),
                "amount" to (amount?.score ?: 0),
                "expiry" to (expiry?.score ?: 0),
                "pin" to (pin?.score ?: 0),
            ),
            candidates.distinct(),
            warnings.distinct(),
            if (
                Regex("(?i)promo code|coupon|discount code").containsMatchIn(text) &&
                    !Regex("(?i)gift ?card|gift ?voucher").containsMatchIn(text)
            )
                "DISCOUNT"
            else if (Regex("(?i)single[ -]use").containsMatchIn(text)) "SINGLE_USE"
            else "STORED_VALUE",
        )
    }

    private fun amountScore(
        line: String,
        previous: String,
        isGift: Boolean,
        start: Int,
        end: Int,
    ): Int {
        val before = line.substring(0, start)
        val nearby = before.takeLast(60)
        var score =
            if (
                amountLabel.containsMatchIn(nearby) ||
                    (before.isBlank() && amountLabel.containsMatchIn(previous))
            )
                90
            else if (isGift) 65 else 45
        val good = amountLabel.findAll(before).lastOrNull()?.range?.last ?: -1
        val bad = badContext.findAll(before).lastOrNull()?.range?.last ?: -1
        if (bad >= 0 && bad >= good) score -= 60
        if (
            Regex("(?i)(?:order|invoice)\\s*(?:total|amount|price)?\\s*[:=]?\\s*$")
                .containsMatchIn(nearby)
        )
            score -= 60
        if (
            Regex("(?i)(?:shipping|tax|discount|save)\\s*[:=]?\\s*$").containsMatchIn(nearby) ||
                Regex("(?i)^\\s*(?:off|discount)\\b").containsMatchIn(line.substring(end))
        )
            score -= 40
        return score
    }

    private fun parseDate(value: String): Pair<String, Int>? {
        val s = value.trim().replace(Regex("[, ]+"), " ")
        val iso = runCatching { LocalDate.parse(s.replace('/', '-')) }.getOrNull()
        if (iso != null) return iso.toString() to 95
        for (pattern in listOf("d-MMM-uuuu", "d MMM uuuu", "d MMMM uuuu")) {
            val d =
                runCatching {
                        LocalDate.parse(
                            s,
                            java.time.format
                                .DateTimeFormatterBuilder()
                                .parseCaseInsensitive()
                                .appendPattern(pattern)
                                .toFormatter(Locale.ENGLISH)
                                .withResolverStyle(ResolverStyle.STRICT),
                        )
                    }
                    .getOrNull()
            if (d != null) return d.toString() to 90
        }
        val parts = s.split(Regex("[-/. ]"))
        if (parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
            val day = parts[0].toInt()
            val month = parts[1].toInt()
            val year = parts[2].toInt()
            if (day in 1..12 && month in 1..12 && day != month) return null
            return runCatching { LocalDate.of(year, month, day).toString() to 85 }.getOrNull()
        }
        return null
    }
}
