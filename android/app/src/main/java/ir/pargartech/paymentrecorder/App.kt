package ir.pargartech.paymentrecorder

import android.app.Application
import ir.pargartech.paymentrecorder.data.AppDatabase
import ir.pargartech.paymentrecorder.data.Prefs
import ir.pargartech.paymentrecorder.data.Repo
import ir.pargartech.paymentrecorder.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app-wide singletons (manual DI; the app is small). */
class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val prefs by lazy { Prefs(this) }
    val db by lazy { AppDatabase.build(this) }
    val repo by lazy { Repo(this, db, prefs) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
