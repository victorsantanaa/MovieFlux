# MovieFlux — Implementation Plan: View Mode Persistence (Home & Favorites)

## Context & Goal

The Home and Favorites screens both let the user toggle between `ViewMode.GRID` and `ViewMode.LIST` via `ViewModeToggle`. Today the selection is held in a local `MutableStateFlow(ViewMode.GRID)` inside each ViewModel (`HomeViewModel.kt:51`, `FavoritesViewModel.kt:26`) and is lost the moment the process dies. The feature persists each screen's choice across app sessions so the user re-enters with the layout they last picked.

This is a UX-polish item; it does not touch any challenge requirement listed in `CHALLENGE_VALIDATION.md`.

## Scope

**In scope:**
- Home screen remembers its `ViewMode` independently across cold and warm app restarts.
- Favorites screen remembers its `ViewMode` independently across cold and warm app restarts.
- The two screens persist independently — toggling Home does not change Favorites and vice versa.
- First-run default remains `ViewMode.GRID` for both screens.
- The analytics event `view_mode_changed` (already fired by both ViewModels) continues to fire on every toggle.

**Dependencies / preconditions:**
- `ViewMode` enum is already defined at `app/src/main/java/com/example/movieflux/view/components/ViewMode.kt`. No changes.
- `AuthPreferences` (EncryptedSharedPreferences) at `data/preferences/AuthPreferences.kt` is the existing precedent for preference storage. The new class follows the same shape but uses plain `SharedPreferences` because view mode is not sensitive.

## Architecture Decisions

- **Decision:** introduce a new `UiPreferences` Hilt-singleton class backed by plain `SharedPreferences` (file name `ui_prefs`), with one string key per screen (`view_mode_home`, `view_mode_favorites`). **Reason:** view mode is non-sensitive UI state; the encryption cost of `EncryptedSharedPreferences` is unjustified, and `SharedPreferences` adds zero new dependencies. **Alternative considered:** `androidx.datastore:datastore-preferences` — rejected for this feature alone because it would add a new library (~150 KB transitive) for two boolean-equivalent values. If the project later grows broader UI-state persistence, migrating `UiPreferences` to DataStore is a one-class refactor.

- **Decision:** expose synchronous `getHomeViewMode()` / `getFavoritesViewMode()` reads plus synchronous `setHomeViewMode(...)` / `setFavoritesViewMode(...)` writes. Do NOT expose a `Flow<ViewMode>`. **Reason:** the ViewModels already hold `MutableStateFlow<ViewMode>` as the UI source of truth; persistence is a side-effect on write and a one-shot read on init. A Flow API would add `callbackFlow` boilerplate around `OnSharedPreferenceChangeListener` for no gain — there is no cross-process or cross-screen sync requirement. **Alternative considered:** Flow-based API — rejected as over-engineering for the data shape.

- **Decision:** keep `_viewMode = MutableStateFlow(...)` inside each ViewModel as the in-memory state, but seed it from `UiPreferences` during construction and write through on every `setViewMode(...)` call. **Reason:** preserves the existing `StateFlow<UiState>` shape consumed by `HomeScreen` and `FavoritesScreen` (no Compose-side changes), while making persistence a write-through concern owned by the ViewModel — not leaked into the UI layer.

- **Decision:** persist the enum as its `name` string (`"GRID"` / `"LIST"`), parse back via `ViewMode.valueOf(...)` inside a `runCatching` so an unknown stored value falls back to `ViewMode.GRID`. **Reason:** human-readable in dev tools; tolerates future enum additions without a migration; rename of an enum constant is the only breaking change and is easy to spot.

## Phases

### Phase 1 — Introduce `UiPreferences`

**Goal:** Provide a typed wrapper around a plain `SharedPreferences` file dedicated to UI-state persistence.

**Touches:**
- `app/src/main/java/com/example/movieflux/data/preferences/UiPreferences.kt` (NEW)

