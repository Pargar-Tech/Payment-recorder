package ir.pargartech.paymentrecorder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import ir.pargartech.paymentrecorder.App
import kotlinx.coroutines.launch

/** Records bank SMS as they arrive, even when the app isn't open. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (messages.isEmpty()) return

        // Long SMS arrive as several parts; join them per sender.
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
        val app = context.applicationContext as App
        val pending = goAsync()
        app.scope.launch {
            try {
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    app.repo.handleSms(sender, body, parts.first().timestampMillis, notify = true)
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to record SMS", e)
            } finally {
                pending.finish()
            }
        }
    }
}
