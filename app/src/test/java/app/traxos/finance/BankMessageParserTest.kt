package app.traxos.finance

import app.traxos.finance.smart.SmartParser
import org.junit.Assert.*
import org.junit.Test

class BankMessageParserTest {
    @Test
    fun bankFormatsKeepCounterpartySeparateFromIssuerAndParseAmountsDatesReferences() {
        val debit =
            SmartParser.parse(
                "A/c X5391 debited by Rs. 6460.00 for UPI payment to Test Person Na on 07-Oct-26. RRN: 217562691 if not you, call 18002123646 -Bank of Maharashtra"
            )!!
        assertEquals("6460.00", debit.amount)
        assertEquals("Test Person Na", debit.merchant)
        assertEquals("Bank of Maharashtra", debit.bankName)
        assertEquals("2026-10-07", debit.day)
        assertEquals("217562691", debit.reference)
        assertEquals("5391", debit.accountHint)
        val credit =
            SmartParser.parse(
                "A/c X5391 credited with Rs. 1.00 on 12-Nov-26 from Test Person Na RRN: 2201832437 -Bank of Maharashtra"
            )!!
        assertEquals("INCOME", credit.type)
        assertEquals("Test Person Na", credit.merchant)
        assertEquals("1.00", credit.amount)
        val sbi =
            SmartParser.parse(
                "Dear UPI user A/C X1259 debited by 1.00 on date 12Nov26 trf to TEST PERSON NA Refno 6316315\n729 If not u? call-18111109 for other services-18001234-SBI"
            )!!
        assertEquals("1.00", sbi.amount)
        assertEquals("INR", sbi.currency)
        assertEquals("2026-11-12", sbi.day)
        assertEquals("TEST PERSON NA", sbi.merchant)
        assertEquals("6316315729", sbi.reference)
        assertEquals("SBI", sbi.bankName)
    }
}
