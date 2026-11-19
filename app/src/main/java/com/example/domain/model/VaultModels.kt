package com.example.domain.model

enum class VaultItemType(val displayLabel: String, val pluralLabel: String) {
    LOGIN("Login", "Logins"),
    PASSKEY("Passkey", "Passkeys"),
    SECURE_NOTE("Secure Note", "Secure Notes"),
    IDENTITY("Identity", "Identities"),
    PAYMENT_CARD("Card", "Cards"),
}

enum class VaultCategoryFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    LOGINS("Logins"),
    PASSKEYS("Passkeys"),
    SECURE_NOTES("Notes"),
    IDENTITIES("Identities"),
    CARDS("Cards"),
}

data class VaultItem(
    val id: String,
    val type: VaultItemType,
    val title: String,
    val username: String,
    val secret: String,
    val website: String,
    val normalizedDomain: String,
    val notes: String,
    val isFavorite: Boolean,
    val createdAtLabel: String,
    val updatedAtLabel: String,
    val lastUsedEpochMs: Long,
    val secondaryIdentifier: String = "",
) {
    /**
     * Returns a list-safe subtitle without ever exposing secret values.
     */
    fun maskedSubtitle(): String {
        return when (type) {
            VaultItemType.LOGIN -> username.ifBlank { normalizedDomain.ifBlank { "No username" } }
            VaultItemType.PASSKEY -> username.ifBlank { normalizedDomain.ifBlank { "FIDO2 Passkey • Offline" } }
            VaultItemType.SECURE_NOTE -> secondaryIdentifier.ifBlank { "Encrypted note • Masked" }
            VaultItemType.IDENTITY -> username.ifBlank { secondaryIdentifier.ifBlank { "Personal identity" } }
            VaultItemType.PAYMENT_CARD -> secondaryIdentifier.ifBlank { "•••• ••••" }
        }
    }
}

enum class DevicePlatform(val label: String) {
    ANDROID("Android"),
    LINUX("Linux Desktop"),
    WINDOWS("Windows Desktop"),
}

enum class DeviceStatus(val label: String) {
    THIS_DEVICE("This Device"),
    ACTIVE("Active"),
    REVOKED("Revoked"),
}

data class PairedDevice(
    val id: String,
    val friendlyName: String,
    val platform: DevicePlatform,
    val lastSyncLabel: String,
    val status: DeviceStatus,
    val fingerprintPreview: String,
)

enum class ThemePreference(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System"),
}

enum class AutoLockInterval(val label: String) {
    ONE_MINUTE("1 minute"),
    FIVE_MINUTES("5 minutes"),
    FIFTEEN_MINUTES("15 minutes"),
    NEVER("Manual lock only"),
}

enum class ClipboardClearInterval(val label: String) {
    THIRTY_SECONDS("30 seconds"),
    ONE_MINUTE("1 minute"),
    OFF("Off"),
}

data class SecuritySettings(
    val autoLockInterval: AutoLockInterval = AutoLockInterval.FIVE_MINUTES,
    val biometricUnlockEnabled: Boolean = true,
    val clipboardClearInterval: ClipboardClearInterval = ClipboardClearInterval.THIRTY_SECONDS,
    val hideAppPreviewEnabled: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.DARK,
)

enum class GeneratorMode(val label: String) {
    PASSWORD("Password"),
    PASSPHRASE("Passphrase"),
}

data class GeneratorConfig(
    val mode: GeneratorMode = GeneratorMode.PASSWORD,
    val length: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val wordCount: Int = 5,
    val wordSeparator: String = "-",
    val capitalizeWords: Boolean = true,
    val includePassphraseNumber: Boolean = true,
)

enum class StrengthLevel(val label: String, val fraction: Float) {
    WEAK("Weak", 0.25f),
    FAIR("Fair", 0.50f),
    STRONG("Strong", 0.78f),
    VERY_STRONG("Very Strong", 1.0f),
}

data class PasswordStrength(
    val level: StrengthLevel,
    val entropyBits: Double,
    val explanation: String,
    val isExactFormula: Boolean,
)

data class DomainNormalizationResult(
    val rawInput: String,
    val normalizedDomain: String,
    val isValid: Boolean,
    val guidance: String,
)
