# Project Map — SecureVault (Phase 2)

## Entry Points

- `app/src/main/java/com/example/MainActivity.kt` — Edge-to-edge activity hosting `SecureVaultApp()`.
- `app/src/main/java/com/example/ui/navigation/SecureVaultApp.kt` — Navigation host, safe clipboard manager, `FLAG_SECURE` bridge, and one-time Recovery Key reveal sheet.

## Cryptographic Boundary (Phase 2)

- `app/src/main/java/com/example/crypto/VaultCryptoEngine.kt` — `DefaultVaultCryptoEngine`, `VaultSessionKey`, `SecureRandomNonceGenerator`.
- `app/src/main/java/com/example/crypto/KdfParameters.kt` — Documented OWASP-aligned Argon2id parameter profiles & benchmark result.
- `app/src/main/java/com/example/crypto/RecoveryKeyCodec.kt` — 256-bit independent recovery key generator, Crockford Base32 + SHA-256 checksum codec, HKDF-SHA256 KEK derivation.
- `app/src/main/java/com/example/crypto/VaultFormat.kt` — `VaultHeader`, `EncryptedPayload`, `EncryptedVaultRecord`, `AadBuilder`, `VaultRecordCodec`, `VaultFormatMigrator`.
- `app/src/main/java/com/example/crypto/CryptoExceptions.kt` — Secret-free typed cryptographic exceptions.

## Repository & UI State

- `app/src/main/java/com/example/data/VaultRepository.kt` — Async repository interfaces & `VaultCryptoMetadata`.
- `app/src/main/java/com/example/data/EncryptedMemoryVaultRepository.kt` — In-memory AES-256-GCM encrypted store (predecessor to Phase 3 SQLite persistence).
- `app/src/main/java/com/example/ui/state/VaultViewModel.kt` — Coroutine-backed ViewModel exposing `StateFlow<VaultUiState>`.
- `app/src/main/java/com/example/ui/screens/CryptoDialogs.kt` — `RecoveryKeyCreatedSheet`, `ChangeMasterPasswordSheet`, `RecoverVaultSheet`.

## Tests & Focused Commands

- Crypto vectors, tamper, nonce, rotation & recovery tests: `app/src/test/java/com/example/CryptoVectorsAndTamperTest.kt`
- Password generator & domain tests: `app/src/test/java/com/example/PasswordEngineTest.kt`
- Robolectric Compose UI tests: `app/src/test/java/com/example/SecureVaultUiTest.kt`, `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- Run all unit & Robolectric tests: `gradle :app:testDebugUnitTest`
- Build debug APK: `gradle :app:assembleDebug`
