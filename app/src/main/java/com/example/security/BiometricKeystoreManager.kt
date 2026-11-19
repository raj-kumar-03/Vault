package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import com.example.crypto.VaultFormatConstants
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BiometricAvailability {
    READY,
    NO_BIOMETRICS_ENROLLED,
    DEVICE_LOCK_NOT_SET,
    HARDWARE_UNAVAILABLE,
    KEY_INVALIDATED,
}

data class BiometricKeyState(
    val availability: BiometricAvailability = BiometricAvailability.HARDWARE_UNAVAILABLE,
    val hasBoundEnvelope: Boolean = false,
    val isDeviceSecure: Boolean = false,
    val keyAlias: String = AndroidBiometricKeystoreManager.DEFAULT_KEY_ALIAS,
    val statusMessage: String = "Checking device biometric hardware…",
) {
    val canUnlockWithBiometrics: Boolean
        get() = availability == BiometricAvailability.READY && hasBoundEnvelope

    val canEnrollBiometrics: Boolean
        get() = availability == BiometricAvailability.READY ||
            availability == BiometricAvailability.KEY_INVALIDATED
}

sealed class CipherPreparationResult {
    data class Ready(val cipher: Cipher) : CipherPreparationResult()
    data object KeyInvalidated : CipherPreparationResult()
    data class Unavailable(val reason: String) : CipherPreparationResult()
}

data class SealedVekEnvelope(
    val keyAlias: String,
    val iv: ByteArray,
    val ciphertextAndTag: ByteArray,
)

/**
 * Architectural boundary separating Android Keystore & BiometricManager hardware APIs
 * from repository and UI layers, enabling deterministic JVM/Robolectric testing.
 */
interface PlatformKeystoreBridge {
    val keyAlias: String

    fun queryAvailability(hasStoredEnvelope: Boolean, wasInvalidated: Boolean): BiometricKeyState

    fun prepareEncryptCipher(): CipherPreparationResult

    fun prepareDecryptCipher(iv: ByteArray): CipherPreparationResult

    fun sealVek(authenticatedCipher: Cipher, vekBytes: ByteArray): SealedVekEnvelope

    fun unsealVek(authenticatedCipher: Cipher, ciphertextAndTag: ByteArray): ByteArray

    fun deleteKey()
}

/**
 * Production Android Keystore implementation for Phase 4.
 *
 * Security invariants:
 * - Generates a device-bound AES-256-GCM key inside "AndroidKeyStore".
 * - Requires strong biometric authentication per cryptographic operation (timeout = 0 / -1)
 *   so BiometricPrompt acts strictly as a CryptoObject authorization gate, never a boolean flag.
 * - Sets setInvalidatedByBiometricEnrollment(true) so enrolling/removing biometrics or disabling
 *   device lock permanently invalidates the key and forces master-password re-verification.
 */
