package com.example.data

import com.example.crypto.KdfBenchmarkResult
import com.example.crypto.KdfParameters
import com.example.domain.model.PairedDevice
import com.example.domain.model.SecuritySettings
import com.example.domain.model.VaultItem
import kotlinx.coroutines.flow.StateFlow

data class VaultCryptoMetadata(
    val formatVersion: Int = 1,
    val vaultIdPreview: String = "",
    val kdfSummary: String = KdfParameters.OWASP_MOBILE_BALANCED.summaryLabel(),
    val encryptedRecordCount: Int = 0,
    val lastBenchmark: KdfBenchmarkResult? = null,
)

/**
 * Architectural boundary for vault operations.
 * Phase 2 encrypts all vault records and VEK wrappers in memory via VaultCryptoEngine
 * (Argon2id + HKDF-SHA256 + AES-256-GCM with AAD).
 * Phase 3 will persist the encrypted VaultHeader and EncryptedVaultRecord envelopes to SQLite.
 */
interface VaultRepository {
    val items: StateFlow<List<VaultItem>>
    val hasVault: StateFlow<Boolean>
    val isUnlocked: StateFlow<Boolean>
    val cryptoMetadata: StateFlow<VaultCryptoMetadata>

    suspend fun createVault(masterPassword: CharArray): String
    suspend fun openDemoVault(): String
    suspend fun unlockWithMasterPassword(candidate: CharArray): Boolean
    suspend fun unlockWithMockBiometric(): Boolean
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): Boolean
    suspend fun recoverWithKey(recoveryKey: String, newMasterPassword: CharArray): Boolean
    suspend fun benchmarkKdf(): KdfBenchmarkResult
    fun lockVault()
    fun upsertItem(item: VaultItem): VaultItem
    fun deleteItem(itemId: String)
    fun toggleFavorite(itemId: String)
    fun markItemUsed(itemId: String)
}

/**
 * Architectural boundary for paired device management (mocked until Phase 6).
 */
interface DeviceRepository {
    val devices: StateFlow<List<PairedDevice>>
    fun revokeDevice(deviceId: String)
}

/**
 * Architectural boundary for local security and appearance preferences.
 */
interface SettingsRepository {
    val settings: StateFlow<SecuritySettings>
    fun updateSettings(transform: (SecuritySettings) -> SecuritySettings)
}
