package com.example.data

import com.example.domain.model.PairedDevice
import com.example.domain.model.SecuritySettings
import com.example.domain.model.VaultItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Architectural boundary for vault item operations.
 * Phase 1 is backed solely by an in-memory mock implementation.
 * Phase 3 will supply an encrypted SQLite implementation behind this same contract.
 */
interface VaultRepository {
    val items: StateFlow<List<VaultItem>>
    val hasVault: StateFlow<Boolean>
    val isUnlocked: StateFlow<Boolean>

    fun createMockVault(masterPassword: String)
    fun unlockWithMasterPassword(candidate: String): Boolean
    fun unlockWithMockBiometric(): Boolean
    fun lockVault()
    fun upsertItem(item: VaultItem): VaultItem
    fun deleteItem(itemId: String)
    fun toggleFavorite(itemId: String)
    fun markItemUsed(itemId: String)
}

/**
 * Architectural boundary for paired device management.
 * Phase 1 uses deterministic mocked device states only.
 * Phase 6 will implement X25519 + HKDF local Wi-Fi pairing behind this boundary.
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
