# Vault Cryptographic & Storage Format Specification (`v1` / `.svbk v1`)

**Document:** `docs/vault-format.md`  
**Applies to:** Phase 2 (Cryptography) & Phase 3 (Encrypted Local SQLite + Backup Format)  
**Status:** Implemented & verified against RFC 9106, NIST SP 800-38D, SQLite migration, and `.svbk` tamper tests

---

## 1. Key Hierarchy

```text
Master Password (UTF-8)                     256-bit Random Recovery Secret (CSPRNG)
   │                                           │  encoded as SVRK-<Crockford-Base32 + 2B SHA-256 checksum>
   ▼ Argon2id (v0x13, m=19 MiB, t=2, p=1)      ▼ HKDF-SHA256 (RFC 5869, salt=recoverySalt, info="SecureVault-Recovery-KEK-v1:<vaultId>")
Master KEK (256-bit)                        Recovery KEK (256-bit)
   │                                           │
   │ AES-256-GCM + Master-Wrap AAD             │ AES-256-GCM + Recovery-Wrap AAD
   ▼                                           ▼
masterWrappedVek                            recoveryWrappedVek
   └──────────────► Vault Encryption Key (VEK, 256-bit random CSPRNG) ◄──────────────┘
                                       │
                                       │ AES-256-GCM (fresh 96-bit CSPRNG nonce per write)
                                       │ + Canonical Record AAD
                                       ▼
                           EncryptedVaultRecord[]
```

## 2. Argon2id Parameter Selection Rationale

- **Implementation:** Bouncy Castle `org.bouncycastle:bcprov-jdk18on:1.80` (`Argon2BytesGenerator`, RFC 9106 version `0x13`, Argon2id hybrid mode).
- **Default interactive mobile profile (`OWASP_MOBILE_BALANCED`):** `m=19_456 KiB` (19 MiB), `t=2`, `p=1`, 16-byte CSPRNG salt, 32-byte KEK.
- **On-device measurement:** Settings → Security → *Benchmark Argon2id on this device*.

## 3. Authenticated Encryption & Canonical AAD

- **Cipher:** Platform JCA `AES/GCM/NoPadding` (256-bit key, 96-bit CSPRNG nonce, 128-bit authentication tag).
- **Canonical length-prefixed AAD (`AadBuilder`):**
  - **Record AAD:** `"SV-REC-AAD-v1" || formatVersion || vaultId || recordId || recordType`
  - **Master VEK Wrap AAD:** `"SV-WRAP-MASTER-v1" || formatVersion || vaultId || keyRotationEpoch`
  - **Recovery VEK Wrap AAD:** `"SV-WRAP-RECOVERY-v1" || formatVersion || vaultId || keyRotationEpoch`

## 4. Record Plaintext Framing (`VaultRecordCodec` v1)

Encrypted inside `vault_records.ciphertext_and_tag`:
`schemaVersion(Int32=1) || title || username || secret || website || normalizedDomain || notes || isFavorite(Bool) || createdAtLabel || updatedAtLabel || lastUsedEpochMs(Int64) || secondaryIdentifier`

Supported `VaultItemType` values bound into AAD: `LOGIN`, `PASSKEY` (Phase 5 envelope ready), `SECURE_NOTE`, `IDENTITY`, `PAYMENT_CARD`.

## 5. Local SQLite Schema & Metadata Exposure (`secure_vault.db`, Room v2)

| Table | Unencrypted Columns | Encrypted / Protected Blobs | Rationale |
|---|---|---|---|
| `vault_headers` | `singleton_id`, `format_version`, `vault_id`, KDF params, `master_salt`, `recovery_salt`, `key_rotation_epoch`, timestamps | `master_wrap_nonce`, `master_wrap_ciphertext`, `recovery_wrap_nonce`, `recovery_wrap_ciphertext` | Enables KDF unlock, password rotation, and recovery without revealing VEK |
| `vault_records` | `record_id`, `vault_id`, `format_version`, `record_type`, `updated_at_epoch_ms`, `is_deleted`, `record_revision` | `nonce` (12B), `ciphertext_and_tag` (AES-256-GCM) | **Zero plaintext titles, usernames, domains, notes, favorites, or secrets** in SQLite; search runs strictly in memory while unlocked |
| `vault_preferences` | `singleton_id`, `auto_lock_interval`, `biometric_unlock_enabled`, `clipboard_clear_interval`, `hide_app_preview_enabled`, `theme_preference` | None (non-secret UI/session preferences) | Restores user theme and lock preferences across app restarts |

- **Migrations:** `VaultDatabase.MIGRATION_1_2` adds `is_deleted`, `record_revision`, and composite index `(vault_id, is_deleted)` non-destructively without touching encrypted blobs.
- **Corruption quarantine:** If any row fails AES-256-GCM verification or record decoding on unlock, its `record_id` is collected into `VaultCryptoMetadata.corruptedRecordIds` so intact items still open safely and the user receives a clear warning banner.

## 6. Versioned Encrypted Backup Format (`.svbk` v1 — `VaultBackupCodec`)

```text
[4B Magic "SVBK"]
[Int32 backupVersion = 1]
[Int64 exportedAtEpochMs]
[VaultHeader: formatVersion, vaultId, KDF params, masterSalt, masterWrappedVek, recoverySalt, recoveryWrappedVek, keyRotationEpoch, timestamps]
[Int32 recordCount]
[EncryptedVaultRecord[recordCount]: formatVersion, vaultId, recordId, recordType, nonce, ciphertextAndTag, updatedAtEpochMs]
[32B SHA-256 integrity digest over all preceding bytes]
```

**Non-destructive restore guarantee:** Before modifying local SQLite, `PersistentVaultRepository` verifies (1) the 32-byte SHA-256 file digest, (2) backup & vault format versions, (3) VEK unwrapping via Master Password or Recovery Key, and (4) AES-256-GCM authentication and decoding of **every** record in the backup. Only after 100% verification does Room execute `replaceVaultAtomically(...)`.
