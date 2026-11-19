# Password Manager — Product Design Specification

**Document:** `design.md`  
**Applies to:** Phase 1 — Product definition, architecture and UI/UX  
**Design reference:** Kannaq's Midnight Emerald visual language  
**Status:** Initial design baseline; validate through implementation and usability testing

---

## 1. Design intent

Build a calm, trustworthy, local-first password manager. The interface should feel secure without looking intimidating. It should make the common tasks—unlocking, finding a login, copying a username, generating a password, and adding a credential—fast and predictable.

Borrow from Kannaq:

- Midnight Emerald as the primary visual identity.
- Clear information hierarchy and restrained use of accent color.
- Consistent spacing, typography, radii and reusable components.
- State-driven UI: screens render from explicit state and emit user actions through callbacks.
- Distinct navigation destinations with predictable back behavior.
- Dense information presented in readable groups rather than cluttered dashboards.

Do **not** copy Kannaq's finance concepts, ledger navigation, transaction cards, financial charts, or Scan to Log feature. This is a security product with its own information architecture.

## 2. Product principles

1. **Local-first:** the vault is usable without an account, internet connection or cloud service.
2. **Security is visible but understandable:** explain lock state, device trust, backup status and autofill permissions in plain language.
3. **Fast retrieval:** search is prominent; categories are secondary.
4. **Safe by default:** never reveal passwords in lists; require an intentional action to reveal or copy.
5. **No deceptive security claims:** distinguish implemented, tested and planned protections.
6. **Accessible:** support large text, screen readers, keyboard navigation, contrast and reduced motion.
7. **Predictable:** destructive actions require clear confirmation and explain consequences.

## 3. Visual direction

### 3.1 Theme

Use a dark-first theme inspired by Kannaq's Midnight Emerald. Support light theme as a later refinement, but define semantic color tokens from the beginning so the theme can be added without rewriting components.

Suggested dark palette (initial values; validate contrast):

| Token | Suggested value | Use |
|---|---:|---|
| `background` | `#0B1210` | App background |
| `surface` | `#111C18` | Main cards and sheets |
| `surfaceElevated` | `#17251F` | Dialogs, menus, elevated surfaces |
| `primary` | `#35C98A` | Primary actions and selected states |
| `primaryMuted` | `#1D6046` | Subtle selected backgrounds |
| `textPrimary` | `#EAF4EE` | Main text |
| `textSecondary` | `#A5B7AD` | Supporting text |
| `outline` | `#2A3B33` | Dividers and borders |
| `danger` | `#F07878` | Destructive actions and errors |
| `warning` | `#E7B85C` | Warnings and attention states |

These are design starting points, not certified contrast values. Verify text and control contrast against WCAG 2.2 AA targets before release.

### 3.2 Shape, spacing and typography

- Use a consistent 4 dp spacing base: 4, 8, 12, 16, 20, 24, 32.
- Main screen horizontal padding: 20–24 dp on phones; responsive constraints on wider screens.
- Card radius: 16 dp; input radius: 12 dp; pill controls only where their meaning is clear.
- Use a readable sans-serif typeface. Establish semantic text styles: display, headline, title, body, label and caption.
- Avoid tiny metadata. Never communicate security status through color alone.
- Motion should be brief and purposeful; no decorative animation on security-critical flows.

## 4. Information architecture

### Primary navigation

1. **Vault** — all saved items, search, categories and favorites.
2. **Generator** — password/passphrase generation and strength explanation.
3. **Devices** — paired devices, pairing flow and sync status.
4. **Settings** — security, autofill, backup/recovery, appearance and about.

On compact phones, use a bottom navigation bar. On tablets and desktop, use a navigation rail or sidebar. Keep navigation labels visible where space allows.

### Vault item types

- Login
- Secure note
- Identity
- Payment card (optional; do not prioritize until threat model and UX are reviewed)
- Wi-Fi credential (optional future type)

Do not add document/file attachments in the first release unless there is a clear requirement and a safe encrypted storage design.

## 5. Required Phase 1 screens

Phase 1 uses mock repositories and fake data only. Do not store real credentials or implement cryptography in this phase.

### 5.1 Welcome / onboarding

- Explain local-first operation and the absence of a cloud account.
- Explain that losing both the master password and recovery material may make the vault unrecoverable.
- Provide clear actions: Create vault, Restore backup, Learn about recovery.
- Do not imply that a recovery method exists until it is implemented and tested.

### 5.2 Create vault

