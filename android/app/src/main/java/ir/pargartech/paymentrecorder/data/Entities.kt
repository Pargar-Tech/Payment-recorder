package ir.pargartech.paymentrecorder.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.pargartech.paymentrecorder.core.parser.TxType

// Every synced entity has a UUID id, updatedAt and deviceId so v2 sync can merge
// records from several phones without a schema migration.

enum class Scope { UNSET, PERSONAL, BUSINESS }

enum class TxSource { SMS, MANUAL }

enum class ReviewStatus { PENDING, RESOLVED, DISMISSED }

enum class SenderTrust { TRUSTED, IGNORED }

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["bankId", "accountKey"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    /** Last digits of the account/card number from the SMS; "" when the SMS has none. */
    val accountKey: String,
    val displayName: String,
    val scope: Scope,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("occurredAt"),
        Index(value = ["dedupeHash"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: TxType,
    val amountRial: Long,
    val balanceRial: Long?,
    val occurredAt: Long,
    val smsSender: String,
    val smsBody: String,
    val dedupeHash: String,
    val categoryId: String?,
    val payerName: String,
    val note: String,
    /** Hidden instead of deleted, so the hash keeps a re-scan from bringing it back. */
    val hidden: Boolean,
    val source: TxSource,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
)

@Entity(tableName = "category_rules", indices = [Index("categoryId")])
data class CategoryRuleEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val bodyContains: String?,
    val accountId: String?,
    val type: TxType?,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
)

/** A bank-looking SMS the parser couldn't fully read. */
@Entity(
    tableName = "review_items",
    indices = [Index(value = ["dedupeHash"], unique = true), Index("sender")],
)
data class ReviewItemEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val dedupeHash: String,
    val reason: String,
    val status: ReviewStatus,
    val resolvedTxId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
)

/** What the user told us about a sender (keyed by normalized sender). */
@Entity(tableName = "senders")
data class SenderEntity(
    @PrimaryKey val sender: String,
    val trust: SenderTrust,
    val bankId: String?,
    val updatedAt: Long,
)

/** A transaction joined with its account and category, for lists. */
data class TxRow(
    @Embedded val tx: TransactionEntity,
    val accountName: String,
    val bankId: String,
    val accountKey: String,
    val scope: Scope,
    val categoryName: String?,
)
