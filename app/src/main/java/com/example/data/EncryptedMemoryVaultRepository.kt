package com.example.data

import com.example.crypto.DefaultVaultCryptoEngine
import com.example.crypto.EncryptedVaultRecord
import com.example.crypto.KdfBenchmarkResult
import com.example.crypto.KdfParameters
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultCryptoException
import com.example.crypto.VaultHeader
import com.example.crypto.VaultRecordCodec
import com.example.crypto.VaultSessionKey
import com.example.domain.model.DevicePlatform
import com.example.domain.model.DeviceStatus
import com.example.domain.model.PairedDevice
import com.example.domain.model.PasswordEngine
import com.example.domain.model.SecuritySettings
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Phase 2 encrypted in-memory vault repository.
 *
 * All vault items are encrypted at rest inside [encryptedStore] as [EncryptedVaultRecord]s
 * using AES-256-GCM with canonical AAD and a random 256-bit VEK wrapped by Argon2id (master
 * password) and HKDF-SHA256 (independent recovery key). Locking wipes the VEK and clears
 * plaintext records from memory.
 *
 * Phase 3 will persist [vaultHeader] and [encryptedStore] to local SQLite.
 */
class EncryptedMemoryVaultRepository(
    private val cryptoEngine: VaultCryptoEngine = DefaultVaultCryptoEngine(),
    private val kdfParameters: KdfParameters = KdfParameters.OWASP_MOBILE_BALANCED,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VaultRepository, DeviceRepository, SettingsRepository {

    private var vaultHeader: VaultHeader? = null
    private var activeSessionKey: VaultSessionKey? = null
    private val encryptedStore = LinkedHashMap<String, EncryptedVaultRecord>()
    private var prototypeDemoRecoveryKey: String? = null
    private var nextIdCounter: Int = 100
    private var clockTick: Long = 1_730_000_000_000L

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    override val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _hasVault = MutableStateFlow(false)
    override val hasVault: StateFlow<Boolean> = _hasVault.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _cryptoMetadata = MutableStateFlow(
        VaultCryptoMetadata(kdfSummary = kdfParameters.summaryLabel())
    )
    override val cryptoMetadata: StateFlow<VaultCryptoMetadata> = _cryptoMetadata.asStateFlow()

    private val _devices = MutableStateFlow(seedMockDevices())
    override val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _settings = MutableStateFlow(SecuritySettings())
    override val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    override suspend fun createVault(masterPassword: CharArray): String = withContext(cryptoDispatcher) {
        try {
            activeSessionKey?.wipe()
            encryptedStore.clear()

            val bundle = cryptoEngine.createVault(
                masterPassword = masterPassword,
                kdfParameters = kdfParameters,
            )
            vaultHeader = bundle.header
            activeSessionKey = bundle.sessionKey
            prototypeDemoRecoveryKey = bundle.formattedRecoveryKey

            _items.value = emptyList()
            _hasVault.value = true
            _isUnlocked.value = true
            publishMetadata(bundle.header)
            bundle.formattedRecoveryKey
        } finally {
            masterPassword.fill('\u0000')
        }
    }

    override suspend fun openDemoVault(): String = withContext(cryptoDispatcher) {
        val existingHeader = vaultHeader
        if (existingHeader != null && activeSessionKey?.isActive == true) {
            return@withContext prototypeDemoRecoveryKey.orEmpty()
        }

        val demoChars = DEFAULT_DEMO_PASSPHRASE.toCharArray()
        try {
            activeSessionKey?.wipe()
            encryptedStore.clear()

            val bundle = cryptoEngine.createVault(
                masterPassword = demoChars,
                kdfParameters = kdfParameters,
            )
            vaultHeader = bundle.header
            activeSessionKey = bundle.sessionKey
            prototypeDemoRecoveryKey = bundle.formattedRecoveryKey

            // Encrypt every synthetic seed record through the real Phase 2 AES-256-GCM boundary.
            val seeded = seedSyntheticItems()
            for (item in seeded) {
                encryptAndPutLocked(bundle.sessionKey, item)
            }

            _items.value = decryptAllRecords(bundle.sessionKey)
            _hasVault.value = true
            _isUnlocked.value = true
            publishMetadata(bundle.header)
            bundle.formattedRecoveryKey
        } finally {
            demoChars.fill('\u0000')
        }
    }

    override suspend fun unlockWithMasterPassword(candidate: CharArray): Boolean = withContext(cryptoDispatcher) {
        val header = vaultHeader ?: return@withContext false
        try {
            val session = cryptoEngine.unlockWithMasterPassword(header, candidate)
            val decrypted = decryptAllRecords(session)
            activeSessionKey?.wipe()
            activeSessionKey = session
            _items.value = decrypted
            _isUnlocked.value = true
            publishMetadata(header)
            true
        } catch (_: VaultCryptoException) {
            false
        } finally {
            candidate.fill('\u0000')
        }
    }

    /**
     * Phase 4 will bind Android Keystore + BiometricPrompt to unwrap a hardware-protected VEK.
     * In Phase 2, biometric unlock uses the recovery envelope only when enabled in Settings
     * as a clearly labeled prototype convenience.
     */
    override suspend fun unlockWithMockBiometric(): Boolean = withContext(cryptoDispatcher) {
        if (!_settings.value.biometricUnlockEnabled) return@withContext false
        val header = vaultHeader ?: return@withContext false
        val demoKey = prototypeDemoRecoveryKey ?: return@withContext false
        try {
            val session = cryptoEngine.unlockWithRecoveryKey(header, demoKey)
            val decrypted = decryptAllRecords(session)
            activeSessionKey?.wipe()
            activeSessionKey = session
            _items.value = decrypted
            _isUnlocked.value = true
            publishMetadata(header)
            true
        } catch (_: VaultCryptoException) {
            false
        }
    }

    override suspend fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray,
    ): Boolean = withContext(cryptoDispatcher) {
        val header = vaultHeader ?: return@withContext false
        try {
            val updatedHeader = cryptoEngine.changeMasterPassword(
                header = header,
                currentMasterPassword = currentPassword,
                newMasterPassword = newPassword,
                newKdfParameters = kdfParameters,
            )
            vaultHeader = updatedHeader
            publishMetadata(updatedHeader)
            true
        } catch (_: VaultCryptoException) {
            false
        } finally {
            currentPassword.fill('\u0000')
            newPassword.fill('\u0000')
        }
    }

    override suspend fun recoverWithKey(
        recoveryKey: String,
        newMasterPassword: CharArray,
    ): Boolean = withContext(cryptoDispatcher) {
        val header = vaultHeader ?: return@withContext false
        try {
            val (updatedHeader, newSession) = cryptoEngine.recoverAndResetMasterPassword(
                header = header,
                formattedRecoveryKey = recoveryKey,
                newMasterPassword = newMasterPassword,
                newKdfParameters = kdfParameters,
            )
            val decrypted = decryptAllRecords(newSession)
            activeSessionKey?.wipe()
            vaultHeader = updatedHeader
            activeSessionKey = newSession
            _items.value = decrypted
            _isUnlocked.value = true
            publishMetadata(updatedHeader)
            true
        } catch (_: VaultCryptoException) {
            false
        } finally {
            newMasterPassword.fill('\u0000')
        }
    }

    override suspend fun benchmarkKdf(): KdfBenchmarkResult = withContext(cryptoDispatcher) {
        val result = cryptoEngine.benchmarkKdf(KdfParameters.OWASP_MOBILE_BALANCED)
        _cryptoMetadata.update { it.copy(lastBenchmark = result) }
        result
    }

    override fun lockVault() {
        activeSessionKey?.wipe()
        activeSessionKey = null
        _items.value = emptyList()
        _isUnlocked.value = false
    }

    override fun upsertItem(item: VaultItem): VaultItem {
        val session = activeSessionKey ?: return item
        clockTick += 60_000L
        val normalized = PasswordEngine.normalizeDomain(item.website).normalizedDomain
        val isNew = item.id.isBlank()
        val resolvedId = if (isNew) "rec-${++nextIdCounter}" else item.id
        val finalItem = item.copy(
            id = resolvedId,
            normalizedDomain = normalized,
            createdAtLabel = if (isNew) "Just now" else item.createdAtLabel,
            updatedAtLabel = "Just now",
            lastUsedEpochMs = clockTick,
        )
        encryptAndPutLocked(session, finalItem)
        _items.value = decryptAllRecords(session)
        vaultHeader?.let { publishMetadata(it) }
        return finalItem
    }

    override fun deleteItem(itemId: String) {
        val session = activeSessionKey ?: return
        encryptedStore.remove(itemId)
        _items.value = decryptAllRecords(session)
        vaultHeader?.let { publishMetadata(it) }
    }

    override fun toggleFavorite(itemId: String) {
        val existing = _items.value.firstOrNull { it.id == itemId } ?: return
        upsertItem(existing.copy(isFavorite = !existing.isFavorite))
    }

    override fun markItemUsed(itemId: String) {
        val session = activeSessionKey ?: return
        val existing = _items.value.firstOrNull { it.id == itemId } ?: return
        clockTick += 1_000L
        val updated = existing.copy(lastUsedEpochMs = clockTick)
        encryptAndPutLocked(session, updated)
        _items.value = _items.value.map { if (it.id == itemId) updated else it }
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

    private fun encryptAndPutLocked(session: VaultSessionKey, item: VaultItem) {
        val plaintext = VaultRecordCodec.encode(item)
        try {
            val encrypted = cryptoEngine.encryptRecord(
                sessionKey = session,
                recordId = item.id,
                recordType = item.type,
                plaintext = plaintext,
            )
            encryptedStore[item.id] = encrypted
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decryptAllRecords(session: VaultSessionKey): List<VaultItem> {
        return encryptedStore.values.map { record ->
            val plaintext = cryptoEngine.decryptRecord(session, record)
            try {
                VaultRecordCodec.decode(
                    recordId = record.recordId,
                    recordType = record.recordType,
                    plaintext = plaintext,
                )
            } finally {
                plaintext.fill(0)
            }
        }.sortedByDescending { it.lastUsedEpochMs }
    }

    private fun publishMetadata(header: VaultHeader) {
        _cryptoMetadata.update { current ->
            current.copy(
                formatVersion = header.formatVersion,
                vaultIdPreview = header.vaultId.take(8).uppercase(),
                kdfSummary = header.kdfParameters.summaryLabel(),
                encryptedRecordCount = encryptedStore.size,
            )
        }
    }

    companion object {
        const val DEFAULT_DEMO_PASSPHRASE: String = "emerald-vault"

        private fun seedSyntheticItems(): List<VaultItem> = listOf(
            VaultItem(
                id = "mock-1",
                type = VaultItemType.LOGIN,
                title = "CodeForge Git",
                username = "alex.chen@example.com",
                secret = "Pine-Harbor-48!Quartz",
                website = "https://codeforge.example.com",
                normalizedDomain = "codeforge.example.com",
                notes = "Synthetic fixture encrypted in memory via Phase 2 AES-256-GCM.",
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
                notes = "Primary personal inbox (synthetic fixture).",
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
                notes = "Synthetic financial login for verification.",
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
                notes = "Synthetic contact details for verification.",
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
                notes = "Non-functional test card number.",
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
                lastSyncLabel = "Local encrypted vault active",
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
