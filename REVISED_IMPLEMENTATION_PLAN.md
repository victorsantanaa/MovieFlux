# MovieFlux — Revised Implementation Plan

> Source documents: `CHALLENGE_VALIDATION.md` (primary), `IMPLEMENTATION_PLAN.md` (preserve), code on branch `feat/cache_first`.
> Several items flagged by the validation report have since been fixed in code (see "Already resolved" notes inside each Fix phase). This plan only covers what still needs work.
>
> **Out of scope by user decision:** Firebase Authentication (Phase 2B) and Google Sign-In via Credential Manager (Phase 2C) are dropped from the submission entirely. The mocked `admin/1234` login remains the only authentication path. The `RegisterScreen` and `Screen.Register` destination are not in scope. Phase 9 (Sentry/Crashlytics) is similarly dropped, though Timber-based analytics already in the tree stays.

---

## Current State Summary

| Challenge requirement | Status | Phase that addresses it |
| --- | --- | --- |
| Mocked login `admin/1234` | Done | Preserved (Phase 2) |
| Secure credential storage (EncryptedSharedPreferences) | Done | Preserved (Phase 2) |
| Post-login biometric opt-in dialog | **Missing** | **Fix-2** |
| Biometric on subsequent app open | Done | Preserved (Phase 2 gate in `MainActivity`) |
| Biometric `onAuthenticationFailed` correctness | **Wrong** (still treats transient as terminal) | **Fix-4A** |
| Biometric fallback UX on terminal error | **Missing** (silent stay on AuthGraph) | **Fix-4B** |
| Home: popular list/grid, infinite scroll, search, Loading/Error/Empty | Done | Preserved (Phase 5) |
| Home: empty-state copy carries query | Done (`isQueryActive` + `R.string.empty_no_results_for`) | Preserved |
| Home: genre chips on cards | Done (commit `238794b`) | Preserved |
| Details: poster, title, rating, overview, genres, favorite, share | Done | Preserved (Phase 6) |
| Details: genres sourced from `MovieModel.genreNames` (no extra `/genre` call) | Done (`DetailsViewModel.kt:46-48`) | Preserved |
| Details: optimistic toggle + rollback on failure | Done (`DetailsViewModel.kt:60-66`) | Preserved |
| Details: `movieId` parsing is loud | Done (`checkNotNull` at `DetailsViewModel.kt:27`) | Preserved |
| Favorites (Room, cross-screen sync, dedicated tab, offline) | Done | Preserved (Phase 7) |
| Genre cache (`Map<Int,String>` behind `Mutex`) in repo | Done (`MovieRepositoryImpl.kt:22-23, 66-73`) | Preserved |
| Unit tests — ViewModels | Done (Login, Home, Details, Profile, Favorites) | Preserved |
| Unit tests — Repository | Done (`MovieRepositoryImplTest` — 12 tests) | Preserved; verify per **Fix-3** |
| README at project root | **Missing** | **Fix-1** |
| Room migration (no destructive fallback) | **Wrong** (`fallbackToDestructiveMigration(dropAllTables = true)`) | **Fix-6A** |
| `kotlin-android` plugin in `app/build.gradle.kts` | **Missing** | **Fix-6B** |
| Compose UI test (differential) | Missing | **Fix-5** |

---

## Validation-Driven Fix Phases

### Fix-1 — README (blocks delivery)

**Goal:** Ship `README.md` at project root covering the four challenge-mandated sections.

**Touches:**
- `README.md` (new, project root)

**Rule:**
- update the README.md with Brazilian Portuguese (PT-Br)

**Steps:**

1. **Section "Setup".**
   - State min Android Studio version (Hedgehog or later), JDK 17, Android SDK 36.
   - Instruct reviewer to create `local.properties` at project root and add `TMDB_API_KEY=<their_key>`. Link to https://www.themoviedb.org/settings/api for obtaining a v3 key.
   - Build commands: `./gradlew clean assembleDebug` (or `gradlew.bat` on Windows) produces `app/build/outputs/apk/debug/app-debug.apk`.
   - Reason: without this, a fresh clone cannot compile — `BuildConfig.TMDB_API_KEY` is read from `local.properties` in `app/build.gradle.kts:8`.

