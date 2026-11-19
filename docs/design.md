# SecureVault — Phase 1 Design Record (`docs/design.md`)

**Visual language:** Midnight Emerald (dark-first with semantic light theme parity)  
**Typography:** `Plus Jakarta Sans` (UI & display) paired with `JetBrains Mono` (generated secrets, masked wells, key fingerprints)  
**Spacing system:** 4 dp base grid (`4, 8, 12, 16, 20, 24, 32 dp`), `16 dp` cards, `12 dp` inputs, `48 dp` minimum touch targets.

## Implemented Decisions vs Initial Baseline

- **Theme parity:** Implemented both Midnight Emerald Dark (`#0B1210` background, `#111C18` surface, `#35C98A` primary) and Mineral Sage Light (`#F3F7F5` background, `#147E54` primary) via `LocalVaultColors` so the Appearance selector in Settings works immediately.
- **Honest onboarding:** Added an **Explore Demo Vault** shortcut on `WelcomeScreen` alongside **Create Vault**, **Restore Backup** (coming in Phase 3 sheet), and **Learn About Recovery** so reviewers can evaluate both onboarding and the populated vault quickly without fake cloud claims.
- **Temporary secret reveal:** `ItemDetailScreen` masks secrets by default, displays an explicit warning banner when revealed, and auto-re-masks after 15 seconds.
- **Generator transparency:** Random password entropy is calculated directly as `length * log2(poolSize)`. Passphrase entropy reflects the 128-word Phase 1 prototype list and notes that the full 7,776-word EFF list arrives in Phase 3.
- **Screen privacy (`FLAG_SECURE`):** Exposed as a live Settings toggle (default off in Phase 1 prototype preview; enforced by default in Phase 4).
