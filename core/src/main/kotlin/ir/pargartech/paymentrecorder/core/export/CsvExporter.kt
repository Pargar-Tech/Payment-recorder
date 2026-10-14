package ir.pargartech.paymentrecorder.core.export

import ir.pargartech.paymentrecorder.core.date.JalaliDate
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.TxType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ExportRow(
    val occurredAt: Long,
    val bankName: String,
    val accountName: String,
    val scopeLabel: String,
    val type: TxType,
    val amountRial: Long,
    val balanceRial: Long?,
    val category: String?,
    val payerName: String,
    val note: String,
)

/** CSV that Excel opens correctly with Persian text (UTF-8 with BOM). Amounts in toman. */
object CsvExporter {
    private val HEADER = listOf(
        "تاریخ", "ساعت", "بانک", "حساب", "شخصی/کاری", "نوع",
        "مبلغ (تومان)", "مانده (تومان)", "دسته", "پرداخت‌کننده", "یادداشت",
    )
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun export(rows: List<ExportRow>, zone: ZoneId = JalaliDate.TEHRAN): String = buildString {
        append('﻿')
        appendLine(HEADER.joinToString(",") { cell(it) })
        for (r in rows) {
            val fields = listOf(
                JalaliDate.fromEpochMillis(r.occurredAt, zone).format(),
                TIME.format(Instant.ofEpochMilli(r.occurredAt).atZone(zone)),
                r.bankName,
                r.accountName,
                r.scopeLabel,
                if (r.type == TxType.DEPOSIT) "واریز" else "برداشت",
                Money.tomanPlain(r.amountRial),
                r.balanceRial?.let { Money.tomanPlain(it) } ?: "",
                r.category ?: "",
                r.payerName,
                r.note,
            )
            appendLine(fields.joinToString(",") { cell(it) })
        }
    }

    /** Quotes every cell and neutralises spreadsheet formulas in user-entered text. */
    private fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@" && value.toDoubleOrNull() == null) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
