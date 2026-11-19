package com.example.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

sealed class BiometricAuthOutcome {
    data class Succeeded(val authenticatedCipher: Cipher) : BiometricAuthOutcome()
    data object Cancelled : BiometricAuthOutcome()
    data class Error(val message: String, val isLockout: Boolean = false) : BiometricAuthOutcome()
}

/**
 * Launches Jetpack [BiometricPrompt] strictly bound to a [BiometricPrompt.CryptoObject].
 *
 * Never treats a non-cryptographic biometric callback as authorization: if the returned
 * AuthenticationResult lacks an authenticated Cipher, authentication fails closed.
 */
object BiometricPromptController {

    fun authenticateForCipher(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use Master Password",
        onOutcome: (BiometricAuthOutcome) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authedCipher = result.cryptoObject?.cipher
                if (authedCipher == null) {
                    onOutcome(
                        BiometricAuthOutcome.Error(
                            "Biometric prompt returned without an authenticated Keystore cipher."
                        )
                    )
                } else {
                    onOutcome(BiometricAuthOutcome.Succeeded(authedCipher))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onOutcome(BiometricAuthOutcome.Cancelled)
                    }

                    BiometricPrompt.ERROR_LOCKOUT -> {
                        onOutcome(
                            BiometricAuthOutcome.Error(
                                message = "Biometric sensor temporarily locked. Wait 30 seconds or unlock with your master password.",
                                isLockout = true,
                            )
                        )
                    }

                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onOutcome(
                            BiometricAuthOutcome.Error(
                                message = "Biometric sensor locked until device screen unlock. Use your master password.",
                                isLockout = true,
                            )
                        )
                    }

                    else -> {
                        onOutcome(
                            BiometricAuthOutcome.Error(
                                message = errString.toString().ifBlank {
                                    "Biometric authentication failed. Use your master password."
                                }
                            )
                        )
                    }
                }
            }

            override fun onAuthenticationFailed() {
                // System sheet remains open for retry; no state mutation needed.
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()

        val prompt = BiometricPrompt(activity, executor, callback)
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}
