package com.example.ui.state

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crypto.KdfBenchmarkResult
import com.example.crypto.VaultBackupCodec
import com.example.crypto.VaultCryptoException
import com.example.data.PersistentVaultRepository
import com.example.data.VaultCryptoMetadata
import com.example.data.VaultRuntime
import com.example.domain.model.AutoLockInterval
import com.example.domain.model.ClipboardClearInterval
import com.example.domain.model.GeneratorConfig
import com.example.domain.model.GeneratorMode
import com.example.domain.model.PairedDevice
import com.example.domain.model.PasswordEngine
import com.example.domain.model.PasswordStrength
import com.example.domain.model.SecuritySettings
import com.example.domain.model.ThemePreference
import com.example.domain.model.VaultCategoryFilter
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.security.BiometricKeyState
import com.example.security.CipherPreparationResult
import com.example.security.LockReason
import com.example.security.VaultAutoLockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher

data class GeneratorUiState(
    val config: GeneratorConfig = GeneratorConfig(),
    val generatedValue: String = "",
    val strength: PasswordStrength = PasswordEngine.calculateGeneratorStrength(GeneratorConfig()),
)

data class PendingBackupRestore(
    val rawBytes: ByteArray,
    val summary: VaultBackupCodec.BackupInspectionSummary,
)

data class VaultUiState(
    val isStorageInitialized: Boolean = false,
    val hasVault: Boolean = false,
    val isUnlocked: Boolean = false,
    val isCryptoBusy: Boolean = false,
    val lockReason: LockReason = LockReason.NONE,
    val biometricState: BiometricKeyState = BiometricKeyState(),
    val pendingRecoveryKeyReveal: String? = null,
    val pendingBackupRestore: PendingBackupRestore? = null,
    val cryptoMetadata: VaultCryptoMetadata = VaultCryptoMetadata(),
    val items: List<VaultItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: VaultCategoryFilter = VaultCategoryFilter.ALL,
    val selectedItemId: String? = null,
    val devices: List<PairedDevice> = emptyList(),
    val settings: SecuritySettings = SecuritySettings(biometricUnlockEnabled = false),
    val generator: GeneratorUiState = GeneratorUiState(),
    val pendingGeneratedPasswordForEditor: String? = null,
) {
    val filteredItems: List<VaultItem>
        get() {
            val query = searchQuery.trim().lowercase()
            return items.filter { item ->
                val matchesCategory = when (selectedCategory) {
                    VaultCategoryFilter.ALL -> true
                    VaultCategoryFilter.FAVORITES -> item.isFavorite
                    VaultCategoryFilter.LOGINS -> item.type == VaultItemType.LOGIN
                    VaultCategoryFilter.PASSKEYS -> item.type == VaultItemType.PASSKEY
                    VaultCategoryFilter.SECURE_NOTES -> item.type == VaultItemType.SECURE_NOTE
                    VaultCategoryFilter.IDENTITIES -> item.type == VaultItemType.IDENTITY
                    VaultCategoryFilter.CARDS -> item.type == VaultItemType.PAYMENT_CARD
                }
                val matchesQuery = query.isEmpty() ||
                    item.title.lowercase().contains(query) ||
                    item.username.lowercase().contains(query) ||
                    item.normalizedDomain.lowercase().contains(query) ||
                    item.website.lowercase().contains(query) ||
                    item.notes.lowercase().contains(query)
                matchesCategory && matchesQuery
            }
        }

    val favoriteItems: List<VaultItem>
        get() = items.filter { it.isFavorite }

    fun countForFilter(filter: VaultCategoryFilter): Int = when (filter) {
        VaultCategoryFilter.ALL -> items.size
        VaultCategoryFilter.FAVORITES -> items.count { it.isFavorite }
        VaultCategoryFilter.LOGINS -> items.count { it.type == VaultItemType.LOGIN }
        VaultCategoryFilter.PASSKEYS -> items.count { it.type == VaultItemType.PASSKEY }
        VaultCategoryFilter.SECURE_NOTES -> items.count { it.type == VaultItemType.SECURE_NOTE }
        VaultCategoryFilter.IDENTITIES -> items.count { it.type == VaultItemType.IDENTITY }
        VaultCategoryFilter.CARDS -> items.count { it.type == VaultItemType.PAYMENT_CARD }
    }
}

