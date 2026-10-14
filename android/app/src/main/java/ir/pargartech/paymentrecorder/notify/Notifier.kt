package ir.pargartech.paymentrecorder.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.pargartech.paymentrecorder.R
import ir.pargartech.paymentrecorder.core.money.Money
import ir.pargartech.paymentrecorder.core.parser.Banks
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.ui.MainActivity

/** Local notifications only — the app has no network access. */
object Notifier {
    private const val CHANNEL_TX = "transactions"
    private const val CHANNEL_REVIEW = "review"
    private const val REVIEW_NOTIFICATION_ID = 1
    const val EXTRA_OPEN_REVIEW = "open_review"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TX, context.getString(R.string.channel_transactions), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun transactionRecorded(context: Context, type: TxType, amountRial: Long, bankId: String) {
        val title = if (type == TxType.DEPOSIT) "واریز ثبت شد" else "برداشت ثبت شد"
        val text = "${Money.formatToman(amountRial)} تومان — ${Banks.displayName(bankId)}"
        notify(context, CHANNEL_TX, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), title, text, openReview = false)
    }

    fun needsReview(context: Context) {
        notify(
            context, CHANNEL_REVIEW, REVIEW_NOTIFICATION_ID,
            "پیامک بانکی نیاز به بررسی دارد",
            "این پیامک خودکار خوانده نشد. برای ثبت دستی بزنید.",
            openReview = true,
        )
    }

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String, openReview: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_REVIEW, openReview)
        val pending = PendingIntent.getActivity(
            context, if (openReview) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
