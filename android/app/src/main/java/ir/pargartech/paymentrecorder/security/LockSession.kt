package ir.pargartech.paymentrecorder.security

/**
 * Whether the app is currently unlocked. Held for the life of the process (see [AppLock.session]),
 * so it survives the activity being recreated on rotation, while a new process — including one
 * Android restores from recents after killing the app in the background — always starts locked.
 */
class LockSession(private val relockAfterMs: Long) {
    private var unlocked = false
    private var backgroundedAt: Long? = null

    fun isLocked(lockEnabled: Boolean) = lockEnabled && !unlocked

    fun onUnlocked() {
        unlocked = true
        backgroundedAt = null
    }

    /** [now] is `SystemClock.elapsedRealtime()`. */
    fun onBackground(now: Long) {
        backgroundedAt = now
    }

    fun onForeground(now: Long) {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        if (now - at > relockAfterMs) unlocked = false
    }
}
