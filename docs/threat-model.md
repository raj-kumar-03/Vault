# Threat Model Draft — SecureVault

**Status:** Phase 1 draft (pre-cryptography).  
**Critical warning:** Phase 1 stores **no real secrets** and implements **no cryptographic protection**. Never enter production credentials into a Phase 1 build.

## Trust Boundaries

1. **User ↔ UI Layer:** Shoulder-surfing, app-switcher snapshots, and accidental disclosure.
   - *Phase 1 mitigations:* Passwords are never rendered in list rows; secrets default to masked; temporary reveal shows an explicit warning banner and auto-masks after 15 seconds; snackbars never echo secrets; `FLAG_SECURE` toggle available in Settings (enforced by default in Phase 4); generic unlock error messages prevent oracle leakage.
2. **UI Layer ↔ Clipboard:** Android clipboard history and third-party keyboards may retain copied text.
   - *Phase 1 mitigations:* `ClipDescription.EXTRA_IS_SENSITIVE` set on Android 13+, best-effort auto-clear timer implemented, and UI explicitly states clearing is best-effort only.
3. **App Process ↔ Local Storage (Phase 2 & 3):**
   - *Planned:* Argon2id KDF → KEK wrapping random VEK; AES-256-GCM per-record encryption with unique nonces and AAD binding `(vault_id, record_id, record_type, format_version)`; independent recovery key wrapping VEK.
4. **App Process ↔ Platform Hardware (Phase 4):**
   - *Planned:* Android Keystore key wrapping with `BiometricPrompt` crypto-object authorization and key-invalidation recovery.
5. **Local Device ↔ Paired Device (Phase 6):**
   - *Planned:* Ephemeral X25519 + HKDF-SHA256 authenticated transport over LAN with user-verified Short Authentication Strings (SAS), replay counters, and explicit revocation. Master password, VEK, and recovery key are never placed in QR codes.

## Non-Goals (All Phases)

- No cloud backend, account servers, remote analytics, or third-party telemetry.
- No defense against a fully compromised/rooted OS actively instrumenting the running process while unlocked.