class AndroidBiometricKeystoreManager(
    private val context: Context,
    override val keyAlias: String = DEFAULT_KEY_ALIAS,
) : PlatformKeystoreBridge {

    override fun queryAvailability(
        hasStoredEnvelope: Boolean,
        wasInvalidated: Boolean,
    ): BiometricKeyState {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = runCatching { keyguard?.isDeviceSecure == true }.getOrDefault(false)

        if (!isDeviceSecure) {
            return BiometricKeyState(
                availability = BiometricAvailability.DEVICE_LOCK_NOT_SET,
                hasBoundEnvelope = false,
                isDeviceSecure = false,
                keyAlias = keyAlias,
                statusMessage = "Set a device screen lock (PIN, pattern, or password) in Android Settings before enabling Keystore biometric unlock.",
            )
        }

        val biometricManager = BiometricManager.from(context)
        val canAuth = runCatching {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)

        if (wasInvalidated) {
            return BiometricKeyState(
                availability = BiometricAvailability.KEY_INVALIDATED,
                hasBoundEnvelope = false,
                isDeviceSecure = true,
                keyAlias = keyAlias,
                statusMessage = "Biometric enrollment or device lock changed. The previous Keystore key was invalidated—unlock with your master password and re-enable biometrics.",
            )
        }

        return when (canAuth) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricKeyState(
                availability = BiometricAvailability.READY,
                hasBoundEnvelope = hasStoredEnvelope && hasKeystoreKey(),
                isDeviceSecure = true,
                keyAlias = keyAlias,
                statusMessage = if (hasStoredEnvelope && hasKeystoreKey()) {
                    "Android Keystore AES-256-GCM key bound to strong biometrics (per-operation CryptoObject)."
                } else {
                    "Strong biometric hardware ready. Enable to bind your vault key to Android Keystore."
                },
            )

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricKeyState(
                availability = BiometricAvailability.NO_BIOMETRICS_ENROLLED,
                hasBoundEnvelope = false,
                isDeviceSecure = true,
                keyAlias = keyAlias,
                statusMessage = "No strong biometrics enrolled on this device. Add a fingerprint or Class 3 biometric in Android Settings.",
            )

            else -> BiometricKeyState(
                availability = BiometricAvailability.HARDWARE_UNAVAILABLE,
                hasBoundEnvelope = false,
                isDeviceSecure = true,
                keyAlias = keyAlias,
                statusMessage = "Strong biometric hardware is unavailable on this device. Master password unlock remains fully active.",
            )
        }
    }

    override fun prepareEncryptCipher(): CipherPreparationResult {
        return try {
            // Recreate fresh key on new enrollment so stale invalidation state is cleared cleanly.
            deleteKey()
            val secretKey = generateHardwareBoundKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            CipherPreparationResult.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            CipherPreparationResult.KeyInvalidated
        } catch (_: InvalidKeyException) {
            deleteKey()
            CipherPreparationResult.KeyInvalidated
        } catch (e: Exception) {
            CipherPreparationResult.Unavailable(
                "Android Keystore could not prepare biometric encryption (${e.javaClass.simpleName})."
            )
        }
    }

    override fun prepareDecryptCipher(iv: ByteArray): CipherPreparationResult {
        if (iv.size != VaultFormatConstants.GCM_NONCE_BYTES) {
            return CipherPreparationResult.Unavailable("Biometric envelope IV is malformed.")
        }
        return try {
            val secretKey = loadExistingKey()
                ?: return CipherPreparationResult.KeyInvalidated
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(VaultFormatConstants.GCM_TAG_BITS, iv),
            )
            CipherPreparationResult.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            CipherPreparationResult.KeyInvalidated
        } catch (_: UnrecoverableKeyException) {
            deleteKey()
            CipherPreparationResult.KeyInvalidated
        } catch (_: InvalidKeyException) {
            deleteKey()
            CipherPreparationResult.KeyInvalidated
        } catch (e: Exception) {
            CipherPreparationResult.Unavailable(
                "Android Keystore could not prepare biometric decryption (${e.javaClass.simpleName})."
            )
        }
    }

    override fun sealVek(authenticatedCipher: Cipher, vekBytes: ByteArray): SealedVekEnvelope {
        require(vekBytes.size == VaultFormatConstants.AES_KEY_BYTES) {
            "VEK must be ${VaultFormatConstants.AES_KEY_BYTES} bytes."
        }
        val iv = authenticatedCipher.iv?.copyOf()
            ?: throw IllegalStateException("Authenticated Keystore cipher produced no IV.")
        val ciphertextAndTag = authenticatedCipher.doFinal(vekBytes)
        return SealedVekEnvelope(
            keyAlias = keyAlias,
            iv = iv,
            ciphertextAndTag = ciphertextAndTag,
        )
    }

    override fun unsealVek(authenticatedCipher: Cipher, ciphertextAndTag: ByteArray): ByteArray {
        val unwrapped = authenticatedCipher.doFinal(ciphertextAndTag)
        if (unwrapped.size != VaultFormatConstants.AES_KEY_BYTES) {
            unwrapped.fill(0)
            throw IllegalStateException("Unsealed VEK length mismatch.")
        }
        return unwrapped
    }

    override fun deleteKey() {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias)
            }
        }
    }

    private fun hasKeystoreKey(): Boolean = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.containsAlias(keyAlias)
    }.getOrDefault(false)

    private fun loadExistingKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey(keyAlias, null) as? SecretKey
    }

    private fun generateHardwareBoundKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "aran_biometric_vek_wrap_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
