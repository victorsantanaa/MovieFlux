# MovieFlux — Challenge & Plan Validation Report
**Branch:** feat/cache_first
**Plan file:** IMPLEMENTATION_PLAN.md
**Challenge:** Android Technical Challenge — MovieFlux
**Reviewed by:** Claude (Validator Agent)
**Date:** 2026-05-18

---

## Executive Summary

The codebase satisfies every functional requirement of the technical challenge. All four screens (Login, Home, Details, Favorites) are fully implemented with the correct UI states. Authentication uses EncryptedSharedPreferences (AES256_GCM/SIV) backed by the Android Keystore. Biometric opt-in after first login and biometric gate on subsequent app opens are both wired end-to-end. Popular-movies pagination, search, genre labels, favorite toggling, and offline-first favorites are all present. The single gap against the challenge spec is that `alias(libs.plugins.kotlin.android)` is absent from `app/build.gradle.kts` — the Kotlin Android plugin is only included transitively via the Kotlin Compose plugin, which may cause lint or incremental compilation issues in some AGP/KSP configurations and is the one item a careful reviewer is likely to flag.

The plan's biggest internal risk is reader confusion caused by two cancelled phases (Firebase Auth 2B and Google Sign-In 2C) that remain in IMPLEMENTATION_PLAN.md without any cancellation notice. The README correctly discloses the cancellation, but a reviewer reading the plan first will expect features that were never shipped. The analytics and performance packages (`analytics/`, `performance/`) are fully in source but were never described in any plan phase — they are well-written, tested, and constitute intentional but undocumented scope expansion.

---

## Part A — Challenge Requirements Coverage

### A1. Authentication & Security

| Requirement | In Plan? | In Code? | Notes |
| --- | --- | --- | --- |
| Login screen with username/password fields | Yes | Yes | `LoginScreen.kt` — OutlinedTextField with testTag "username" and "password" |
| admin/1234 validation | Yes | Yes | `LoginViewModel.kt:37` — hard-coded comparison `username == "admin" && password == "1234"` |
| Post-login biometric opt-in dialog (shown once, immediately after first successful login) | Yes | Yes | Dialog shown in `LoginScreen.kt:81-86` when `state.shouldPromptBiometric == true`; gate flag is `!authPreferences.biometricPrompted && biometricHelper.canAuthenticate() == Available` checked at `LoginViewModel.kt:41-42`; calling `confirmBiometricOptIn()` sets `biometricPrompted = true` so it never reappears |
| Biometric gate on subsequent app opens | Yes | Yes | `MainActivity.kt:37-56` — `BiometricGate` composable wraps the entire `AppNavHost`; triggers when `isLoggedIn && biometricEnabled && canAuthenticate == Available` |
| EncryptedSharedPreferences (security differential) | Yes | Yes | `AuthPreferences.kt:17-23` — `MasterKey.KeyScheme.AES256_GCM` + `PrefKeyEncryptionScheme.AES256_SIV` + `PrefValueEncryptionScheme.AES256_GCM` |

### A2. Home — Popular Movies

| Requirement | In Plan? | In Code? | Notes |
| --- | --- | --- | --- |
| List or grid of popular movies | Yes | Yes | `HomeScreen.kt` — `LazyVerticalGrid` (GRID mode) and `LazyColumn` (LIST mode) with toggle via `ViewModeToggle` |
| Infinite scroll (pagination) | Yes | Yes | `HomeScreen.kt:92-117` — `snapshotFlow` on both `gridState` and `listState` triggers `vm.loadNextPage()` when `lastVisible >= total - 3`; `RemoteDataSource.kt:10-12` passes `@Query("page") page: Int` |
| Search bar calling /search/movie | Yes | Yes | `RemoteDataSource.kt:15-18` — `@GET("search/movie")`; wired through `MovieRepositoryImpl.searchMovies` to `HomeViewModel` with 300 ms debounce |
| Loading state | Yes | Yes | `HomeUiState.Loading` — renders `MovieCardSkeleton` grid (`HomeScreen.kt:152-165`) |
| Error state | Yes | Yes | `HomeUiState.Error` — renders `ErrorView` with retry callback (`HomeScreen.kt:166-170`) |
| Empty state | Yes | Yes | `HomeUiState.Success` with empty movie list — renders `EmptyView` (`HomeScreen.kt:173-186`) |

