package ir.pargartech.paymentrecorder.data

import android.content.Context
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Stable per-install id, stamped on every record for future multi-phone sync. */
    val deviceId: String
        get() = sp.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    var lockEnabled: Boolean
        get() = sp.getBoolean(KEY_LOCK, false)
        set(value) = sp.edit().putBoolean(KEY_LOCK, value).apply()

    var initialScanDone: Boolean
        get() = sp.getBoolean(KEY_SCAN_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_SCAN_DONE, value).apply()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_SCAN_DONE = "initial_scan_done"
    }
}
