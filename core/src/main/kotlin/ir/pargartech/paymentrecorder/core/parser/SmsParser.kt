package ir.pargartech.paymentrecorder.core.parser

enum class TxType { DEPOSIT, WITHDRAWAL }

sealed interface ParseResult {
    /** A transaction we're confident about. */
    data class Parsed(
        val bankId: String,
        val type: TxType,
        val amountRial: Long,
        val balanceRial: Long?,
        val accountKey: String?,
    ) : ParseResult

    /**
     * Looks like a bank transaction, but something is missing. Goes to the review list;
     * whatever was extracted is kept to pre-fill the review form.
     */
    data class NeedsReview(
        val reason: ReviewReason,
        val bankId: String?,
        val type: TxType?,
        val amountRial: Long?,
        val balanceRial: Long?,
        val accountKey: String?,
    ) : ParseResult

    /** Not a transaction (OTP, promotion, personal SMS, ignored sender). */
    data object Ignored : ParseResult
}

enum class ReviewReason { UNKNOWN_SENDER, NO_TYPE, NO_AMOUNT }

/**
 * Generic keyword/regex parser for Iranian bank SMS.
 *
 * Bank-specific formats should be handled by adding samples to the tests and adjusting
 * keywords here; see docs/TECH.md "Parser".
 */
class SmsParser(private val senders: SenderDirectory = SenderDirectory.EMPTY) {

    fun parse(sender: String, body: String): ParseResult {
        val senderKey = SenderDirectory.normalize(sender)
        val senderRule = senders.lookup(senderKey)
        if (senderRule == SenderRule.Ignored) return ParseResult.Ignored

        val text = TextNormalizer.normalize(body)
        val lower = text.lowercase()
        if (IGNORE_KEYWORDS.any { lower.contains(it) }) return ParseResult.Ignored

        val bankId = Banks.detect(sender, text)?.id ?: (senderRule as? SenderRule.Trusted)?.bankId

        val scan = NumberScanner.scan(text)
        val amountToken = scan.amounts.firstOrNull { it.label == Label.AMOUNT }
            ?: scan.amounts.firstOrNull { it.sign != null && it.label != Label.BALANCE }
            ?: scan.amounts.firstOrNull { it.label == null && it.grouped }
        val balanceToken = scan.amounts.firstOrNull { it.label == Label.BALANCE && it !== amountToken }

        val type = when (amountToken?.sign) {
            '+' -> TxType.DEPOSIT
            '-' -> TxType.WITHDRAWAL
            else -> keywordType(lower)
        }
        val amount = amountToken?.value
        val balance = balanceToken?.let { if (it.sign == '-') -it.value else it.value }
        val accountKey = scan.accountKeys.firstOrNull()

        val hasAmountKeyword = AMOUNT_KEYWORDS.any { lower.contains(it) }
        val looksLikeTx = if (bankId == null) {
            type != null && amount != null
        } else {
            (type != null && scan.amounts.isNotEmpty()) || (hasAmountKeyword && amount != null)
        }
        if (!looksLikeTx) return ParseResult.Ignored

        val reason = when {
            bankId == null -> ReviewReason.UNKNOWN_SENDER
            type == null -> ReviewReason.NO_TYPE
            amount == null -> ReviewReason.NO_AMOUNT
            else -> return ParseResult.Parsed(bankId, type, amount, balance, accountKey)
        }
        return ParseResult.NeedsReview(reason, bankId, type, amount, balance, accountKey)
    }

    /** Picks the type whose keyword appears first; null if there is none. */
    private fun keywordType(lower: String): TxType? {
        val deposit = DEPOSIT_KEYWORDS.mapNotNull { kw -> lower.indexOf(kw).takeIf { it >= 0 } }.minOrNull()
        val withdrawal = WITHDRAWAL_KEYWORDS.mapNotNull { kw -> lower.indexOf(kw).takeIf { it >= 0 } }.minOrNull()
        return when {
            deposit == null && withdrawal == null -> null
            withdrawal == null -> TxType.DEPOSIT
            deposit == null -> TxType.WITHDRAWAL
            deposit < withdrawal -> TxType.DEPOSIT
            else -> TxType.WITHDRAWAL
        }
    }

    internal enum class Label { AMOUNT, BALANCE }

    internal data class AmountToken(
        val value: Long,
        val sign: Char?,
        val grouped: Boolean,
        val label: Label?,
    )

    internal data class Scan(val amounts: List<AmountToken>, val accountKeys: List<String>)