- Master password and confirmation.
- Password strength guidance and passphrase option.
- Recovery setup explanation.
- Avoid arbitrary password-composition rules; guide users toward long, unique passphrases.
- Make the consequences of forgotten credentials explicit.

### 5.3 Unlock

- Master password entry.
- Show/hide control.
- Biometric unlock affordance only when supported and configured.
- Generic failure message that does not leak whether a particular credential or key exists.
- Lockout/rate-limiting behavior must be designed with the security architecture, not improvised in UI.

### 5.4 Vault home

- Search field at the top.
- Quick actions: Add item, Generate password.
- Favorites and recently used items (recently used metadata must not leak secrets).
- Category filters.
- Empty state with one clear next action.
- No password values visible in cards or list rows.

### 5.5 Item list and detail

List row:
- Item title, username or masked identifier, domain/app label and category icon.
- Never display the password.
- Avoid exposing sensitive metadata on the lock screen or in notifications.

Detail:
- Title and origin/domain.
- Username with explicit copy action.
- Masked password with reveal and copy actions.
- Website field, notes and timestamps where useful.
- Edit and delete actions.
- Confirm before deletion; explain whether deletion is local-only or will propagate to paired devices once sync exists.

### 5.6 Add / edit item

- Title, username, password, website, notes.
- Password generator integration.
- Domain field with normalization preview.
- Save and cancel actions.
- Inline validation; preserve user input when validation fails.
- Do not silently trim or rewrite passwords.

### 5.7 Password generator

- Random password and passphrase modes.
- Length control.
- Character-set toggles with clear explanation.
- Copy action and strength estimate.
- Never claim entropy or strength is exact unless calculated from the actual generation process and character set.

### 5.8 Devices

- Paired device list with platform, friendly name and last sync time.
- Pair new device action.
- Revoke device action with confirmation.
- Phase 1 shows mocked device states only. No QR pairing or network sync yet.

### 5.9 Settings

Sections:
- Security: auto-lock interval, biometric unlock, clipboard clearing.
- Autofill: status, setup instructions, allowed behavior.
- Backup & recovery: status and educational explanation.
- Appearance: dark/light/system.
- About: version, privacy statement, open-source licenses.

Do not display controls that appear functional when they are not. Use a clear “Coming in a later phase” state or omit them.

## 6. Component system

Build reusable Compose components with explicit state and callbacks:

- `AppScaffold`
- `PrimaryButton`, `SecondaryButton`, `DestructiveButton`
- `SearchField`
- `VaultItemRow`
- `CategoryChip`
- `SecretField` (masked by default)
- `CopyAction`
- `EmptyState`
- `InlineError`
- `ConfirmationDialog`
- `SecurityStatusRow`
- `DeviceCard`
- `SettingsSection`
- `PasswordStrengthIndicator`

Components must not own database, cryptographic, networking or platform-key logic. UI receives state and emits intent callbacks. Keep business logic in ViewModels/use cases and platform integrations behind interfaces.

## 7. Interaction and privacy rules

- Passwords are masked by default.
- Reveal is temporary and visually obvious.
- Copy is an explicit action; show a short confirmation without displaying the secret.
- Clipboard clearing is best-effort and must not be represented as guaranteed on every platform.
- Hide sensitive content in app-switcher previews where the platform permits.
- Avoid secrets in analytics, logs, crash reports, screenshots, test names and debug output.
- Use generic error messages where detailed errors could reveal sensitive state.
- Destructive actions explain scope and sync implications.

## 8. Accessibility and responsive behavior

- Minimum 48 dp touch targets on Android.
- Content descriptions for icon-only controls.
- Logical TalkBack order and clear labels.
- Keyboard focus and shortcuts on desktop.
- Support system font scaling without clipping.
- Use responsive layouts, not fixed phone-only widths.
- Verify contrast for text, borders, focus indicators and disabled states.
- Never rely only on color or animation to communicate state.

## 9. Phase 1 acceptance criteria

- All required screens exist and are navigable.
- UI uses mock repositories and deterministic sample data.
- No real secret is persisted.
- Every visible control either works in the prototype or is clearly disabled/labeled as future functionality.
- Loading, empty, error, success and locked states are designed.
- Phone and wide-screen layouts are reviewed.
- Components use explicit state/callback contracts.
- UI tests cover primary navigation and critical form validation.
- Screenshots are captured for the design review.
- `design.md` is updated when implementation decisions change.

## 10. Explicit non-goals for Phase 1

- Real encryption or key derivation.
- Real database persistence.
- Android Keystore integration.
- Browser extension.
- Autofill.
- QR pairing.
- Wi-Fi synchronization.
- Production backup/recovery.
- Telemetry or cloud services.

