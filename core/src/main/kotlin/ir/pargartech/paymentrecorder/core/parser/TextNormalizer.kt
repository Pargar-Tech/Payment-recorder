package ir.pargartech.paymentrecorder.core.parser

/**
 * Brings Persian/Arabic SMS text into one canonical form so the parser only has to
 * deal with ASCII digits, Persian ی/ک and plain separators.
 */
object TextNormalizer {
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                '٬' -> sb.append(',') // Arabic thousands separator
                '٫' -> sb.append('.') // Arabic decimal separator
                '−', '–' -> sb.append('-')
                '‌' -> sb.append(' ') // ZWNJ
                '‎', '‏', in '‪'..'‮', in '⁦'..'⁩' -> Unit // bidi marks
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Converts ASCII digits to Persian digits for display. */
    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') sb.append('۰' + (ch - '0')) else sb.append(ch)
        }
        return sb.toString()
    }
}
