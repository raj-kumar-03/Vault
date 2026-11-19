# Phase Status — SecureVault (Offline Password Manager)

**Current Active Phase:** Phase 1 — Product definition, architecture, and UI/UX  
**Gate Status:** Ready for human review before Phase 2

## Phase Matrix

| Phase | Scope | Status |
|---|---|---|
| Phase 1 | Product definition, Midnight Emerald design system, adaptive Android UI, mock repositories, unit & UI tests | **Implemented (Awaiting Gate Approval)** |
| Phase 2 | Cryptographic engine (Argon2id, VEK wrapping, AES-256-GCM, AAD binding, recovery key, test vectors) | Not started |
| Phase 3 | Encrypted local SQLite persistence, CRUD, search, schema migrations, encrypted backup/restore | Not started |
| Phase 4 | Android Keystore, BiometricPrompt crypto binding, auto-lock lifecycle, process-death handling | Not started |
| Phase 5 | Android AutofillService & Desktop Manifest V3 extension via Native Messaging | Not started |
| Phase 6 | Ephemeral X25519 + HKDF-SHA256 device pairing, SAS verification, local Wi-Fi sync, tombstones | Not started |
| Phase 7 | Security audit preparation, performance, cross-platform compatibility, release verification | Not started |

## Phase 1 Acceptance Checklist

- [x] All required screens implemented and navigable (`Welcome`, `Create Vault`, `Unlock`, `Vault Home`, `Item Detail`, `Add/Edit Item`, `Generator`, `Devices`, `Settings`).
- [x] Mock repositories with deterministic synthetic fixtures only (`.example` / `.test` domains).
- [x] No real secrets persisted to disk; no simulated cryptography.
- [x] Every visible control works in prototype or is explicitly labeled with its target phase (`Coming in Phase 5`, `Coming in Phases 2–3`, `Ships in Phase 6`).
- [x] Empty, filtered, error, revealed, locked, and unlocked states implemented.
- [x] Compact phone (bottom navigation bar) and wide-screen (navigation rail + list-detail pane) layouts implemented.
- [x] Reusable Compose component library with explicit state/callback contracts.
- [x] Unit tests and Robolectric Compose UI tests covering navigation and form validation.

## Known Limitations (Intentional Phase 1 Boundaries)

- Vault entries live strictly in memory (`MockVaultRepository`) and reset on process restart.
- Passphrase generator uses a 128-word prototype wordlist (7.0 bits/word, transparently reported in UI) until Phase 3 bundles the full 7,776-word EFF list.
- Auto-lock interval selection is stored in memory; OS background lifecycle locking ships in Phase 4.
- `FLAG_SECURE` is user-toggleable in Settings during Phase 1 preview and will be enforced by default in Phase 4.
