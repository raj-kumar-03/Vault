# Project Map — SecureVault

## Entry Points

- `app/src/main/java/com/example/MainActivity.kt` — `ComponentActivity` enabling edge-to-edge and hosting `SecureVaultApp()`.
- `app/src/main/java/com/example/ui/navigation/SecureVaultApp.kt` — Navigation Compose `NavHost`, clipboard bridge, and `FLAG_SECURE` effect.

## Data Flow

1. `VaultViewModel` (`app/src/main/java/com/example/ui/state/VaultViewModel.kt`) combines `MockVaultRepository` flows with UI search/filter/generator flows into a single `StateFlow<VaultUiState>`.
2. Screens in `app/src/main/java/com/example/ui/screens/` render `VaultUiState` and invoke explicit callbacks.
3. Reusable stateless widgets live in `app/src/main/java/com/example/ui/components/`.

## Domain & Mock Data Boundaries

- `app/src/main/java/com/example/domain/model/VaultModels.kt` — Immutable models (`VaultItem`, `PairedDevice`, `SecuritySettings`, `GeneratorConfig`, `PasswordStrength`).
- `app/src/main/java/com/example/domain/model/PasswordEngine.kt` — `SecureRandom` password/passphrase generator, exact Shannon entropy calculator, heuristic master-password evaluator, and domain normalizer.
- `app/src/main/java/com/example/data/VaultRepository.kt` — Storage/device/settings interfaces.
- `app/src/main/java/com/example/data/MockVaultRepository.kt` — In-memory Phase 1 synthetic fixtures.

## Theme & Assets

- `app/src/main/java/com/example/ui/theme/` — `Color.kt`, `Type.kt`, `Theme.kt`.
- `app/src/main/res/font/` — Bundled `plus_jakarta_sans.ttf` and `jetbrains_mono.ttf`.
- `app/src/main/res/drawable/` — Custom adaptive launcher icon and Midnight Emerald welcome backdrop.

## Tests & Focused Commands

- Unit tests: `app/src/test/java/com/example/PasswordEngineTest.kt`
- Robolectric UI tests: `app/src/test/java/com/example/SecureVaultUiTest.kt`, `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- Run JVM & Robolectric tests: `gradle :app:testDebugUnitTest`
- Compile debug APK: `gradle :app:assembleDebug`
