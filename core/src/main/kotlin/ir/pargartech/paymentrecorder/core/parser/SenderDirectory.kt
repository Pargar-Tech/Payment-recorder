package ir.pargartech.paymentrecorder.core.parser

/** What the user has told us about a sender, learned from the review list. */
sealed interface SenderRule {
    /** Messages from this sender are bank messages for [bankId] (a bank id or a `sender:` id). */
    data class Trusted(val bankId: String) : SenderRule

    /** Never parse or review messages from this sender. */
    data object Ignored : SenderRule
}

fun interface SenderDirectory {
    fun lookup(normalizedSender: String): SenderRule?

    companion object {
        val EMPTY = SenderDirectory { null }

        fun of(rules: Map<String, SenderRule>) = SenderDirectory { rules[it] }

        /** "+98 700-717" and "98700717" are the same sender. */
        fun normalize(sender: String): String =
            sender.trim().lowercase().filterNot { it == ' ' || it == '-' }.removePrefix("+")
    }
}