2. **Section "Testing Biometric Login".**
   - On emulator: create AVD with API ≥ 26, open Settings → Security → Fingerprint, enroll using `adb -e emu finger touch 1`.
   - First-run flow: launch app → enter `admin` / `1234` → tap Done → biometric opt-in dialog appears → tap "Enable" → fingerprint enrolled is used on next launch.
   - To re-test: clear app data (`adb shell pm clear com.example.movieflux`) or uninstall, since `biometricPrompted` is persisted.
   - Reason: the challenge calls this out specifically — "como testar biometria".

3. **Section "Architecture".**
   - One paragraph: MVVM + Clean Architecture, three layers (`domain`, `data`, `view`/`navigation`/`ui`).
   - Data flow diagram: `Composable → ViewModel (StateFlow<UiState>) → UseCase → Repository → Remote (Retrofit) / Local (Room)`.
   - Cache-first policy: `MovieRepositoryImpl` emits Room cache first, then refreshes from network. Genre map is cached in-memory behind a `Mutex`.
   - Note that authentication is intentionally mocked (`admin/1234`) per the challenge brief; Firebase/Google sign-in were explicitly de-scoped.
   - Reason: the challenge calls out "decisões de arquitetura" as a delivery requirement.

4. **Section "AI Usage Disclosure".**
   - Disclose: Claude Code (Anthropic) used for code generation, plan auditing (`CHALLENGE_VALIDATION.md`), and this revised plan (`REVISED_IMPLEMENTATION_PLAN.md`). Human review on every commit.
   - Reason: the challenge requires "uso de IA" disclosure.

5. **Section "Known Limitations".**
   - Profile email/name hard-coded (`admin@movieflux.app`) — intentional, since auth is mocked.
   - Phases 2B/2C/9 from `IMPLEMENTATION_PLAN.md` (Firebase auth, Google sign-in, Sentry/Crashlytics) are not implemented.
   - Reason: prevents the reviewer from filing the hard-coding as a bug.

**Acceptance criterion:** `README.md` exists at project root with the five sections above. A reviewer following only the README can configure `TMDB_API_KEY`, build the debug APK, and test the biometric flow.

---

### Fix-2 — Post-login biometric opt-in prompt + `biometricPrompted` flag

**Goal:** On the very first successful login, ask the user once whether to enable biometric. Never ask again, regardless of subsequent enable/disable from Profile.

**Touches:**
- `app/src/main/java/com/example/movieflux/data/preferences/AuthPreferences.kt`
- `app/src/main/java/com/example/movieflux/view/login/LoginViewModel.kt`
- `app/src/main/java/com/example/movieflux/view/login/LoginUiState.kt`
- `app/src/main/java/com/example/movieflux/view/login/LoginScreen.kt`
- `app/src/main/java/com/example/movieflux/view/login/BiometricOptInDialog.kt` (new)
- `app/src/main/res/values/strings.xml` (dialog copy)

**Steps:**

1. **Add `biometricPrompted` to `AuthPreferences`.**
   - Add `private const val KEY_BIOMETRIC_PROMPTED = "biometric_prompted"`.
   - Add `var biometricPrompted: Boolean` with the same get/set pattern as `biometricEnabled` (lines 29-31).
   - Reason: this is the only durable signal that "the user has already been asked". `biometricEnabled` alone cannot distinguish "never asked" from "asked, declined".

2. **Decide where the dialog is shown.**
   - Decision: show the dialog from `LoginScreen`, not from `AppNavHost`. `LoginScreen` already owns the `LoginUiState.Success` collection and the `onLoginSuccess` callback timing.
   - The dialog must appear *before* `onLoginSuccess()` runs, otherwise the user is already on `MainGraph` when it appears.
   - Reason: keeps the auth-flow responsibility in the auth feature module; `AppNavHost` should not know about biometric.