**Steps:**
1. Create `UiPreferences` as a `@Singleton class` with `@Inject constructor(@ApplicationContext context: Context)`. Hold a `SharedPreferences` reference acquired via `context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)`. Reason: matches the structure of `AuthPreferences` so Hilt wires it without a new module.
2. Declare two `private const val` keys inside a `companion object`: `KEY_VIEW_MODE_HOME = "view_mode_home"` and `KEY_VIEW_MODE_FAVORITES = "view_mode_favorites"`. Reason: per-screen keys keep the two screens independent without modelling a screen enum.
3. Add private helpers `readViewMode(key: String): ViewMode` returning `runCatching { ViewMode.valueOf(prefs.getString(key, null) ?: return@runCatching ViewMode.GRID) }.getOrDefault(ViewMode.GRID)` and `writeViewMode(key: String, mode: ViewMode)` calling `prefs.edit().putString(key, mode.name).apply()`. Reason: centralises the enum string round-trip in one place.
4. Expose four thin public functions: `getHomeViewMode()`, `setHomeViewMode(mode: ViewMode)`, `getFavoritesViewMode()`, `setFavoritesViewMode(mode: ViewMode)`. Each delegates to the helpers above. Reason: ViewModels stay readable; no caller touches the key strings directly.

**Tests:**
- `app/src/test/java/com/example/movieflux/data/preferences/UiPreferencesTest.kt` (NEW). Use Robolectric (`@RunWith(RobolectricTestRunner::class)`) to obtain a real `Context`; otherwise inject a fake `SharedPreferences` by extracting the `SharedPreferences` parameter via a secondary constructor used only by tests.
- Cases: `default returns GRID for both screens when nothing stored`; `setHomeViewMode(LIST) then getHomeViewMode() returns LIST`; `setFavoritesViewMode does NOT affect getHomeViewMode and vice versa`; `corrupt stored value falls back to GRID without throwing`.

**Acceptance criterion:** `gradlew testDebugUnitTest --tests "com.example.movieflux.data.preferences.UiPreferencesTest"` passes; `UiPreferences` is `@Singleton`-scoped and injectable into any class without a new Hilt module.

---

### Phase 2 — Wire persistence into `HomeViewModel`

**Goal:** Seed `_viewMode` from `UiPreferences` at construction and write through on toggle.

**Touches:**
- `app/src/main/java/com/example/movieflux/view/home/HomeViewModel.kt` (EDIT)

**Steps:**
1. Add `private val uiPreferences: UiPreferences` to the `@Inject constructor(...)` parameter list. Reason: Hilt will resolve the new singleton automatically once Phase 1 lands.
2. Replace `private val _viewMode = MutableStateFlow(ViewMode.GRID)` (line 51) with `private val _viewMode = MutableStateFlow(uiPreferences.getHomeViewMode())`. Reason: the seeded value is the source of truth on cold start; the rest of the `combine` chain stays intact.
3. In `setViewMode(mode: ViewMode)` (line 146), insert `uiPreferences.setHomeViewMode(mode)` immediately before `_viewMode.value = mode`. Keep the existing `tracker.trackEvent(...)` call in place. Reason: write-through ensures persistence survives even if the process is killed before the next read.

**Tests:**
- `app/src/test/java/com/example/movieflux/view/home/HomeViewModelTest.kt` (EDIT). Add a MockK `mockk<UiPreferences>(relaxed = true)` to the existing test setup; default `every { getHomeViewMode() } returns ViewMode.GRID`.
- New case `viewMode_seeded_from_preferences`: stub `every { getHomeViewMode() } returns ViewMode.LIST`, instantiate `HomeViewModel`, assert `uiState.first()` of type `Success` has `viewMode == ViewMode.LIST`.
- New case `setViewMode_persists_through_preferences`: call `vm.setViewMode(ViewMode.LIST)`, then `verify(exactly = 1) { uiPreferences.setHomeViewMode(ViewMode.LIST) }`.

**Acceptance criterion:** Both new tests pass; existing `HomeViewModelTest` cases (9 today) continue to pass with the relaxed mock; manual smoke: toggle Home to LIST, force-stop the app, relaunch — Home opens in LIST.

---

### Phase 3 — Wire persistence into `FavoritesViewModel`

