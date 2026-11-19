package com.example.data

import com.example.domain.model.DevicePlatform
import com.example.domain.model.DeviceStatus
import com.example.domain.model.PairedDevice
import com.example.domain.model.PasswordEngine
import com.example.domain.model.SecuritySettings
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Phase 1 in-memory mock repository.
 *
 * Security boundary notice:
 * - No real credentials are persisted to disk.
 * - No cryptography is performed or claimed in Phase 1.
 * - Deterministic sample data is strictly synthetic (.example / .test domains).
 */
class MockVaultRepository : VaultRepository, DeviceRepository, SettingsRepository {

    private var activePrototypePassphrase: String = DEFAULT_DEMO_PASSPHRASE
    private var nextIdCounter: Int = 100
    private var clockTick: Long = 1_730_000_000_000L

    private val _items = MutableStateFlow(seedMockItems())
    override val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _hasVault = MutableStateFlow(false)
    override val hasVault: StateFlow<Boolean> = _hasVault.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _devices = MutableStateFlow(seedMockDevices())
    override val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _settings = MutableStateFlow(SecuritySettings())
    override val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    override fun createMockVault(masterPassword: String) {
        // Never trim or mutate the user's master password.
        activePrototypePassphrase = masterPassword
        _hasVault.value = true
        _isUnlocked.value = true
    }

    fun openDemoVault() {
        _hasVault.value = true
        _isUnlocked.value = true
    }

    override fun unlockWithMasterPassword(candidate: String): Boolean {
        val matched = candidate == activePrototypePassphrase
        if (matched) {
            _hasVault.value = true
            _isUnlocked.value = true
        }
        return matched
    }

    override fun unlockWithMockBiometric(): Boolean {
        if (!_settings.value.biometricUnlockEnabled) return false
        _hasVault.value = true
        _isUnlocked.value = true
        return true
    }

    override fun lockVault() {
        _isUnlocked.value = false
    }

    override fun upsertItem(item: VaultItem): VaultItem {
        clockTick += 60_000L
        val normalized = PasswordEngine.normalizeDomain(item.website).normalizedDomain
        val isNew = item.id.isBlank()
        val resolvedId = if (isNew) "mock-${++nextIdCounter}" else item.id
        val finalItem = item.copy(
            id = resolvedId,
            normalizedDomain = normalized,
            createdAtLabel = if (isNew) "Just now" else item.createdAtLabel,
            updatedAtLabel = "Just now",
            lastUsedEpochMs = clockTick,
        )
        _items.update { current ->
            if (isNew) {
                listOf(finalItem) + current
            } else {
                current.map { existing -> if (existing.id == resolvedId) finalItem else existing }
            }
        }
        return finalItem
    }

    override fun deleteItem(itemId: String) {
        _items.update { current -> current.filterNot { it.id == itemId } }
    }

    override fun toggleFavorite(itemId: String) {
        _items.update { current ->
            current.map { item ->
                if (item.id == itemId) item.copy(isFavorite = !item.isFavorite) else item
            }
        }
    }

    override fun markItemUsed(itemId: String) {
        clockTick += 1_000L
        _items.update { current ->
            current.map { item ->
                if (item.id == itemId) item.copy(lastUsedEpochMs = clockTick) else item
            }
        }
    }

    override fun revokeDevice(deviceId: String) {
        _devices.update { current ->
            current.map { device ->
                if (device.id == deviceId && device.status != DeviceStatus.THIS_DEVICE) {
                    device.copy(
                        status = DeviceStatus.REVOKED,
                        lastSyncLabel = "Access revoked (mock)",
                    )
                } else {
                    device
                }
            }
        }
    }

    override fun updateSettings(transform: (SecuritySettings) -> SecuritySettings) {
        _settings.update(transform)
    }

