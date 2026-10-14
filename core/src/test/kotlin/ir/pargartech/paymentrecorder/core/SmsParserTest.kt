package ir.pargartech.paymentrecorder.core

import ir.pargartech.paymentrecorder.core.parser.ParseResult
import ir.pargartech.paymentrecorder.core.parser.ReviewReason
import ir.pargartech.paymentrecorder.core.parser.SenderDirectory
import ir.pargartech.paymentrecorder.core.parser.SenderRule
import ir.pargartech.paymentrecorder.core.parser.SmsParser
import ir.pargartech.paymentrecorder.core.parser.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTE: these samples are SYNTHETIC — written to cover common Iranian bank SMS shapes,
 * not copied from real messages. Add real (masked) samples per bank under
 * "Real samples" as they arrive; see docs/TECH.md.
 */
class SmsParserTest {
    private val parser = SmsParser()

    private fun parsed(sender: String, body: String, p: SmsParser = parser): ParseResult.Parsed {
        val r = p.parse(sender, body)
        assertTrue("expected Parsed but was $r", r is ParseResult.Parsed)
        return r as ParseResult.Parsed
    }

    @Test
    fun `labelled deposit with balance and masked account`() {
        val r = parsed(
            "+98700000",
            "بانک ملت\nواریز: 1,500,000 ریال\nحساب: 123***4567\nمانده: 12,345,678\n1403/07/22-10:15",
        )
        assertEquals("mellat", r.bankId)
        assertEquals(TxType.DEPOSIT, r.type)
        assertEquals(1_500_000L, r.amountRial)
        assertEquals(12_345_678L, r.balanceRial)
        assertEquals("4567", r.accountKey)
    }

    @Test
    fun `persian digits and arabic separators`() {
        val r = parsed(
            "98700001",
            "بانک صادرات\nبرداشت ۲۵۰٬۰۰۰ ریال از حساب ...۹۸۷۶ مانده ۱۰٬۰۰۰٬۰۰۰ ۱۴۰۳/۰۷/۲۲ ۱۰:۱۵",
        )
        assertEquals("saderat", r.bankId)
        assertEquals(TxType.WITHDRAWAL, r.type)
        assertEquals(250_000L, r.amountRial)
        assertEquals(10_000_000L, r.balanceRial)
        assertEquals("9876", r.accountKey)
    }

    @Test
    fun `trailing minus sign means withdrawal`() {
        val r = parsed("Saman", "حساب 0201234567005\nمبلغ:350,000-\nمانده:1,250,000\n0722-1015")
        assertEquals("saman", r.bankId)
        assertEquals(TxType.WITHDRAWAL, r.type)
        assertEquals(350_000L, r.amountRial)
        assertEquals(1_250_000L, r.balanceRial)
        assertEquals("7005", r.accountKey)
    }

    @Test
    fun `leading plus sign means deposit even with transfer wording`() {
        val r = parsed("BankMellat", "انتقال\n+2,000,000\nمانده: 5,000,000")
        assertEquals(TxType.DEPOSIT, r.type)
        assertEquals(2_000_000L, r.amountRial)
    }

    @Test
    fun `purchase with card mask`() {
        val r = parsed(
            "+98700002",
            "بانک تجارت\nخرید از کارت 6037-99**-****-1234\nمبلغ 480,000 ریال\nمانده 3,520,000",
        )
        assertEquals("tejarat", r.bankId)
        assertEquals(TxType.WITHDRAWAL, r.type)
        assertEquals(480_000L, r.amountRial)
        assertEquals(3_520_000L, r.balanceRial)
        assertEquals("1234", r.accountKey)
    }

    @Test
    fun `toman unit is converted to rial`() {
        val r = parsed("+98700003", "بانک پاسارگاد واریز 150,000 تومان به حساب 12x34")
        assertEquals(1_500_000L, r.amountRial)
        assertEquals("34", r.accountKey)
    }

    @Test
    fun `negative balance keeps its sign`() {
        val r = parsed("+98700004", "بانک ملی\nبرداشت: 100,000\nمانده: 50,000-")
        assertEquals(-50_000L, r.balanceRial)
    }

    @Test
    fun `dynamic password is ignored`() {
        val r = parser.parse("BankMellat", "رمز پویا: 123456\nمبلغ: 1,000,000\nاعتبار: 2 دقیقه")
        assertEquals(ParseResult.Ignored, r)
    }

    @Test
    fun `personal sms is ignored`() {
        assertEquals(ParseResult.Ignored, parser.parse("09121234567", "سلام، ساعت ۵ می‌بینمت"))
    }

    @Test
    fun `bank promotion without amount is ignored`() {
        assertEquals(ParseResult.Ignored, parser.parse("+98700000", "بانک ملت: با همراه بانک سریع‌تر خرید کنید"))
    }

    @Test
    fun `transaction from unknown sender goes to review`() {
        val r = parser.parse("+98700999", "واریز: 1,500,000\nمانده: 2,000,000")
        assertTrue(r is ParseResult.NeedsReview)
        r as ParseResult.NeedsReview
        assertEquals(ReviewReason.UNKNOWN_SENDER, r.reason)
        assertEquals(1_500_000L, r.amountRial)
        assertEquals(TxType.DEPOSIT, r.type)
    }

    @Test
    fun `trusted sender is parsed with its learned bank id`() {
        val dir = SenderDirectory.of(mapOf("98700999" to SenderRule.Trusted("sender:98700999")))
        val r = parsed("+98 700-999", "واریز: 1,500,000\nمانده: 2,000,000", SmsParser(dir))
        assertEquals("sender:98700999", r.bankId)
    }

    @Test
    fun `ignored sender is ignored`() {
        val dir = SenderDirectory.of(mapOf("98700000" to SenderRule.Ignored))
        assertEquals(ParseResult.Ignored, SmsParser(dir).parse("+98700000", "بانک ملت واریز 1,000,000"))
    }

    @Test
    fun `transfer without sign or direction goes to review`() {
        val r = parser.parse("+98700000", "بانک ملت\nانتقال وجه\nمبلغ: 700,000\nمانده: 900,000")
        assertTrue(r is ParseResult.NeedsReview)
        assertEquals(ReviewReason.NO_TYPE, (r as ParseResult.NeedsReview).reason)
        assertEquals(700_000L, r.amountRial)
    }

    @Test
    fun `person named Saman in body does not make it Saman bank`() {
        val r = parser.parse("+98700999", "واریز از سامان احمدی مبلغ 1,000,000")
        assertTrue(r is ParseResult.NeedsReview)
        assertEquals(null, (r as ParseResult.NeedsReview).bankId)
    }

    @Test
    fun `date and time digits are not taken as amounts`() {
        val r = parsed("+98700000", "بانک ملت 1403/07/22 12:30 واریز 2,500,000")
        assertEquals(2_500_000L, r.amountRial)
        assertEquals(null, r.balanceRial)
    }

    // ---- Real samples (masked) go here, one test per bank format. ----
}
