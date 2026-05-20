# MovieFlux — Revised Implementation Plan

> Source documents: `CHALLENGE_VALIDATION.md` (2026-05-17, primary), `IMPLEMENTATION_PLAN.md` (preserve where correct), code on branch `feat/improve_tests_and_review`.
>
> **State as of 2026-05-18:** the branch has absorbed every CRITICAL, HIGH, and most MEDIUM items from the validation report. What remains is a single MEDIUM build-hygiene fix plus a handful of documentation/test polish items. The plan below covers only that residual work; everything else is summarised under **Preserved Phases**.
>
> **Out of scope by user decision:** Firebase Authentication (Phase 2B), Google Sign-In (Phase 2C), Sentry/Crashlytics (Phase 9). Mocked `admin/1234` is the only authentication path. Analytics stay Timber-only.

---

## Current State Summary

| Challenge / Plan requirement | Status | Phase that addresses it |
| --- | --- | --- |
| Mocked login `admin/1234` | Done | Preserved (Phase 2) |
| EncryptedSharedPreferences for `biometricEnabled` / `biometricPrompted` | Done | Preserved |
| Post-login biometric opt-in dialog (fires once, `testTag = "biometric_opt_in_dialog"`) | Done | Preserved |
| Biometric gate on subsequent app open | Done | Preserved |
| `BiometricPrompt.AuthenticationCallback` correctness (transient vs terminal) | Done | Preserved |
| Biometric fallback UX on terminal error (`BiometricGateState.Failed`) | Done | Preserved |
| Home: list/grid, infinite scroll, search, Loading/Error/Empty | Done | Preserved (Phase 5) |
| Home: query-aware empty copy | Done | Preserved |
| Home: genre chips on `MovieCard` / `MovieListItem` | Done | Preserved |
| Genre map cache (`Map<Int,String>` behind `Mutex`) in repo | Done | Preserved |
| Details: poster, title, rating, overview, genre names, favorite, share | Done | Preserved (Phase 6) |
| Details: `genreNames` from `MovieDetailMapper`, no redundant `/genre/movie/list` | Done | Preserved |
| Details: optimistic toggle + rollback + snackbar event | Done | Preserved |
| Favorites tab (Room, cross-screen sync, offline) | Done | Preserved (Phase 7) |
| Favorites entity carries genres (`MovieEntity.genreIds`) so offline Details shows chips | Done (`MIGRATION_4_5` + `EntityMapper` round-trip) | Preserved |
| Room migration chain complete (`MIGRATION_1_2 … 4_5` registered) | Done (`DatabaseModule.kt:62`) | Preserved |
| `LoginScreen` strings unified via `stringResource` | Done | Preserved |
| Example test stubs removed | Done (`ExampleUnitTest` / `ExampleInstrumentedTest` deleted) | Preserved |
| `LoginScreenTest` asserts dialog by `testTag`, not by copy | Done | Preserved |
| README mentions analytics + JankStats subsystems | Done (`README.md:107`) | Preserved |
| `IMPLEMENTATION_PLAN.md` annotates Phases 2B/2C/9 as `REMOVED FROM SCOPE` | Done | Preserved |
| Unit tests — ViewModels (Login, Home, Details, Favorites, Profile) | Done | Preserved |
| Unit tests — Repository (`MovieRepositoryImplTest`, 13 cases) | Done | Preserved |
| Compose UI test for Login (`LoginScreenTest`, Hilt) | Done | Preserved |
| **`kotlin-android` plugin applied in `app/build.gradle.kts`** | **Missing** — relies on `kotlin-compose` implicit activation | **Fix-1 (MEDIUM)** |

---

## Validation-Driven Fix Phases

### Fix-1 — Apply the `kotlin-android` plugin explicitly (MEDIUM, build hygiene)

**Goal:** Make Kotlin compilation an explicit, named plugin dependency instead of a side effect of `kotlin-compose`. Today the build works because `org.jetbrains.kotlin.plugin.compose` transitively triggers Kotlin source compilation; a future AGP/Kotlin upgrade may break that implicit contract silently.

