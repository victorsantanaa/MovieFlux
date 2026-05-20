# MovieFlux — Implementation Plan: Theme Selector (Profile)

## Context & Goal

Today `MovieFluxTheme` (`ui/theme/Theme.kt:28`) hard-derives its colour scheme from `isSystemInDarkTheme()` — the user has no way to override. Add a tri-state theme selector on the Profile screen (**System** / **Light** / **Dark**), persist the choice across app sessions, and propagate the change live (no app restart). The choice must survive process death; on first launch the default is **System**, matching today's behaviour.

This is a UX-polish item; it does not touch any challenge requirement listed in `CHALLENGE_VALIDATION.md`.

## Scope

**In scope:**
- A new `ThemeMode` enum: `SYSTEM`, `LIGHT`, `DARK`.
- Persistence in a Hilt singleton (`UiPreferences`-style, plain `SharedPreferences`); survives process death.
- A `ThemeRepository` singleton exposing `StateFlow<ThemeMode>` so writes from `ProfileViewModel` are observed live by the root `MainActivity` composable.
- `MovieFluxTheme` accepts a `ThemeMode` parameter and resolves the colour scheme accordingly.
- Profile screen renders a tri-state selector below the existing biometric toggle, using Material 3 `SingleChoiceSegmentedButtonRow` for the three options.
- Localised PT-BR labels (`Sistema`, `Claro`, `Escuro`) in `strings.xml`.
- Analytics event `theme_changed` with `mode` payload fired on every change.

**Dependencies / preconditions:**
- The "view mode persistence" plan (`IMPLEMENTATION_view-mode-persistence.md`) proposes introducing `UiPreferences`. If that plan has not yet landed, this plan creates `UiPreferences` itself in Phase 1; if it has landed, this plan extends it. Both paths are documented below.
- No new third-party dependency required.

## Architecture Decisions

- **Decision:** introduce a Hilt singleton `ThemeRepository` that wraps `UiPreferences` and exposes a `StateFlow<ThemeMode>`. **Reason:** the theme must be observed by `MainActivity` (root of the composition) AND mutated by `ProfileViewModel` (deep in the navigation tree). A plain getter/setter on `UiPreferences` cannot push changes to `MainActivity` without a Flow seam — `ThemeRepository` provides that seam. **Alternative considered:** make `MainActivity` re-read `UiPreferences` on every recomposition — rejected; SharedPreferences reads on every frame are wasteful and `MainActivity` would not recompose unless something else triggered it.

- **Decision:** back persistence with plain (non-encrypted) `SharedPreferences` in the `ui_prefs` file. **Reason:** theme preference is non-sensitive UI state, sharing the file with view-mode persistence keeps related state colocated. **Alternative considered:** DataStore Preferences — rejected for the same reason as in the view-mode plan (a new ~150 KB dependency for one enum); the `ThemeRepository` seam means a future swap to DataStore is a one-class refactor.

- **Decision:** `MovieFluxTheme` accepts a `themeMode: ThemeMode` parameter (default `ThemeMode.SYSTEM` for previews) and resolves the boolean `darkTheme` internally via `when (themeMode) { SYSTEM -> isSystemInDarkTheme(); LIGHT -> false; DARK -> true }`. **Reason:** keeps the theme function's contract typed and explicit; calling sites do not have to know about `isSystemInDarkTheme()` themselves. **Alternative considered:** keep `darkTheme: Boolean` and resolve at the call site — rejected; pushes the resolution logic into `MainActivity`, making future theming overrides (e.g. AMOLED black) harder to add.

- **Decision:** observe the theme in `MainActivity` via `themeRepository.themeMode.collectAsStateWithLifecycle()` and pass the resulting `ThemeMode` into `MovieFluxTheme(themeMode = ...)`. **Reason:** `collectAsStateWithLifecycle` respects the activity lifecycle (no leaks across configuration changes) and triggers recomposition automatically on every write from any ViewModel. **Alternative considered:** `collectAsState()` — works but does not respect lifecycle; `collectAsStateWithLifecycle` is the modern AndroidX recommendation for Activity-scoped flows.

- **Decision:** use Material 3 `SingleChoiceSegmentedButtonRow` (three `SegmentedButton` children) for the selector on Profile. **Reason:** segmented buttons are the M3 idiom for short, mutually-exclusive option sets; they fit one row on Profile's existing layout and need no new components. **Alternative considered:** `RadioButton` group — works but takes three vertical rows, adds visual weight, and breaks the current Profile information density.

