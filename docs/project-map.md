# Project Map — SecureVault (Phase 3)

## Entry Points

- `app/src/main/java/com/example/MainActivity.kt` — Edge-to-edge activity hosting `SecureVaultApp()`.
- `app/src/main/java/com/example/ui/navigation/SecureVaultApp.kt` — Cold-start auto-routing (`Unlock` vs `Welcome`), Navigation host, zero-permission SAF `.svbk` backup export/import, safe clipboard manager, `FLAG_SECURE` bridge, and Recovery/Backup sheets.

## Cryptographic Boundary (Phases 2 & 3)

- `app/src/main/java/com/example/crypto/VaultCryptoEngine.kt` — `DefaultVaultCryptoEngine`, `VaultSessionKey`, `SecureRandomNonceGenerator`.
- `app/src/main/java/com/example/crypto/KdfParameters.kt` — Documented OWASP-aligned Argon2id parameter profiles & benchmark result.
- `app/src/main/java/com/example/crypto/RecoveryKeyCodec.kt` — 256-bit independent recovery key generator, Crockford Base32 + SHA-256 checksum codec, HKDF-SHA256 KEK derivation.
- `app/src/main/java/com/example/crypto/VaultFormat.kt` — `VaultHeader`, `EncryptedPayload`, `EncryptedVaultRecord`, `AadBuilder`, `VaultRecordCodec`, `VaultFormatMigrator`.
- `app/src/main/java/com/example/crypto/VaultBackupCodec.kt` — Versioned `.svbk` v1 encrypted backup binary encoder, inspector, and SHA-256 verified decoder.
- `app/src/main/java/com/example/crypto/CryptoExceptions.kt` — Secret-free typed cryptographic exceptions.

## Local Encrypted Persistence & UI State (Phase 3)

- `app/src/main/java/com/example/data/local/VaultEntities.kt` — Room entities (`VaultHeaderEntity`, `VaultRecordEntity`, `VaultPreferencesEntity`).
- `app/src/main/java/com/example/data/local/VaultDao.kt` — Room DAO with atomic `@Transaction` writes and soft-delete tombstones.
- `app/src/main/java/com/example/data/local/VaultDatabase.kt` — Room database (`secure_vault.db`, v2) with non-destructive `MIGRATION_1_2`.
- `app/src/main/java/com/example/data/VaultRepository.kt` — Async repository interfaces & `VaultCryptoMetadata`.
- `app/src/main/java/com/example/data/PersistentVaultRepository.kt` — Encrypted Room-backed repository with corruption quarantine & non-destructive backup restore.
- `app/src/main/java/com/example/ui/state/VaultViewModel.kt` — Coroutine-backed ViewModel with `VaultViewModel.factory(context)`.
- `app/src/main/java/com/example/ui/screens/BackupRestoreDialogs.kt` — `RestoreBackupSheet` for `.svbk` inspection & verification.
- `app/src/main/java/com/example/ui/screens/CryptoDialogs.kt` — `RecoveryKeyCreatedSheet`, `ChangeMasterPasswordSheet`, `RecoverVaultSheet`.

## Tests & Focused Commands

- Crypto vectors, tamper, nonce, rotation, recovery & `.svbk` codec tests: `app/src/test/java/com/example/CryptoVectorsAndTamperTest.kt`
- SQLite persistence, metadata minimization, `MIGRATION_1_2`, corruption quarantine & backup/restore tests: `app/src/test/java/com/example/PersistentVaultStorageTest.kt`
- Password generator & domain tests: `app/src/test/java/com/example/PasswordEngineTest.kt`
- Robolectric Compose UI & cold-start tests: `app/src/test/java/com/example/SecureVaultUiTest.kt`, `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- Run all unit & Robolectric tests: `gradle :app:testDebugUnitTest`
- Build debug APK: `gradle :app:assembleDebug`