3. **Refactor `BiometricHelper.canAuthenticate` to use an injected `@ApplicationContext`.**
   - Change the constructor to `class BiometricHelper @Inject constructor(@ApplicationContext private val appContext: Context)`. Drop the `Context` parameter from `canAuthenticate`.
   - Update all call sites (`MainActivity.kt:34`).
   - Reason: lets `LoginViewModel` query availability without leaking an Activity `Context`.

4. **Extend `LoginUiState.Success` with an opt-in flag.**
   - Change `data object Success` to `data class Success(val shouldPromptBiometric: Boolean)`.
   - In `LoginViewModel.login()` after `authPreferences.isLoggedIn = true`, compute `shouldPromptBiometric = !authPreferences.biometricPrompted && biometricHelper.canAuthenticate() == Available` and emit it.
   - Inject `BiometricHelper` into `LoginViewModel`.
   - Reason: keeps the decision in the ViewModel (testable) and the Composable purely declarative.

5. **Replace the unconditional navigate in `LoginScreen.LaunchedEffect` with state-driven dialog.**
   - Hoist `var showDialog by remember { mutableStateOf(false) }`.
   - Replace `LaunchedEffect(uiState) { if (uiState is Success) onLoginSuccess() }` with:
     - If `uiState is Success && uiState.shouldPromptBiometric && !showDialog`: set `showDialog = true`.
     - If `uiState is Success && !uiState.shouldPromptBiometric`: call `onLoginSuccess()`.
   - When `showDialog`, render `BiometricOptInDialog` overlay.
   - Reason: a `Dialog` composable cannot be shown from a `LaunchedEffect`; it must be hoisted into the composition.

6. **Create `BiometricOptInDialog` composable.**
   - Signature: `@Composable fun BiometricOptInDialog(onEnable: () -> Unit, onSkip: () -> Unit)`.
   - Use `androidx.compose.material3.AlertDialog` with title/text from string resources, confirm button "Enable" → `onEnable`, dismiss button "Skip" → `onSkip`.
   - `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)` — the user must explicitly choose so the prompted flag is always set.
   - Reason: `AlertDialog` is the Material3 idiomatic primitive for blocking decisions.

7. **Wire the dialog actions through the ViewModel.**
   - Add `fun confirmBiometricOptIn(enable: Boolean)` to `LoginViewModel`:
     - `authPreferences.biometricEnabled = enable`
     - `authPreferences.biometricPrompted = true`
     - Emit `LoginUiState.Success(shouldPromptBiometric = false)` so the next recomposition triggers `onLoginSuccess()`.
   - In `LoginScreen`: `onEnable = { vm.confirmBiometricOptIn(true) }`, `onSkip = { vm.confirmBiometricOptIn(false) }`.
   - Reason: keeps the write-and-navigate sequencing atomic in one place; tests can verify both prefs are set on every dialog action.

8. **Add string resources.**
   - `R.string.biometric_opt_in_title` = "Enable biometric login?"
   - `R.string.biometric_opt_in_message` = "Use your fingerprint or face to sign in faster next time."
   - `R.string.biometric_opt_in_enable` = "Enable"
   - `R.string.biometric_opt_in_skip` = "Skip"

9. **Add `LoginViewModelTest` cases.**
   - `login_success_with_biometric_available_and_not_prompted_emits_Success_with_shouldPromptBiometric_true`.
   - `login_success_when_biometricPrompted_already_true_emits_Success_with_shouldPromptBiometric_false`.
   - `login_success_when_biometric_unavailable_emits_Success_with_shouldPromptBiometric_false`.
   - `confirmBiometricOptIn_true_sets_both_prefs_to_true`.
   - `confirmBiometricOptIn_false_sets_only_prompted_true`.

