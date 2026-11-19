package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MockVaultRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class GeneratorUiState(
    val config: GeneratorConfig = GeneratorConfig(),
    val generatedValue: String = "",
    val strength: PasswordStrength = PasswordEngine.calculateGeneratorStrength(GeneratorConfig()),
)

data class VaultUiState(
    val hasVault: Boolean = false,
    val isUnlocked: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: VaultCategoryFilter = VaultCategoryFilter.ALL,
    val selectedItemId: String? = null,
    val devices: List<PairedDevice> = emptyList(),
    val settings: SecuritySettings = SecuritySettings(),
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

    val recentItems: List<VaultItem>
        get() = items.sortedByDescending { it.lastUsedEpochMs }.take(4)

    fun countForFilter(filter: VaultCategoryFilter): Int = when (filter) {
        VaultCategoryFilter.ALL -> items.size
        VaultCategoryFilter.FAVORITES -> items.count { it.isFavorite }
        VaultCategoryFilter.LOGINS -> items.count { it.type == VaultItemType.LOGIN }
        VaultCategoryFilter.SECURE_NOTES -> items.count { it.type == VaultItemType.SECURE_NOTE }
        VaultCategoryFilter.IDENTITIES -> items.count { it.type == VaultItemType.IDENTITY }
        VaultCategoryFilter.CARDS -> items.count { it.type == VaultItemType.PAYMENT_CARD }
    }
}

class VaultViewModel(
    private val repository: MockVaultRepository = MockVaultRepository(),
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedCategoryFlow = MutableStateFlow(VaultCategoryFilter.ALL)
    private val selectedItemIdFlow = MutableStateFlow<String?>(null)
    private val pendingGeneratedPasswordFlow = MutableStateFlow<String?>(null)

    private val initialConfig = GeneratorConfig()
    private val generatorFlow = MutableStateFlow(
        GeneratorUiState(
            config = initialConfig,
            generatedValue = PasswordEngine.generate(initialConfig),
            strength = PasswordEngine.calculateGeneratorStrength(initialConfig),
        )
    )

    private val baseState = combine(
        repository.hasVault,
        repository.isUnlocked,
        repository.items,
        repository.devices,
        repository.settings,
    ) { hasVault, isUnlocked, items, devices, settings ->
        VaultUiState(
            hasVault = hasVault,
            isUnlocked = isUnlocked,
            items = items,
            devices = devices,
            settings = settings,
        )
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

    val uiState: StateFlow<VaultUiState> = combine(baseState, uiFilters) { base, filters ->
        base.copy(
            searchQuery = filters.query,
            selectedCategory = filters.category,
            selectedItemId = filters.selectedItemId ?: base.items.firstOrNull()?.id,
            generator = filters.generator,
            pendingGeneratedPasswordForEditor = filters.pendingPassword,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = VaultUiState(
            hasVault = repository.hasVault.value,
            isUnlocked = repository.isUnlocked.value,
            items = repository.items.value,
            selectedItemId = repository.items.value.firstOrNull()?.id,
            devices = repository.devices.value,
            settings = repository.settings.value,
            generator = generatorFlow.value,
        ),
    )

    fun createVault(masterPassword: String) {
        repository.createMockVault(masterPassword)
    }

    fun openDemoVault() {
        repository.openDemoVault()
    }

    fun attemptUnlock(candidate: String): Boolean {
        return repository.unlockWithMasterPassword(candidate)
    }

    fun attemptBiometricUnlock(): Boolean {
        return repository.unlockWithMockBiometric()
    }

    fun lockVault() {
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
        repository.markItemUsed(itemId)
    }

    fun toggleFavorite(itemId: String) {
        repository.toggleFavorite(itemId)
    }

    fun saveItem(item: VaultItem): VaultItem {
        val saved = repository.upsertItem(item)
        selectedItemIdFlow.value = saved.id
        pendingGeneratedPasswordFlow.value = null
        return saved
    }

    fun deleteItem(itemId: String) {
        repository.deleteItem(itemId)
        if (selectedItemIdFlow.value == itemId) {
            selectedItemIdFlow.value = repository.items.value.firstOrNull()?.id
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
            // Prevent disabling every character class in random password mode.
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
        repository.updateSettings { it.copy(autoLockInterval = interval) }
    }

    fun updateBiometricUnlock(enabled: Boolean) {
        repository.updateSettings { it.copy(biometricUnlockEnabled = enabled) }
    }

    fun updateClipboardClear(interval: ClipboardClearInterval) {
        repository.updateSettings { it.copy(clipboardClearInterval = interval) }
    }

    fun updateHideAppPreview(enabled: Boolean) {
        repository.updateSettings { it.copy(hideAppPreviewEnabled = enabled) }
    }

    fun updateThemePreference(preference: ThemePreference) {
        repository.updateSettings { it.copy(themePreference = preference) }
    }

    private data class FilterBundle(
        val query: String,
        val category: VaultCategoryFilter,
        val selectedItemId: String?,
        val generator: GeneratorUiState,
        val pendingPassword: String?,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(MockVaultRepository()) as T
            }
        }
    }
}