    companion object {
        const val DEFAULT_DEMO_PASSPHRASE: String = "emerald-vault"

        private fun seedMockItems(): List<VaultItem> = listOf(
            VaultItem(
                id = "mock-1",
                type = VaultItemType.LOGIN,
                title = "CodeForge Git",
                username = "alex.chen@example.com",
                secret = "Pine-Harbor-48!Quartz",
                website = "https://codeforge.example.com",
                normalizedDomain = "codeforge.example.com",
                notes = "Mock SSH backup codes stored offline in hardware binder.",
                isFavorite = true,
                createdAtLabel = "Oct 12, 2026",
                updatedAtLabel = "Today, 9:24 AM",
                lastUsedEpochMs = 1_729_999_900_000L,
            ),
            VaultItem(
                id = "mock-2",
                type = VaultItemType.LOGIN,
                title = "Northwind Mail",
                username = "alex.chen@example.org",
                secret = "V9#mKp2!cL7z@wT4nQ",
                website = "https://mail.northwind.example.org",
                normalizedDomain = "mail.northwind.example.org",
                notes = "Primary personal inbox (prototype fixture).",
                isFavorite = true,
                createdAtLabel = "Sep 03, 2026",
                updatedAtLabel = "Yesterday, 6:15 PM",
                lastUsedEpochMs = 1_729_999_800_000L,
            ),
            VaultItem(
                id = "mock-3",
                type = VaultItemType.LOGIN,
                title = "Evergreen Credit Union",
                username = "achen_member_402",
                secret = "Ridge-Moss-73-Lantern!",
                website = "https://banking.evergreen.test",
                normalizedDomain = "banking.evergreen.test",
                notes = "Mock financial login for Phase 1 UX verification.",
                isFavorite = true,
                createdAtLabel = "Aug 19, 2026",
                updatedAtLabel = "Nov 02, 2026",
                lastUsedEpochMs = 1_729_999_700_000L,
            ),
            VaultItem(
                id = "mock-4",
                type = VaultItemType.LOGIN,
                title = "HomeLab NAS",
                username = "vault_admin",
                secret = "B4@tR8!xN2pL9#qM5s",
                website = "https://nas.local.test",
                normalizedDomain = "nas.local.test",
                notes = "Local LAN storage dashboard.",
                isFavorite = false,
                createdAtLabel = "Nov 05, 2026",
                updatedAtLabel = "Nov 14, 2026",
                lastUsedEpochMs = 1_729_999_500_000L,
            ),
            VaultItem(
                id = "mock-5",
                type = VaultItemType.SECURE_NOTE,
                title = "Router Admin & VLAN Map",
                username = "Home Network",
                secret = "VLAN 10: Trusted • VLAN 20: IoT • Mock Recovery PIN: 8492-3104",
                website = "",
                normalizedDomain = "",
                notes = "Reference topology for local Wi-Fi isolation.",
                isFavorite = false,
                createdAtLabel = "Jul 28, 2026",
                updatedAtLabel = "Nov 10, 2026",
                lastUsedEpochMs = 1_729_999_400_000L,
                secondaryIdentifier = "Network note • Masked",
            ),
            VaultItem(
                id = "mock-6",
                type = VaultItemType.SECURE_NOTE,
                title = "Offline Recovery Checklist",
                username = "Vault Hygiene",
                secret = "1. Keep paper recovery sheet in fireproof box. 2. Verify test restore quarterly.",
                website = "",
                normalizedDomain = "",
                notes = "Reminder checklist for Phase 2/3 recovery implementation.",
                isFavorite = false,
                createdAtLabel = "Oct 01, 2026",
                updatedAtLabel = "Oct 29, 2026",
                lastUsedEpochMs = 1_729_999_300_000L,
                secondaryIdentifier = "Checklist • Masked",
            ),
            VaultItem(
                id = "mock-7",
                type = VaultItemType.IDENTITY,
                title = "Alex Chen — Primary Profile",
                username = "alex.chen@example.com",
                secret = "420 Cedar Ridge Rd, Portland, OR 97205 • +1 (555) 014-8920",
                website = "",
                normalizedDomain = "",
                notes = "Synthetic contact details for prototype review.",
                isFavorite = false,
                createdAtLabel = "Jun 15, 2026",
                updatedAtLabel = "Sep 18, 2026",
                lastUsedEpochMs = 1_729_999_200_000L,
                secondaryIdentifier = "Portland, OR",
            ),
            VaultItem(
                id = "mock-8",
                type = VaultItemType.PAYMENT_CARD,
                title = "Evergreen Debit (Mock)",
                username = "Alex Chen",
                secret = "4000 0000 0000 4821 • Exp 09/29 • Mock CVV 000",
                website = "https://banking.evergreen.test",
                normalizedDomain = "banking.evergreen.test",
                notes = "Non-functional test card number for Phase 1 UI preview.",
                isFavorite = false,
                createdAtLabel = "Aug 19, 2026",
                updatedAtLabel = "Oct 11, 2026",
                lastUsedEpochMs = 1_729_999_100_000L,
                secondaryIdentifier = "•••• 4821",
            ),
        )

        private fun seedMockDevices(): List<PairedDevice> = listOf(
            PairedDevice(
                id = "dev-this",
                friendlyName = "This Android Device",
                platform = DevicePlatform.ANDROID,
                lastSyncLabel = "Local vault active",
                status = DeviceStatus.THIS_DEVICE,
                fingerprintPreview = "4A9C-E17B",
            ),
            PairedDevice(
                id = "dev-linux",
                friendlyName = "ThinkPad Workstation",
                platform = DevicePlatform.LINUX,
                lastSyncLabel = "Mock sync • Today, 9:18 AM",
                status = DeviceStatus.ACTIVE,
                fingerprintPreview = "7F3K-9Q2M",
            ),
            PairedDevice(
                id = "dev-windows",
                friendlyName = "Studio Desktop",
                platform = DevicePlatform.WINDOWS,
                lastSyncLabel = "Mock sync • Yesterday, 8:42 PM",
                status = DeviceStatus.ACTIVE,
                fingerprintPreview = "2D8P-5N1X",
            ),
        )
    }
}
