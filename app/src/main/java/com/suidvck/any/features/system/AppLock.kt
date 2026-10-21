package com.suidvck.any.features.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.suidvck.any.R

/**
 * Optional app lock backed by [BiometricPrompt].
 *
 * The toggle lives in the shared "settings" prefs under "app_lock" (default off).
 * When the device has no biometric hardware or nothing is enrolled, the app is
 * let through with a toast instead of locking the user out.
 */
object AppLock {

    private const val PREFS = "settings"
    private const val KEY = "app_lock"

    fun isEnabled(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, on)
            .apply()
    }

    /**
     * Shows the biometric / device-credential prompt when the lock is on.
     * [onUnlocked] always runs on the main thread. When the lock is disabled it
     * runs immediately; when the hardware cannot authenticate, the app is let
     * through so the user is never stranded outside their own app.
     */
    fun unlock(activity: FragmentActivity, onUnlocked: () -> Unit) {
        if (!isEnabled(activity)) {
            onMain(onUnlocked)
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (!canAuthenticate(activity, authenticators)) {
            allowWithToast(activity, onUnlocked)
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                onMain(onUnlocked)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> allowWithToast(activity, onUnlocked)
                    // Cancel, timeout and lockout keep the app locked.
                }
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            callback
        )

        // DEVICE_CREDENTIAL is part of the allowed authenticators, so a negative
        // button text must NOT be set (the system provides its own cancel action).
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.system_lock_title))
            .setSubtitle(activity.getString(R.string.system_lock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        try {
            prompt.authenticate(info)
        } catch (t: Throwable) {
            allowWithToast(activity, onUnlocked)
        }
    }

    private fun canAuthenticate(
        activity: FragmentActivity,
        authenticators: Int
    ): Boolean = runCatching {
        val manager = BiometricManager.from(activity)
        manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS ||
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS ||
            manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    private fun allowWithToast(activity: FragmentActivity, onUnlocked: () -> Unit) {
        onMain {
            if (!activity.isFinishing && !activity.isDestroyed) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.system_lock_unsupported),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onUnlocked()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post { block() }
        }
    }
}
