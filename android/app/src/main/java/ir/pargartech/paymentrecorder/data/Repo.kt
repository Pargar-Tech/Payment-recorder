package ir.pargartech.paymentrecorder.data

import android.content.Context
import android.provider.Telephony
import androidx.room.withTransaction
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.core.parser.Dedupe
import ir.pargartech.paymentrecorder.core.parser.ParseResult
import ir.pargartech.paymentrecorder.core.parser.SenderDirectory
import ir.pargartech.paymentrecorder.core.parser.SenderRule
import ir.pargartech.paymentrecorder.core.parser.SmsParser
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.core.rules.CategoryRule
import ir.pargartech.paymentrecorder.core.rules.CategoryRules
import ir.pargartech.paymentrecorder.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class HandleResult { RECORDED, NEEDS_REVIEW, DUPLICATE, IGNORED }

data class ScanSummary(val read: Int, val recorded: Int, val needsReview: Int)

/** [showHidden]: also list hidden transactions (so they can be un-hidden); they never count in totals. */
data class TxFilter(
    val accountId: String? = null,
    val scope: Scope? = null,
    val categoryId: String? = null,
    val showHidden: Boolean = false,
)

/** Records SMS as transactions; the only writer to the database. */
class Repo(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
) {
    /** Serializes SMS handling so the receiver and an inbox scan can't create duplicate accounts. */
    private val mutex = Mutex()

    val accounts: Flow<List<AccountEntity>> = db.accounts().observeAll()
    val categories: Flow<List<CategoryEntity>> = db.categories().observeAll()
    val rules: Flow<List<CategoryRuleEntity>> = db.categories().observeRules()
    val pendingReviews: Flow<List<ReviewItemEntity>> = db.reviews().observePending()
    val pendingReviewCount: Flow<Int> = db.reviews().observePendingCount()
    val senders: Flow<List<SenderEntity>> = db.senders().observeAll()

    fun transactions(from: Long, to: Long, filter: TxFilter): Flow<List<TxRow>> =
        db.transactions().observeRange(from, to, filter.accountId, filter.scope?.name, filter.categoryId, filter.showHidden)

    fun transaction(id: String): Flow<TxRow?> = db.transactions().observe(id)

    // ---- Recording ----

    suspend fun handleSms(sender: String, body: String, timestamp: Long, notify: Boolean): HandleResult =
        mutex.withLock { handleLocked(sender, body, timestamp, notify, parser()) }

    /** Reads the SMS inbox from [sinceMillis] (0 = everything) and records what it finds. */
    suspend fun scanInbox(sinceMillis: Long, onProgress: (done: Int, total: Int) -> Unit): ScanSummary =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
            )
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(sinceMillis.toString()),
                "${Telephony.Sms.DATE} ASC",
            ) ?: return@withContext ScanSummary(0, 0, 0)

            var read = 0
            var recorded = 0
            var review = 0
            cursor.use { c ->
                val total = c.count
                val parser = mutex.withLock { parser() }
                while (c.moveToNext()) {
                    val sender = c.getString(0) ?: ""
                    val body = c.getString(1) ?: ""
                    val dateSent = c.getLong(3)
                    val timestamp = if (dateSent > 0) dateSent else c.getLong(2)
                    when (mutex.withLock { handleLocked(sender, body, timestamp, notify = false, parser) }) {
                        HandleResult.RECORDED -> recorded++
                        HandleResult.NEEDS_REVIEW -> review++
                        else -> Unit
                    }
                    read++
                    if (read % 50 == 0 || read == total) onProgress(read, total)
                }
            }
            ScanSummary(read, recorded, review)
        }

    private suspend fun parser(): SmsParser {
        val rules = db.senders().all().associate { s ->
            s.sender to when (s.trust) {
                SenderTrust.IGNORED -> SenderRule.Ignored
                SenderTrust.TRUSTED -> SenderRule.Trusted(s.bankId ?: Banks.idForUnknownSender(s.sender))
            }
        }
        return SmsParser(SenderDirectory.of(rules))
    }

    private suspend fun handleLocked(
        sender: String,
        body: String,
        timestamp: Long,
        notify: Boolean,
        parser: SmsParser,
    ): HandleResult {
        val hash = Dedupe.hash(sender, body, timestamp)
        if (isDuplicate(hash, sender, body, timestamp)) return HandleResult.DUPLICATE

        return when (val r = parser.parse(sender, body)) {
            ParseResult.Ignored -> HandleResult.IGNORED
            is ParseResult.Parsed -> {
                db.withTransaction {
                    val account = accountFor(r.bankId, r.accountKey)
                    insertTx(account, r.type, r.amountRial, r.balanceRial, timestamp, sender, body, hash, TxSource.SMS)
                }
                if (notify) Notifier.transactionRecorded(context, r.type, r.amountRial, r.bankId)
                HandleResult.RECORDED
            }
            is ParseResult.NeedsReview -> {
                val now = System.currentTimeMillis()
                db.reviews().insert(
                    ReviewItemEntity(
                        id = UUID.randomUUID().toString(),
                        sender = SenderDirectory.normalize(sender),
                        body = body,
                        receivedAt = timestamp,
                        dedupeHash = hash,
                        reason = r.reason.name,
                        status = ReviewStatus.PENDING,
                        resolvedTxId = null,
                        createdAt = now,
                        updatedAt = now,
                        deviceId = prefs.deviceId,
                    ),
                )
                if (notify) Notifier.needsReview(context)
                HandleResult.NEEDS_REVIEW
            }
        }
    }

    private suspend fun isDuplicate(hash: String, sender: String, body: String, timestamp: Long): Boolean {
        if (db.transactions().existsHash(hash) || db.reviews().existsHash(hash)) return true
        // Receiver and inbox may report slightly different timestamps for the same SMS.
        val from = timestamp - NEAR_WINDOW_MS
        val to = timestamp + NEAR_WINDOW_MS
        return db.transactions().existsNear(sender, body, from, to) ||
            db.reviews().existsNear(SenderDirectory.normalize(sender), body, from, to)
    }

    private suspend fun accountFor(bankId: String, accountKey: String?): AccountEntity {
        val key = accountKey.orEmpty()
        db.accounts().find(bankId, key)?.let { return it }
        val now = System.currentTimeMillis()
        val name = Banks.displayName(bankId) + if (key.isNotEmpty()) " …$key" else ""
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            bankId = bankId,
            accountKey = key,
            displayName = name,
            scope = Scope.UNSET,
            createdAt = now,
            updatedAt = now,
            deviceId = prefs.deviceId,
        )
        db.accounts().insert(account)
        return db.accounts().find(bankId, key) ?: account
    }

    private suspend fun insertTx(
        account: AccountEntity,
        type: TxType,
        amountRial: Long,
        balanceRial: Long?,
        timestamp: Long,
        sender: String,
        body: String,
        hash: String,
        source: TxSource,
    ): TransactionEntity {
        val rules = db.categories().rules().map {
            CategoryRule(it.categoryId, it.bodyContains, it.accountId, it.type, it.priority)
        }
        val now = System.currentTimeMillis()
        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            accountId = account.id,
            type = type,
            amountRial = amountRial,
            balanceRial = balanceRial,
            occurredAt = timestamp,
            smsSender = sender,
            smsBody = body,
            dedupeHash = hash,
            categoryId = CategoryRules.pick(rules, body, account.id, type),
            payerName = "",
            note = "",
            hidden = false,
            source = source,
            createdAt = now,
            updatedAt = now,
            deviceId = prefs.deviceId,
        )
        db.transactions().insert(tx)
        return tx
    }

    // ---- Review list ----

    /** User filled in a review item by hand. */
    suspend fun resolveReview(
        item: ReviewItemEntity,
        type: TxType,
        amountRial: Long,
        accountId: String?,
        trustSender: Boolean,
    ): Unit = mutex.withLock {
        val parsed = parser().parse(item.sender, item.body)
        val bankId = when (parsed) {
            is ParseResult.Parsed -> parsed.bankId
            is ParseResult.NeedsReview -> parsed.bankId
            ParseResult.Ignored -> null
        } ?: Banks.idForUnknownSender(item.sender)
        val accountKey = when (parsed) {
            is ParseResult.Parsed -> parsed.accountKey
            is ParseResult.NeedsReview -> parsed.accountKey
            ParseResult.Ignored -> null
        }
        val balance = (parsed as? ParseResult.NeedsReview)?.balanceRial

        db.withTransaction {
            val account = accountId?.let { db.accounts().byId(it) }
                ?: accountFor(bankId, accountKey)
            val tx = insertTx(
                account, type, amountRial, balance, item.receivedAt, item.sender, item.body,
                item.dedupeHash, TxSource.MANUAL,
            )
            db.reviews().setStatus(item.id, ReviewStatus.RESOLVED, tx.id, System.currentTimeMillis())
        }
        if (trustSender) setSenderTrustLocked(item.sender, SenderTrust.TRUSTED, bankId)
    }

    suspend fun dismissReview(item: ReviewItemEntity) {
        db.reviews().setStatus(item.id, ReviewStatus.DISMISSED, null, System.currentTimeMillis())
    }

    /** Trust or ignore everything from a sender, and re-process its pending review items. */
    suspend fun setSenderTrust(sender: String, trust: SenderTrust): Unit = mutex.withLock {
        setSenderTrustLocked(sender, trust, null)
    }

    /** Undo "trust" or "ignore": the sender's next SMS are parsed as if we'd never heard of it. */
    suspend fun forgetSender(sender: String): Unit = mutex.withLock { db.senders().delete(sender) }

    private suspend fun setSenderTrustLocked(sender: String, trust: SenderTrust, bankId: String?) {
        val now = System.currentTimeMillis()
        db.senders().upsert(SenderEntity(sender, trust, bankId, now))
        val pending = db.reviews().pendingFrom(sender)
        if (trust == SenderTrust.IGNORED) {
            pending.forEach { db.reviews().setStatus(it.id, ReviewStatus.DISMISSED, null, now) }
            return
        }
        val parser = parser()
        for (item in pending) {
            val r = parser.parse(item.sender, item.body) as? ParseResult.Parsed ?: continue
            db.withTransaction {
                val account = accountFor(r.bankId, r.accountKey)
                val tx = insertTx(
                    account, r.type, r.amountRial, r.balanceRial, item.receivedAt, item.sender, item.body,
                    item.dedupeHash, TxSource.SMS,
                )
                db.reviews().setStatus(item.id, ReviewStatus.RESOLVED, tx.id, now)
            }
        }
    }

    // ---- User edits ----

    suspend fun updateTransaction(id: String, note: String, payerName: String, categoryId: String?, hidden: Boolean) =
        db.transactions().updateUserFields(id, note, payerName, categoryId, hidden, System.currentTimeMillis())

    suspend fun updateAccount(id: String, name: String, scope: Scope) =
        db.accounts().update(id, name, scope, System.currentTimeMillis())

    suspend fun addCategory(name: String) {
        val now = System.currentTimeMillis()
        db.categories().insert(CategoryEntity(UUID.randomUUID().toString(), name.trim(), now, now, prefs.deviceId))
    }

    suspend fun addRule(categoryId: String, bodyContains: String?, accountId: String?, type: TxType?) {
        val now = System.currentTimeMillis()
        val priority = (db.categories().rules().maxOfOrNull { it.priority } ?: 0) + 1
        db.categories().insertRule(
            CategoryRuleEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                bodyContains = bodyContains?.trim()?.ifEmpty { null },
                accountId = accountId,
                type = type,
                priority = priority,
                createdAt = now,
                updatedAt = now,
                deviceId = prefs.deviceId,
            ),
        )
    }

    suspend fun deleteRule(id: String) = db.categories().deleteRule(id)

    private companion object {
        const val NEAR_WINDOW_MS = 10 * 60_000L
    }
}
