package com.example.data

import com.example.crypto.KdfBenchmarkResult
import com.example.crypto.KdfParameters
import com.example.crypto.VaultBackupCodec
import com.example.domain.model.PairedDevice
import com.example.domain.model.SecuritySettings
import com.example.domain.model.VaultItem
import com.example.security.BiometricKeyState
import com.example.security.CipherPreparationResult
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.Cipher

data class VaultCryptoMetadata(
    val formatVersion: Int = 1,
    val vaultIdPreview: String = "",
    val kdfSummary: String = KdfParameters.OWASP_MOBILE_BALANCED.summaryLabel(),
    val encryptedRecordCount: Int = 0,
    val corruptedRecordIds: List<String> = emptyList(),
    val lastBenchmark: KdfBenchmarkResult? = null,
    val isPersistedToSqlite: Boolean = true,
)

/**
 * Architectural boundary for encrypted local SQLite vault operations (Phase 3 & Phase 4).
 */
interface VaultRepository {
    val isStorageInitialized: StateFlow<Boolean>
    val items: StateFlow<List<VaultItem>>
    val hasVault: StateFlow<Boolean>
    val isUnlocked: StateFlow<Boolean>
    val cryptoMetadata: StateFlow<VaultCryptoMetadata>
    val biometricState: StateFlow<BiometricKeyState>

    suspend fun createVault(masterPassword: CharArray): String
    suspend fun openDemoVault(): String
    suspend fun unlockWithMasterPassword(candidate: CharArray): Boolean
    suspend fun refreshBiometricState()
    suspend fun prepareBiometricEnrollmentCipher(): CipherPreparationResult
    suspend fun completeBiometricEnrollment(authenticatedCipher: Cipher): Boolean
    suspend fun disableBiometricUnlock()
    suspend fun prepareBiometricUnlockCipher(): CipherPreparationResult
    suspend fun completeBiometricUnlock(authenticatedCipher: Cipher): Boolean
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): Boolean
    suspend fun recoverWithKey(recoveryKey: String, newMasterPassword: CharArray): Boolean
    suspend fun benchmarkKdf(): KdfBenchmarkResult
    suspend fun upsertItem(item: VaultItem): VaultItem
    suspend fun deleteItem(itemId: String)
    suspend fun toggleFavorite(itemId: String)
    suspend fun markItemUsed(itemId: String)
    suspend fun exportEncryptedBackup(): ByteArray
    suspend fun inspectBackup(backupBytes: ByteArray): VaultBackupCodec.BackupInspectionSummary
    suspend fun restoreBackupWithMasterPassword(backupBytes: ByteArray, masterPassword: CharArray): Boolean
    suspend fun restoreBackupWithRecoveryKey(
        backupBytes: ByteArray,
        recoveryKey: String,
        newMasterPassword: CharArray,
    ): Boolean
    fun lockVault()
}

interface DeviceRepository {
    val devices: StateFlow<List<PairedDevice>>
    fun revokeDevice(deviceId: String)
}

interface SettingsRepository {
    val settings: StateFlow<SecuritySettings>
    suspend fun updateSettings(transform: (SecuritySettings) -> SecuritySettings)
}