These belong to later phases and must not be simulated as secure functionality.


---

# Offline Password Manager — Android Studio Implementation Instructions

**Development plan:** 7 gated phases  
**Primary development environment:** Android Studio  
**Target platforms:** Android first; Windows/Linux desktop and Chrome/Chromium extension in later milestones  
**Operating model:** Offline-first, no cloud backend, no recurring server dependency

---

## 1. Non-negotiable project rules

1. Build an offline password manager. Do not introduce accounts, cloud sync, telemetry, remote analytics, hosted APIs or mandatory internet dependencies.
2. Treat credentials as high-risk data. Never use real passwords during early development.
3. Do not invent security guarantees. Document what is implemented, what is tested and what remains a limitation.
4. Do not implement cryptography yourself. Use established, maintained libraries and platform security APIs. Verify library provenance, supported platforms and version compatibility.
5. Do not let the UI access the database, cryptographic primitives, sockets or platform keystore directly.
6. Keep the project modular but small. Avoid creating a Gradle module for every feature.
7. Keep the browser extension as a separate TypeScript/Manifest V3 project. Do not force browser code into Kotlin Multiplatform.
8. Do not start all targets on every change. Android is the default active target until its phase gates pass.
9. Never commit secrets, test vaults containing real data, signing keys, recovery keys or local machine credentials.
10. Require explicit human approval before moving between phases.

## 2. Kannaq-informed engineering conventions

Apply these workflow conventions from the Kannaq project:

- **Inspect → reproduce → root cause → smallest safe change → focused verification → regression check → review → report.**
- Keep UI declarative and state-driven. UI receives immutable state and emits actions through callbacks.
- Keep ViewModels/use cases responsible for orchestration; keep persistence, cryptography and platform APIs behind interfaces.
- Use dependency injection at architectural boundaries, not as a reason to over-engineer every class.
- Prefer small, reviewable changes over broad rewrites.
- Update project knowledge and architecture notes whenever a decision, module boundary, data format, API contract or important behavior changes.
- Keep tests deterministic. Avoid stale fixtures, duplicate test tags, flaky timing assumptions and assertions against off-screen UI.
- Do not run the entire test/build matrix after every edit. Select the smallest test and compile task that gives meaningful evidence, then run broader regression checks at phase gates.
- Keep documentation aligned with the actual code. Do not leave obsolete architecture notes after refactors.

These are workflow principles, not a requirement to copy Kannaq's finance-domain architecture or dependencies.

## 3. Recommended repository shape

Start with the fewest modules that preserve clean boundaries. A practical starting layout:

```text
password-manager/
├── androidApp/                 # Android entry point, Compose UI, AutofillService
├── shared/                     # Shared domain and application logic
│   ├── src/commonMain/
│   ├── src/commonTest/
│   ├── src/androidMain/
│   └── src/desktopMain/         # Add only when desktop work begins
├── desktopApp/                  # Add in the desktop milestone
├── browser-extension/           # Separate TypeScript + Manifest V3 project
├── docs/
│   ├── design.md
│   ├── architecture.md
│   ├── threat-model.md
│   ├── vault-format.md
│   ├── decisions/
│   └── phase-status.md
└── README.md
```

Do not create `desktopApp` or browser-extension build wiring during the first Android UI milestone unless there is a concrete reason. Keep the intended future boundaries documented.

## 4. Build strategy for an 8 GB RAM laptop

### 4.1 Keep the first build small

- Start with one Android application target and the minimum shared code required.
- Do not configure Android, Windows, Linux and browser targets all at once.
- Avoid large multiplatform dependency graphs before the Android UI is stable.
- Add desktop target configuration only when its phase begins.
- Keep the number of Gradle plugins and annotation processors low.
- Prefer KSP over KAPT when a selected library supports it, but do not add a processor unless required.
- Do not change Gradle, Android Gradle Plugin, Kotlin, Compose, KSP or JDK versions independently without checking their compatibility matrix.

### 4.2 Gradle settings

Use this as a conservative starting point, then measure actual memory use:

```properties
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.daemon=true
org.gradle.caching=true
org.gradle.parallel=false
org.gradle.workers.max=2
kotlin.incremental=true
android.useAndroidX=true
```

Do not enable configuration cache until the selected plugins are verified to support it. On an 8 GB machine, parallel builds can make the system swap and become slower. If memory remains available, test one setting at a time and compare build times.

### 4.3 Build habits

