# Phase Status — SecureVault (Offline Password Manager)

**Current Active Phase:** Phase 2 — Cryptographic engine and vault format  
**Gate Status:** Implemented & verified — awaiting human approval before Phase 3

## Phase Matrix

| Phase | Scope | Status |
|---|---|---|
| Phase 1 | Product definition, Midnight Emerald design system, adaptive Android UI, mock repositories, unit & UI tests | **Completed & Approved** |
| Phase 2 | Cryptographic engine (Argon2id, VEK wrapping, AES-256-GCM + AAD, independent recovery key, password rotation, vault format v1, RFC/NIST test vectors & tamper tests) | **Implemented (Awaiting Gate Approval)** |
| Phase 3 | Encrypted local SQLite persistence, CRUD, search, schema migrations, encrypted backup/restore | Not started |
| Phase 4 | Android Keystore, BiometricPrompt crypto binding, auto-lock lifecycle, process-death handling | Not started |
| Phase 5 | Android AutofillService & Desktop Manifest V3 extension via Native Messaging | Not started |
| Phase 6 | Ephemeral X25519 + HKDF-SHA256 device pairing, SAS verification, local Wi-Fi sync, tombstones | Not started |
| Phase 7 | Security audit preparation, performance, cross-platform compatibility, release verification | Not started |

## Phase 2 Acceptance Checklist

- [x] Argon2id (RFC 9106 v0x13 via Bouncy Castle `1.80`) with documented OWASP mobile parameters (`19 MiB, t=2, p=1`) and live device benchmark tool.
- [x] Random 256-bit VEK wrapped separately by Argon2id master KEK and HKDF-SHA256 independent 256-bit Recovery Key (`SVRK-...` Crockford Base32 + SHA-256 checksum).
- [x] AES-256-GCM authenticated encryption with fresh 96-bit CSPRNG nonces and canonical length-prefixed AAD binding `formatVersion`, `vaultId`, `recordId`, and `recordType`.
- [x] Versioned format (`v1`) and explicit `VaultFormatMigrator` rejecting unsupported versions.
- [x] Master password change and recovery key reset flows re-wrap only the VEK without re-encrypting records.
- [x] Verified against official RFC 9106 Section 5.3 Argon2id vector and NIST SP 800-38D AES-256-GCM Test Cases 13, 14, and 16.
- [x] Tamper rejection, nonce uniqueness, key wiping, and secret-free exception tests pass.
- [x] Full vault format specification documented in `docs/vault-format.md`.

## Remaining Limitations (Intentional Phase 2 Boundaries)

- Encrypted records and `VaultHeader` live in memory (`EncryptedMemoryVaultRepository`) and reset on app restart until **Phase 3 (Encrypted SQLite Persistence)** is approved.
- Biometric unlock remains a Settings-gated prototype affordance until **Phase 4 (Android Keystore + BiometricPrompt crypto binding)**.