- **Decision:** persist the enum as its `name` string (`"SYSTEM"`, `"LIGHT"`, `"DARK"`); parse via `runCatching { ThemeMode.valueOf(...) }.getOrDefault(ThemeMode.SYSTEM)`. **Reason:** human-readable, tolerant of unknown stored values, no migration ceremony if the enum grows. Matches the view-mode persistence convention.

## Phases

### Phase 1 — `ThemeMode` enum and `UiPreferences` theme key

**Goal:** Add the typed enum and a synchronous read/write pair on `UiPreferences`.

**Touches:**
- `app/src/main/java/com/example/movieflux/ui/theme/ThemeMode.kt` (NEW)
- `app/src/main/java/com/example/movieflux/data/preferences/UiPreferences.kt` (NEW if absent, EDIT if view-mode plan landed first)

**Steps:**
1. Create `ThemeMode` as `enum class ThemeMode { SYSTEM, LIGHT, DARK }` under `ui.theme`. Reason: the enum lives next to `MovieFluxTheme`, which is the only producer/consumer of the Compose-side mapping.
2. In `UiPreferences`, add `private const val KEY_THEME_MODE = "theme_mode"` to the companion object alongside any view-mode keys. Add `getThemeMode(): ThemeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: return@runCatching ThemeMode.SYSTEM) }.getOrDefault(ThemeMode.SYSTEM)` and `setThemeMode(mode: ThemeMode) { prefs.edit().putString(KEY_THEME_MODE, mode.name).apply() }`. Reason: synchronous accessors match the existing `AuthPreferences` shape; the `ThemeRepository` layered on top in Phase 2 is what exposes the Flow.
3. If `UiPreferences` does NOT yet exist, scaffold it as the view-mode plan describes (Hilt `@Singleton`, `context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)`) and include only the theme key in this phase; view-mode keys land with the other plan.

**Tests:**
- `app/src/test/java/com/example/movieflux/data/preferences/UiPreferencesThemeTest.kt` (NEW) using Robolectric.
- Cases: `default returns SYSTEM`; `setThemeMode(DARK) then getThemeMode() returns DARK`; `corrupt stored value falls back to SYSTEM`; `theme key is independent from view-mode keys` (only if view-mode plan landed first).

**Acceptance criterion:** `gradlew testDebugUnitTest --tests "*UiPreferencesThemeTest"` passes; `UiPreferences.getThemeMode()` returns `ThemeMode.SYSTEM` on first run.

---

### Phase 2 — `ThemeRepository` (Flow seam)

**Goal:** Expose a `StateFlow<ThemeMode>` that `MainActivity` can observe and `ProfileViewModel` can write to.

**Touches:**
- `app/src/main/java/com/example/movieflux/data/preferences/ThemeRepository.kt` (NEW)

**Steps:**
1. Create `@Singleton class ThemeRepository @Inject constructor(private val uiPreferences: UiPreferences)`. Inside, declare `private val _themeMode = MutableStateFlow(uiPreferences.getThemeMode())` and `val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()`. Reason: seeded once at injection; the singleton scope means every observer sees the same instance.
2. Expose `fun setThemeMode(mode: ThemeMode) { uiPreferences.setThemeMode(mode); _themeMode.value = mode }`. Reason: write-through pattern — persist first, then publish; observers receive the update on the main thread via `StateFlow`.
3. No further methods. The repository is intentionally tiny.

**Tests:**
- `app/src/test/java/com/example/movieflux/data/preferences/ThemeRepositoryTest.kt` (NEW). MockK a `UiPreferences`; assert seeded value, write-through call order (`verifyOrder { uiPreferences.setThemeMode(DARK); }` then `themeMode.value == DARK`), and that a second collector receives the new value via Turbine.

**Acceptance criterion:** Test class passes; `ThemeRepository` is `@Singleton` and injectable with no extra Hilt module.

---

### Phase 3 — Make `MovieFluxTheme` theme-mode aware and observe in `MainActivity`

**Goal:** Plumb `ThemeMode` through the Compose root so theme changes take effect live.

**Touches:**
- `app/src/main/java/com/example/movieflux/ui/theme/Theme.kt` (EDIT)
- `app/src/main/java/com/example/movieflux/MainActivity.kt` (EDIT)