- Do not use `clean` as a routine troubleshooting step.
- Do not invalidate IDE caches unless there is evidence of cache corruption.
- Build only the changed target/module.
- Run unit tests for the changed logic before assembling the full app.
- Keep Android Studio, emulator and browser tabs under control during builds.
- Prefer a physical Android device for routine UI checks if the emulator causes memory pressure.
- Record build time and peak memory for baseline, incremental and clean builds.
- Never claim a build is fast based on one run; compare repeated runs.

Example commands (adjust task paths to the actual project):

```bash
./gradlew :androidApp:compileDebugKotlin
./gradlew :shared:testDebugUnitTest
./gradlew :androidApp:assembleDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## 5. Seven phases and mandatory gates

### Phase 1 — Product definition, architecture and UI/UX

Use `design.md` as the visual and interaction baseline.

Deliver:
- Product requirements and non-goals.
- Threat model draft and trust boundaries.
- Navigation map and screen states.
- Design tokens and reusable Compose components.
- Complete mock-data UI journeys.
- Android phone layout and responsive wide layout.

Do not implement real encryption, persistent credentials, pairing or autofill in this phase.

**Gate:** UI journeys are testable, no visible fake security behavior, design review accepted.

### Phase 2 — Cryptographic engine and vault format

Design and implement the cryptographic boundary before storing real credentials.

Requirements:
- Use Argon2id for master-password key derivation, with parameters selected through current security guidance and measured on supported devices.
- Generate a random vault encryption key (VEK); do not use the master password directly as the data-encryption key.
- Wrap the VEK with a key-encryption key derived from the master password.
- Use authenticated encryption such as AES-256-GCM with a unique nonce for every encryption under a given key.
- Bind record identity, vault identity, record type and format version as authenticated additional data where appropriate.
- Version the vault format and define migration behavior.
- Use a separately generated recovery secret/key to wrap the VEK; do not make the recovery key identical to the master password.
- Define password-change and recovery flows without re-encrypting every record unnecessarily.
- Use cryptographic libraries with maintained implementations and known test vectors.

Do not invent fixed Argon2 parameters without evaluating current guidance, platform latency and memory constraints. Document chosen parameters and why.

Tests:
- Known-answer/test vectors where available.
- Wrong password fails safely.
- Modified ciphertext, nonce, tag, AAD or record metadata is rejected.
- Nonce generation and uniqueness strategy is tested.
- Format version and migration tests.
- No plaintext secrets in logs or exception messages.

**Gate:** Crypto design reviewed; test vectors and tamper tests pass; vault format documented.

### Phase 3 — Local database and vault management

Deliver:
- Local SQLite persistence.
- Encrypted-at-rest record payloads; metadata exposure minimized and documented.
- Repository/use-case boundaries.
- Create, read, update, delete, search and category operations.
- Password/passphrase generation.
- Encrypted export and restore workflow.
- Schema migrations and corruption handling.

Important:
- Decide explicitly which metadata is encrypted and which is visible to SQLite queries.
- Use transactions for multi-record operations.
- Do not silently overwrite data during migration or restore.
- Keep backup format versioned and independently testable.

**Gate:** Offline CRUD, search, migration and backup/restore tests pass.

### Phase 4 — Platform security and authentication

Android:
- Android Keystore for device-bound key protection.
- BiometricPrompt only as an authorization mechanism for a cryptographic operation supported by the chosen key configuration.
- Handle biometric enrollment changes, key invalidation and device lock changes.
- Define app lock, backgrounding and process-death behavior.

Windows and Linux:
- Add desktop support only after the shared vault format and Android core are stable.
- Use Windows platform-provided secure key storage.
- Use Linux Secret Service/libsecret where available.
- Fail safely if secure storage is unavailable; do not silently fall back to plaintext files.

All platforms:
- Auto-lock and explicit lock.
- Best-effort clipboard clearing with honest limitations.
- Hide sensitive app-switcher previews where supported.
- Exclude secrets from logs, crash reports and diagnostic bundles.

**Gate:** Platform key protection and lock lifecycle tests pass on each supported OS.

### Phase 5 — Browser integration and autofill

Android:
- Implement Android AutofillService.
- Parse only relevant form structures.
- Match websites using strict origin/hostname rules.
- Require user selection/confirmation where ambiguity exists.
- Verify app identity/signing certificate for native app autofill where applicable.

Desktop:
- Build a separate Manifest V3 TypeScript extension.
- Use Native Messaging to communicate with the desktop application.
- Do not expose a general unauthenticated localhost HTTP endpoint.
- Keep the extension's decrypted-secret lifetime minimal.
- Do not persist the full decrypted vault in extension storage.
- Block insecure HTTP autofill by default; require an explicit exception.
- Never autofill into an unrelated origin or hidden field without a deliberate, reviewed policy.

**Gate:** Test on controlled sites and malicious/mismatched-origin cases; verify no cross-origin leakage.

### Phase 6 — Device pairing, local synchronization and recovery

- Pair devices using ephemeral X25519 key agreement.
- Derive session keys with HKDF-SHA256.
- Display a short authentication string (SAS) on both devices and require user verification.
- Do not put the master password, VEK or recovery key directly in a QR code.
- Use authenticated encrypted transport over the local network.
- Include replay protection, device identity, session expiry and explicit revocation.
- Support manual IP/port connection if local discovery is blocked by router isolation or firewalls.
- Define record versioning, tombstones and conflict preservation.
- Never silently discard conflicting edits.
- Keep backup and recovery independent of routine device sync.

**Gate:** Android/Windows/Linux interoperability tests pass; replay, tampering, revoked-device and conflict tests pass.

### Phase 7 — Security testing, performance and release

Test:
- Crypto vectors, tampering and malformed input.
- Database migrations, interrupted writes and backup restoration.
- Locking, process death, device-key invalidation and recovery.
- Autofill origin validation and malicious page behavior.
- Pairing authentication, replay resistance, revocation and conflict handling.
- Cross-platform vault compatibility.
- Logging, clipboard, screenshots, app-switcher previews and memory lifetime.
- Accessibility and responsive layouts.
- Release signing, dependency inventory, license review and reproducible build notes.

A successful test suite is not a substitute for independent security review. Do not store critical real-world credentials until the product has received an appropriate security review.

**Gate:** All release criteria documented; no unresolved critical/high findings; supported-platform matrix passes.

## 6. Agent execution protocol

At the start of every task, the coding agent must:

1. Read `README.md`, `docs/architecture.md`, `docs/threat-model.md`, `docs/phase-status.md` and the relevant phase instructions if they exist.
2. Inspect the current branch, working tree and module structure.
3. Identify the exact files and tests likely to be affected.
4. State the smallest implementation plan.
5. Avoid unrelated refactors.

After implementation:

1. Run the smallest relevant compile/test task.
2. Run targeted regression tests.
3. Inspect the diff for secrets, generated files, dependency churn and unrelated edits.
4. Update architecture/decision/phase documents.
5. Report changed files, commands run, results, known limitations and the next gate.
6. Stop at the phase boundary and wait for human approval.

If a test fails, reproduce and diagnose it. Do not delete or weaken the test to make the build green.

## 7. Project knowledge maintenance

Maintain a concise `docs/project-map.md` containing:
- Module responsibilities.
- Important entry points.
- Data flow from UI to repository to storage.
- Crypto/key boundaries.
- Platform-specific implementations.
- Test locations and exact focused build commands.

Update it when code changes alter the map. Prefer concise, factual summaries and links to source files over copying entire source files into documentation. The source code remains authoritative.

## 8. Release and cost constraints

- No hosted backend, cloud database, paid API, telemetry service or recurring infrastructure.
- Local Wi-Fi sync only.
- Distribution may still have one-time or platform-specific costs; zero recurring server cost does not mean every app-store publishing route is free.
- Document any optional external dependency and whether it works fully offline.
- Keep dependencies minimal and pinned to compatible, maintained versions.

## 9. First task to give the coding agent

> Inspect the existing Android Studio project and machine-compatible toolchain before changing anything. Confirm the installed JDK, Gradle wrapper, Android Gradle Plugin, Kotlin and Compose versions and verify their compatibility. Do not upgrade versions by default. Create the minimal Android-first project structure, `docs/phase-status.md`, `docs/architecture.md`, `docs/threat-model.md`, `docs/project-map.md`, and the Phase 1 `design.md`. Implement only the Phase 1 design system, navigation shell and mock-data screens. Do not add cryptography, database persistence, network code, autofill or desktop targets yet. Keep Gradle modules and dependencies minimal for an 8 GB RAM machine. After each small change, run the narrowest relevant compile or test task. Do not run `clean` routinely. At the end, report exact build commands, build times, memory observations, changed files, tests and remaining work. Stop for approval before Phase 2.

---

## Final operating rule

**Do not optimize for the number of features generated per prompt. Optimize for verified progress per build.** A small, compiling, tested change is more valuable than a large implementation that takes ten minutes to compile and is difficult to debug.
