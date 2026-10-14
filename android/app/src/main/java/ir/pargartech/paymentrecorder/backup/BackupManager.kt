package ir.pargartech.paymentrecorder.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import ir.pargartech.paymentrecorder.App
import ir.pargartech.paymentrecorder.data.AppDatabase
import ir.pargartech.paymentrecorder.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manual backup/restore of the whole database to a user-picked file (via the system file
 * picker), so data survives losing the phone without the app needing internet access.
 */
object BackupManager {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    suspend fun export(context: Context, target: Uri) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        // Flush the WAL into the main file so a single-file copy is complete.
        app.db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Validates [source] and replaces the database with it, then restarts the app.
     * Returns an error message, or never returns on success.
     */
    suspend fun restore(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        val temp = File(context.cacheDir, "restore.db")
        context.contentResolver.openInputStream(source)?.use { input ->
            temp.outputStream().use { input.copyTo(it) }
        } ?: return@withContext "فایل باز نشد"

        val error = validate(temp)
        if (error != null) {
            temp.delete()
            return@withContext error
        }

        app.db.close()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        temp.copyTo(dbFile, overwrite = true)
        temp.delete()

        val restart = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(restart)
        Runtime.getRuntime().exit(0)
        null
    }

    private fun validate(file: File): String? {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        if (read != header.size || !header.contentEquals(SQLITE_HEADER)) return "این فایل پشتیبان معتبر نیست"
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val hasTable = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'transactions'", null,
                ).use { it.moveToFirst() }
                if (!hasTable) return "این فایل پشتیبان این برنامه نیست"
                if (db.version > CURRENT_VERSION) return "فایل پشتیبان از نسخه جدیدتر برنامه است"
                null
            }
        } catch (e: Exception) {
            "فایل پشتیبان خراب است"
        }
    }

    private const val CURRENT_VERSION = 1
}
