# Architecture — SecureVault (Phase 2)

## Dependency Direction

`UI Screens & Components` → `VaultViewModel (StateFlow)` → `VaultRepository Interface` → `EncryptedMemoryVaultRepository` → `VaultCryptoEngine (com.example.crypto)`

- UI components and screens never import or invoke `Cipher`, `Argon2BytesGenerator`, or raw key bytes.
- All Argon2id KDF derivations run off the UI thread via coroutines (`Dispatchers.Default`).
- `EncryptedMemoryVaultRepository` holds only `VaultHeader` and `LinkedHashMap<String, EncryptedVaultRecord>` at rest; plaintext `VaultItem`s exist in `_items` only while unlocked and are cleared on `lockVault()` alongside `VaultSessionKey.wipe()`.

## Package Layout

```text
app/src/main/java/com/example/
├── crypto/              # Argon2id KDF, HKDF-SHA256 RecoveryKeyCodec, AES-256-GCM VaultCryptoEngine, VaultFormat v1, AadBuilder, VaultFormatMigrator
├── domain/model/        # Pure Kotlin immutable models & PasswordEngine
├── data/                # VaultRepository / DeviceRepository / SettingsRepository interfaces + EncryptedMemoryVaultRepository
├── ui/theme/            # Midnight Emerald dark/light semantic tokens & bundled typography
├── ui/components/       # Stateless reusable M3 components
├── ui/state/            # VaultViewModel & immutable VaultUiState
├── ui/screens/          # Welcome, CreateVault, Unlock, VaultHome, ItemDetail, AddEditItem, Generator, Devices, Settings, CryptoDialogs, RecoveryInfoSheet
└── ui/navigation/       # SecureVaultApp NavHost, safe clipboard manager, FLAG_SECURE bridge
```

## Phase 3 Readiness

Phase 3 replaces only the in-memory `vaultHeader` field and `encryptedStore` map inside `EncryptedMemoryVaultRepository` with a local SQLite/Room persistence layer storing the exact same `VaultHeader` and `EncryptedVaultRecord` blobs—without changing `VaultCryptoEngine` or UI components.