**Touches:**
- `app/build.gradle.kts`

**Steps:**
1. In the `plugins { ... }` block (currently lines 10–15), insert `alias(libs.plugins.kotlin.android)` as the **second** entry — directly after `alias(libs.plugins.android.application)` and before `alias(libs.plugins.kotlin.compose)`. The catalog already declares the alias at `libs.versions.toml` line 93, so no version change is required.
2. Run `gradlew clean assembleDebug` once to confirm the build remains green. The expected behaviour is zero semantic change today; the gain is a stable, declared contract.
3. Do not touch the `kotlin-compose`, `ksp`, or `hilt` aliases — they are already correct.

**Acceptance criterion:** `gradlew clean assembleDebug` succeeds; the plugins block lists `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt` in that order.

---

## Preserved Phases (no changes required)

All phases below were verified against the working tree on 2026-05-18 and require no further architectural work. The executor's job on these is verification, not modification.

| Phase | What it covers |
| --- | --- |
| **Phase 1 — Infrastructure** | Hilt + KSP, Room (now at schema version 5 with `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, `MIGRATION_4_5` all registered), Retrofit + OkHttp, Compose BOM 2025.01.00, buildConfig wiring of `TMDB_API_KEY`. No changes. |
| **Phase 2 — Auth & Security (mocked)** | `EncryptedSharedPreferences` for `biometricEnabled` / `biometricPrompted`; `BiometricHelper` with correct split between `onAuthenticationFailed` (transient, no-op) and `onAuthenticationError` (terminal, calls `onError`); `BiometricGate` + `BiometricGateState` sealed class with `Checking` / `Passed` / `Failed`; post-login `BiometricOptInDialog` driven by `LoginViewModel.confirmBiometricOptIn(...)` and gated on the `biometricPrompted` flag so it fires exactly once. No changes. |
| **Phase 3 — Nested Navigation** | `Screen` sealed class with `AuthGraph` / `MainGraph`, `AppNavHost` with `popUpTo(0) { inclusive = true }` on auth↔main transitions, `MainScaffold` hiding the bottom bar on `details/` routes, `BottomNavBar` with `saveState` / `restoreState` / `launchSingleTop`. `Screen.Register` correctly absent (Phase 2B dropped). No changes. |
| **Phase 4 — Profile / Logout** | `ProfileScreen`, `ProfileViewModel`, `LogoutConfirmDialog`, biometric toggle (`SettingsSwitchRow`). No changes. |
| **Phase 5 — Home** | Popular movies grid/list, `ViewModeToggle`, 300 ms debounced search via `flatMapLatest`, infinite scroll via `snapshotFlow` watching the last-3 items, `EmptyView` with query-aware copy (`R.string.empty_no_results_for`), genre chips on `MovieCard` and `MovieListItem`, `HomeViewModelTest` (9 cases). No changes. |
| **Phase 6 — Details** | Large poster (`w780`), title/rating/overview, genre chips from `MovieModel.genreNames`, optimistic favorite toggle with rollback and snackbar event, share via `Intent.ACTION_SEND` with title + TMDB URL, loud `movieId` parsing via `checkNotNull`, `DetailsViewModelTest` (8 cases). No changes. |
| **Phase 7 — Favorites** | Dedicated tab, cross-screen state sync via the `getFavorites()` Room Flow, fully offline. `MovieEntity` now persists `genreIds` (`String`, comma-separated); `EntityMapper` round-trips them and `MovieRepositoryImpl` maps them through the in-memory genre cache so chips render offline. `FavoritesViewModelTest` (4 cases). No changes. |
| **Phase 8 — Cache-first repository** | `MovieRepositoryImpl` reads `movie_cache` page rows before hitting `/movie/popular`, writes back on success, swallows network failure only when the cache is non-empty; the same pattern protects `getMovieDetail`. `MovieRepositoryImplTest` covers cache-hit, network fallback, exception when both empty, `toggleFavorite` insert/delete, genre cache reuse — 13 cases total. No changes. |
| **Analytics & performance subsystems** | `CompositeAnalyticsTracker`, `SampledAnalyticsTracker`, `FunnelTracker`, `TimberAnalyticsTracker`, `JankReporter`, `JankStateEffect`, `LogRecompositions`. Tested in `AnalyticsTrackerTest` (4 cases). README documents both subsystems. No changes. |
| **Documentation hygiene** | `README.md` covers API key setup, biometric test instructions, architecture decisions, AI usage, analytics + JankStats. `IMPLEMENTATION_PLAN.md` shows `REMOVED FROM SCOPE` callouts on Phases 2B, 2C, and 9. No changes. |

---

## Acceptance Checklist

### Build
- [ ] `gradlew clean assembleDebug` passes with only `TMDB_API_KEY` set in `local.properties` (no `google-services.json`, no Sentry DSN).
- [ ] `gradlew testDebugUnitTest` passes — no `ExampleUnitTest` entry in the report.
- [ ] `gradlew lint` reports no errors (warnings acceptable).
- [ ] After **Fix-1**: `app/build.gradle.kts` plugins block explicitly applies `alias(libs.plugins.kotlin.android)` as the second entry.

### Database / migrations (preserved — verification only)
- [ ] `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, and `MIGRATION_4_5` are all registered on the Room builder.
- [ ] A v1-schema device launching the new build does not crash and retains its favorites rows.
- [ ] `MovieEntity.genreIds` is `String` (comma-separated); `EntityMapper` round-trips a non-empty genre list correctly.

### Authentication (preserved — verification only)
- [ ] `admin` / `1234` reaches `LoginUiState.Success`.
- [ ] First successful login renders `BiometricOptInDialog` (locatable via `testTag = "biometric_opt_in_dialog"`) before navigating to `MainGraph`.
- [ ] "Enable" sets `biometricEnabled = true` and `biometricPrompted = true`; "Skip" sets only `biometricPrompted = true`.
- [ ] Second app open with `biometricEnabled = true` shows the system biometric prompt before `MainGraph`.
- [ ] `BiometricGateState.Failed` renders the "Use password" fallback and routes to `Screen.AuthGraph`.
- [ ] Both flags are stored in `EncryptedSharedPreferences`.

### Home (preserved — verification only)
- [ ] Popular movies load on launch with cache-first emission.
- [ ] Scrolling near the end triggers the next page load.
- [ ] Search debounces ~300 ms and calls `/search/movie`; clearing it returns to popular.
- [ ] Empty search results render the PT-BR "Nenhum resultado para 'xxx'" copy.
- [ ] Each card shows 1–2 genre names.

### Details (preserved — verification only)
- [ ] Poster, title, rating, overview, and genre names all render online.
- [ ] **Offline** favorited movie: genre chips render from persisted `genreIds` via the in-memory genre map.
- [ ] Favorite toggle persists to Room and reflects on Home and Favorites without a manual refresh.
- [ ] Share opens a system share sheet with title + TMDB URL.

### Favorites (preserved — verification only)
- [ ] Favorited movies appear in the Favorites tab with no network call.
- [ ] Removing a favorite in Details removes it from the Favorites tab.
- [ ] Favorites tab renders correctly with airplane mode on.

### Tests (preserved — verification only)
- [ ] `MovieRepositoryImplTest`: cache-hit, network fallback, exception when both empty, `toggleFavorite` insert and delete, genre cache reuse.
- [ ] `DetailsViewModelTest`: success, error, `toggleFavorite` optimistic flip + rollback, share intent extras.
- [ ] `LoginViewModelTest`, `HomeViewModelTest`, `FavoritesViewModelTest`, `ProfileViewModelTest` all pass.
- [ ] `LoginScreenTest` asserts the biometric dialog by `testTag`, not by hard-coded copy.

### Documentation (preserved — verification only)
- [ ] `README.md` covers API key, biometric test, architecture, AI usage, analytics + JankStats subsystems.
- [ ] `IMPLEMENTATION_PLAN.md` shows a `REMOVED FROM SCOPE` callout at the top of Phases 2B, 2C, and 9.