**Steps:**
1. In `Theme.kt`, change the signature of `MovieFluxTheme` to `fun MovieFluxTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit)`. Resolve internally: `val darkTheme = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }`. Keep the rest of the function unchanged. Reason: callers stop having to know `isSystemInDarkTheme()`; previews using the default `SYSTEM` value still behave as today.
2. In `MainActivity`, add `@Inject lateinit var themeRepository: ThemeRepository` alongside the existing injected fields. Reason: Hilt provides the singleton automatically.
3. Inside `setContent { ... }`, before the call to `MovieFluxTheme(...)`, add `val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle()`. Pass `themeMode = themeMode` to `MovieFluxTheme(...)`. Reason: `collectAsStateWithLifecycle` respects the activity's lifecycle and triggers recomposition on every write.
4. Add the `androidx.lifecycle:lifecycle-runtime-compose` dependency only if `collectAsStateWithLifecycle` is not already available; check `libs.versions.toml` first. The existing `lifecycle-runtime-ktx = "2.10.0"` does NOT include the Compose extension — add `androidx-lifecycle-runtime-compose` library alias pointing to `androidx.lifecycle:lifecycle-runtime-compose:2.10.0` and `implementation(libs.androidx.lifecycle.runtime.compose)` in `app/build.gradle.kts`. Reason: the lifecycle-aware collector is the recommended modern API.

**Tests:**
- Manual: toggle theme on Profile, verify the whole app re-themes within one frame without restart.
- No unit test — Theme composables are exercised by `LoginScreenTest` indirectly; a focused composable test is deferred to the UI test phase of a future plan.

**Acceptance criterion:** `gradlew clean assembleDebug` passes; relaunching the app picks up the persisted theme; changing the theme on Profile updates the entire app live.

---

### Phase 4 — Profile screen UI: the selector

**Goal:** Render a tri-state segmented control on Profile, wired to `ProfileViewModel.setThemeMode(...)`.

**Touches:**
- `app/src/main/java/com/example/movieflux/view/profile/ProfileUiState.kt` (EDIT — add `themeMode: ThemeMode = ThemeMode.SYSTEM`)
- `app/src/main/java/com/example/movieflux/view/profile/ProfileViewModel.kt` (EDIT)
- `app/src/main/java/com/example/movieflux/view/profile/ProfileScreen.kt` (EDIT)
- `app/src/main/java/com/example/movieflux/view/profile/ThemeSelector.kt` (NEW — local composable; not in `view/components` because it has no reuse outside Profile today)
- `app/src/main/res/values/strings.xml` (EDIT — three new labels + section header)

**Steps:**
1. In `ProfileUiState`, add `val themeMode: ThemeMode = ThemeMode.SYSTEM`. Reason: keeps the state holder a single immutable snapshot the screen renders.
2. In `ProfileViewModel`, inject `private val themeRepository: ThemeRepository`. In `init { ... }`, after the existing `_uiState.update`, launch a `viewModelScope.launch { themeRepository.themeMode.collect { mode -> _uiState.update { it.copy(themeMode = mode) } } }`. Reason: keeps `ProfileUiState.themeMode` mirrored to the repository so the segmented control always reflects the current state even if changed elsewhere.
3. Add `fun setThemeMode(mode: ThemeMode)`: `tracker.trackEvent("theme_changed", mapOf("mode" to mode.name)); themeRepository.setThemeMode(mode)`. The local `_uiState` update happens automatically via the collector from step 2. Reason: single source of truth lives in `ThemeRepository`; ViewModel just nudges it.
4. Create `ThemeSelector(themeMode: ThemeMode, onModeChange: (ThemeMode) -> Unit)` composable using `SingleChoiceSegmentedButtonRow` with three `SegmentedButton` children. Each button reads its label from `stringResource(R.string.theme_system / theme_light / theme_dark)`. The button's `selected` parameter compares to `themeMode`; `onClick` calls `onModeChange(...)`. Reason: M3 segmented buttons are the canonical pattern for mutually-exclusive small option sets.
5. In `ProfileScreen`, add a section header `Text(stringResource(R.string.profile_theme_section), style = MaterialTheme.typography.titleMedium)` below the existing biometric `SettingsSwitchRow`, followed by `ThemeSelector(themeMode = state.themeMode, onModeChange = viewModel::setThemeMode)`. Reason: keeps the new control inside the existing visual rhythm of the screen.
6. Add `strings.xml` entries: `theme_system = "Sistema"`, `theme_light = "Claro"`, `theme_dark = "Escuro"`, `profile_theme_section = "Tema"`. Reason: PT-BR matches the existing README and the language unification done for `LoginScreen`.

