# Vault Cryptographic Format Specification (`v1`)

**Document:** `docs/vault-format.md`  
**Applies to:** Phase 2 — Cryptographic Engine & Vault Format  
**Status:** Implemented & verified against RFC 9106 and NIST SP 800-38D vectors

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

**Core security properties:**
- The master password is **never** used directly to encrypt records.
- Changing the master password or recovering via the independent recovery key **re-wraps only the 32-byte VEK** under a fresh salt and fresh nonce — existing encrypted records remain untouched and valid.
- The recovery key is cryptographically independent from the master password and protected by a 16-bit SHA-256 checksum to catch transcription typos immediately.

## 2. Argon2id Parameter Selection Rationale

- **Implementation:** Bouncy Castle `org.bouncycastle:bcprov-jdk18on:1.80` (`Argon2BytesGenerator`, RFC 9106 version `0x13`, Argon2id hybrid mode).
- **Default interactive mobile profile (`OWASP_MOBILE_BALANCED`):**
  - `memoryKiB = 19_456` (19 MiB)
  - `iterations = 2`
  - `parallelism = 1`
  - `saltLengthBytes = 16` (128-bit CSPRNG salt)
  - `hashLengthBytes = 32` (256-bit KEK)
- **Why this profile:** Matches the OWASP Password Storage Cheat Sheet minimum interactive Argon2id recommendation (`m=19 MiB, t=2, p=1`), avoids out-of-memory pressure on constrained Android devices running pure-Java Bouncy Castle, and keeps unlock latency interactive. A higher-memory profile (`OWASP_HIGH_MEMORY`: 46 MiB, `t=2, p=1`) is defined for higher-RAM devices, and `KdfParameters` are persisted inside `VaultHeader` so vaults can upgrade seamlessly during password rotation.
- **On-device measurement:** Settings → Security → *Benchmark Argon2id on this device* measures live derivation latency on the user's hardware.

## 3. Authenticated Encryption & Nonce Strategy

- **Cipher:** Platform JCA `AES/GCM/NoPadding` (256-bit key, 96-bit nonce, 128-bit authentication tag).
- **Nonce strategy:** Every encryption call obtains a fresh 12-byte (96-bit) nonce from `SecureRandomNonceGenerator`, rejecting all-zero nonces and enforcing uniqueness across the active session.
- **Canonical length-prefixed AAD (`AadBuilder`):** All integers are 4-byte big-endian; all strings are UTF-8 preceded by their 4-byte length to prevent boundary-shift collisions:
  - **Record AAD:** `"SV-REC-AAD-v1" || formatVersion || vaultId || recordId || recordType`
  - **Master VEK Wrap AAD:** `"SV-WRAP-MASTER-v1" || formatVersion || vaultId || keyRotationEpoch`
  - **Recovery VEK Wrap AAD:** `"SV-WRAP-RECOVERY-v1" || formatVersion || vaultId || keyRotationEpoch`

## 4. Record Plaintext Framing (`VaultRecordCodec` v1)

Encrypted inside `EncryptedVaultRecord.payload`:
`schemaVersion(Int32=1) || title || username || secret || website || normalizedDomain || notes || isFavorite(Bool) || createdAtLabel || updatedAtLabel || lastUsedEpochMs(Int64) || secondaryIdentifier`

## 5. Versioning & Migration Strategy

- `VaultFormatConstants.CURRENT_FORMAT_VERSION = 1`.
- `VaultFormatMigrator` validates `formatVersion` before unwrapping headers or decrypting records.
- Version `0` and unknown future versions (e.g., `v99`) are explicitly rejected with `UnsupportedFormatVersionException` rather than silently corrupted.

## 6. Memory Hygiene & Error Safety

- Derived KEKs, UTF-8 password byte buffers, raw recovery secrets, and plaintext record buffers are zeroized in `finally` blocks (`ByteArray.fill(0)`).
- `VaultSessionKey.wipe()` zeroes the in-memory VEK on lock and transitions the session key to inactive (`VaultLockedException` on further access).
- Note on JVM/ART limitation: `String` instances created by Compose text fields cannot be deterministically wiped in managed runtimes; transient `CharArray` conversion and immediate `ByteArray` zeroization minimize exposure inside the crypto boundary.
- `VaultCryptoException` subclasses never include passwords, recovery keys, nonces, or plaintext in messages.
