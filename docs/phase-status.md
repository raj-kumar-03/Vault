# Phase Status — Aran (Offline Password Manager)

**Current Active Phase:** Phase 3 — Local Database, Vault Management & Aran Design System  
**Gate Status:** Implemented & verified — awaiting human approval before Phase 4

## Phase Matrix

| Phase | Scope | Status |
|---|---|---|
| Phase 1 | Product definition, adaptive Android UI, mock repositories, unit & UI tests | **Completed & Approved** |
| Phase 2 | Cryptographic engine (Argon2id, VEK wrapping, AES-256-GCM + AAD, independent recovery key, password rotation, vault format v1, RFC/NIST test vectors & tamper tests) | **Completed & Approved** |
| Phase 3 | Encrypted local SQLite persistence (Room v2 + `MIGRATION_1_2`), metadata minimization, soft-delete tombstones, corruption quarantine, versioned `.svbk` encrypted backup/restore, **Aran** dark charcoal & lime design system, offline **Password Health** diagnostics, and build-time optimization | **Implemented (Awaiting Gate Approval)** |
| Phase 4 | Android Keystore, `BiometricPrompt` CryptoObject (Fingerprint & Class 3 Face Unlock), forced `FLAG_SECURE`, background auto-lock lifecycle, process-death handling | Not started |
| Phase 5 | Android `AutofillService` + **Offline Android Passkey Provider (Android 14+ `CredentialProviderService` — FIDO2/WebAuthn ES256 keys stored encrypted inside local vault)** | Not started |
| Phase 6 | Ephemeral X25519 + HKDF-SHA256 device pairing, SAS verification, local Wi-Fi sync, conflict resolution & tombstones | Not started |
| Phase 7 | Security audit preparation, performance, cross-platform compatibility, release verification | Not started |

## Phase 3 Acceptance Checklist

- [x] Room local SQLite persistence (`aran_encrypted_vault.db`) storing `vault_headers`, `vault_records`, and `vault_preferences` with atomic `@Transaction` writes (`replaceVaultAtomically`, `upsertRecordAndTouchHeader`, `softDeleteRecordAndTouchHeader`).
- [x] Strict metadata minimization: titles, usernames, domains, notes, favorites, and secrets are encrypted inside `vault_records.ciphertext_and_tag`; raw SQLite queries verified to contain zero plaintext credentials or metadata.
- [x] Non-destructive schema migration (`VaultDatabase.MIGRATION_1_2`) adding `is_deleted` and `record_revision` without touching encrypted payloads; verified on a live v1 SQLite fixture.
- [x] Per-record corruption quarantine: tampered or malformed SQLite rows are isolated into `corruptedRecordIds` with an explicit warning banner on `VaultHomeScreen` while intact entries remain accessible.
- [x] Versioned encrypted backup & restore (`.svbk` v1 via zero-permission Android Storage Access Framework): trailing 32-byte SHA-256 integrity verification, full VEK + per-record AES-256-GCM authentication before touching local SQLite, and support for both Master Password and Recovery Key restore.
- [x] Cold-start auto-routing: launches directly to `UnlockScreen` when a persisted SQLite vault exists, or `WelcomeScreen` on first run.
- [x] **Aran Design System & Offline Password Health**: Dark charcoal/slate surfaces + vibrant lime full-pill CTAs, onboarding graphic hero, search + circular lime utility action, featured security carousel, and offline `PasswordHealthScreen` with segmented health bar and diagnostic drill-downs.
- [x] **Build Speed Optimization**: Replaced ~30 MB `material-icons-extended` with lightweight `AranIcons.kt`, disabled unused Gradle plugins (`secrets`, `google-services`) and `buildConfig`, and enabled Kotlin incremental compilation with classpath snapshots.

## Remaining Limitations (Intentional Phase 3 Boundaries)

- Native Fingerprint / Class 3 Face Unlock binding via Android Keystore + `BiometricPrompt` `CryptoObject`, automatic background inactivity locking, and forced `FLAG_SECURE` by default remain scheduled for **Phase 4**.
- System-wide `AutofillService` and **Offline Android Passkey Provider (Android 14+ `CredentialProviderService`)** remain scheduled for **Phase 5**.
- Paired Devices remain local prototype fixtures until **Phase 6 (Local Wi-Fi Sync)**.