class VaultViewModel(
    private val repository: PersistentVaultRepository,
    elapsedClockMs: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val autoLockPolicy = VaultAutoLockPolicy(elapsedClockMs)

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedCategoryFlow = MutableStateFlow(VaultCategoryFilter.ALL)
    private val selectedItemIdFlow = MutableStateFlow<String?>(null)
    private val pendingGeneratedPasswordFlow = MutableStateFlow<String?>(null)
    private val isCryptoBusyFlow = MutableStateFlow(false)
    private val pendingRecoveryKeyFlow = MutableStateFlow<String?>(null)
    private val pendingBackupRestoreFlow = MutableStateFlow<PendingBackupRestore?>(null)
    private val lockReasonFlow = MutableStateFlow(LockReason.COLD_START)

    private val initialConfig = GeneratorConfig()
    private val generatorFlow = MutableStateFlow(
        GeneratorUiState(
            config = initialConfig,
            generatedValue = PasswordEngine.generate(initialConfig),
            strength = PasswordEngine.calculateGeneratorStrength(initialConfig),
        )
    )

    private val repoState = combine(
        repository.isStorageInitialized,
        repository.hasVault,
        repository.isUnlocked,
        repository.items,
        repository.devices,
    ) { initialized, hasVault, isUnlocked, items, devices ->
        RepoBundle(initialized, hasVault, isUnlocked, items, devices)
    }

    private val securityStateFlow = combine(
        repository.settings,
        repository.cryptoMetadata,
        repository.biometricState,
        lockReasonFlow,
    ) { settings, meta, bioState, lockReason ->
        SecurityBundle(settings, meta, bioState, lockReason)
    }

    private val transientFlow = combine(
        isCryptoBusyFlow,
        pendingRecoveryKeyFlow,
        pendingBackupRestoreFlow,
    ) { busy, recoveryKey, pendingBackup ->
        TransientBundle(busy, recoveryKey, pendingBackup)
    }

    private val uiFilters = combine(
        searchQueryFlow,
        selectedCategoryFlow,
        selectedItemIdFlow,
        generatorFlow,
        pendingGeneratedPasswordFlow,
    ) { query, category, selectedId, generator, pendingPassword ->
        FilterBundle(query, category, selectedId, generator, pendingPassword)
    }

    val uiState: StateFlow<VaultUiState> = combine(
        repoState,
        securityStateFlow,
        transientFlow,
        uiFilters,
    ) { repo, sec, transient, filters ->
        VaultUiState(
            isStorageInitialized = repo.isInitialized,
            hasVault = repo.hasVault,
            isUnlocked = repo.isUnlocked,
            isCryptoBusy = transient.isBusy,
            lockReason = sec.lockReason,
            biometricState = sec.biometricState,
            pendingRecoveryKeyReveal = transient.pendingRecoveryKey,
            pendingBackupRestore = transient.pendingBackup,
            cryptoMetadata = sec.metadata,
            items = repo.items,
            searchQuery = filters.query,
            selectedCategory = filters.category,
            selectedItemId = filters.selectedItemId ?: repo.items.firstOrNull()?.id,
            devices = repo.devices,
            settings = sec.settings,
            generator = filters.generator,
            pendingGeneratedPasswordForEditor = filters.pendingPassword,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = VaultUiState(
            isStorageInitialized = repository.isStorageInitialized.value,
            hasVault = repository.hasVault.value,
            isUnlocked = repository.isUnlocked.value,
            lockReason = lockReasonFlow.value,
            biometricState = repository.biometricState.value,
            cryptoMetadata = repository.cryptoMetadata.value,
            items = repository.items.value,
            selectedItemId = repository.items.value.firstOrNull()?.id,
            devices = repository.devices.value,
            settings = repository.settings.value,
            generator = generatorFlow.value,
        ),
    )

    fun onAppBackgrounded(): Boolean {
        if (!repository.isUnlocked.value) return false
        val interval = repository.settings.value.autoLockInterval
        val lockNow = autoLockPolicy.onAppBackgrounded(interval)
        if (lockNow) {
            lockVault(LockReason.AUTO_LOCK_BACKGROUND)
        }
        return lockNow
    }

    fun onAppForegrounded(): Boolean {
        viewModelScope.launch { repository.refreshBiometricState() }
        if (!repository.isUnlocked.value) return false
        val interval = repository.settings.value.autoLockInterval
        if (autoLockPolicy.shouldLockOnForeground(interval)) {
            lockVault(LockReason.AUTO_LOCK_BACKGROUND)
            return true
        }
        autoLockPolicy.resetActivity()
        return false
    }

    fun onUserInteraction() {
        if (repository.isUnlocked.value) {
            autoLockPolicy.resetActivity()
        }
    }

    fun checkInactivityTimeout(): Boolean {
        if (!repository.isUnlocked.value) return false
        val interval = repository.settings.value.autoLockInterval
        if (autoLockPolicy.shouldLockForInactivity(interval)) {
            lockVault(LockReason.AUTO_LOCK_INACTIVITY)
            return true
        }
        return false
    }

    fun refreshBiometricState() {
        viewModelScope.launch { repository.refreshBiometricState() }
    }

    fun prepareBiometricEnrollment(
        onCipherReady: (Cipher) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val prep = repository.prepareBiometricEnrollmentCipher()) {
                is CipherPreparationResult.Ready -> onCipherReady(prep.cipher)
                is CipherPreparationResult.KeyInvalidated -> {
                    onError("Previous biometric key was invalidated. Try enabling again after checking device biometrics.")
                }
                is CipherPreparationResult.Unavailable -> onError(prep.reason)
            }
        }
    }

    fun completeBiometricEnrollment(
        authenticatedCipher: Cipher,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.completeBiometricEnrollment(authenticatedCipher)
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun disableBiometricUnlock(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.disableBiometricUnlock()
            onDone()
        }
    }

    fun prepareBiometricUnlock(
        onCipherReady: (Cipher) -> Unit,
        onInvalidated: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val prep = repository.prepareBiometricUnlockCipher()) {
                is CipherPreparationResult.Ready -> onCipherReady(prep.cipher)
                is CipherPreparationResult.KeyInvalidated -> {
                    onInvalidated(
                        "Biometric enrollment or device lock changed. Keystore key invalidated—unlock with your master password to re-enable."
                    )
                }
                is CipherPreparationResult.Unavailable -> onError(prep.reason)
            }
        }
    }

    fun completeBiometricUnlock(
        authenticatedCipher: Cipher,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.completeBiometricUnlock(authenticatedCipher)
                if (ok) {
                    lockReasonFlow.value = LockReason.NONE
                    autoLockPolicy.resetActivity()
                }
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun createVault(masterPassword: String, onCreated: () -> Unit) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val recoveryKey = repository.createVault(masterPassword.toCharArray())
                pendingRecoveryKeyFlow.value = recoveryKey
                lockReasonFlow.value = LockReason.NONE
                autoLockPolicy.resetActivity()
                onCreated()
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun openDemoVault(onOpened: () -> Unit) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                repository.openDemoVault()
                lockReasonFlow.value = LockReason.NONE
                autoLockPolicy.resetActivity()
                onOpened()
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun dismissRecoveryKeyReveal() {
        pendingRecoveryKeyFlow.value = null
    }

    fun attemptUnlock(candidate: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.unlockWithMasterPassword(candidate.toCharArray())
                if (ok) {
                    lockReasonFlow.value = LockReason.NONE
                    autoLockPolicy.resetActivity()
                }
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun changeMasterPassword(
        currentPassword: String,
        newPassword: String,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.changeMasterPassword(
                    currentPassword = currentPassword.toCharArray(),
                    newPassword = newPassword.toCharArray(),
                )
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun recoverWithKey(
        recoveryKey: String,
        newMasterPassword: String,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.recoverWithKey(
                    recoveryKey = recoveryKey,
                    newMasterPassword = newMasterPassword.toCharArray(),
                )
                if (ok) {
                    lockReasonFlow.value = LockReason.NONE
                    autoLockPolicy.resetActivity()
                }
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun exportEncryptedBackup(onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val bytes = runCatching { repository.exportEncryptedBackup() }.getOrNull()
                onResult(bytes)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun inspectBackupFile(backupBytes: ByteArray, onError: (String) -> Unit) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val summary = repository.inspectBackup(backupBytes)
                pendingBackupRestoreFlow.value = PendingBackupRestore(
                    rawBytes = backupBytes,
                    summary = summary,
                )
            } catch (e: VaultCryptoException) {
                onError(e.message ?: "Backup file failed integrity verification.")
            } catch (_: Exception) {
                onError("Unable to read backup file.")
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun dismissBackupRestore() {
        pendingBackupRestoreFlow.value = null
    }

    fun restorePendingBackupWithPassword(
        masterPassword: String,
        onResult: (Boolean) -> Unit,
    ) {
        val pending = pendingBackupRestoreFlow.value ?: run {
            onResult(false)
            return
        }
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.restoreBackupWithMasterPassword(
                    backupBytes = pending.rawBytes,
                    masterPassword = masterPassword.toCharArray(),
                )
                if (ok) {
                    pendingBackupRestoreFlow.value = null
                    lockReasonFlow.value = LockReason.NONE
                    autoLockPolicy.resetActivity()
                }
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun restorePendingBackupWithRecoveryKey(
        recoveryKey: String,
        newMasterPassword: String,
        onResult: (Boolean) -> Unit,
    ) {
        val pending = pendingBackupRestoreFlow.value ?: run {
            onResult(false)
            return
        }
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val ok = repository.restoreBackupWithRecoveryKey(
                    backupBytes = pending.rawBytes,
                    recoveryKey = recoveryKey,
                    newMasterPassword = newMasterPassword.toCharArray(),
                )
                if (ok) {
                    pendingBackupRestoreFlow.value = null
                    lockReasonFlow.value = LockReason.NONE
                    autoLockPolicy.resetActivity()
                }
                onResult(ok)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun runKdfBenchmark(onCompleted: (KdfBenchmarkResult) -> Unit) {
        viewModelScope.launch {
            isCryptoBusyFlow.value = true
            try {
                val result = repository.benchmarkKdf()
                onCompleted(result)
            } finally {
                isCryptoBusyFlow.value = false
            }
        }
    }

    fun lockVault(reason: LockReason = LockReason.MANUAL) {
        pendingRecoveryKeyFlow.value = null
        pendingGeneratedPasswordFlow.value = null
        lockReasonFlow.value = reason
        repository.lockVault()
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun selectCategory(filter: VaultCategoryFilter) {
        selectedCategoryFlow.value = filter
    }

    fun selectItem(itemId: String) {
        selectedItemIdFlow.value = itemId
        viewModelScope.launch { repository.markItemUsed(itemId) }
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch { repository.toggleFavorite(itemId) }
    }

    fun saveItem(item: VaultItem, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val saved = repository.upsertItem(item)
            selectedItemIdFlow.value = saved.id
            pendingGeneratedPasswordFlow.value = null
            onSaved()
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteItem(itemId)
            if (selectedItemIdFlow.value == itemId) {
                selectedItemIdFlow.value = repository.items.value.firstOrNull()?.id
            }
        }
    }

    fun findItemById(itemId: String?): VaultItem? {
        if (itemId.isNullOrBlank()) return null
        return repository.items.value.firstOrNull { it.id == itemId }
    }

    fun updateGeneratorMode(mode: GeneratorMode) {
        updateGeneratorConfig { it.copy(mode = mode) }
    }

    fun updateGeneratorConfig(transform: (GeneratorConfig) -> GeneratorConfig) {
        generatorFlow.update { current ->
            val updated = transform(current.config)
            val safeConfig = if (
                updated.mode == GeneratorMode.PASSWORD &&
                !updated.includeUppercase &&
                !updated.includeLowercase &&
                !updated.includeNumbers &&
                !updated.includeSymbols
            ) {
                updated.copy(includeLowercase = true)
            } else {
                updated
            }
            GeneratorUiState(
                config = safeConfig,
                generatedValue = PasswordEngine.generate(safeConfig),
                strength = PasswordEngine.calculateGeneratorStrength(safeConfig),
            )
        }
    }

    fun regeneratePassword() {
        generatorFlow.update { current ->
            current.copy(
                generatedValue = PasswordEngine.generate(current.config),
                strength = PasswordEngine.calculateGeneratorStrength(current.config),
            )
        }
    }

    fun stageGeneratedPasswordForNewItem(password: String) {
        pendingGeneratedPasswordFlow.value = password
    }

    fun consumePendingGeneratedPassword(): String? {
        val value = pendingGeneratedPasswordFlow.value
        pendingGeneratedPasswordFlow.value = null
        return value
    }

    fun revokeDevice(deviceId: String) {
        repository.revokeDevice(deviceId)
    }

    fun updateAutoLock(interval: AutoLockInterval) {
        viewModelScope.launch { repository.updateSettings { it.copy(autoLockInterval = interval) } }
    }

    fun updateClipboardClear(interval: ClipboardClearInterval) {
        viewModelScope.launch { repository.updateSettings { it.copy(clipboardClearInterval = interval) } }
    }

    fun updateHideAppPreview(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings { it.copy(hideAppPreviewEnabled = enabled) } }
    }

    fun updateAutoPromptBiometricOnLaunch(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings { it.copy(autoPromptBiometricOnLaunch = enabled) } }
    }

    fun updateAllowInsecureHttpAutofill(allowed: Boolean) {
        viewModelScope.launch { repository.updateSettings { it.copy(allowInsecureHttpAutofill = allowed) } }
    }

    fun updateThemePreference(preference: ThemePreference) {
        viewModelScope.launch { repository.updateSettings { it.copy(themePreference = preference) } }
    }

    private data class RepoBundle(
        val isInitialized: Boolean,
        val hasVault: Boolean,
        val isUnlocked: Boolean,
        val items: List<VaultItem>,
        val devices: List<PairedDevice>,
    )

    private data class SecurityBundle(
        val settings: SecuritySettings,
        val metadata: VaultCryptoMetadata,
        val biometricState: BiometricKeyState,
        val lockReason: LockReason,
    )

    private data class TransientBundle(
        val isBusy: Boolean,
        val pendingRecoveryKey: String?,
        val pendingBackup: PendingBackupRestore?,
    )

    private data class FilterBundle(
        val query: String,
        val category: VaultCategoryFilter,
        val selectedItemId: String?,
        val generator: GeneratorUiState,
        val pendingPassword: String?,
    )

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    repository = VaultRuntime.getRepository(context.applicationContext),
                ) as T
            }
        }
    }
}
