package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.KdfParameters
import com.example.crypto.VaultFormatConstants
import com.example.data.PersistentVaultRepository
import com.example.data.local.VaultDatabase
import com.example.domain.model.AutoLockInterval
import com.example.domain.model.ClipboardClearInterval
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.security.BiometricAvailability
import com.example.security.BiometricKeyState
import com.example.security.CipherPreparationResult
import com.example.security.LockReason
import com.example.security.PlatformKeystoreBridge
import com.example.security.SealedVekEnvelope
import com.example.security.SecretClipboardController
import com.example.security.VaultAutoLockPolicy
import com.example.ui.state.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Phase 4 Platform Security & Authentication verification suite.
 *
 * Verifies:
 * 1. Device-bound Keystore AES-256-GCM biometric CryptoObject wrap/unwrap roundtrip.
 * 2. Biometric enrollment change / KeyPermanentlyInvalidatedException detection, cleanup,
 *    and safe fallback to master-password unlock with zero vault data loss.
 * 3. Monotonic-clock background & foreground inactivity auto-lock enforcement (including
 *    IMMEDIATELY lock-on-background and clock-rollback fail-closed protection).
 * 4. Best-effort secret clipboard match-only clearing (clears matching secret without
 *    clobbering unrelated text copied by the user afterwards).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlatformSecurityPhase4Test {

    private lateinit var context: Context
    private lateinit var database: VaultDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VaultDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `keystore biometric cryptoobject enrolls unseals VEK and recovers cleanly from key invalidation`() = runTest {
        val fakeKeystore = FakeSoftwareKeystoreBridge()
        val repo = PersistentVaultRepository(
            vaultDao = database.vaultDao(),
            keystoreBridge = fakeKeystore,
            kdfParameters = KdfParameters.TEST_FAST,
            ioDispatcher = Dispatchers.Unconfined,
            cryptoDispatcher = Dispatchers.Unconfined,
        )
        repo.initializeFromDisk()

        val masterPass = "phase4-hardware-keystore-pass"
        repo.createVault(masterPass.toCharArray())
        repo.upsertItem(
            VaultItem(
                id = "rec-bio-1",
                type = VaultItemType.LOGIN,
                title = "Keystore Protected Login",
                username = "dev@example.com",
                secret = "Secret-Protected-By-Keystore-42!",
                website = "https://secure.example.com",
                normalizedDomain = "secure.example.com",
                notes = "",
                isFavorite = true,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 1_000L,
            )
        )

        // 1. Enroll biometrics via CryptoObject encrypt cipher
        assertFalse(repo.biometricState.value.canUnlockWithBiometrics)
        val encryptPrep = repo.prepareBiometricEnrollmentCipher()
        assertTrue(encryptPrep is CipherPreparationResult.Ready)
        val enrolled = repo.completeBiometricEnrollment((encryptPrep as CipherPreparationResult.Ready).cipher)
        assertTrue(enrolled)
        assertTrue(repo.biometricState.value.canUnlockWithBiometrics)
        assertTrue(repo.settings.value.biometricUnlockEnabled)

        // Verify SQLite biometric_envelopes stores encrypted ciphertext + IV (not raw VEK)
        val storedEnvelope = database.vaultDao().getBiometricEnvelope()
        assertNotNull(storedEnvelope)
        assertEquals(VaultFormatConstants.GCM_NONCE_BYTES, storedEnvelope!!.iv.size)
        assertTrue(storedEnvelope.ciphertextAndTag.size > VaultFormatConstants.AES_KEY_BYTES)

        // 2. Lock vault (wipes VEK) and unlock via BiometricPrompt CryptoObject decrypt cipher
        repo.lockVault()
        assertFalse(repo.isUnlocked.value)
        assertTrue(repo.items.value.isEmpty())

        val decryptPrep = repo.prepareBiometricUnlockCipher()
        assertTrue(decryptPrep is CipherPreparationResult.Ready)
        val unlocked = repo.completeBiometricUnlock((decryptPrep as CipherPreparationResult.Ready).cipher)
        assertTrue(unlocked)
        assertTrue(repo.isUnlocked.value)
        assertEquals("Secret-Protected-By-Keystore-42!", repo.items.value.first().secret)

        // 3. Simulate Android biometric enrollment change -> KeyPermanentlyInvalidatedException
        repo.lockVault()
        fakeKeystore.simulateEnrollmentInvalidation()

        val invalidatedPrep = repo.prepareBiometricUnlockCipher()
        assertTrue(invalidatedPrep is CipherPreparationResult.KeyInvalidated)
        assertEquals(BiometricAvailability.KEY_INVALIDATED, repo.biometricState.value.availability)
        assertFalse(repo.biometricState.value.canUnlockWithBiometrics)
        assertFalse(repo.settings.value.biometricUnlockEnabled)
        assertNull(database.vaultDao().getBiometricEnvelope())

        // 4. Master password fallback still unlocks vault with zero data loss
        assertTrue(repo.unlockWithMasterPassword(masterPass.toCharArray()))
        assertEquals(1, repo.items.value.size)
        assertEquals("Secret-Protected-By-Keystore-42!", repo.items.value.first().secret)
    }

    @Test
    fun `monotonic auto lock enforces manual-only timed resume clock rollback and foreground idle`() {
        var nowMs = 10_000L
        val policy = VaultAutoLockPolicy(elapsedClockMs = { nowMs })

        // NEVER ("Manual lock only") does not auto-lock on backgrounding or inactivity
        assertFalse(policy.onAppBackgrounded(AutoLockInterval.NEVER))
        nowMs += 3_600_000L
        assertFalse(policy.shouldLockOnForeground(AutoLockInterval.NEVER))
        assertFalse(policy.shouldLockForInactivity(AutoLockInterval.NEVER))

        // 1-minute interval does NOT lock immediately on backgrounding or after 30s
        policy.resetActivity(nowMs)
        assertFalse(policy.onAppBackgrounded(AutoLockInterval.ONE_MINUTE))
        nowMs += 30_000L
        assertFalse(policy.shouldLockOnForeground(AutoLockInterval.ONE_MINUTE))

        // Locks after 60s elapsed in background
        assertFalse(policy.onAppBackgrounded(AutoLockInterval.ONE_MINUTE))
        nowMs += 60_000L
        assertTrue(policy.shouldLockOnForeground(AutoLockInterval.ONE_MINUTE))

        // Clock moving backwards fails closed
        assertFalse(policy.onAppBackgrounded(AutoLockInterval.FIVE_MINUTES))
        nowMs -= 1_000L
        assertTrue(policy.shouldLockOnForeground(AutoLockInterval.FIVE_MINUTES))

        // Foreground inactivity timeout resets on user interaction
        nowMs = 100_000L
        policy.resetActivity(nowMs)
        nowMs += 4 * 60_000L
        assertFalse(policy.shouldLockForInactivity(AutoLockInterval.FIVE_MINUTES))
        policy.resetActivity(nowMs) // touch resets timer
        nowMs += 4 * 60_000L
        assertFalse(policy.shouldLockForInactivity(AutoLockInterval.FIVE_MINUTES))
        nowMs += 60_000L
        assertTrue(policy.shouldLockForInactivity(AutoLockInterval.FIVE_MINUTES))
    }

    @Test
    fun `viewmodel lifecycle auto lock wipes VEK and records accurate lock reason`() = runTest {
        var fakeClockMs = 5_000L
        val repo = PersistentVaultRepository(
            vaultDao = database.vaultDao(),
            kdfParameters = KdfParameters.TEST_FAST,
            ioDispatcher = Dispatchers.Unconfined,
            cryptoDispatcher = Dispatchers.Unconfined,
        )
        repo.initializeFromDisk()
        repo.openDemoVault()
        repo.updateSettings { it.copy(autoLockInterval = AutoLockInterval.ONE_MINUTE) }

        val vm = VaultViewModel(repository = repo, elapsedClockMs = { fakeClockMs })
        backgroundScope.launch(Dispatchers.Unconfined) { vm.uiState.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(repo.isUnlocked.value)

        // Background for 20s -> remains unlocked
        assertFalse(vm.onAppBackgrounded())
        fakeClockMs += 20_000L
        assertFalse(vm.onAppForegrounded())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(repo.isUnlocked.value)

        // Background for 65s -> auto-locks and wipes items
        assertFalse(vm.onAppBackgrounded())
        fakeClockMs += 65_000L
        assertTrue(vm.onAppForegrounded())
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(repo.isUnlocked.value)
        assertTrue(repo.items.value.isEmpty())
        assertEquals(LockReason.AUTO_LOCK_BACKGROUND, vm.uiState.value.lockReason)
    }

    @Test
    fun `secret clipboard controller clears matching secret without clobbering newer non-secret clipboard items`() {
        val controller = SecretClipboardController(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Copy secret -> clearing removes it
        val secret = "Super-Secret-Clipboard-99!"
        controller.copyValue("Password", secret, isSecret = true, clearInterval = ClipboardClearInterval.THIRTY_SECONDS)
        assertEquals(secret, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(controller.clearIfStillHoldingSecret())
        val afterClear = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        assertTrue(afterClear.isNullOrEmpty())

        // Copy secret, then user copies something unrelated -> controller must NOT clobber user's text
        controller.copyValue("Password", secret, isSecret = true, clearInterval = ClipboardClearInterval.THIRTY_SECONDS)
        clipboard.setPrimaryClip(ClipData.newPlainText("Other", "User copied a grocery list"))
        assertFalse(controller.clearIfStillHoldingSecret())
        assertEquals("User copied a grocery list", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    /**
     * Software AES-256-GCM Keystore bridge for deterministic JVM/Robolectric testing of real
     * Cipher encrypt/decrypt envelopes and simulated biometric enrollment invalidation.
     */
    private class FakeSoftwareKeystoreBridge : PlatformKeystoreBridge {
        override val keyAlias: String = "test_fake_keystore_key"
        private var key: SecretKey? = null
        private var invalidated: Boolean = false
        private val random = SecureRandom()

        fun simulateEnrollmentInvalidation() {
            invalidated = true
        }

        override fun queryAvailability(hasStoredEnvelope: Boolean, wasInvalidated: Boolean): BiometricKeyState {
            if (wasInvalidated || invalidated) {
                return BiometricKeyState(
                    availability = BiometricAvailability.KEY_INVALIDATED,
                    hasBoundEnvelope = false,
                    isDeviceSecure = true,
                    keyAlias = keyAlias,
                    statusMessage = "Biometric enrollment changed; key invalidated.",
                )
            }
            return BiometricKeyState(
                availability = BiometricAvailability.READY,
                hasBoundEnvelope = hasStoredEnvelope && key != null,
                isDeviceSecure = true,
                keyAlias = keyAlias,
                statusMessage = "Ready",
            )
        }

        override fun prepareEncryptCipher(): CipherPreparationResult {
            invalidated = false
            val gen = KeyGenerator.getInstance("AES").apply { init(256, random) }
            val secretKey = gen.generateKey()
            key = secretKey
            val iv = ByteArray(VaultFormatConstants.GCM_NONCE_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return CipherPreparationResult.Ready(cipher)
        }

        override fun prepareDecryptCipher(iv: ByteArray): CipherPreparationResult {
            if (invalidated || key == null) {
                key = null
                return CipherPreparationResult.KeyInvalidated
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return CipherPreparationResult.Ready(cipher)
        }

        override fun sealVek(authenticatedCipher: Cipher, vekBytes: ByteArray): SealedVekEnvelope {
            val ciphertext = authenticatedCipher.doFinal(vekBytes)
            return SealedVekEnvelope(
                keyAlias = keyAlias,
                iv = authenticatedCipher.iv.copyOf(),
                ciphertextAndTag = ciphertext,
            )
        }

        override fun unsealVek(authenticatedCipher: Cipher, ciphertextAndTag: ByteArray): ByteArray =
            authenticatedCipher.doFinal(ciphertextAndTag)

        override fun deleteKey() {
            key = null
            invalidated = false
        }
    }
}
