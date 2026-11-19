# Threat Model — SecureVault (Phase 2)

**Status:** Phase 2 cryptographic boundary implemented and tested (in-memory only).  
**Notice:** Do not store critical real-world credentials until Phase 3 persistent storage, Phase 4 platform key protection, and an independent security review are complete.

## Implemented Controls (Phases 1 & 2)

1. **Master-Password Brute Force Resistance (Phase 2):** Argon2id (RFC 9106 `0x13`, `m=19 MiB, t=2, p=1`, 128-bit random salt) derives a 256-bit KEK wrapping a random 256-bit VEK.
2. **Confidentiality & Integrity of Records (Phase 2):** Every record is encrypted with AES-256-GCM using a fresh 96-bit CSPRNG nonce and canonical length-prefixed AAD binding `formatVersion`, `vaultId`, `recordId`, and `recordType`. Bit flips, nonce reuse/modification, tag tampering, and cross-record swapping are rejected.
3. **Independent Recovery Without Master-Password Equivalence (Phase 2):** A separately generated 256-bit random recovery key (`SVRK-...` with SHA-256 checksum) wraps the VEK via HKDF-SHA256, enabling master-password reset without re-encrypting records.
4. **Memory & Exception Hygiene (Phase 2):** `VaultSessionKey.wipe()` zeroes the VEK on lock, KDF/plaintext buffers are zeroed in `finally` blocks, and typed `VaultCryptoException`s omit secrets from messages.
5. **UI & Clipboard Mitigations (Phase 1):** Masked-by-default secrets, 15s auto-re-masking reveal banner, `EXTRA_IS_SENSITIVE` clipboard flag (API 33+), best-effort auto-clear, optional `FLAG_SECURE`, generic unlock error messages.

## Planned Controls (Phases 3–6)

- **Phase 3:** Encrypted SQLite storage, minimized unencrypted metadata, transactional writes, versioned encrypted backup/restore.
- **Phase 4:** Android Keystore hardware-backed key wrapping, `BiometricPrompt` CryptoObject authorization, forced `FLAG_SECURE`, background auto-lock & process-death wipe.
- **Phase 5:** Android `AutofillService` with strict origin & app-signature checks.
- **Phase 6:** Ephemeral X25519 + HKDF-SHA256 local Wi-Fi sync with SAS confirmation, replay protection, and revocation.