**Acceptance criterion:**
- On a fresh install, entering `admin/1234` shows an `AlertDialog` with Enable/Skip before `MainGraph` is rendered.
- After tapping either button, `authPreferences.biometricPrompted` is `true`.
- On the second login of the same app install, no dialog appears (regardless of whether biometric was enabled or skipped).

---

### Fix-3 — Repository unit tests + `DetailsViewModelTest` verification

**Status:** Validation report listed both as "absent". They are **already resolved** — `MovieRepositoryImplTest` (12 tests) and `DetailsViewModelTest` (7 tests) both exist on the branch. This phase is a *verification* phase, not new implementation.

**Goal:** Confirm the existing suites cover the cases the challenge evaluates, and fill any gap.

**Touches:**
- `app/src/test/java/com/example/movieflux/data/repository/MovieRepositoryImplTest.kt` (audit)
- `app/src/test/java/com/example/movieflux/view/details/DetailsViewModelTest.kt` (audit + add `share` test)

**Steps:**

1. **Audit `MovieRepositoryImplTest` for these exact cases. If any is missing, add it.**
   - `getPopularMovies_emits_cache_first_then_network`.
   - `getPopularMovies_emits_only_cache_when_network_throws_and_cache_present`.
   - `getPopularMovies_propagates_exception_when_no_cache_and_network_throws`.
   - `toggleFavorite_isFavorite_true_calls_dao_delete`.
   - `toggleFavorite_isFavorite_false_calls_dao_insert`.
   - `getGenres_caches_first_call_and_returns_cached_on_second_call_without_hitting_api`.
   - `searchMovies_emits_results_with_isFavorite_and_genreNames_populated`.
   - `getMovieDetail_emits_favorite_then_cache_then_network`.
   - Use `MockK` (`coEvery { dao.getCachedPage(1) } returns ...`), `kotlinx-coroutines-test` (`runTest`), and `Turbine` (`flow.test { ... }`) for cold-flow assertions.
   - Reason: the challenge cites "Testes unitários (ViewModels **e Repositories**)" verbatim — the repository test must exercise the cache-first contract explicitly.

2. **Add `share_buildsCorrectIntent` test to `DetailsViewModelTest`.**
   - Use `mockk<Context>()`; capture the `Intent` passed to `context.startActivity(...)` via a `slot<Intent>()`.
   - Assert `intent.action == Intent.ACTION_CHOOSER`, the wrapped intent's `Intent.EXTRA_TEXT` contains both `movie.title` and `https://www.themoviedb.org/movie/${movie.id}`.
   - Reason: `share()` (`DetailsViewModel.kt:69-80`) is currently uncovered.

3. **Coroutine test dispatcher policy.**
   - For ViewModels using `StateFlow` + `Turbine`: use `UnconfinedTestDispatcher` set via `Dispatchers.setMain(...)` in `@Before`, `Dispatchers.resetMain()` in `@After`.
   - For pagination tests that need to observe intermediate states deterministically: `StandardTestDispatcher` + explicit `advanceUntilIdle()`.
   - Reason: `UnconfinedTestDispatcher` collapses launches eagerly — best for "emit, then assert"; `StandardTestDispatcher` is required when ordering of multiple `launch` blocks matters.

4. **Fix the `DetailsViewModelTest.events` timing flake noted in the validation report (B7).**
   - Drain events inside the same `vm.uiState.test { ... }` block via `vm.events.test { awaitItem() }`, or use a `TestScope.backgroundScope` to keep both flows hot.
   - Reason: event channels are buffered; running `vm.events.test {}` after the first `uiState.test {}` may find the buffer already consumed.

**Acceptance criterion:** `./gradlew testDebugUnitTest` passes with zero failures, and the eight `MovieRepositoryImplTest` cases above are present.

---

### Fix-4 — BiometricHelper correctness + fallback UX

#### Fix-4A — `onAuthenticationFailed` API misuse

