# Threat Model — SecureVault (Phase 3)

**Status:** Phase 3 encrypted local SQLite storage & `.svbk` backup/restore implemented and tested.  
**Notice:** Hardware Keystore biometric wrapping, background auto-lock enforcement, and default `FLAG_SECURE` ship in Phase 4. Do not rely on this pre-Phase-4 build for high-stakes credentials until Phase 4 hardening and an independent security audit are complete.

## Implemented Controls (Phases 1–3)

1. **Master-Password Brute Force Resistance (Phase 2):** Argon2id (RFC 9106 `0x13`, `m=19 MiB, t=2, p=1`, 128-bit random salt) derives a 256-bit KEK wrapping a random 256-bit VEK.
2. **Confidentiality & Integrity of Records (Phase 2):** Every record is encrypted with AES-256-GCM using a fresh 96-bit CSPRNG nonce and canonical length-prefixed AAD binding `formatVersion`, `vaultId`, `recordId`, and `recordType`.
3. **Independent Recovery Without Master-Password Equivalence (Phase 2):** A separately generated 256-bit random recovery key (`SVRK-...` with SHA-256 checksum) wraps the VEK via HKDF-SHA256, enabling master-password reset without re-encrypting records.
4. **Minimized On-Disk Metadata & Atomic Storage (Phase 3):** Local SQLite (`secure_vault.db`) never stores plaintext titles, usernames, domains, notes, favorites, or secrets. Multi-record mutations run inside atomic Room `@Transaction`s; corrupted rows are quarantined into `corruptedRecordIds` without crashing.
5. **Verified Offline Backups Without Storage Permissions (Phase 3):** `.svbk` v1 exports never decrypt vault contents and use Android SAF (`CreateDocument`/`OpenDocument`) with zero broad storage permissions. Restore verifies the 32-byte SHA-256 digest, unwraps the VEK, and authenticates every record before touching local SQLite.
6. **Memory, UI & Clipboard Hygiene (Phases 1–3):** `VaultSessionKey.wipe()` zeroes VEK on lock, masked-by-default secrets, 15s auto-re-masking reveal banner, `EXTRA_IS_SENSITIVE` clipboard flag (API 33+), best-effort auto-clear, toggleable `FLAG_SECURE`, and generic unlock error messages.

## Planned Controls (Phases 4–6)

- **Phase 4:** Android Keystore hardware-backed key wrapping, `BiometricPrompt` `CryptoObject` (Class 3 Fingerprint & Face Unlock), default `FLAG_SECURE`, background auto-lock & process-death wipe.
- **Phase 5:** Android `AutofillService` with strict origin & app-signature checks + **Offline Android Passkey Provider (API 34+ `CredentialProviderService`)** enforcing strict RP ID / asset-links verification and biometric user verification (`UV=1`) before signing WebAuthn assertions with vault-encrypted ES256 private keys.
- **Phase 6:** Ephemeral X25519 + HKDF-SHA256 local Wi-Fi sync with SAS confirmation, replay protection, and revocation.
