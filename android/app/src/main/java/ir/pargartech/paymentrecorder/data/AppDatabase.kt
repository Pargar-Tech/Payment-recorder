package ir.pargartech.paymentrecorder.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val TX_ROW_SELECT = """
    SELECT t.*, a.displayName AS accountName, a.bankId AS bankId, a.accountKey AS accountKey,
           a.scope AS scope, c.name AS categoryName
    FROM transactions t
    JOIN accounts a ON a.id = t.accountId
    LEFT JOIN categories c ON c.id = t.categoryId
"""

@Dao
interface TransactionDao {
    @Query(
        TX_ROW_SELECT + """
        WHERE t.hidden = 0 AND t.occurredAt >= :from AND t.occurredAt < :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND (:scope IS NULL OR a.scope = :scope)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
        ORDER BY t.occurredAt DESC
        """,
    )
    fun observeRange(from: Long, to: Long, accountId: String?, scope: String?, categoryId: String?): Flow<List<TxRow>>

    @Query(TX_ROW_SELECT + " WHERE t.id = :id")
    fun observe(id: String): Flow<TxRow?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dedupeHash = :hash)")
    suspend fun existsHash(hash: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM transactions
           WHERE smsSender = :sender AND smsBody = :body AND occurredAt BETWEEN :from AND :to)""",
    )
    suspend fun existsNear(sender: String, body: String, from: Long, to: Long): Boolean

    @Query(
        """UPDATE transactions SET note = :note, payerName = :payerName, categoryId = :categoryId,
           hidden = :hidden, updatedAt = :now WHERE id = :id""",
    )
    suspend fun updateUserFields(id: String, note: String, payerName: String, categoryId: String?, hidden: Boolean, now: Long)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY displayName")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE bankId = :bankId AND accountKey = :accountKey")
    suspend fun find(bankId: String, accountKey: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Query("UPDATE accounts SET displayName = :name, scope = :scope, updatedAt = :now WHERE id = :id")
    suspend fun update(id: String, name: String, scope: Scope, now: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT * FROM category_rules ORDER BY priority")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules ORDER BY priority")
    suspend fun rules(): List<CategoryRuleEntity>

    @Insert
    suspend fun insertRule(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteRule(id: String)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_items WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    fun observePending(): Flow<List<ReviewItemEntity>>

    @Query("SELECT COUNT(*) FROM review_items WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM review_items WHERE status = 'PENDING' AND sender = :sender")
    suspend fun pendingFrom(sender: String): List<ReviewItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ReviewItemEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM review_items WHERE dedupeHash = :hash)")
    suspend fun existsHash(hash: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM review_items
           WHERE sender = :sender AND body = :body AND receivedAt BETWEEN :from AND :to)""",
    )
    suspend fun existsNear(sender: String, body: String, from: Long, to: Long): Boolean

    @Query("UPDATE review_items SET status = :status, resolvedTxId = :txId, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: ReviewStatus, txId: String?, now: Long)
}

@Dao
interface SenderDao {
    @Query("SELECT * FROM senders")
    suspend fun all(): List<SenderEntity>

    @Upsert
    suspend fun upsert(sender: SenderEntity)
}

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        CategoryRuleEntity::class,
        ReviewItemEntity::class,
        SenderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun reviews(): ReviewDao
    abstract fun senders(): SenderDao

    companion object {
        const val NAME = "payment-recorder.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