**Tests:**
- `app/src/test/java/com/example/movieflux/view/profile/ProfileViewModelTest.kt` (EDIT).
- New cases: `init_observes_themeRepository_and_seeds_state` (Turbine on `uiState`, emit `DARK` from a `MutableStateFlow` provided to the mock repo, assert state mirrors); `setThemeMode_delegates_to_repository_and_fires_analytics` (verify `themeRepository.setThemeMode(LIGHT)` is called and `tracker.trackEvent("theme_changed", mapOf("mode" to "LIGHT"))` is fired).
- Update existing test setup to mock `ThemeRepository` with `every { themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)` and `every { setThemeMode(any()) } just Runs`.

**Acceptance criterion:** New ProfileViewModel tests pass; existing 7 cases continue to pass; the Profile screen renders the three-segment control; tapping a segment immediately re-themes the app and persists across kill/relaunch.

---

## Acceptance Checklist

### Build
- [ ] `gradlew clean assembleDebug` passes.
- [ ] `gradlew testDebugUnitTest` passes with all new tests included.
- [ ] No new third-party dependency added beyond `androidx-lifecycle-runtime-compose` (and only if `collectAsStateWithLifecycle` is not already on the classpath).

### Data
- [ ] `ThemeMode` enum has exactly three constants: `SYSTEM`, `LIGHT`, `DARK`.
- [ ] `UiPreferences.getThemeMode()` returns `SYSTEM` on first launch.
- [ ] `ThemeRepository.themeMode` emits a new value within one frame of `setThemeMode(...)` being called.
- [ ] A corrupt or unknown stored string falls back to `SYSTEM` without throwing.

### UI (manual)
- [ ] Profile shows three segmented buttons labelled **Sistema**, **Claro**, **Escuro** below the biometric toggle.
- [ ] Selecting **Claro** with the system in dark mode immediately switches the whole app to the light scheme.
- [ ] Selecting **Escuro** with the system in light mode immediately switches the whole app to the dark scheme.
- [ ] Selecting **Sistema** restores the colour scheme to whatever the device-level dark-mode setting is.
- [ ] Force-stop the app while **Escuro** is selected → relaunch: app comes up in dark.
- [ ] Logout (`AuthPreferences.clear()`) does NOT reset the theme — `ui_prefs` is a separate file from `auth_prefs`.

### Tests
- [ ] `UiPreferencesThemeTest`: default, round-trip, corrupt-value fallback, key independence from view mode.
- [ ] `ThemeRepositoryTest`: seeded value, write-through emits new value, second collector receives the update.
- [ ] `ProfileViewModelTest`: state mirrors `themeRepository.themeMode`; `setThemeMode` delegates + fires analytics; all 7 existing cases still pass.

### Documentation
- [ ] If `IMPLEMENTATION_view-mode-persistence.md` has also landed, its README "Qualidade de Código"-adjacent section gets an extra paragraph noting that the same `ui_prefs` file now stores both view-mode and theme.
- [ ] Otherwise, the README "Decisões fora do escopo" section gains a one-line note that theme preference is persisted in plain `SharedPreferences` (non-sensitive UI state).

## Out of Scope / Future Work

- **Dynamic colour (Material You / `dynamicColorScheme`).** Deferred — adds an Android 12+ branch and pulls user-wallpaper colours that may clash with the TealGreen brand identity. Worth revisiting if product wants a "Use system colours" extension on top of the existing three options.
- **AMOLED-black variant of Dark.** Deferred — needs an additional `darkColorScheme` definition and a fourth option on the selector. Add only if user feedback requests it.
- **Per-screen theme overrides.** Out of scope — the selector applies to the whole app; allowing different screens to opt into a different scheme is a design problem, not an implementation one.
- **Sync theme across devices.** Out of scope — would require a remote profile service that does not exist in this project.
- **System-level edge-to-edge / status-bar colour matching.** Deferred — `MainActivity` does not currently call `enableEdgeToEdge()`; aligning system bars with the chosen theme is a separate small phase that can land independently.