### A3. Details Screen

| Requirement | In Plan? | In Code? | Notes |
| --- | --- | --- | --- |
| Large poster image | Yes | Yes | `DetailsScreen.kt:150-159` — `AsyncImage` with `fillMaxWidth`, `aspectRatio(2f/3f)`, tappable to open a fullscreen pinch-zoom viewer |
| Title | Yes | Yes | `DetailsScreen.kt:162-167` — `Text(movie.title)` with `headlineSmall` + bold weight |
| Rating | Yes | Yes | `DetailsScreen.kt:170-181` — star icon + `"%.1f".format(movie.rating) + " / 10"` |
| Synopsis (overview) | Yes | Yes | `DetailsScreen.kt:200-206` — `Text(movie.overview)` |
| Genre text labels (not IDs) | Yes | Yes | Detail endpoint `/movie/{id}` returns full `GenreDto` objects; `MovieDetailMapper.kt:16` maps them to `genreNames`; displayed as `AssistChip` at `DetailsScreen.kt:184-197` |
| Favorite toggle button | Yes | Yes | `DetailsScreen.kt:111-131` — FAB with optimistic flip in `DetailsViewModel.toggleFavorite()`; reverts state and fires `DetailsEvent.ShowError` on failure |
| Share button | Yes | Yes | `DetailsScreen.kt:105-108` — `Icons.Default.Share` in TopAppBar; `DetailsViewModel.share()` fires `ACTION_SEND` chooser intent containing title and TMDB URL |

### A4. Favorites (Offline First)

| Requirement | In Plan? | In Code? | Notes |
| --- | --- | --- | --- |
| Room persistence | Yes | Yes | `MovieDao.kt:24-28` — `@Insert(onConflict = REPLACE)` and `@Delete` on `MovieEntity` (table: `favorites`) |
| Cross-screen sync | Yes | Yes | `HomeViewModel.kt:72-93` — `combine(activeMovies, repository.getFavorites(), ...)` merges favorites Flow into home UI state; `FavoritesViewModel.kt:30-38` — same `getFavorites()` flow drives favorites tab |
| Dedicated tab | Yes | Yes | `FavoritesScreen.kt` reachable via `TopLevelTab` entry in `BottomNavBar` |
| Offline access (no network call in getFavorites) | Yes | Yes | `MovieRepositoryImpl.getFavorites():60-67` — calls only `dao.getFavorites()`, zero network dependency |

### A5. Technical Essentials

