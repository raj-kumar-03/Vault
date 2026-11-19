package com.example.data

import com.example.crypto.DefaultVaultCryptoEngine
import com.example.crypto.EncryptedVaultRecord
import com.example.crypto.KdfBenchmarkResult
import com.example.crypto.KdfParameters
import com.example.crypto.VaultBackupCodec
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultCryptoException
import com.example.crypto.VaultHeader
import com.example.crypto.VaultRecordCodec
import com.example.crypto.VaultSessionKey
import com.example.data.local.BiometricEnvelopeEntity
import com.example.data.local.VaultDao
import com.example.data.local.VaultHeaderEntity
import com.example.data.local.VaultPreferencesEntity
import com.example.data.local.VaultRecordEntity
import com.example.domain.model.DevicePlatform
import com.example.domain.model.DeviceStatus
import com.example.domain.model.PairedDevice
import com.example.domain.model.PasswordEngine
import com.example.domain.model.SecuritySettings
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.security.BiometricAvailability
import com.example.security.BiometricKeyState
import com.example.security.CipherPreparationResult
import com.example.security.PlatformKeystoreBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.Cipher

/**
 * Phase 3 & Phase 4 encrypted local SQLite repository backed by native Android SQLiteOpenHelper
 * ([VaultDao]), [VaultCryptoEngine], and [PlatformKeystoreBridge] (Android Keystore).
 *
 * Security & durability properties:
 * - Every record stored in SQLite is AES-256-GCM encrypted with canonical AAD; titles, usernames,
 *   domains, notes, favorites, and secrets never touch SQLite in plaintext.
 * - Multi-record writes run inside atomic SQLite transactions.
 * - Biometric unlock wraps the 256-bit VEK under a device-bound Android Keystore AES-256-GCM key
 *   authorized strictly by a per-operation BiometricPrompt CryptoObject.
 * - Biometric enrollment changes / KeyPermanentlyInvalidatedException cleanly wipe the stale key
 *   and envelope and fall back to master-password unlock without data loss.
 */