    internal object NumberScanner {
        private val maskedAccount = Regex("""[\d\-]*[*•]+[\d*•\-]*\d+|\d+[xX]+\d+|(?:\.{2,}|…)\d{3,}""")
        private val dashedAccount = Regex("""(?<![\d,])\d{2,}(?:-\d{2,}){2,}(?![\d,])""")
        private val longNumber = Regex("""(?<![\d,.])\d{10,}(?![\d,])""")
        private val date = Regex("""(?<!\d)\d{2,4}[/.\-]\d{1,2}[/.\-]\d{1,2}(?!\d)""")
        private val compactDateTime = Regex("""(?<![\d,])\d{4}-\d{4}(?![\d,])""")
        private val time = Regex("""(?<!\d)\d{1,2}:\d{2}(?::\d{2})?(?!\d)""")
        private val amount = Regex("""([+\-])?(\d{1,3}(?:,\d{3})+|\d+)([+\-])?""")
        private val unitAfter = Regex("""^\s*(ریال|تومان)""")

        private const val LOOKBACK = 25

        fun scan(text: String): Scan {
            val work = StringBuilder(text)
            val accounts = mutableListOf<Pair<Int, String>>()

            fun blank(range: IntRange) {
                for (i in range) work.setCharAt(i, ' ')
            }

            fun takeAccounts(regex: Regex, accept: (MatchResult) -> Boolean = { true }) {
                for (m in regex.findAll(work.toString())) {
                    if (!accept(m)) continue
                    val digits = m.value.takeLastWhile { it.isDigit() || it == '-' }.filter { it.isDigit() }
                    if (digits.length >= 2) accounts += m.range.first to digits.takeLast(4)
                    blank(m.range)
                }
            }

            takeAccounts(maskedAccount)
            for (regex in listOf(date, compactDateTime, time)) {
                for (m in regex.findAll(work.toString())) blank(m.range)
            }
            takeAccounts(dashedAccount)
            // A long unseparated number is an account/card number only if it looks like one;
            // otherwise it may be an amount written without separators.
            takeAccounts(longNumber) { m ->
                val before = work.substring(maxOf(0, m.range.first - 15), m.range.first).lowercase()
                m.value.startsWith("0") || m.value.length >= 13 || ACCOUNT_KEYWORDS.any { before.contains(it) }
            }

            val cleaned = work.toString()
            val amounts = amount.findAll(cleaned).mapNotNull { m ->
                val number = m.groupValues[2]
                val value = number.replace(",", "").toLongOrNull() ?: return@mapNotNull null
                val sign = m.groupValues[1].firstOrNull() ?: m.groupValues[3].firstOrNull()
                val unit = unitAfter.find(cleaned.substring(m.range.last + 1))?.groupValues?.get(1)
                val rial = if (unit == "تومان") value * 10 else value
                if (rial < 1_000) return@mapNotNull null
                AmountToken(rial, sign, number.contains(','), labelBefore(cleaned, m.range.first))
            }.toList()

            return Scan(amounts, accounts.sortedBy { it.first }.map { it.second })
        }

        /** Label from the closest keyword shortly before the number. */
        private fun labelBefore(text: String, start: Int): Label? {
            val window = text.substring(maxOf(0, start - LOOKBACK), start).lowercase()
            val balanceAt = BALANCE_KEYWORDS.maxOf { window.lastIndexOf(it) }
            val amountAt = (AMOUNT_KEYWORDS + DEPOSIT_KEYWORDS + WITHDRAWAL_KEYWORDS + TRANSFER_KEYWORDS)
                .maxOf { window.lastIndexOf(it) }
            return when {
                balanceAt < 0 && amountAt < 0 -> null
                balanceAt > amountAt -> Label.BALANCE
                else -> Label.AMOUNT
            }
        }
    }

    companion object {
        /** Messages containing these are never transactions (OTP, login codes). */
        val IGNORE_KEYWORDS = listOf(
            "رمز پویا", "رمز دوم پویا", "رمز یکبار", "رمز یک بار", "کد تایید", "کد تأیید",
            "کد فعالسازی", "کد فعال سازی", "کد ورود", "otp", "verification code",
        )
        val DEPOSIT_KEYWORDS = listOf("واریز", "بستانکار", "دریافت", "deposit", "credit")
        val WITHDRAWAL_KEYWORDS = listOf(
            "برداشت", "خرید", "بدهکار", "پرداخت", "کارمزد", "withdraw", "purchase", "debit",
        )
        val TRANSFER_KEYWORDS = listOf("انتقال")
        val AMOUNT_KEYWORDS = listOf("مبلغ", "amount", "amt")
        val ACCOUNT_KEYWORDS = listOf("حساب", "کارت", "سپرده", "شبا", "acc", "card")
        val BALANCE_KEYWORDS = listOf("مانده", "موجودی", "ماند", "balance", "bal")
    }
}
