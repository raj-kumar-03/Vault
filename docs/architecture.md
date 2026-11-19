# Architecture — SecureVault (Phase 3)

## Dependency Direction

`UI Screens & Components` → `VaultViewModel (StateFlow)` → `VaultRepository Interface` → `PersistentVaultRepository` → `VaultDao (Room SQLite)` + `VaultCryptoEngine (com.example.crypto)`

- UI components and screens never import Room DAOs, `Cipher`, `Argon2BytesGenerator`, or raw key bytes.
- All Argon2id KDF derivations run off the UI thread (`Dispatchers.Default`); all SQLite reads/writes and SAF backup I/O run on `Dispatchers.IO`.
- `PersistentVaultRepository` persists only `VaultHeaderEntity` and AES-256-GCM `VaultRecordEntity` rows in SQLite; plaintext `VaultItem`s exist in `_items` strictly while unlocked and are wiped on `lockVault()` alongside `VaultSessionKey.wipe()`.

## Package Layout

```text
app/src/main/java/com/example/
├── crypto/              # Argon2id KDF, HKDF-SHA256 RecoveryKeyCodec, AES-256-GCM VaultCryptoEngine, VaultFormat v1, VaultBackupCodec (.svbk v1), AadBuilder, VaultFormatMigrator
├── domain/model/        # Pure Kotlin immutable models (including VaultItemType.PASSKEY) & PasswordEngine
├── data/
│   ├── local/           # Room VaultDatabase (v2 + MIGRATION_1_2), VaultDao (@Transaction writes), VaultHeaderEntity, VaultRecordEntity, VaultPreferencesEntity
│   ├── VaultRepository.kt
│   └── PersistentVaultRepository.kt
├── ui/theme/            # Midnight Emerald dark/light semantic tokens & bundled typography
├── ui/components/       # Stateless reusable M3 components
├── ui/state/            # VaultViewModel & immutable VaultUiState
├── ui/screens/          # Welcome, CreateVault, Unlock, VaultHome, ItemDetail, AddEditItem, Generator, Devices, Settings, CryptoDialogs, BackupRestoreDialogs, RecoveryInfoSheet
└── ui/navigation/       # SecureVaultApp NavHost, cold-start auto-routing, zero-permission SAF backup/restore, safe clipboard manager, FLAG_SECURE bridge
```

## Planned Extensions (Phases 4–6)

- **Phase 4:** Adds `AndroidKeystoreWrapper` + `BiometricPrompt` `CryptoObject` (Class 3 Fingerprint & Face Unlock), background inactivity auto-lock observer, and enforced `FLAG_SECURE`.
- **Phase 5:** Adds Android `AutofillService` and an **Offline Android Passkey Provider** (`androidx.credentials.provider.CredentialProviderService` on Android 14+ / API 34+), storing WebAuthn/FIDO2 ES256 private keys encrypted inside `VaultItemType.PASSKEY` records with zero cloud dependency.
- **Phase 6:** Adds ephemeral X25519 + HKDF-SHA256 LAN pairing and delta sync leveraging the `is_deleted` tombstones and `record_revision` counters introduced in Room schema v2.
