# Phase Status — Aran (Offline Password Manager)

**Current Active Phase:** Phase 5 — Android Autofill, Offline Passkeys & Biometric Entry Gate  
**Gate Status:** Implemented & verified — awaiting human approval before Phase 6

## Phase Matrix

| Phase | Scope | Status |
|---|---|---|
| Phase 1 | Product definition, adaptive Android UI, mock repositories, unit & UI tests | **Completed & Approved** |
| Phase 2 | Cryptographic engine (Argon2id, VEK wrapping, AES-256-GCM + AAD, independent recovery key, password rotation, vault format v1, RFC/NIST test vectors & tamper tests) | **Completed & Approved** |
| Phase 3 | Encrypted local SQLite persistence (migrated from Room/KSP to native `SQLiteOpenHelper`), metadata minimization, soft-delete tombstones, corruption quarantine, versioned `.svbk` encrypted backup/restore, **Aran** dark charcoal & lime design system, offline **Password Health** diagnostics | **Completed & Approved** |
| Phase 4 | Android Keystore AES-256-GCM VEK wrapping, `BiometricPrompt` `CryptoObject` (Fingerprint & Class 3 Face Unlock) with entry-point auto-prompt, key invalidation recovery, monotonic auto-lock lifecycle, `FLAG_SECURE` + recents hiding, match-only sensitive clipboard clearing, disabled OS Auto Backup | **Completed & Verified** |
| Phase 5 | Android `AutofillService` (strict HTTPS origin & APK SHA-256 verification, HTTP blocking, hidden-field filtering, conflicting-origin rejection) + **Offline Android Passkey Provider** (API 34+ `CredentialProviderService`, FIDO2/WebAuthn ES256 keys encrypted inside local AES-256-GCM vault) | **Implemented & Verified (Awaiting Gate Approval)** |
| Phase 6 | Ephemeral X25519 + HKDF-SHA256 device pairing, SAS verification, local Wi-Fi sync, conflict resolution & tombstones | Not started |
| Phase 7 | Security audit preparation, performance, cross-platform compatibility, release verification | Not started |

## Phase 4 & 5 Acceptance Checklist

- [x] Native `SQLiteOpenHelper` database (`aran_encrypted_vault.db`, schema v4 with non-destructive v1 → v2 → v3 → v4 upgrades); Room & KSP removed for fast incremental compiles while keeping audited Bouncy Castle Argon2id.
- [x] Android Keystore device-bound AES-256-GCM key (`setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`) bound strictly to `BiometricPrompt.CryptoObject(cipher)`.
- [x] Application entry point (`UnlockScreen`, `AutofillAuthActivity`, `PasskeyAuthActivity`) automatically invokes `BiometricPrompt` (Fingerprint or Class 3 Face Unlock) when bound, with master-password & recovery-key fallback.
- [x] Monotonic auto-lock (`SystemClock.elapsedRealtime()`) across backgrounding, foreground inactivity, and process death; `FLAG_SECURE` + recents screenshot protection; match-only SHA-256 sensitive clipboard clearing; Android Auto Backup disabled.
- [x] `AranAutofillService` + `AutofillAuthActivity` registered in manifest with strict `OriginMatcher` (HTTPS host/registrable domain, multi-label public suffix table, insecure HTTP blocked by default, APK SHA-256 certificate pinning, hidden-field & mixed-origin rejection).
- [x] `AranCredentialProviderService` + `PasskeyAuthActivity` (Android 14+ / API 34+) + `OfflinePasskeyEngine` implementing 100% offline ECDSA P-256 (ES256) WebAuthn registration & assertion signing stored encrypted inside local SQLite.
- [x] Live Phase 5 Settings controls for Android AutofillService activation, HTTP autofill blocking policy, Offline Passkey Provider settings, and entry-point biometric auto-prompt.

## Remaining Limitations (Intentional Phase 5 Boundaries)

- Paired Devices remain local prototype fixtures until **Phase 6 (Local Wi-Fi Sync)**.
- Desktop Windows/Linux keystore support and desktop Manifest V3 browser extension sit outside this Android module.
