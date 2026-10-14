package ir.pargartech.paymentrecorder.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint/face or the phone's own PIN/pattern, via BiometricPrompt. */
object AppLock {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Lock after the app has been in the background this long. */
    const val RELOCK_AFTER_MS = 30_000L

    /** Process-wide: a fresh process starts locked. */
    val session = LockSession(RELOCK_AFTER_MS)

    /** True if the phone has a screen lock or biometrics we can ask for. */
    fun isAvailable(context: Context): Boolean {
        val biometric = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        val keyguard = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        return biometric || keyguard
    }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن تراکنش‌های من")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