**Goal:** Stop treating transient mis-scans as terminal errors.

**Touches:**
- `app/src/main/java/com/example/movieflux/data/biometric/BiometricHelper.kt`

**Steps:**

1. **Remove or no-op `onAuthenticationFailed`.**
   - Replace `BiometricHelper.kt:45-47` with an empty override (or delete the override entirely). The system already shows "Not recognized" UI on the prompt — Helper must not exit the flow.
   - Use `onAuthenticationError` as the **only** terminal callback.
   - Reason: per `androidx.biometric.BiometricPrompt.AuthenticationCallback` documentation: `onAuthenticationFailed` fires for each failed attempt within the same prompt session and the user can retry. `onAuthenticationError` is invoked for terminal codes (`ERROR_LOCKOUT`, `ERROR_USER_CANCELED`, `ERROR_NEGATIVE_BUTTON`, etc.). Calling `onError` from `onAuthenticationFailed` dismisses the navigation flow on the first wrong fingerprint, which is wrong UX and prevents the user from ever succeeding.

2. **Optionally log failed attempts via `Timber`.**
   - `override fun onAuthenticationFailed() { Timber.d("Biometric attempt failed (transient)") }`.
   - Reason: still observable for debugging without breaking the flow.

3. **Document the manual test in README.**
   - "After 2 wrong scans, the prompt should keep accepting attempts until the system locks out (5 failures), at which point the fallback UI from Fix-4B appears."
   - Reason: pure JUnit testing of `BiometricPrompt` is impractical; document the manual verification step instead.

**Acceptance criterion:** Wrong fingerprint scan does not navigate or dismiss the activity; system shows "Not recognized" and re-prompts. `onAuthenticationError` (lockout, cancel) still triggers fallback UX (Fix-4B).

---

#### Fix-4B — Missing fallback UX

**Goal:** When biometric terminally fails, present a clear "Use password" affordance instead of silently landing on the Login screen.

**Touches:**
- `app/src/main/java/com/example/movieflux/MainActivity.kt`
- `app/src/main/java/com/example/movieflux/view/biometric/BiometricGate.kt` (new)
- `app/src/main/java/com/example/movieflux/view/biometric/BiometricGateState.kt` (new)
- `app/src/main/res/values/strings.xml`

**Steps:**

1. **Introduce a `BiometricGateState` sealed class.**
   - File: `view/biometric/BiometricGateState.kt`.
   - `sealed class BiometricGateState { object Checking; object Passed; data class Failed(val message: String) : BiometricGateState() }` (when biometric is not required, jump straight to `Passed`).
   - Reason: a `when` over three explicit states is clearer than nullable booleans and Composable side effects.

2. **Hoist the state in `MainActivity`.**
   - Replace `val needsBiometric = ...` block with `var gateState by remember { mutableStateOf(if (needsBiometric) Checking else Passed) }`.
   - Render `BiometricGate(state = gateState, onRetry = ..., onUsePassword = ..., onResolved = ...) { AppNavHost(rootNavController, startDestination) }`.
   - Reason: keeps `MainActivity` thin; `BiometricGate` owns the UX.

3. **Implement `BiometricGate` composable.**
   - File: `view/biometric/BiometricGate.kt`.
   - When `state == Checking`: trigger `biometricHelper.authenticate(...)` once via `LaunchedEffect(Unit)`; on success → `onResolved(Passed)`, on error → `onResolved(Failed(errString))`.
   - When `state == Passed`: render `content()` (the `NavHost`).
   - When `state is Failed`: render full-screen `Column` centered with an icon, `Text(R.string.biometric_failed_title)`, `Text(state.message)`, `PrimaryButton(text = stringResource(R.string.biometric_use_password), onClick = onUsePassword)`, `SecondaryButton(text = stringResource(R.string.biometric_retry), onClick = onRetry)`.
   - Reason: makes the fallback affordance the only thing on screen so the user cannot miss it.