| Requirement | In Plan? | In Code? | Notes |
| --- | --- | --- | --- |
| Kotlin | Yes | Yes | Kotlin 2.2.10 (`libs.versions.toml:9`) |
| MVVM | Yes | Yes | Every screen has a paired `@HiltViewModel` |
| Flow + Coroutines | Yes | Yes | `StateFlow`, `Flow`, `viewModelScope`, `Channel`, `Mutex` throughout |
| Hilt (DI) | Yes | Yes | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject constructor` |
| Retrofit | Yes | Yes | `NetworkModule.kt` — Retrofit 2.11.0 + OkHttp + `GsonConverterFactory` |
| Room | Yes | Yes | `MovieDatabase.kt` version 5; two entities (`favorites`, `movie_cache`) |
| AndroidX Biometric Library | Yes | Yes | `biometric:1.2.0-alpha05`; `BiometricHelper.kt` |
| Unit tests — ViewModels | Yes | Yes | See test inventory below |
| Unit tests — Repository | Yes | Yes | See test inventory below |

**ViewModel unit test inventory:**

| Test Class | Test Count |
| --- | --- |
| `HomeViewModelTest` | 9 |
| `LoginViewModelTest` | 9 |
| `FavoritesViewModelTest` | 5 |
| `DetailsViewModelTest` | 8 |
| `ProfileViewModelTest` | 7 |

**Repository unit test inventory:**

| Test Class | Test Count |
| --- | --- |
| `MovieRepositoryImplTest` | 13 |

### A6. Differentials

| Requirement | In Plan? | In Code? | Notes |
| --- | --- | --- | --- |
| Jetpack Compose | Yes | Yes | Entire UI layer is Compose + Material 3 |
| Clean Architecture | Yes | Yes | Three distinct layers: `domain/`, `data/`, `ui/+view/` |
| Reusable components | Yes | Yes | `PrimaryButton`, `SecondaryButton` (ButtonSize: SMALL/MEDIUM/LARGE), `MovieCard`, `MovieListItem`, `SearchBar`, `ViewModeToggle`, `EmptyView`, `ErrorView`, `LoadingView`, `MovieCardSkeleton`, `MovieFluxLogo` |
| Teal Green as primary color | Yes | Yes | `Color.kt:13` — `TealGreen = Color(0xFF00897B)`; assigned to `primary` in `LightColors` at `Theme.kt:14` |
| UI tests (Compose UI Test) | Partial in plan | Yes (1 file) | `LoginScreenTest.kt` — uses `@HiltAndroidTest`, `createAndroidComposeRule<MainActivity>()`, `HiltAndroidRule` |
| EncryptedSharedPreferences | Yes | Yes | `AuthPreferences.kt:17-23` |

**UI test file inventory:**

| File | What it tests | Uses Hilt? | Uses Compose test rule? |
| --- | --- | --- | --- |
| `view/login/LoginScreenTest.kt` | Valid credentials (`admin`/`1234`) → biometric opt-in dialog is displayed | Yes — `@HiltAndroidTest` + `HiltAndroidRule` | Yes — `createAndroidComposeRule<MainActivity>()` |

Support files: `HiltTestRunner.kt` (custom `AndroidJUnitRunner` substituting `HiltTestApp_Application`) and `HiltTestApp.kt` (`@CustomTestApplication(BaseTestApp::class)`).

**Finding:** The single UI test will fail on any emulator/device without biometric hardware enrolled because `shouldPromptBiometric` evaluates to `false` in that environment — the dialog never appears and `assertIsDisplayed()` throws. This is a test environment sensitivity, not a functional bug in the app.

### A7. Delivery — README

| Section | Present? | Quality |
| --- | --- | --- |
| API key setup instructions | Yes | Explains TMDB account creation, `local.properties`, `TMDB_API_KEY=`, `BuildConfig.TMDB_API_KEY` reference — complete |
| Biometric test instructions (step-by-step, emulator) | Yes | ADB fingerprint enroll command (`adb -e emu finger touch 1`), credentials, opt-in steps, force-stop to re-test, `adb shell pm clear` to reset — complete |
| Architecture decisions | Yes | Three-layer diagram, cache-first policy description, analytics subsystem explanation, auth intentionally mocked — complete |
| AI usage documentation | Yes | Names Claude Code (Anthropic), lists specific tasks (code gen, plan audit, test review) — complete |
| No company/institution names | Pass | Package is `com.example.movieflux`; no employer or client names found in any source file or README |

**Finding:** README references `REVISED_IMPLEMENTATION_PLAN.md` at line 121 but that file does not exist in the repository. A reviewer following that reference will get a 404.

---

## Part B — Plan Internal Consistency

### B1. Phase Completion Accuracy

| Phase | Plan Claims | Reality | Verdict |
| --- | --- | --- | --- |
| Phase 1 — Infrastructure | Done | All DI modules, Room `@Database`, Retrofit, `@HiltAndroidApp`, Timber all present | Accurate |
| Phase 2 — Auth & Security (mocked) | Done (mocked) | `EncryptedSharedPreferences`, `BiometricHelper`, `LoginViewModel admin/1234`, biometric opt-in all present | Accurate |
| Phase 2B — Firebase Auth | Added to plan | **No Firebase code in source tree** — correctly cancelled | Plan still contains full phase text with no cancellation notice — misleading |
| Phase 2C — Google Sign-In | Added to plan | **No Google Sign-In code in source tree** — correctly cancelled | Same issue as 2B |
| Phase 3 — Nested Navigation | Done | `AppNavHost`, `MainScaffold`, `BottomNavBar`, `Screen` sealed class all present | Accurate |
| Phase 4 — Profile / Logout | Done | `ProfileScreen`, `ProfileViewModel`, `LogoutConfirmDialog`, `SettingsSwitchRow`, `ProfileHeader` all present | Accurate; hard-coded email is intentional and documented |
| Phase 5+ — Home / Details / Favorites | "Partial" | All screens fully implemented — pagination, search, cache-first, all UI states | Code is ahead of the plan's "partial" claim |

### B2. Dependency Version Coherence

| Check | Expected | Actual | Verdict |
| --- | --- | --- | --- |
| `alias(libs.plugins.kotlin.android)` in plugins block | Present (standard Kotlin Android project) | **Absent** — only `kotlin.compose`, `android.application`, `ksp`, `hilt` declared in `app/build.gradle.kts:10-15` | **FAIL** |
| KSP version matches Kotlin 2.2.10 | `2.2.10-2.0.2` | `ksp = "2.2.10-2.0.2"` at `libs.versions.toml:20` | Pass |
| `jvmToolchain(17)` present | Yes | `app/build.gradle.kts:50` — `kotlin { jvmToolchain(17) }` | Pass |
| Hilt version | Plan references 2.51.1 | `hilt = "2.59.2"` at `libs.versions.toml:18` | Acceptable — newer version |
| Compose BOM | Current at implementation time | `composeBom = "2025.01.00"` at `libs.versions.toml:10` | Acceptable |
| Biometric library stability | Stable recommended | `biometric = "1.2.0-alpha05"` — alpha channel | Medium risk |
| Security Crypto stability | Stable recommended | `securityCrypto = "1.1.0-alpha06"` — alpha channel | Medium risk |

### B3. Architecture Violations

1. **`LoginViewModel.kt:5-6`** — imports `com.example.movieflux.analytics.AnalyticsTracker` and `com.example.movieflux.analytics.FunnelTracker`. These are infrastructure-layer concerns injected directly into a UI-layer ViewModel. Clean Architecture convention positions analytics as a cross-cutting concern that should be abstracted behind a domain interface or injected from outside the ViewModel constructor. The same pattern appears in `HomeViewModel.kt:33`, `DetailsViewModel.kt:10`, `FavoritesViewModel.kt:9`, and `ProfileViewModel.kt:10`.

2. **`HomeViewModel.kt:32`** — injects `MovieRepository` (domain interface) directly in addition to `GetPopularMoviesUseCase`. This bypasses the Use Case layer for `getFavorites()`, `searchMovies()`, and `toggleFavorite()`. Three use cases are missing: `GetFavoritesUseCase`, `SearchMoviesUseCase`, `ToggleFavoriteUseCase`.

No UI layer importing from `data/` directly (the most critical leakage) was found — the domain boundary between UI and data layers is otherwise clean.

### B4. Navigation Correctness

| Check | Expected | Actual | Verdict |
| --- | --- | --- | --- |
| `NavType.IntType` for movieId argument | Present | `MainScaffold.kt:63-65` — `navArgument(ARG_MOVIE_ID) { type = NavType.IntType }` | Pass |
| `popUpTo(0) { inclusive = true }` on Login → Main | Present | `AppNavHost.kt:27-29` | Pass |
| `popUpTo(0) { inclusive = true }` on Logout → Auth | Present | `AppNavHost.kt:38-40` | Pass |
| `launchSingleTop = true` on tab navigation | Present | `BottomNavBar.kt:29` | Pass |
| BottomBar hidden on Details screen | Present | `MainScaffold.kt:27` — `currentRoute?.startsWith("details/") == false` | Pass |
| `saveState = true` / `restoreState = true` on tab clicks | Present | `BottomNavBar.kt:28-30` | Pass |

### B5. Cache-First Branch Review

| Check | Verdict |
| --- | --- |
| `MovieRepositoryImpl.getPopularMovies()` reads Room before network | Pass — `MovieRepositoryImpl.kt:29-32` emits cached page first; network result only re-emitted if IDs changed |
| `MIGRATION_1_2` present | Pass — `DatabaseModule.kt:16-21` adds `overview` and `rating` columns to `favorites` |
| `MIGRATION_2_3` present | Pass — `DatabaseModule.kt:23-39` creates `movie_cache` table |
| `MIGRATION_3_4` present | Pass — `DatabaseModule.kt:41-46` adds `rank` and `genreNames` columns to `movie_cache` |
| `MIGRATION_4_5` present | Pass — `DatabaseModule.kt:48-52` adds `genreIds` column to `favorites` |
| All migrations registered in `addMigrations(...)` | Pass — `DatabaseModule.kt:62` |
| `fallbackToDestructiveMigration` removed | Pass (good) — absent; Room will crash rather than silently drop data on a missing migration |
| Database version matches highest migration endpoint | Pass — `MovieDatabase.kt:7` version 5; highest migration is 4→5 |
| Network failure with non-empty cache completes silently | Pass — `MovieRepositoryImpl.kt:55-57` catches exception and only rethrows if `cached.isEmpty()` |

### B6. Missing Files

| File | Required By | Present? |
| --- | --- | --- |
| `view/login/LoginUiState.kt` | `LoginViewModel`, `LoginScreen` | Yes — confirmed via import usage in `LoginViewModelTest.kt` (`LoginUiState.Idle`, `Loading`, `Success`, `Error`) |
| `view/home/HomeEvent.kt` | `HomeViewModel`, `HomeViewModelTest` | Yes — confirmed by glob |
| `view/details/DetailsEvent.kt` | `DetailsViewModel`, `DetailsViewModelTest` | Yes — confirmed by glob |
| `view/favorites/FavoritesUiState.kt` | `FavoritesViewModel`, `FavoritesViewModelTest` | Yes — confirmed by glob |
| `view/profile/ProfileUiState.kt` | `ProfileViewModel`, `ProfileScreen` | Yes — confirmed by glob |
| `data/remote/GenreResponse.kt` | `RemoteDataSource`, `MovieRepositoryImpl` | Yes — confirmed by glob |
| `navigation/TopLevelTab.kt` | `BottomNavBar` | Yes — confirmed by glob |
| Firebase / Google Sign-In files (plan phases 2B/2C) | `IMPLEMENTATION_PLAN.md` | Absent — correctly excluded |
| `REVISED_IMPLEMENTATION_PLAN.md` | `README.md:121` | **Absent** — README references it but file does not exist |

### B7. Test Coverage

| Test Class | Type | Exists? | Notes |
| --- | --- | --- | --- |
| `HomeViewModelTest` | Unit — ViewModel | Yes | 9 tests: initial load, screen tracking, error state, view mode toggle, no-refetch, pagination append+dedup, toggle favorite, pagination error event + errorOnPage, retry after error, search error event, `isQueryActive` toggle |
| `LoginViewModelTest` | Unit — ViewModel | Yes | 9 tests: valid login, invalid login, screen tracking, funnel success/failure, biometric prompt (3 scenarios), `confirmBiometricOptIn` (2 scenarios) |
| `FavoritesViewModelTest` | Unit — ViewModel | Yes | 5 tests: flow emission → Success, screen tracking, view mode independence, search filter case-insensitive, toggle removes movie |
| `DetailsViewModelTest` | Unit — ViewModel | Yes | 8 tests: Success with genre names, Error on throw, screen tracking, optimistic favorite flip, repo call verified, rollback + ShowError event on failure, no-op when Loading, share intent content |
| `ProfileViewModelTest` | Unit — ViewModel | Yes | 7 tests: biometric enable (Available), biometric unavailable event (NoneEnrolled), biometric disable, confirmLogout clears prefs + event, requestLogout sets dialog flag, dismissLogoutDialog clears flag, screen tracking |
| `MovieRepositoryImplTest` | Unit — Repository | Yes | 13 tests: isFavorite join, cache-first emit + network once, no-cache exception propagates, cache-present silent completion, search isFavorite, search exception, toggleFavorite insert, toggleFavorite delete, getMovieDetail isFavorite, DAO before network order, genre caching (1 API call), genreNames populated, search genreNames+isFavorite, detail fallback favorites→network |
| `AnalyticsTrackerTest` | Unit — Analytics | Yes | 4 tests: composite forward, composite resilience on sink throw, error always forwarded, zero-rate drop |
| `LoginScreenTest` | Instrumented — Compose UI | Yes | 1 test: valid credentials → biometric opt-in dialog visible |

**Pass/fail assessment:** All unit tests are structurally correct — `UnconfinedTestDispatcher`, Turbine, MockK, and `FakeMovieRepository` are used appropriately. The single UI test (`LoginScreenTest`) will fail on a device/emulator without biometric hardware enrolled because `shouldPromptBiometric` will be `false` in that environment.

### B8. Plan Scope vs. Challenge Scope

The challenge requires: login, biometric, popular movies + pagination + search + states, detail with genres/favorite/share, and favorites with Room. The plan added Firebase Auth (2B) and Google Sign-In (2C) which were cancelled, and the code adds an analytics subsystem and JankStats integration that appear in no plan phase.

**Firebase / Google Sign-In:** Correctly cancelled and absent from source tree. The README documents the cancellation. The IMPLEMENTATION_PLAN.md still contains both phases in full — this is the main readability risk for a reviewer.

**Recommendation:** Add a `> **[CANCELLED — not delivered. See README §Limitations.]**` notice at the top of plan phases 2B and 2C, or delete them from the plan entirely.

### B9. Unresolved Open Questions

| Section | Question | Risk |
| --- | --- | --- |
| Phase 2B.18 (plan) | Firebase Auth integration — `google-services.json` in public repo | Cancelled; no code impact. Risk: reviewer reads plan before README and expects Firebase |
| Phase 2C.12 (plan) | Google Sign-In token storage strategy | Cancelled; no code impact |
| Biometric gate UX | Device without enrolled fingerprints: `BiometricGate` shows "Failed" state with system error string (`errString` from `MainActivity.kt:74`). Error message is system-generated and not localized by the app | Low — acceptable behaviour; documented in README |

### B10. Undocumented Code (Drift)

| File/Package | Description | Risk |
| --- | --- | --- |
| `analytics/` — 6 files + `analytics/di/AnalyticsModule.kt` | Full analytics subsystem: `CompositeAnalyticsTracker`, `SampledAnalyticsTracker`, `SamplingPolicy`, `FunnelTracker`, `TimberAnalyticsTracker`. Not in any plan phase. | Low functional risk — works and has 4 unit tests. Risk: reviewer may ask why an analytics system exists with no real analytics backend wired in |
| `performance/JankReporter.kt` + `JankStateEffect.kt` + `LogRecompositions.kt` | JankStats integration and Compose recomposition logging. Not in any plan phase. `JankStats.createAndTrack` called in `MainActivity.kt:85`. | Low — first-party Jetpack library. `LogRecompositions` calls are debug-only effective |
| `view/biometric/BiometricGate.kt` + `BiometricGateState.kt` | Stateful gate composable wrapping app content in `MainActivity`. Plan describes the biometric flow but does not name or spec this composable | No risk — well-implemented and necessary |

---

## Priority Fix List

### Critical — blocks build, runtime crash, or fails a core challenge requirement

1. **[CRITICAL]** `app/build.gradle.kts:10-15` is missing `alias(libs.plugins.kotlin.android)` in the plugins block. The Kotlin Android plugin (`org.jetbrains.kotlin.android`) is only arriving transitively via the `kotlin.compose` plugin. In certain AGP 9.x / KSP 2.2.x configurations this causes KSP annotation processing to not attach correctly to the Kotlin compilation task, potentially producing "no such class" errors for generated Hilt components or Room DAO implementations at build time. The alias `libs.plugins.kotlin.android` is already declared at `libs.versions.toml:93`. Add it as the second entry in the plugins block.

### High — challenge evaluator will likely notice and penalize

1. **[HIGH]** `LoginScreenTest.kt:27` — `assertIsDisplayed()` on `biometric_opt_in_dialog` will fail on any device or emulator without biometric hardware enrolled (including standard CI runners) because `shouldPromptBiometric` evaluates to `false` in that environment. Fix by injecting a `FakeBiometricHelper` via a Hilt test module that always returns `BiometricAvailability.Available`, or add `org.junit.Assume.assumeTrue(biometricAvailableOnDevice)` as a guard.

2. **[HIGH]** `IMPLEMENTATION_PLAN.md` contains full Phase 2B (Firebase Authentication) and Phase 2C (Google Sign-In) text with no cancellation notice. A reviewer reading the plan will expect these features and mark them as undelivered. Add a prominent cancellation banner at the top of each phase or remove them.

3. **[HIGH]** `README.md:121` references `REVISED_IMPLEMENTATION_PLAN.md` but that file does not exist in the repository. Remove the reference or create the file.

### Medium — gap or inconsistency, workaround exists

1. **[MEDIUM]** `biometric = "1.2.0-alpha05"` (`libs.versions.toml:23`) and `securityCrypto = "1.1.0-alpha06"` (`libs.versions.toml:22`) are alpha-channel dependencies. Both have stable counterparts (`biometric:1.1.0`, `security-crypto:1.0.0`). Using alpha in a challenge submission may raise a quality concern.

2. **[MEDIUM]** `HomeViewModel.kt:32` injects `MovieRepository` directly (bypassing the Use Case layer) for `getFavorites()`, `searchMovies()`, and `toggleFavorite()`. The Clean Architecture differential expects all domain calls to be mediated by use cases. Three use cases are missing: `GetFavoritesUseCase`, `SearchMoviesUseCase`, `ToggleFavoriteUseCase`.

3. **[MEDIUM]** All five `@HiltViewModel` classes inject `AnalyticsTracker` (and some inject `FunnelTracker`) from `com.example.movieflux.analytics`. This is a minor layering violation — the UI layer couples to an infrastructure package outside the domain boundary. If the analytics interface were moved to `domain/` or the injection were handled at the use-case layer this would be resolved.

4. **[MEDIUM]** `RemoteDataSource.search()` (`RemoteDataSource.kt:15-18`) has no `page` parameter. Multi-page search is not required by the challenge spec but the omission means search always returns page 1 only. A reviewer doing a detailed code read will notice the asymmetry with `getPopular(page)`.

### Low / Informational

1. **[LOW]** `LogRecompositions.kt` calls `Timber.tag(...).v(...)` unconditionally — it is a no-op in release builds since `Timber.DebugTree` is only planted in debug (`MovieFluxApp.kt:12`), but the string interpolation for the tag/message still runs in release. Wrapping the call in `if (BuildConfig.DEBUG)` or guarding with a Timber-level check would eliminate this minor overhead.

2. **[LOW]** `FavoritesViewModel` does not expose a `Loading` state for the transition from initial launch to first Room emission in production. The Loading state is emitted as the `stateIn` initial value (`FavoritesViewModel.kt:38`) but Room resolves so fast that the spinner is invisible. Not a functional issue.

3. **[LOW]** `AnalyticsModule.kt:20-23` wires only `TimberAnalyticsTracker` as the analytics sink. In a production app this would be a real analytics backend; for a challenge submission a Timber-backed tracker is appropriate.

4. **[LOW]** `GenreResponse.kt` exists as a wrapper data class but the `genres()` endpoint is only called in `MovieRepositoryImpl` and no test mocks the missing genres case for `getFavorites()` — when `cachedGenres` is null, `getFavorites()` returns movies with empty `genreNames` (`MovieRepositoryImpl.kt:63` — `cachedGenres ?: emptyMap()`). This is a silent degradation, not a crash.
