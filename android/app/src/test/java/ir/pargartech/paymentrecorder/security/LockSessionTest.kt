package ir.pargartech.paymentrecorder.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSessionTest {
    private val session = LockSession(relockAfterMs = 30_000)

    @Test
    fun `a new process starts locked`() {
        // Android may kill the app in the background and restore it later; that must not skip the lock.
        assertTrue(session.isLocked(lockEnabled = true))
    }

    @Test
    fun `never locked when the lock is off`() {
        assertFalse(session.isLocked(lockEnabled = false))
    }

    @Test
    fun `stays unlocked after a short trip to the background`() {
        session.onUnlocked()
        session.onBackground(now = 1_000)
        session.onForeground(now = 20_000)
        assertFalse(session.isLocked(lockEnabled = true))
    }

    @Test
    fun `relocks after the timeout in the background`() {
        session.onUnlocked()
        session.onBackground(now = 1_000)
        session.onForeground(now = 31_001)
        assertTrue(session.isLocked(lockEnabled = true))
    }

    @Test
    fun `coming to the foreground without going to the background keeps the state`() {
        session.onUnlocked()
        session.onForeground(now = 999_999)
        assertFalse(session.isLocked(lockEnabled = true))
    }
}
