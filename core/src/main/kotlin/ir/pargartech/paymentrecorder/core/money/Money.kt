package ir.pargartech.paymentrecorder.core.money

import ir.pargartech.paymentrecorder.core.parser.TextNormalizer

/** Amounts are stored in rial (as banks send them) and shown in toman (rial / 10). */
object Money {
    private const val PERSIAN_GROUP = '٬'
    private const val PERSIAN_DECIMAL = '٫'

    /** 1_500_005 rial -> "۱۵۰٬۰۰۰٫۵" (toman). */
    fun formatToman(rial: Long, persianDigits: Boolean = true): String {
        val negative = rial < 0
        val abs = Math.abs(rial)
        val whole = group(abs / 10, if (persianDigits) PERSIAN_GROUP else ',')
        val fraction = abs % 10
        val ascii = buildString {
            if (negative) append('-')
            append(whole)
            if (fraction != 0L) append(if (persianDigits) PERSIAN_DECIMAL else '.').append(fraction)
        }
        return if (persianDigits) TextNormalizer.toPersianDigits(ascii) else ascii
    }

    /** Plain toman number for spreadsheets: "150000.5". */
    fun tomanPlain(rial: Long): String = formatToman(rial, persianDigits = false).replace(",", "")

    /** Parses user input in toman (Persian or ASCII digits, any separators) into rial. */
    fun parseTomanInput(input: String): Long? {
        val normalized = TextNormalizer.normalize(input).replace(",", "").replace(" ", "").trim()
        if (normalized.isEmpty()) return null
        val parts = normalized.split('.')
        if (parts.size > 2 || parts.any { p -> p.any { !it.isDigit() } }) return null
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let {
            if (it.length != 1) return null
            it.toLong()
        } ?: 0L
        val rial = whole * 10 + fraction
        return rial.takeIf { it > 0 }
    }

    private fun group(value: Long, separator: Char): String {
        val s = value.toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(separator)
            sb.append(c)
        }
        return sb.toString()
    }
}
