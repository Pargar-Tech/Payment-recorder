package ir.pargartech.paymentrecorder.core.parser

/**
 * Known Iranian banks.
 *
 * Detection is deliberately conservative: Persian names only match when written as
 * "بانک X" (or a multi-word name that can't be a person's name), because many bank
 * names are also common words or first names (سامان، سینا، آینده، شهر، دی).
 *
 * Sender keywords match alphanumeric sender IDs. They are best guesses and are NOT
 * verified against real SMS — numeric short-code senders are learned at runtime when
 * the user marks a sender as trusted (see [SenderDirectory]).
 */
data class Bank(
    val id: String,
    val nameFa: String,
    val bodyPatterns: List<Regex>,
    val senderKeywords: List<String>,
)

object Banks {
    const val UNKNOWN_SENDER_PREFIX = "sender:"

    private fun bank(id: String, nameFa: String, sender: List<String>, vararg extra: String): Bank {
        val namePattern = Regex.escape(nameFa).replace(" ", "\\E\\s*\\Q")
        val patterns = buildList {
            add(Regex("بانک\\s*$namePattern"))
            extra.forEach { add(Regex(it)) }
        }
        return Bank(id, nameFa, patterns, sender)
    }

    val all: List<Bank> = listOf(
        bank("melli", "ملی", listOf("bmi", "melli", "bankmelli")),
        bank("sepah", "سپه", listOf("sepah", "banksepah")),
        bank("mellat", "ملت", listOf("mellat", "bankmellat")),
        bank("tejarat", "تجارت", listOf("tejarat", "tejaratbank")),
        bank("saderat", "صادرات", listOf("saderat", "bsi")),
        bank("refah", "رفاه", listOf("refah", "refahbank")),
        bank("maskan", "مسکن", listOf("maskan", "bankmaskan")),
        bank("keshavarzi", "کشاورزی", listOf("keshavarzi", "bki", "agri")),
        bank("pasargad", "پاسارگاد", listOf("pasargad", "bpi")),
        bank("saman", "سامان", listOf("saman", "sb24", "samanbank")),
        bank("parsian", "پارسیان", listOf("parsian")),
        bank("eghtesad_novin", "اقتصاد نوین", listOf("enbank", "eghtesad"), "اقتصاد\\s*نوین"),
        bank("ayandeh", "آینده", listOf("ayandeh", "ba24")),
        bank("sina", "سینا", listOf("sinabank")),
        bank("shahr", "شهر", listOf("shahrbank", "bankshahr")),
        bank("dey", "دی", listOf("deybank", "bankdey")),
        bank("sarmayeh", "سرمایه", listOf("sarmayeh")),
        bank("karafarin", "کارآفرین", listOf("karafarin"), "بانک\\s*کارافرین"),
        bank("gardeshgari", "گردشگری", listOf("tourism", "gardeshgari")),
        bank("iran_zamin", "ایران زمین", listOf("izbank", "iranzamin"), "ایران\\s*زمین"),
        bank("khavarmianeh", "خاورمیانه", listOf("middleeast", "khavarmianeh"), "خاور\\s*میانه"),
        bank("post_bank", "پست بانک", listOf("postbank"), "پست\\s*بانک"),
        bank("resalat", "رسالت", listOf("resalat"), "قرض\\s*الحسنه\\s*رسالت"),
        bank("mehr_iran", "مهر ایران", listOf("mehriran", "qmb"), "مهر\\s*ایران"),
        bank("tosee_taavon", "توسعه تعاون", listOf("ttbank"), "توسعه\\s*تعاون"),
        bank("tosee_saderat", "توسعه صادرات", listOf("edbi"), "توسعه\\s*صادرات"),
        bank("sanat_madan", "صنعت و معدن", listOf("bim"), "صنعت\\s*و\\s*معدن"),
        bank("melal", "ملل", listOf("melal")),
        bank("blu", "بلو", listOf("blu", "blubank"), "بلو\\s*بانک"),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Bank? = byId[id]

    /** Human-readable name for a bank id, including learned `sender:` ids. */
    fun displayName(bankId: String): String =
        byId[bankId]?.nameFa ?: bankId.removePrefix(UNKNOWN_SENDER_PREFIX)

    fun idForUnknownSender(normalizedSender: String) = UNKNOWN_SENDER_PREFIX + normalizedSender

    /** Detects the bank from the sender ID first, then from the (normalized) body. */
    fun detect(sender: String, normalizedBody: String): Bank? {
        val letters = sender.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        if (letters.any { it in 'a'..'z' }) {
            all.firstOrNull { bank ->
                bank.senderKeywords.any { kw -> if (kw.length >= 5) letters.contains(kw) else letters == kw }
            }?.let { return it }
        }
        return all.firstOrNull { bank -> bank.bodyPatterns.any { it.containsMatchIn(normalizedBody) } }
    }
}