4. **Wire "Use password" to start at AuthGraph.**
   - Decision: keep `isLoggedIn = true` (the user's credentials are still valid; they are only bypassing biometric this session). Set `gateState = Passed` and call `rootNavController.navigate(Screen.AuthGraph.route) { popUpTo(0) { inclusive = true } }`.
   - Reason: prevents the user from being stuck — they can always fall back to credentials.

5. **"Try again" simply resets `gateState` to `Checking`.**
   - The `LaunchedEffect(Unit)` inside `BiometricGate` will re-fire when keyed off the state transition (use `LaunchedEffect(state)` instead of `Unit`).

6. **Add string resources.**
   - `biometric_failed_title` = "Biometric login failed"
   - `biometric_use_password` = "Use password instead"
   - `biometric_retry` = "Try again"

**Acceptance criterion:**
- Trigger biometric lockout in emulator (5 wrong scans) → app shows fallback UI with "Use password" and "Try again" buttons.
- Tapping "Use password" navigates to `LoginScreen`.
- Tapping "Try again" re-invokes `BiometricPrompt`.

---

### Fix-5 — One Compose UI test (differential)

**Goal:** Earn the differential bonus and prove the test harness works.

**Touches:**
- `app/src/androidTest/java/com/example/movieflux/view/login/LoginScreenTest.kt` (new)
- `gradle/libs.versions.toml` — add `hilt-android-testing`

**Steps:**

1. **Add `hilt-android-testing` dependency.**
   - `androidTestImplementation(libs.hilt.android.testing)` and `kspAndroidTest(libs.hilt.compiler)`.
   - Reason: `LoginScreen` depends on a `@HiltViewModel`, so the test runner needs Hilt's test entry point.

2. **Set up `createAndroidComposeRule<MainActivity>()` with `HiltAndroidRule` and `@HiltAndroidTest`.**
   - Add a `@CustomTestApplication` + custom `TestRunner` per Hilt testing docs.

3. **One happy-path test: `login_with_valid_credentials_shows_biometric_opt_in_dialog`.**
   - `composeTestRule.onNodeWithText("Credencial").performTextInput("admin")`.
   - `composeTestRule.onNodeWithText("Senha").performTextInput("1234")`.
   - `composeTestRule.onNodeWithText("Done").performClick()`.
   - `composeTestRule.onNodeWithText("Enable biometric login?").assertIsDisplayed()` (post-Fix-2).
   - Reason: smallest demonstration of Compose UI testing competence.

**Acceptance criterion:** `./gradlew connectedDebugAndroidTest` passes the single login test on a running emulator.

---

### Fix-6 — Build hygiene

#### Fix-6A — Replace destructive Room migration

**Goal:** Stop wiping the `favorites` table on every schema bump.

**Touches:**
- `app/src/main/java/com/example/movieflux/di/DatabaseModule.kt`

**Steps:**

1. **Add `Migration(2, 3)` object.**
   - At the top of `DatabaseModule.kt`, declare `private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS movie_cache (...)") } }`.
   - Mirror the columns and types from `CachedMovieEntity` (read the entity declaration to keep the schema exact).
   - Reason: explicit migration preserves the `favorites` table; `fallbackToDestructiveMigration` would wipe it.

2. **Replace the builder call.**
   - Change `.fallbackToDestructiveMigration(dropAllTables = true)` to `.addMigrations(MIGRATION_2_3)`.
   - Reason: explicit migrations are required for any app that persists user data.

**Acceptance criterion:** Revert `MovieDatabase` to version 2, favorite a movie, bump back to 3 with the new migration — favorite persists.

#### Fix-6B — Add `kotlin-android` plugin

**Touches:**
- `app/build.gradle.kts`

**Steps:**

1. **Insert `alias(libs.plugins.kotlin.android)` as the second plugin in the `plugins {}` block** (currently lines 10-15 of `app/build.gradle.kts`). The plugin is already declared in `libs.versions.toml:92`.
   - Final order: `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt`.
   - Reason: `kotlin.compose` and `ksp` rely on `kotlin-android` being applied first for Kotlin source set configuration; absence works today by coincidence and is fragile.

**Acceptance criterion:** `./gradlew clean assembleDebug` builds, and `./gradlew :app:dependencies | rg kotlin-stdlib` confirms the stdlib comes in via `kotlin-android`.

---

## Preserved Phases

The following phases from `IMPLEMENTATION_PLAN.md` are already correctly specified and implemented. **No changes required.**

| Phase | What it covers |
| --- | --- |
| Phase 1 — Infrastructure | DI modules, `@HiltAndroidApp`, JVM 17, `libs.versions.toml` baseline. |
| Phase 2 (mocked path) — Auth/Security | `LoginViewModel` validates `admin/1234`, `AuthPreferences` uses `EncryptedSharedPreferences`, `BiometricHelper` and `MainActivity` biometric gate work. *Excludes* the post-login dialog (Fix-2) and the `onAuthenticationFailed` bug (Fix-4A). |
| Phase 3 — Nested Navigation | `Screen.kt`, `AppNavHost.kt`, `MainScaffold.kt`, `BottomNavBar.kt`, `TopLevelTab.kt` all wired; details route uses `NavType.IntType`; tabs use `launchSingleTop` + `restoreState`. |
| Phase 4 — Profile / Logout | `ProfileScreen`, `ProfileViewModel`, `LogoutConfirmDialog`, `SettingsSwitchRow` complete. |
| Phase 5 — Home | List/grid toggle, pagination via `snapshotFlow`, debounced search, Loading/Error/Empty (including query-specific empty copy via `isQueryActive` + `R.string.empty_no_results_for`), pagination snackbar event, search `.catch`. |
| Phase 6 — Details | Poster, title, rating, overview, genre chips from `MovieModel.genreNames`, optimistic favorite toggle with rollback, share intent, `checkNotNull` movieId. |
| Phase 7 — Favorites | Room persistence, cross-screen sync via `repository.getFavorites()` Flow combine in `HomeViewModel`, dedicated tab, fully offline. |
| Phase 8 — Repository cache-first | `MovieRepositoryImpl` emits Room cache then refreshes from network; genre `Map<Int,String>` cached behind `Mutex`; detail fallback chain favorites → cached → network. |

### Explicitly de-scoped (not on submission branch)

| Phase | Reason for de-scope |
| --- | --- |
| Phase 2B — Firebase Authentication + Registration | Beyond challenge brief; the challenge explicitly allows mocked `admin/1234`. Removed per user decision. |
| Phase 2C — Google Sign-In via Credential Manager | Beyond challenge brief; depends on Firebase + SHA-1 fingerprint config. Removed per user decision. |
| Phase 9 — Sentry / Crashlytics / Firebase Performance | Beyond challenge brief and would introduce reviewer-build risk (DSN/secrets). Timber-based analytics already in tree stays as-is. |

---

## Acceptance Checklist

### Build
- [ ] `./gradlew clean assembleDebug` passes on a machine with only `TMDB_API_KEY` in `local.properties`.
- [ ] `./gradlew testDebugUnitTest` passes with no failures. [Fix-3]
- [ ] `./gradlew lint` produces no errors. [Preserved + Fix-6]
- [ ] `app/build.gradle.kts` `plugins {}` block contains `kotlin.android` before `kotlin.compose`, `ksp`, `hilt`. [Fix-6B]

### Authentication
- [ ] Entering `admin / 1234` on `LoginScreen` emits `LoginUiState.Success(shouldPromptBiometric = …)`. [Fix-2]
- [ ] After the first successful login, a biometric opt-in `AlertDialog` appears before `MainGraph` is rendered. [Fix-2]
- [ ] Tapping "Enable" sets `biometricEnabled = true` and `biometricPrompted = true` in `AuthPreferences`. [Fix-2]
- [ ] Tapping "Skip" sets `biometricPrompted = true` and leaves `biometricEnabled = false`. [Fix-2]
- [ ] On second app open with `biometricEnabled = true`, the biometric prompt appears before `MainGraph`. [Preserved]
- [ ] Multiple failed fingerprint scans on the same prompt session do **not** dismiss the flow; only `onAuthenticationError` (lockout / cancel) does. [Fix-4A]
- [ ] On biometric terminal failure, a fallback screen appears with "Use password" and "Try again". [Fix-4B]
- [ ] `biometricEnabled` and `biometricPrompted` are stored in `EncryptedSharedPreferences`. [Preserved + Fix-2]

### Home
- [ ] Popular movies list loads on launch. [Preserved]
- [ ] Scrolling near the end triggers `loadNextPage()`. [Preserved]
- [ ] Typing in the search bar debounces 300ms and calls `/search/movie`. [Preserved]
- [ ] Clearing the search bar returns to the popular movies list. [Preserved]
- [ ] "No results for 'xxx'" copy appears when search returns empty (`R.string.empty_no_results_for`). [Preserved]
- [ ] Network error shows an `ErrorView` with retry. [Preserved]
- [ ] Each movie card shows 1–2 genre name chips. [Preserved]
- [ ] Pagination failure surfaces a `SnackbarHostState` message; the user can retry. [Preserved]

### Details
- [ ] Poster, title, vote_average, overview, and genre names all render. [Preserved]
- [ ] Genre names come from `MovieModel.genreNames` — no per-detail `/genre/movie/list` call. [Preserved]
- [ ] Favorite toggle is optimistic; failure reverts UI and emits a `DetailsEvent.ShowError`. [Preserved]
- [ ] Share button opens a system share sheet with title + `https://www.themoviedb.org/movie/{id}`. [Preserved + Fix-3 test]
- [ ] Missing/non-int `movieId` arg throws `IllegalArgumentException` (`checkNotNull`) rather than silently hitting `/movie/0`. [Preserved]

### Favorites
- [ ] Favorited movies appear in the Favorites tab without a network call. [Preserved]
- [ ] Removing a favorite in Details removes it from the Favorites tab. [Preserved]
- [ ] Favorites tab renders correctly with no network connection. [Preserved]
- [ ] A schema upgrade does **not** wipe favorites (`MIGRATION_2_3` present; no `fallbackToDestructiveMigration`). [Fix-6A]

### Tests
- [ ] `MovieRepositoryImplTest` covers: cache-hit, network fallback with cache, exception without cache, `toggleFavorite` insert, `toggleFavorite` delete, `getGenres` mutex caching, `searchMovies` mapping, detail fallback chain. [Fix-3]
- [ ] `DetailsViewModelTest` covers: success, error, `toggleFavorite` optimistic + rollback, `share` Intent extras. [Fix-3]
- [ ] `LoginViewModelTest` covers: valid/invalid login, biometric opt-in flag emission, `confirmBiometricOptIn` writes both prefs. [Fix-2]
- [ ] `HomeViewModelTest`, `FavoritesViewModelTest`, `ProfileViewModelTest`, `AnalyticsTrackerTest` all pass. [Preserved]
- [ ] One Compose UI test (`LoginScreenTest`) passes via `connectedDebugAndroidTest`. [Fix-5]

### README
- [ ] `README.md` exists at project root. [Fix-1]
- [ ] README explains how to set `TMDB_API_KEY` in `local.properties`. [Fix-1]
- [ ] README explains how to trigger the biometric opt-in dialog on first run. [Fix-1]
- [ ] README explains Clean Architecture + MVVM layers and the cache-first data policy. [Fix-1]
- [ ] README discloses AI usage in development. [Fix-1]
- [ ] README notes that Firebase auth, Google sign-in, and Sentry/Crashlytics are explicitly out of scope. [Fix-1]