class PersistentVaultRepository(
    private val vaultDao: VaultDao,
    private val cryptoEngine: VaultCryptoEngine = DefaultVaultCryptoEngine(),
    private val keystoreBridge: PlatformKeystoreBridge = UnavailableKeystoreBridge,
    private val kdfParameters: KdfParameters = KdfParameters.OWASP_MOBILE_BALANCED,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VaultRepository, DeviceRepository, SettingsRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile
    private var activeSessionKey: VaultSessionKey? = null

    @Volatile
    private var biometricKeyWasInvalidated: Boolean = false

    private val _isStorageInitialized = MutableStateFlow(false)
    override val isStorageInitialized: StateFlow<Boolean> = _isStorageInitialized.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    override val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _hasVault = MutableStateFlow(false)
    override val hasVault: StateFlow<Boolean> = _hasVault.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _cryptoMetadata = MutableStateFlow(
        VaultCryptoMetadata(kdfSummary = kdfParameters.summaryLabel(), isPersistedToSqlite = true)
    )
    override val cryptoMetadata: StateFlow<VaultCryptoMetadata> = _cryptoMetadata.asStateFlow()

    private val _biometricState = MutableStateFlow(BiometricKeyState())
    override val biometricState: StateFlow<BiometricKeyState> = _biometricState.asStateFlow()

    private val _devices = MutableStateFlow(seedMockDevices())
    override val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _settings = MutableStateFlow(SecuritySettings(biometricUnlockEnabled = false))
    override val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    init {
        repoScope.launch {
            initializeFromDisk()
        }
    }

    suspend fun initializeFromDisk() = withContext(ioDispatcher) {
        val prefs = vaultDao.getPreferences()?.toDomain()
            ?: SecuritySettings(biometricUnlockEnabled = false)
        _settings.value = prefs

        val headerEntity = vaultDao.getHeader()
        if (headerEntity != null) {
            val header = headerEntity.toDomain()
            val recordCount = vaultDao.getActiveRecords(header.vaultId).size
            _hasVault.value = true
            publishMetadata(header, recordCount, emptyList())
        } else {
            _hasVault.value = false
        }

        refreshBiometricStateLocked()
        _isStorageInitialized.value = true
    }

    override suspend fun refreshBiometricState() = withContext(ioDispatcher) {
        refreshBiometricStateLocked()
    }

    private suspend fun refreshBiometricStateLocked() {
        val header = vaultDao.getHeader()
        val envelope = vaultDao.getBiometricEnvelope()
        val envelopeMatchesVault = header != null &&
            envelope != null &&
            envelope.vaultId == header.vaultId

        // If an envelope exists for a different vault, remove it cleanly.
        if (envelope != null && !envelopeMatchesVault) {
            vaultDao.deleteBiometricEnvelope()
        }

        val queried = keystoreBridge.queryAvailability(
            hasStoredEnvelope = envelopeMatchesVault,
            wasInvalidated = biometricKeyWasInvalidated,
        )

        // If device lock was removed or biometrics were unenrolled while an envelope existed,
        // clean up the unusable envelope and reflect honest state.
        val hadBoundEnvelopeBefore = _settings.value.biometricUnlockEnabled
        if (envelopeMatchesVault && !queried.canUnlockWithBiometrics) {
            if (queried.availability == BiometricAvailability.DEVICE_LOCK_NOT_SET ||
                queried.availability == BiometricAvailability.NO_BIOMETRICS_ENROLLED
            ) {
                biometricKeyWasInvalidated = true
                keystoreBridge.deleteKey()
                vaultDao.deleteBiometricEnvelope()
            }
        }

        val finalState = keystoreBridge.queryAvailability(
            hasStoredEnvelope = vaultDao.getBiometricEnvelope()?.vaultId == header?.vaultId && header != null,
            wasInvalidated = biometricKeyWasInvalidated,
        )
        _biometricState.value = finalState

        val shouldBeEnabled = finalState.canUnlockWithBiometrics
        if (hadBoundEnvelopeBefore != shouldBeEnabled) {
            val syncedSettings = _settings.value.copy(biometricUnlockEnabled = shouldBeEnabled)
            _settings.value = syncedSettings
            vaultDao.upsertPreferences(VaultPreferencesEntity.fromDomain(syncedSettings))
        }
    }

    override suspend fun prepareBiometricEnrollmentCipher(): CipherPreparationResult =
        withContext(ioDispatcher) {
            if (activeSessionKey == null || !_isUnlocked.value) {
                return@withContext CipherPreparationResult.Unavailable(
                    "Unlock the vault before binding a biometric Keystore key."
                )
            }
            val currentState = keystoreBridge.queryAvailability(
                hasStoredEnvelope = false,
                wasInvalidated = false,
            )
            if (!currentState.canEnrollBiometrics) {
                return@withContext CipherPreparationResult.Unavailable(currentState.statusMessage)
            }
            keystoreBridge.prepareEncryptCipher()
        }

    override suspend fun completeBiometricEnrollment(authenticatedCipher: Cipher): Boolean {
        val session = activeSessionKey ?: return false
        val header = withContext(ioDispatcher) { vaultDao.getHeader()?.toDomain() } ?: return false
        if (header.vaultId != session.vaultId) return false

        val sealed = withContext(cryptoDispatcher) {
            val vek = session.borrowKeyBytes()
            try {
                keystoreBridge.sealVek(authenticatedCipher, vek)
            } catch (_: Exception) {
                null
            } finally {
                vek.fill(0)
            }
        } ?: return false

        withContext(ioDispatcher) {
            biometricKeyWasInvalidated = false
            vaultDao.upsertBiometricEnvelope(
                BiometricEnvelopeEntity(
                    singletonId = 0,
                    vaultId = header.vaultId,
                    keyAlias = sealed.keyAlias,
                    iv = sealed.iv,
                    ciphertextAndTag = sealed.ciphertextAndTag,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            )
            val updatedSettings = _settings.value.copy(biometricUnlockEnabled = true)
            _settings.value = updatedSettings
            vaultDao.upsertPreferences(VaultPreferencesEntity.fromDomain(updatedSettings))
            refreshBiometricStateLocked()
        }
        return true
    }

    override suspend fun disableBiometricUnlock() = withContext(ioDispatcher) {
        biometricKeyWasInvalidated = false
        keystoreBridge.deleteKey()
        vaultDao.deleteBiometricEnvelope()
        val updatedSettings = _settings.value.copy(biometricUnlockEnabled = false)
        _settings.value = updatedSettings
        vaultDao.upsertPreferences(VaultPreferencesEntity.fromDomain(updatedSettings))
        refreshBiometricStateLocked()
    }

    override suspend fun prepareBiometricUnlockCipher(): CipherPreparationResult =
        withContext(ioDispatcher) {
            val header = vaultDao.getHeader() ?: return@withContext CipherPreparationResult.Unavailable(
                "No local vault found."
            )
            val envelope = vaultDao.getBiometricEnvelope()
            if (envelope == null || envelope.vaultId != header.vaultId) {
                return@withContext CipherPreparationResult.Unavailable(
                    "Biometric unlock is not configured for this vault."
                )
            }

            when (val result = keystoreBridge.prepareDecryptCipher(envelope.iv)) {
                is CipherPreparationResult.Ready -> result
                is CipherPreparationResult.KeyInvalidated -> {
                    handleBiometricKeyInvalidatedLocked()
                    CipherPreparationResult.KeyInvalidated
                }
                is CipherPreparationResult.Unavailable -> result
            }
        }

    override suspend fun completeBiometricUnlock(authenticatedCipher: Cipher): Boolean {
        val (header, envelope) = withContext(ioDispatcher) {
            vaultDao.getHeader()?.toDomain() to vaultDao.getBiometricEnvelope()
        }
        if (header == null || envelope == null || envelope.vaultId != header.vaultId) {
            return false
        }

        val session = withContext(cryptoDispatcher) {
            var unwrappedVek: ByteArray? = null
            try {
                unwrappedVek = keystoreBridge.unsealVek(authenticatedCipher, envelope.ciphertextAndTag)
                VaultSessionKey.fromUnwrappedVek(
                    vaultId = header.vaultId,
                    formatVersion = header.formatVersion,
                    vekBytes = unwrappedVek,
                )
            } catch (_: Exception) {
                null
            } finally {
                unwrappedVek?.fill(0)
            }
        } ?: return false

        activeSessionKey?.wipe()
        activeSessionKey = session
        _hasVault.value = true
        _isUnlocked.value = true
        reloadDecryptedItemsLocked(header, session)
        return true
    }

    private suspend fun handleBiometricKeyInvalidatedLocked() {
        biometricKeyWasInvalidated = true
        keystoreBridge.deleteKey()
        vaultDao.deleteBiometricEnvelope()
        val updatedSettings = _settings.value.copy(biometricUnlockEnabled = false)
        _settings.value = updatedSettings
        vaultDao.upsertPreferences(VaultPreferencesEntity.fromDomain(updatedSettings))
        refreshBiometricStateLocked()
    }

    override suspend fun createVault(masterPassword: CharArray): String {
        val bundle = withContext(cryptoDispatcher) {
            try {
                cryptoEngine.createVault(
                    masterPassword = masterPassword,
                    kdfParameters = kdfParameters,
                )
            } finally {
                masterPassword.fill('\u0000')
            }
        }

        withContext(ioDispatcher) {
            biometricKeyWasInvalidated = false
            keystoreBridge.deleteKey()
            vaultDao.replaceVaultAtomically(
                header = VaultHeaderEntity.fromDomain(bundle.header),
                records = emptyList(),
            )
            refreshBiometricStateLocked()
        }

        activeSessionKey?.wipe()
        activeSessionKey = bundle.sessionKey
        _items.value = emptyList()
        _hasVault.value = true
        _isUnlocked.value = true
        _isStorageInitialized.value = true
        publishMetadata(bundle.header, 0, emptyList())
        return bundle.formattedRecoveryKey
    }

    override suspend fun openDemoVault(): String {
        val demoChars = DEFAULT_DEMO_PASSPHRASE.toCharArray()
        val bundle = withContext(cryptoDispatcher) {
            try {
                cryptoEngine.createVault(
                    masterPassword = demoChars,
                    kdfParameters = kdfParameters,
                )
            } finally {
                demoChars.fill('\u0000')
            }
        }

        val seeded = seedSyntheticItems()
        val encryptedEntities = withContext(cryptoDispatcher) {
            seeded.map { item ->
                val plaintext = VaultRecordCodec.encode(item)
                try {
                    val encrypted = cryptoEngine.encryptRecord(
                        sessionKey = bundle.sessionKey,
                        recordId = item.id,
                        recordType = item.type,
                        plaintext = plaintext,
                    )
                    VaultRecordEntity.fromDomain(encrypted, isDeleted = false, revision = 1L)
                } finally {
                    plaintext.fill(0)
                }
            }
        }

        withContext(ioDispatcher) {
            biometricKeyWasInvalidated = false
            keystoreBridge.deleteKey()
            vaultDao.replaceVaultAtomically(
                header = VaultHeaderEntity.fromDomain(bundle.header),
                records = encryptedEntities,
            )
            refreshBiometricStateLocked()
        }

        activeSessionKey?.wipe()
        activeSessionKey = bundle.sessionKey
        _hasVault.value = true
        _isUnlocked.value = true
        _isStorageInitialized.value = true
        reloadDecryptedItemsLocked(bundle.header, bundle.sessionKey)
        return bundle.formattedRecoveryKey
    }

    override suspend fun unlockWithMasterPassword(candidate: CharArray): Boolean {
        val header = withContext(ioDispatcher) { vaultDao.getHeader()?.toDomain() }
        if (header == null) {
            candidate.fill('\u0000')
            return false
        }

        val session = withContext(cryptoDispatcher) {
            try {
                cryptoEngine.unlockWithMasterPassword(header, candidate)
            } catch (_: VaultCryptoException) {
                null
            } finally {
                candidate.fill('\u0000')
            }
        } ?: return false

        activeSessionKey?.wipe()
        activeSessionKey = session
        _hasVault.value = true
        _isUnlocked.value = true
        reloadDecryptedItemsLocked(header, session)
        return true
    }

    override suspend fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray,
    ): Boolean {
        val header = withContext(ioDispatcher) { vaultDao.getHeader()?.toDomain() }
        if (header == null) {
            currentPassword.fill('\u0000')
            newPassword.fill('\u0000')
            return false
        }

        val updatedHeader = withContext(cryptoDispatcher) {
            try {
                cryptoEngine.changeMasterPassword(
                    header = header,
                    currentMasterPassword = currentPassword,
                    newMasterPassword = newPassword,
                    newKdfParameters = kdfParameters,
                )
            } catch (_: VaultCryptoException) {
                null
            } finally {
                currentPassword.fill('\u0000')
                newPassword.fill('\u0000')
            }
        } ?: return false

        withContext(ioDispatcher) {
            vaultDao.upsertHeader(VaultHeaderEntity.fromDomain(updatedHeader))
            val count = vaultDao.getActiveRecords(updatedHeader.vaultId).size
            publishMetadata(updatedHeader, count, _cryptoMetadata.value.corruptedRecordIds)
        }
        return true
    }

    override suspend fun recoverWithKey(
        recoveryKey: String,
        newMasterPassword: CharArray,
    ): Boolean {
        val header = withContext(ioDispatcher) { vaultDao.getHeader()?.toDomain() }
        if (header == null) {
            newMasterPassword.fill('\u0000')
            return false
        }

        val recovered = withContext(cryptoDispatcher) {
            try {
                cryptoEngine.recoverAndResetMasterPassword(
                    header = header,
                    formattedRecoveryKey = recoveryKey,
                    newMasterPassword = newMasterPassword,
                    newKdfParameters = kdfParameters,
                )
            } catch (_: VaultCryptoException) {
                null
            } finally {
                newMasterPassword.fill('\u0000')
            }
        } ?: return false

        val (updatedHeader, newSession) = recovered
        withContext(ioDispatcher) {
            vaultDao.upsertHeader(VaultHeaderEntity.fromDomain(updatedHeader))
        }

        activeSessionKey?.wipe()
        activeSessionKey = newSession
        _hasVault.value = true
        _isUnlocked.value = true
        reloadDecryptedItemsLocked(updatedHeader, newSession)
        return true
    }

    override suspend fun benchmarkKdf(): KdfBenchmarkResult = withContext(cryptoDispatcher) {
        val result = cryptoEngine.benchmarkKdf(KdfParameters.OWASP_MOBILE_BALANCED)
        _cryptoMetadata.update { it.copy(lastBenchmark = result) }
        result
    }

    override suspend fun upsertItem(item: VaultItem): VaultItem {
        val session = activeSessionKey ?: return item
        val now = System.currentTimeMillis()
        val normalized = PasswordEngine.normalizeDomain(item.website).normalizedDomain
        val isNew = item.id.isBlank()
        val resolvedId = if (isNew) "rec-${UUID.randomUUID().toString().take(8)}" else item.id
        val finalItem = item.copy(
            id = resolvedId,
            normalizedDomain = normalized,
            createdAtLabel = if (isNew) "Just now" else item.createdAtLabel,
            updatedAtLabel = "Just now",
            lastUsedEpochMs = now,
        )

        val encrypted = withContext(cryptoDispatcher) {
            val plaintext = VaultRecordCodec.encode(finalItem)
            try {
                cryptoEngine.encryptRecord(
                    sessionKey = session,
                    recordId = finalItem.id,
                    recordType = finalItem.type,
                    plaintext = plaintext,
                )
            } finally {
                plaintext.fill(0)
            }
        }

        val header = withContext(ioDispatcher) {
            val previousRevision = vaultDao.getRecordRevision(finalItem.id) ?: 0L
            vaultDao.upsertRecordAndTouchHeader(
                record = VaultRecordEntity.fromDomain(
                    record = encrypted,
                    isDeleted = false,
                    revision = previousRevision + 1L,
                ),
                updatedAtEpochMs = now,
            )
            vaultDao.getHeader()?.toDomain()
        }

        if (header != null) {
            reloadDecryptedItemsLocked(header, session)
        }
        return finalItem
    }

    override suspend fun deleteItem(itemId: String) {
        val session = activeSessionKey ?: return
        val now = System.currentTimeMillis()
        val header = withContext(ioDispatcher) {
            vaultDao.softDeleteRecordAndTouchHeader(recordId = itemId, deletedAtEpochMs = now)
            vaultDao.getHeader()?.toDomain()
        }
        if (header != null) {
            reloadDecryptedItemsLocked(header, session)
        }
    }

    override suspend fun toggleFavorite(itemId: String) {
        val existing = _items.value.firstOrNull { it.id == itemId } ?: return
        upsertItem(existing.copy(isFavorite = !existing.isFavorite))
    }

    override suspend fun markItemUsed(itemId: String) {
        val existing = _items.value.firstOrNull { it.id == itemId } ?: return
        upsertItem(existing)
    }

    override suspend fun exportEncryptedBackup(): ByteArray = withContext(ioDispatcher) {
        val header = vaultDao.getHeader()?.toDomain()
            ?: throw IllegalStateException("No persisted vault exists to export.")
        val activeEntities = vaultDao.getActiveRecords(header.vaultId)
        val records = activeEntities.mapNotNull { it.toDomainOrNull() }
        VaultBackupCodec.encode(header = header, records = records)
    }

    override suspend fun inspectBackup(backupBytes: ByteArray): VaultBackupCodec.BackupInspectionSummary =
        withContext(cryptoDispatcher) {
            VaultBackupCodec.inspect(backupBytes)
        }

    override suspend fun restoreBackupWithMasterPassword(
        backupBytes: ByteArray,
        masterPassword: CharArray,
    ): Boolean {
        val decodedAndVerified = withContext(cryptoDispatcher) {
            try {
                val backup = VaultBackupCodec.decode(backupBytes)
                val session = cryptoEngine.unlockWithMasterPassword(backup.header, masterPassword)
                verifyAllBackupRecords(session, backup.records)
                backup to session
            } catch (_: VaultCryptoException) {
                null
            } finally {
                masterPassword.fill('\u0000')
            }
        } ?: return false

        val (backup, session) = decodedAndVerified
        commitVerifiedBackupAtomically(backup.header, backup.records, session)
        return true
    }

    override suspend fun restoreBackupWithRecoveryKey(
        backupBytes: ByteArray,
        recoveryKey: String,
        newMasterPassword: CharArray,
    ): Boolean {
        val decodedAndVerified = withContext(cryptoDispatcher) {
            try {
                val backup = VaultBackupCodec.decode(backupBytes)
                val (updatedHeader, session) = cryptoEngine.recoverAndResetMasterPassword(
                    header = backup.header,
                    formattedRecoveryKey = recoveryKey,
                    newMasterPassword = newMasterPassword,
                    newKdfParameters = kdfParameters,
                )
                verifyAllBackupRecords(session, backup.records)
                Triple(updatedHeader, backup.records, session)
            } catch (_: VaultCryptoException) {
                null
            } finally {
                newMasterPassword.fill('\u0000')
            }
        } ?: return false

        val (updatedHeader, records, session) = decodedAndVerified
        commitVerifiedBackupAtomically(updatedHeader, records, session)
        return true
    }

    override fun lockVault() {
        activeSessionKey?.wipe()
        activeSessionKey = null
        _items.value = emptyList()
        _isUnlocked.value = false
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

    override suspend fun updateSettings(transform: (SecuritySettings) -> SecuritySettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        withContext(ioDispatcher) {
            vaultDao.upsertPreferences(VaultPreferencesEntity.fromDomain(updated))
        }
    }

    private fun verifyAllBackupRecords(
        session: VaultSessionKey,
        records: List<EncryptedVaultRecord>,
    ) {
        for (record in records) {
            val plaintext = cryptoEngine.decryptRecord(session, record)
            try {
                VaultRecordCodec.decode(record.recordId, record.recordType, plaintext)
            } finally {
                plaintext.fill(0)
            }
        }
    }

    private suspend fun commitVerifiedBackupAtomically(
        header: VaultHeader,
        records: List<EncryptedVaultRecord>,
        session: VaultSessionKey,
    ) {
        withContext(ioDispatcher) {
            biometricKeyWasInvalidated = false
            keystoreBridge.deleteKey()
            val entities = records.map { rec ->
                VaultRecordEntity.fromDomain(rec, isDeleted = false, revision = 1L)
            }
            vaultDao.replaceVaultAtomically(
                header = VaultHeaderEntity.fromDomain(header),
                records = entities,
            )
            refreshBiometricStateLocked()
        }
        activeSessionKey?.wipe()
        activeSessionKey = session
        _hasVault.value = true
        _isUnlocked.value = true
        _isStorageInitialized.value = true
        reloadDecryptedItemsLocked(header, session)
    }

    private suspend fun reloadDecryptedItemsLocked(
        header: VaultHeader,
        session: VaultSessionKey,
    ) {
        val rawEntities = withContext(ioDispatcher) {
            vaultDao.getActiveRecords(header.vaultId)
        }
        val (decryptedItems, corruptedIds) = withContext(cryptoDispatcher) {
            val okItems = ArrayList<VaultItem>(rawEntities.size)
            val badIds = ArrayList<String>()
            for (entity in rawEntities) {
                val domainRecord = entity.toDomainOrNull()
                if (domainRecord == null) {
                    badIds.add(entity.recordId)
                    continue
                }
                try {
                    val plaintext = cryptoEngine.decryptRecord(session, domainRecord)
                    try {
                        okItems.add(
                            VaultRecordCodec.decode(
                                recordId = domainRecord.recordId,
                                recordType = domainRecord.recordType,
                                plaintext = plaintext,
                            )
                        )
                    } finally {
                        plaintext.fill(0)
                    }
                } catch (_: VaultCryptoException) {
                    badIds.add(entity.recordId)
                }
            }
            okItems.sortedByDescending { it.lastUsedEpochMs } to badIds
        }

        _items.value = decryptedItems
        publishMetadata(header, rawEntities.size, corruptedIds)
    }

    private fun publishMetadata(
        header: VaultHeader,
        recordCount: Int,
        corruptedIds: List<String>,
    ) {
        _cryptoMetadata.update { current ->
            current.copy(
                formatVersion = header.formatVersion,
                vaultIdPreview = header.vaultId.take(8).uppercase(),
                kdfSummary = header.kdfParameters.summaryLabel(),
                encryptedRecordCount = recordCount,
                corruptedRecordIds = corruptedIds,
                isPersistedToSqlite = true,
            )
        }
    }

    private object UnavailableKeystoreBridge : PlatformKeystoreBridge {
        override val keyAlias: String = "unavailable"
        override fun queryAvailability(hasStoredEnvelope: Boolean, wasInvalidated: Boolean) =
            BiometricKeyState(
                availability = BiometricAvailability.HARDWARE_UNAVAILABLE,
                hasBoundEnvelope = false,
                isDeviceSecure = false,
                keyAlias = keyAlias,
                statusMessage = "Biometric hardware unavailable in this environment.",
            )

        override fun prepareEncryptCipher(): CipherPreparationResult =
            CipherPreparationResult.Unavailable("Biometric hardware unavailable.")

        override fun prepareDecryptCipher(iv: ByteArray): CipherPreparationResult =
            CipherPreparationResult.Unavailable("Biometric hardware unavailable.")

        override fun sealVek(authenticatedCipher: Cipher, vekBytes: ByteArray) =
            throw UnsupportedOperationException("Biometric hardware unavailable.")

        override fun unsealVek(authenticatedCipher: Cipher, ciphertextAndTag: ByteArray) =
            throw UnsupportedOperationException("Biometric hardware unavailable.")

        override fun deleteKey() = Unit
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
                notes = "Encrypted in local SQLite via AES-256-GCM + AAD.",
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
                type = VaultItemType.PASSKEY,
                title = "CodeForge Passkey (Preview)",
                username = "alex.chen@example.com",
                secret = "rpId=codeforge.example.com • alg=ES256 (Android 14+ Provider in Phase 5)",
                website = "https://codeforge.example.com",
                normalizedDomain = "codeforge.example.com",
                notes = "Offline passkey storage envelope ready for Phase 5 CredentialProviderService.",
                isFavorite = true,
                createdAtLabel = "Nov 15, 2026",
                updatedAtLabel = "Today, 9:30 AM",
                lastUsedEpochMs = 1_729_999_750_000L,
            ),
            VaultItem(
                id = "mock-4",
                type = VaultItemType.LOGIN,
                title = "Evergreen Credit Union",
                username = "achen_member_402",
                secret = "Ridge-Moss-73-Lantern!",
                website = "https://banking.evergreen.test",
                normalizedDomain = "banking.evergreen.test",
                notes = "Synthetic financial login for verification.",
                isFavorite = false,
                createdAtLabel = "Aug 19, 2026",
                updatedAtLabel = "Nov 02, 2026",
                lastUsedEpochMs = 1_729_999_700_000L,
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
                id = "mock-7",
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
                lastSyncLabel = "Local encrypted SQLite active",
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
