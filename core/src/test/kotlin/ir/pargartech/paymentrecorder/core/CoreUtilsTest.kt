package ir.pargartech.paymentrecorder.core

import ir.pargartech.paymentrecorder.core.date.JalaliDate
import ir.pargartech.paymentrecorder.core.date.JalaliMonth
import ir.pargartech.paymentrecorder.core.export.CsvExporter
import ir.pargartech.paymentrecorder.core.export.ExportRow
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.Dedupe
import ir.pargartech.paymentrecorder.core.parser.SmsMasker
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.core.rules.CategoryRule
import ir.pargartech.paymentrecorder.core.rules.CategoryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class JalaliTest {
    @Test
    fun `known dates convert both ways`() {
        val cases = mapOf(
            LocalDate.of(2024, 10, 13) to JalaliDate(1403, 7, 22),
            LocalDate.of(2025, 3, 21) to JalaliDate(1404, 1, 1),
            LocalDate.of(2025, 3, 20) to JalaliDate(1403, 12, 30),
            LocalDate.of(2024, 3, 20) to JalaliDate(1403, 1, 1),
            LocalDate.of(2026, 10, 14) to JalaliDate(1405, 7, 22),
        )
        for ((g, j) in cases) {
            assertEquals(j, JalaliDate.of(g))
            assertEquals(g, j.toLocalDate())
        }
    }

    @Test
    fun `leap years and month lengths`() {
        assertTrue(JalaliDate.isLeapYear(1403))
        assertFalse(JalaliDate.isLeapYear(1404))
        assertEquals(30, JalaliDate.monthLength(1403, 12))
        assertEquals(29, JalaliDate.monthLength(1404, 12))
        assertEquals(31, JalaliDate.monthLength(1404, 6))
    }

    @Test
    fun `every day of a range round-trips`() {
        var d = LocalDate.of(2020, 1, 1)
        while (d.isBefore(LocalDate.of(2030, 1, 1))) {
            assertEquals(d, JalaliDate.of(d).toLocalDate())
            d = d.plusDays(1)
        }
    }

    @Test
    fun `month navigation and range`() {
        val m = JalaliMonth(1403, 12)
        assertEquals(JalaliMonth(1404, 1), m.next())
        assertEquals(JalaliMonth(1403, 12), m.next().previous())
        val days = (m.endMillis(ZoneOffset.UTC) - m.startMillis(ZoneOffset.UTC)) / 86_400_000
        assertEquals(30L, days)
        assertEquals("مهر ۱۴۰۳", JalaliMonth(1403, 7).label())
    }
}

class MoneyTest {
    @Test
    fun `formats rial as toman`() {
        assertEquals("۱۵۰٬۰۰۰", Money.formatToman(1_500_000))
        assertEquals("۱۵۰٬۰۰۰٫۵", Money.formatToman(1_500_005))
        assertEquals("150,000", Money.formatToman(1_500_000, persianDigits = false))
        assertEquals("-500", Money.formatToman(-5_000, persianDigits = false))
        assertEquals("150000.5", Money.tomanPlain(1_500_005))
    }

    @Test
    fun `parses toman input into rial`() {
        assertEquals(1_500_000L, Money.parseTomanInput("۱۵۰٬۰۰۰"))
        assertEquals(1_500_000L, Money.parseTomanInput("150,000"))
        assertEquals(15L, Money.parseTomanInput("1.5"))
        assertNull(Money.parseTomanInput(""))
        assertNull(Money.parseTomanInput("abc"))
        assertNull(Money.parseTomanInput("0"))
    }
}

class SmsUtilsTest {
    @Test
    fun `hash is stable within a minute and normalizes the sender`() {
        val a = Dedupe.hash("+98 700", "body", 60_000)
        assertEquals(a, Dedupe.hash("98700", "body ", 119_999))
        assertNotEquals(a, Dedupe.hash("98700", "body", 120_000))
        assertNotEquals(a, Dedupe.hash("98700", "other", 60_000))
    }

    @Test
    fun `masker hides digits but keeps format`() {
        assertEquals("مانده: 7,777,777 ۷۷۷۷/۷۷", SmsMasker.mask("مانده: 1,234,567 ۱۴۰۳/۰۷"))
    }
}

class CategoryRulesTest {
    private val rules = listOf(
        CategoryRule("salary", bodyContains = "حقوق", accountId = null, type = TxType.DEPOSIT, priority = 1),
        CategoryRule("sales", bodyContains = null, accountId = "shop", type = TxType.DEPOSIT, priority = 2),
    )

    @Test
    fun `first matching rule by priority wins`() {
        assertEquals("salary", CategoryRules.pick(rules, "واريز حقوق", "shop", TxType.DEPOSIT))
        assertEquals("sales", CategoryRules.pick(rules, "واریز", "shop", TxType.DEPOSIT))
        assertNull(CategoryRules.pick(rules, "برداشت", "shop", TxType.WITHDRAWAL))
    }
}

class CsvExporterTest {
    @Test
    fun `exports bom header and escaped cells`() {
        val csv = CsvExporter.export(
            listOf(
                ExportRow(
                    occurredAt = 1_728_812_700_000, // 2024-10-13 09:45 UTC
                    bankName = "ملت", accountName = "مغازه", scopeLabel = "کاری",
                    type = TxType.DEPOSIT, amountRial = 1_500_000, balanceRial = null,
                    category = null, payerName = "=HYPERLINK(\"x\")", note = "a,b",
                ),
            ),
            zone = ZoneOffset.UTC,
        )
        assertTrue(csv.startsWith("﻿\"تاریخ\""))
        val line = csv.lines()[1]
        assertTrue(line, line.startsWith("\"1403/07/22\",\"09:45\",\"ملت\""))
        assertTrue(line.contains("\"150000\""))
        assertTrue(line.contains("\"'=HYPERLINK(\"\"x\"\")\""))
        assertTrue(line.contains("\"a,b\""))
    }
}
