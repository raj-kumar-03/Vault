# Architecture — SecureVault (Phase 1)

## Design Philosophy

SecureVault is an offline-first, zero-cloud Android password manager structured around explicit architectural boundaries so that Phase 2 cryptography, Phase 3 encrypted SQLite persistence, Phase 4 Android Keystore, and Phase 6 local Wi-Fi sync can replace Phase 1 mock adapters without rewriting UI components.

## Module & Package Layout (Phase 1 Android-First)

To keep Gradle memory overhead minimal on constrained hardware, Phase 1 remains in a single `:app` module with clean internal package boundaries:

```text
app/src/main/java/com/example/
├── domain/model/        # Pure Kotlin immutable models & PasswordEngine (zero Android framework deps)
├── data/                # VaultRepository, DeviceRepository, SettingsRepository interfaces + MockVaultRepository
├── ui/theme/            # Midnight Emerald dark/light semantic tokens, Plus Jakarta Sans & JetBrains Mono typography
├── ui/components/       # Stateless reusable M3 components (state-in, callbacks-out)
├── ui/state/            # VaultViewModel & immutable VaultUiState
├── ui/screens/          # All 9 Phase 1 screens + RecoveryInfoSheet
└── ui/navigation/       # SecureVaultApp NavHost, safe clipboard manager, FLAG_SECURE bridge
```

## Strict Dependency Direction

`UI Screens & Components` → `VaultViewModel (StateFlow)` → `Repository Interfaces` → `MockVaultRepository (Phase 1)`

- UI components never touch storage, cryptographic primitives, platform keystores, or network sockets.
- Domain utilities (`PasswordEngine`) are pure Kotlin and unit-tested directly on the JVM.
- Clipboard copy operations mark secrets with `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+), schedule best-effort clearing, and never display copied secrets in snackbars or logs.

## Adaptive Layout Strategy

- `AppScaffold` uses `BoxWithConstraints` rather than hardcoded screen metrics:
  - `< 640.dp`: Bottom `NavigationBar` with safe `WindowInsets.navigationBars` padding.
  - `>= 640.dp`: Left `NavigationRail` with persistent emblem and quick-lock action.
  - `>= 840.dp`: Two-pane List-Detail layout on `VaultHomeScreen` so users inspect credentials without leaving the vault list.