**Goal:** Same write-through pattern for Favorites, independent from Home.

**Touches:**
- `app/src/main/java/com/example/movieflux/view/favorites/FavoritesViewModel.kt` (EDIT)

**Steps:**
1. Add `private val uiPreferences: UiPreferences` to the `@Inject constructor(...)` parameter list. Reason: same singleton instance Home uses.
2. Replace `private val _viewMode = MutableStateFlow(ViewMode.GRID)` (line 26) with `private val _viewMode = MutableStateFlow(uiPreferences.getFavoritesViewMode())`. Reason: seeds from the Favorites-specific key so Home's choice does not leak in.
3. In `setViewMode(mode: ViewMode)` (line 49), insert `uiPreferences.setFavoritesViewMode(mode)` before `_viewMode.value = mode`. Keep the analytics event. Reason: write-through on the Favorites key only.

**Tests:**
- `app/src/test/java/com/example/movieflux/view/favorites/FavoritesViewModelTest.kt` (EDIT). Add `mockk<UiPreferences>(relaxed = true)` with `every { getFavoritesViewMode() } returns ViewMode.GRID` default.
- New case `viewMode_seeded_from_preferences_favorites`: stub `getFavoritesViewMode() returns ViewMode.LIST`, assert initial `Success.viewMode == LIST`.
- New case `setViewMode_persists_to_favorites_key_only`: call `vm.setViewMode(ViewMode.LIST)`; `verify(exactly = 1) { uiPreferences.setFavoritesViewMode(ViewMode.LIST) }`; `verify(exactly = 0) { uiPreferences.setHomeViewMode(any()) }`.

**Acceptance criterion:** Both new tests pass; existing 4 `FavoritesViewModelTest` cases still pass; manual smoke: toggle Favorites to LIST while Home stays GRID, force-stop, relaunch — each tab re-opens in its previous mode independently.

---

## Acceptance Checklist

### Build
- [ ] `gradlew testDebugUnitTest` passes with no regressions across all ViewModel tests.
- [ ] `gradlew lint` reports no new warnings related to `UiPreferences`.
- [ ] No new third-party dependency added (DataStore explicitly avoided).

### Data
- [ ] `UiPreferences` is `@Singleton`-scoped and injectable with no extra Hilt module.
- [ ] `getHomeViewMode()` / `getFavoritesViewMode()` return `ViewMode.GRID` on first run.
- [ ] A corrupt or unknown stored string falls back to `ViewMode.GRID` without throwing.

### UI (manual)
- [ ] Toggle Home to LIST → force-stop → relaunch: Home opens in LIST.
- [ ] Toggle Favorites to LIST while Home is GRID → force-stop → relaunch: Home stays GRID, Favorites opens in LIST.
- [ ] Toggle Home back to GRID → relaunch: Home opens in GRID.
- [ ] Logout (`AuthPreferences.clear()`) does NOT wipe the saved view modes — `ui_prefs` is a separate file.

### Tests
- [ ] `UiPreferencesTest`: default, round-trip per screen, screen independence, corrupt-value fallback.
- [ ] `HomeViewModelTest`: seeding from preferences, write-through on toggle, all existing cases still pass.
- [ ] `FavoritesViewModelTest`: seeding from preferences, write-through only on Favorites key, all existing cases still pass.

## Out of Scope / Future Work

- **DataStore migration.** Deferred — only justified once the project persists more than 2–3 small UI flags. Migration path is a one-class swap of `UiPreferences` internals; no caller changes.
- **Logout-time wipe of view mode.** Deferred — current decision is that layout preference is not auth-scoped; if product later wants logout to reset UI, add a `UiPreferences.clear()` call inside the existing logout flow.
- **Sync across devices.** Out of scope — would require a remote profile service that does not exist in this project.
- **Persisting other UI toggles** (e.g., sort order, search history). Worth revisiting once a second persisted toggle appears; at that point introduce a per-screen enum and collapse the four `UiPreferences` accessors into a generic `getViewMode(screen)` / `setViewMode(screen, mode)` pair.
