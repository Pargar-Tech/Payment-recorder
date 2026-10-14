package ir.pargartech.paymentrecorder.core.rules

import ir.pargartech.paymentrecorder.core.parser.TextNormalizer
import ir.pargartech.paymentrecorder.core.parser.TxType

/** "If the SMS contains X / is on account Y / is a deposit → category Z". Null fields match anything. */
data class CategoryRule(
    val categoryId: String,
    val bodyContains: String?,
    val accountId: String?,
    val type: TxType?,
    val priority: Int,
)

object CategoryRules {
    /** First matching rule by ascending priority; null if none match. */
    fun pick(rules: List<CategoryRule>, body: String, accountId: String, type: TxType): String? {
        val text = TextNormalizer.normalize(body).lowercase()
        return rules.sortedBy { it.priority }.firstOrNull { rule ->
            val needle = rule.bodyContains?.let { TextNormalizer.normalize(it).lowercase().trim() }
            (needle.isNullOrEmpty() || text.contains(needle)) &&
                (rule.accountId == null || rule.accountId == accountId) &&
                (rule.type == null || rule.type == type)
        }?.categoryId
    }
}
