# MovieFlux — 9-Phase Implementation Plan

## Current Baseline

The codebase has Clean Architecture scaffolding (domain models, repository interface, Room DAO, Retrofit interface, navigation routes, theme) but **no wiring**: no DI, no Room `@Database`, no Retrofit client, no ViewModels, and all screens are stubs.

Navigation today is a single flat `NavHost`: `Login → Home → (Details | Favorites)`, with logout living inside Home as a button. This plan restructures navigation into a **nested NavHost** (Auth graph + Main graph) with a **bottom tab bar** in the Main graph hosting three tabs: **Home**, **Favorites**, **Profile**. Logout moves out of Home and into a new **Profile** screen with a confirmation dialog.

---

## Navigation Architecture (target state)

```
RootNavHost  (MainActivity)
├── AuthGraph
│   └── Login
└── MainGraph                       ← MainScaffold (BottomBar host)
    ├── Home          (tab #1)
    ├── Favorites     (tab #2)
    ├── Profile       (tab #3)
    └── Details/{id}  (full-screen, BottomBar hidden via route check)
```

Conventions:
- Tab clicks use `popUpTo(graph.startDestination) { saveState = true }`, `launchSingleTop = true`, `restoreState = true` so tab back stacks survive switching.
- BottomBar is hidden when current route matches `"details/{movieId}"`.
- Login → Main and Logout → Auth both use `popUpTo(0) { inclusive = true }` to wipe history.

---

## Phase 1 — Infrastructure: DI, Database, Network

**Goal:** The app compiles with all dependencies wired. No screen logic yet.

### 1.1 — Add dependencies to `libs.versions.toml` + `app/build.gradle.kts`

| Library | Version |
|---|---|
| Hilt | 2.51.1 |
| Hilt Navigation Compose | 1.2.0 |
| KSP (replaces kapt) | 2.2.10-1.0.29 |
| Room KSP compiler | 2.6.1 |
| Coil Compose | 2.7.0 |
| Timber | 5.0.1 |
| AndroidX Security Crypto (EncryptedSharedPreferences) | 1.1.0-alpha06 |
| AndroidX Biometric | 1.2.0-alpha05 |
| Material Icons Extended | (BOM-managed) |
| Kotlinx Coroutines Test | 1.8.1 |
| MockK | 1.13.12 |
| Turbine (Flow testing) | 1.1.0 |

### 1.2 — Apply KSP + Hilt plugins
Add to root and `:app` `build.gradle.kts`. Remove any `kapt` references.

### 1.3 — `MovieFluxApp.kt` — Application class
```kotlin
@HiltAndroidApp
class MovieFluxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
```
Register `android:name=".MovieFluxApp"` in `AndroidManifest.xml`.

### 1.4 — `BuildConfig` for the TMDB API key
- Add `TMDB_API_KEY=...` to `local.properties` (gitignored).
- In `app/build.gradle.kts`, read the property and emit a `buildConfigField("String", "TMDB_API_KEY", "\"$key\"")`.
- Enable `buildFeatures.buildConfig = true`.

### 1.5 — `di/NetworkModule.kt`
- `ApiKeyInterceptor` appends `?api_key=<BuildConfig.TMDB_API_KEY>` to every request URL.
- `HttpLoggingInterceptor` at `BODY` level on debug, `BASIC` on release.
- `OkHttpClient` with both interceptors, 15s timeouts.
- `Retrofit` with base URL `https://api.themoviedb.org/3/` + Moshi/Kotlinx-serialization converter (pick one — Moshi is conventional with Retrofit).
- Provide `RemoteDataSource`.

### 1.6 — `di/DatabaseModule.kt`
- Build `MovieDatabase` (`@Database(entities = [MovieEntity::class], version = 1)`).
- Provide `MovieDao`.

### 1.7 — `di/RepositoryModule.kt`
- `@Binds` `MovieRepositoryImpl` → `MovieRepository`.

### 1.8 — Extend `RemoteDataSource`
- Add `@GET("movie/{movie_id}") suspend fun getMovieDetail(@Path("movie_id") id: Int): MovieDetailDto`.
- `MovieDetailDto` fields: `id`, `title`, `overview`, `poster_path`, `vote_average`, `genres: List<GenreDto>` (detail endpoint returns full genre objects, no separate `/genre/movie/list` call needed for Details).

### 1.9 — `MovieDatabase.kt` body
```kotlin
@Database(entities = [MovieEntity::class], version = 1)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
}
```

### 1.10 — Smoke-compile gate
Run `./gradlew assembleDebug`. Must succeed before any feature work.

---

## Phase 2 — Authentication & Security

**Goal:** Working login (mocked), biometric infrastructure ready, session persistence. (Biometric **UI toggle** moves to the Profile screen in Phase 4 — Phase 2 only builds the plumbing.)

### 2.1 — `data/preferences/AuthPreferences.kt`
Wraps `EncryptedSharedPreferences` (AES256_SIV key encryption, AES256_GCM value encryption). Exposes:
- `isLoggedIn: Boolean` (read/write)
- `biometricEnabled: Boolean` (read/write)
- `clear()` — wipes all keys (used by logout)

### 2.2 — `data/biometric/BiometricHelper.kt`
Wraps `BiometricManager` and `BiometricPrompt`:
- `canAuthenticate(context): BiometricAvailability` — sealed class: `Available`, `NoHardware`, `NoneEnrolled`, `Unavailable`.
- `authenticate(activity, onSuccess, onError)` — builds and shows the prompt.

### 2.3 — `di/PreferencesModule.kt`
Provides `AuthPreferences` and `BiometricHelper` as singletons.

### 2.4 — `view/login/LoginUiState.kt`
```kotlin
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
```

### 2.5 — `view/login/LoginViewModel.kt`
- Injects `AuthPreferences`.
- `login(user, pass)`: validates `user == "admin" && pass == "1234"` (mocked); on success sets `isLoggedIn = true` and emits `Success`. On failure emits `Error("Invalid credentials")`.

### 2.6 — Color mapping (reuse existing tokens, no new colors)
Reference: `docs/design/Login.png`. The design uses three colors; map each to the closest existing token in `ui/theme/Color.kt`:

| Design element | Design hex (approx.) | Reused token | Notes |
|---|---|---|---|
| Page background | `#0E2622` (dark teal) | `BackgroundDark` (`#121212`) | Closest existing dark; slightly less teal-tinted than the mock — accepted trade-off to avoid adding tokens |
| Field fill | (same as bg in mock) | `BackgroundDark` | Fields blend into the background; only the border defines them |
| Field border | mint teal | `TealGreenLight` (`#4DB6AC`) | Used at full opacity |
| "Done" button background | bright mint | `TealGreenLight` | Maps to `MaterialTheme.colorScheme.primary` in dark theme |
| Button text | dark | `Color.Black` | Already `DarkColors.onPrimary` |
| Logo — "Movie" wordmark (lighter half) | light mint | `TealGreenLight.copy(alpha = 0.7f)` | |
| Logo — "Flux" wordmark (brighter half) | bright mint | `TealGreenLight` | |
| Logo — "M" mark fills | mint gradient | `TealGreenLight` (flat) + `TealGreen` (`#00897B`) for the darker right half | Flatten gradient to two solid fills using existing tokens |
| Logo — film-strip dots | near-black | `BackgroundDark` | |

No edits to `Color.kt` or `Theme.kt` are required — the existing `DarkColors` already provides `primary = TealGreenLight`, `onPrimary = Color.Black`, `background = BackgroundDark`. Login is rendered with `MovieFluxTheme(darkTheme = true)` regardless of system setting (the design is dark-only); other screens may continue to follow the system theme.

### 2.7 — Logo asset + `MovieFluxLogo` composable
- Export the "M + film-strip" mark from Figma as **SVG**. In Android Studio: *File → New → Vector Asset → Local file* → save as `res/drawable/ic_logo_movieflux.xml`. (If the SVG fails to import due to gradients, use Inkscape or `https://shapeshifter.design/` to flatten gradients to solid fills first, or keep the gradient and switch to a PNG in `res/drawable-xxxhdpi/`.)
- `view/components/MovieFluxLogo.kt`:
  ```kotlin
  @Composable
  fun MovieFluxLogo(modifier: Modifier = Modifier, size: Dp = 120.dp) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
          Icon(
              painter = painterResource(R.drawable.ic_logo_movieflux),
              contentDescription = "MovieFlux",
              tint = Color.Unspecified,        // preserve SVG colors baked into the vector
              modifier = Modifier.size(size)
          )
          Spacer(Modifier.height(8.dp))
          Row {
              Text(
                  text = "Movie",
                  style = MaterialTheme.typography.headlineSmall,
                  color = TealGreenLight.copy(alpha = 0.7f),
                  fontWeight = FontWeight.Light
              )
              Text(
                  text = "Flux",
                  style = MaterialTheme.typography.headlineSmall,
                  color = TealGreenLight,
                  fontWeight = FontWeight.Bold
              )
          }
      }
  }
  ```
  When flattening the SVG, use `TealGreenLight` (`#4DB6AC`) for the lighter half of the "M" and `TealGreen` (`#00897B`) for the darker right half + film strip outline; use `BackgroundDark` (`#121212`) for the film-strip dots.

### 2.8 — Replace `LoginScreen` stub (match `docs/design/Login.png`)

No `TopAppBar`, no back arrow — Login is the start destination of `AuthGraph` and has nowhere to navigate back to.

Layout, top to bottom inside a `Box(Modifier.fillMaxSize().background(BackgroundDark))`:

| # | Element | Spec |
|---|---|---|
| 1 | Top spacer | `weight(1f)` — pushes content into upper-middle third |
| 2 | `MovieFluxLogo(size = 120.dp)` | Centered horizontally |
| 3 | Spacer | 48.dp |
| 4 | `OutlinedTextField` — credential | `value = state.username`, placeholder `"Credencial"`, `leadingIcon = Icons.Default.Person`, `singleLine = true`, `keyboardOptions = KeyboardOptions(imeAction = Next)`. Border color `TealGreenLight`, fill `BackgroundDark` (same as page so the field is bg-transparent), rounded corners 12.dp. Horizontal padding 24.dp from screen edge. |
| 5 | Spacer | 12.dp |
| 6 | `OutlinedTextField` — password | `value = state.password`, placeholder `"Senha"`, `leadingIcon = Icons.Default.Lock` *(design shows a magnifier — treated as a Figma slip; using lock for password convention. Confirm with stakeholder.)*, `visualTransformation = PasswordVisualTransformation()`, trailing show/hide eye toggle, `keyboardOptions = KeyboardOptions(keyboardType = Password, imeAction = Done)`, `keyboardActions = KeyboardActions(onDone = { vm.login(...) })`. Same border/fill/padding as field 4. |
| 7 | Spacer | 24.dp |
| 8 | Error text | Shown only when `uiState is Error`. `MaterialTheme.colorScheme.error` (resolves to `ErrorRed`), `bodySmall`, horizontal padding 24.dp. |
| 9 | `PrimaryButton("Done")` | Pill shape (`RoundedCornerShape(50)`), uses theme `colorScheme.primary` (= `TealGreenLight`) for container and `colorScheme.onPrimary` (= `Color.Black`) for text. Height 52.dp, `fillMaxWidth()` minus 24.dp horizontal padding, font weight Bold. Disabled when fields are blank OR `uiState is Loading`. *Label text "Done" matches the mock; consider "Entrar" or "Sign in" if you want a single language across the form.* |
| 10 | Loading | When `uiState is Loading`, replace button label with a 20.dp `CircularProgressIndicator(color = Color.Black)` (matches `onPrimary`). |
| 11 | Bottom spacer | `weight(2f)` — leaves the lower half empty to match the mock |

Behavior:
- On `Success`, the screen calls the `onLoginSuccess` lambda passed in from the nav graph (Phase 3.5), which navigates to `MainGraph` and clears the back stack with `popUpTo(0) { inclusive = true }`.
- Status bar: set transparent + light icons via `WindowCompat.getInsetsController(window, ...).isAppearanceLightStatusBars = false` in `MainActivity` so the dark background flows under the status bar.

### 2.9 — Open design questions (track here, decide before implementation)

1. **Field language**: keep `"Credencial"` / `"Senha"` (PT), or unify to English (`"Username"` / `"Password"`)? Affects `strings.xml` keys.
2. **Button label**: `"Done"` (mock) vs `"Entrar"` (PT) vs `"Sign in"` (EN)?
3. **Password leading icon**: mock shows magnifier — likely a slip. Default to `Icons.Default.Lock` unless explicitly confirmed.
4. **Localization scope**: is the rest of the app PT-BR, EN, or both via `values-pt/strings.xml`?

### 2.7 — Biometric gate in `MainActivity`
- In `onCreate`, read `AuthPreferences`:
  - If `isLoggedIn && biometricEnabled && BiometricHelper.canAuthenticate() == Available` → show prompt before composing `MainGraph`.
  - On success → render `MainGraph` with start destination `Home`.
  - On error/cancel → render `AuthGraph` (force re-login).
- If `isLoggedIn` and biometric not required → start at `MainGraph`.
- If `!isLoggedIn` → start at `AuthGraph`.

### 2.8 — Compile + manual smoke test
Run on emulator, log in with `admin / 1234`, confirm `Success`.

---

## Phase 3 — Navigation Restructure: Nested Graphs + Bottom Tab Bar

**Goal:** Replace the flat NavHost with a nested Auth + Main structure, add the bottom tab bar shell, and hook all three tabs to placeholder screens. After this phase the app boots into a tab-bar shell with stub tabs; later phases fill each tab's logic.

### 3.1 — Rewrite `navigation/Screen.kt`
```kotlin
sealed class Screen(val route: String) {
    // Graph routes
    object AuthGraph : Screen("auth_graph")
    object MainGraph : Screen("main_graph")

    // Auth destinations
    object Login : Screen("login")

    // Main tab destinations
    object Home : Screen("home")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")

    // Detail (in MainGraph, hides bottom bar)
    object Details : Screen("details/{movieId}") {
        const val ARG_MOVIE_ID = "movieId"
        fun createRoute(movieId: Int) = "details/$movieId"
    }
}
```

### 3.2 — `navigation/TopLevelTab.kt`
Enum describing the three tabs, their route, icon, and label string-res:
```kotlin
enum class TopLevelTab(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int
) {
    HOME(Screen.Home.route, Icons.Default.Home, R.string.tab_home),
    FAVORITES(Screen.Favorites.route, Icons.Default.Favorite, R.string.tab_favorites),
    PROFILE(Screen.Profile.route, Icons.Default.Person, R.string.tab_profile);
}
```
Add `tab_home`, `tab_favorites`, `tab_profile` to `strings.xml`.

### 3.3 — `view/components/BottomNavBar.kt`
- Material3 `NavigationBar` with one `NavigationBarItem` per `TopLevelTab`.
- Reads current route via `navController.currentBackStackEntryAsState()`.
- Selection determined by `currentDestination?.hierarchy?.any { it.route == tab.route }`.
- Click handler navigates with `popUpTo(graph.findStartDestination().id) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`.

### 3.4 — `view/main/MainScaffold.kt`
- `Scaffold` whose `bottomBar` is `BottomNavBar(innerNavController)`, **conditionally rendered**.
- Visibility rule: hide bottom bar when current route matches `Screen.Details.route` (i.e. starts with `details/`). Use `currentBackStackEntryAsState()` + a derived `shouldShowBottomBar` boolean.
- Wraps an inner `NavHost(innerNavController, startDestination = Home.route)`.

### 3.5 — Rewrite `navigation/AppNavHost.kt`
Outer NavHost with two `navigation { }` sub-graphs:
```kotlin
NavHost(rootNavController, startDestination = Screen.AuthGraph.route) {
    navigation(route = Screen.AuthGraph.route, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                rootNavController.navigate(Screen.MainGraph.route) {
                    popUpTo(0) { inclusive = true }
                }
            })
        }
    }
    navigation(route = Screen.MainGraph.route, startDestination = Screen.Home.route) {
        composable(Screen.MainGraph.route) {
            MainScaffold(
                onLogout = {
                    rootNavController.navigate(Screen.AuthGraph.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
```
**Note:** The Main graph's destinations live inside `MainScaffold`'s inner NavHost, not the outer one — only `MainGraph` itself is a destination of the outer NavHost. This is the cleanest way to scope the BottomBar to the main flow.

### 3.6 — Inner NavHost inside `MainScaffold`
Routes:
- `Home.route` → `HomeScreen(onMovieClick = { id -> innerNav.navigate(Details.createRoute(id)) })`
- `Favorites.route` → `FavoritesScreen(onMovieClick = ...)`
- `Profile.route` → `ProfileScreen(onLogout = onLogout)` (the `onLogout` lambda from the parent)
- `Details.route` → `DetailsScreen(movieId = ..., onBackClick = { innerNav.popBackStack() })`

### 3.7 — Strip the logout button + favorites button from `HomeScreen`
- Remove `onFavoritesClick` and `onLogoutClick` parameters from `HomeScreen`.
- Home only knows about movie navigation now (`onMovieClick`).

### 3.8 — `MainActivity` updates
- `MainActivity` now owns just one `NavHostController` — `rootNavController` — and renders `AppNavHost(rootNavController, startDestinationOverride = ...)`.
- The biometric-gate logic from 2.7 picks the start destination (`AuthGraph` vs `MainGraph`).

### 3.9 — Stub `ProfileScreen.kt` (filled out in Phase 4)
```kotlin
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    Text("Profile — coming in Phase 4")
}
```

### 3.10 — Compile + smoke test
Boot the app, log in, verify:
- Bottom bar appears with three tabs.
- Tabs switch correctly and preserve scroll state when switching back.
- Tapping a movie from Home (when wired in Phase 5) opens Details with the bottom bar hidden.
- Back from Details restores the tab you came from.

---

## Phase 4 — Profile Screen & Logout Flow

**Goal:** Build the full Profile screen with user info header, biometric toggle, app version, and a logout button that opens a confirmation dialog.

### 4.1 — `view/profile/ProfileUiState.kt`
```kotlin
data class ProfileUiState(
    val username: String = "admin",
    val email: String = "admin@movieflux.app",
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val appVersion: String = "",
    val showLogoutDialog: Boolean = false
)
```
Single data class is fine here — Profile is non-async and doesn't need Loading/Error states.

### 4.2 — `view/profile/ProfileViewModel.kt`
Injects `AuthPreferences`, `BiometricHelper`, `@ApplicationContext context`. Exposes:
- `uiState: StateFlow<ProfileUiState>`
- `setBiometricEnabled(enabled: Boolean)` — writes to `AuthPreferences`; if user is **enabling** and `BiometricHelper.canAuthenticate() != Available`, instead emit a one-shot UI event `BiometricUnavailable(reason)` (use `Channel<UiEvent>` + `receiveAsFlow()` for events distinct from state).
- `requestLogout()` — sets `showLogoutDialog = true`.
- `dismissLogoutDialog()` — sets `showLogoutDialog = false`.
- `confirmLogout()` — calls `authPreferences.clear()`, then emits a one-shot `LogoutComplete` event so the screen can call `onLogout()`.
- App version read from `BuildConfig.VERSION_NAME` on `init`.

### 4.3 — `view/components/ProfileHeader.kt`
- Circular avatar placeholder (`Icons.Default.AccountCircle`, 96dp, primary tint).
- Username text — `titleLarge`.
- Email text — `bodyMedium`, lower emphasis.
- Centered, padded layout.

### 4.4 — `view/components/SettingsSwitchRow.kt`
Reusable row: leading icon + title + subtitle + trailing `Switch`. Reused by biometric toggle (and future settings).

### 4.5 — `view/components/LogoutConfirmDialog.kt`
- Material3 `AlertDialog`.
- Title: "Log out?"
- Body: "You will need to sign in again to access your favorites and account."
- Confirm button: "Log out" (color = `MaterialTheme.colorScheme.error`).
- Dismiss button: "Cancel".

### 4.6 — `view/profile/ProfileScreen.kt` (replace stub from 3.9)
Layout from top to bottom:
1. `TopAppBar(title = "Profile")`.
2. `ProfileHeader(username, email)`.
3. Section title: "Security".
4. `SettingsSwitchRow` — "Biometric login" / "Use your fingerprint to sign in faster" / bound to `biometricEnabled`.
5. Section title: "About".
6. Static row — "Version" / `appVersion`.
7. Static row — "About MovieFlux" / one-line description.
8. Spacer (push button down).
9. `PrimaryButton("Log out")` — destructive variant (error container color); `onClick = vm::requestLogout`.
10. `LogoutConfirmDialog` rendered when `showLogoutDialog == true`.

### 4.7 — Wire one-shot events
- Collect `vm.events` in a `LaunchedEffect`.
- On `LogoutComplete` → call the `onLogout` lambda passed in from `MainScaffold` (which triggers root navigation back to `AuthGraph`).
- On `BiometricUnavailable` → show a `Snackbar` with the reason (no hardware / none enrolled / etc).

### 4.8 — Manual test matrix
- Toggle biometric on → reflects in `AuthPreferences` → kill + restart app → biometric prompt appears at launch (Phase 2.7 logic).
- Toggle biometric on with no fingerprint enrolled → snackbar appears, switch stays off.
- Tap Logout → dialog appears → tap Cancel → dialog dismisses, still logged in.
- Tap Logout → dialog appears → tap Log out → returns to Login screen, back stack cleared (pressing back exits the app, doesn't return to Profile).

---

## Phase 5 — Home Screen: Movie Grid & Pagination

**Goal:** Scrollable grid of movies loaded from TMDB with infinite scroll.

### 5.1 — `view/home/HomeUiState.kt`
```kotlin
sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val movies: List<MovieModel>,
        val isLoadingMore: Boolean,
        val viewMode: ViewMode = ViewMode.GRID
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
```

### 5.2 — `view/home/HomeViewModel.kt`
- `currentPage = 1`, `canLoadMore = true`.
- `loadMovies()` calls `GetPopularMoviesUseCase(page)`, accumulates into a `MutableStateFlow<List<MovieModel>>`.
- `loadNextPage()` triggered when last visible index reaches `lastIndex - 2`.
- Collects `repository.getFavorites()` Flow and merges `isFavorite` flags across all loaded pages so the heart icon on each card stays in sync with edits made on Favorites or Details.
- `toggleFavorite(movie: MovieModel)` → `viewModelScope.launch { repository.toggleFavorite(movie) }`. Wired to the `onToggleFavorite` lambda passed into `MovieCard` (5.3) and `MovieListItem` (5.3b).
- `viewMode: MutableStateFlow<ViewMode>` defaulting to `ViewMode.GRID`. `setViewMode(mode: ViewMode)` writes it. Exposed in `HomeUiState.Success` (extend the data class with `viewMode: ViewMode = ViewMode.GRID`).

### 5.3 — `view/components/MovieCard.kt` (matches `docs/design/Filme Item.png`)

Layout, top to bottom inside a `Column(modifier = Modifier.clickable { onClick(movie.id) })`:

| # | Element | Spec |
|---|---|---|
| 1 | Poster + heart overlay | `Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f))` containing two children: <br/>**(a) `AsyncImage`** (Coil, w342) — `Modifier.matchParentSize()`, `RoundedCornerShape(4.dp)`, `contentScale = ContentScale.Crop`. Placeholder + error painter = solid `Color.Gray` box (matches the mock's grey rectangle while loading or on error). <br/>**(b) `IconButton`** with the heart toggle — `Modifier.align(Alignment.BottomEnd).padding(4.dp).size(36.dp)`. Icon is `Icons.Filled.Favorite` when `movie.isFavorite`, `Icons.Outlined.FavoriteBorder` otherwise. `tint = MaterialTheme.colorScheme.primary` (= `TealGreenLight`). Background = `Color.Black.copy(alpha = 0.35f)` in a `CircleShape` so it stays legible against any poster. `onClick = { onToggleFavorite(movie) }`. |
| 2 | Spacer | 8.dp |
| 3 | Title | `Text(movie.title)`, `MaterialTheme.typography.titleSmall`, `fontWeight = FontWeight.SemiBold`, `color = MaterialTheme.colorScheme.onBackground`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`. Left-aligned. |
| 4 | Spacer | 2.dp |
| 5 | Rating | `Text("${(movie.voteAverage / 2).roundToInt()}/5")`, `MaterialTheme.typography.bodySmall`, `color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)`. Left-aligned. TMDB returns `vote_average` on a 0–10 scale; divide by 2 and round to match the design's 0–5 scale. |

Composable signature:
```kotlin
@Composable
fun MovieCard(
    movie: MovieModel,
    onClick: (Int) -> Unit,
    onToggleFavorite: (MovieModel) -> Unit,
    modifier: Modifier = Modifier
)
```

Interaction notes:
- The heart `IconButton` must call `stopPropagation` implicitly — Compose handles this because the inner `IconButton` consumes the click before it reaches the outer `Column.clickable`. No extra work needed.
- Tap anywhere outside the heart → opens Details.
- Tap heart → toggles favorite in place; Room Flow propagates to every other screen showing the same movie.

### 5.3a — `view/components/ViewMode.kt` + `ViewModeToggle.kt`

Both Home and Favorites support two display modes: **Grid** (the `MovieCard` from 5.3) and **List** (the `MovieListItem` from 5.3b). The toggle is an `IconButton` rendered in the `TopAppBar`'s `actions` slot (top-right) on both screens.

```kotlin
enum class ViewMode { GRID, LIST }

@Composable
fun ViewModeToggle(
    current: ViewMode,
    onToggle: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val next = if (current == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    // Show the icon of the mode the user will switch TO (common pattern)
    val (icon, label) = when (next) {
        ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList to "Switch to list view"
        ViewMode.GRID -> Icons.Default.GridView           to "Switch to grid view"
    }
    IconButton(onClick = { onToggle(next) }, modifier = modifier) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onBackground)
    }
}
```

Notes:
- `ViewMode` lives in `view/components/` (shared UI concept, not domain).
- The toggle is purely in-memory per-screen — switching tabs and coming back preserves the mode because the ViewModel survives tab switches; killing the app resets to the default (`GRID`).
- Each screen owns its own `ViewMode` state independently — Home and Favorites can be in different modes simultaneously.
- Persisting across restarts is a future enhancement (would live in `AuthPreferences` or a new `UiPreferences`); explicitly out of scope for v1.

### 5.3b — `view/components/MovieListItem.kt` (standard list row)

Used when `ViewMode == LIST`. Standard horizontal layout — thumbnail on the left, text in the middle, heart on the right.

| # | Element | Spec |
|---|---|---|
| 1 | Outer container | `Row(modifier = Modifier.fillMaxWidth().clickable { onClick(movie.id) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically)` |
| 2 | Thumbnail | `AsyncImage` (Coil, w185) — `Modifier.size(width = 72.dp, height = 108.dp)` (2:3), `RoundedCornerShape(4.dp)`, `contentScale = ContentScale.Crop`, placeholder/error = `Color.Gray`. |
| 3 | Spacer | 12.dp width |
| 4 | Text column | `Column(Modifier.weight(1f))` containing: <br/>• Title — `titleMedium`, semibold, `maxLines = 2`, ellipsis, `onBackground`. <br/>• Spacer 4.dp <br/>• Rating — `"${(voteAverage / 2).roundToInt()}/5"`, `bodySmall`, `onBackground.copy(alpha = 0.6f)`. <br/>• Spacer 4.dp <br/>• Overview snippet — `movie.overview`, `bodySmall`, `maxLines = 2`, ellipsis, `onBackground.copy(alpha = 0.7f)`. |
| 5 | Spacer | 12.dp width |
| 6 | Heart `IconButton` | Same icon logic as `MovieCard` (Filled when favorite, Outlined otherwise), `tint = MaterialTheme.colorScheme.primary`, `size = 40.dp`. No background pill needed since it sits on the screen background, not a poster. `onClick = { onToggleFavorite(movie) }`. |

A 1.dp `HorizontalDivider(color = onBackground.copy(alpha = 0.08f))` is rendered between items by the parent `LazyColumn` (`itemsIndexed` + divider) — not inside `MovieListItem` itself, so the component stays reusable.

Composable signature mirrors `MovieCard`:
```kotlin
@Composable
fun MovieListItem(
    movie: MovieModel,
    onClick: (Int) -> Unit,
    onToggleFavorite: (MovieModel) -> Unit,
    modifier: Modifier = Modifier
)
```

### 5.4 — `view/home/HomeScreen.kt` (replace stub)
- `Scaffold` with `TopAppBar(title = "Movies", actions = { ViewModeToggle(state.viewMode, vm::setViewMode) })` — toggle icon sits at top-right, switches between Grid and List mode for this screen.
- Body is a `when (state.viewMode)` branch:
  - `ViewMode.GRID` → `LazyVerticalGrid(columns = Fixed(2), contentPadding = PaddingValues(8.dp), verticalArrangement = spacedBy(12.dp), horizontalArrangement = spacedBy(8.dp))` of `MovieCard(...)` items.
  - `ViewMode.LIST` → `LazyColumn { itemsIndexed(movies) { i, m -> MovieListItem(...); if (i < movies.lastIndex) HorizontalDivider(...) } }`.
- Pre-fetch trigger via `LaunchedEffect` on the appropriate `LazyGridState`/`LazyListState` — same logic, applied to whichever list state is active.
- Bottom loading spinner while `isLoadingMore == true` — rendered as the final item in both grid and list (use `item(span = { GridItemSpan(maxLineSpan) }) { ... }` for grid).
- `@HiltViewModel` + `hiltViewModel()` at the composable call site.

### 5.5 — Loading skeleton
- While `HomeUiState.Loading`, show a 6-card shimmer grid (placeholder boxes).
- Reusable `MovieCardSkeleton` composable in `view/components/`.

---

## Phase 6 — Home Screen: Search & UI States

**Goal:** Functional search with debounce, and proper Loading / Error / Empty states.

### 6.1 — Extend `HomeViewModel`
- `searchQuery: MutableStateFlow<String>("")`.
- `.debounce(300ms).distinctUntilChanged().flatMapLatest { q -> if (q.isBlank()) popularFlow else searchFlow(q) }`.
- `isSearchMode: Boolean` flag — disables pagination during search.

### 6.2 — `view/components/SearchBar.kt`
- `TextField` with leading search icon and trailing clear icon (visible only when query non-empty).
- Hoisted state — emits query upward to ViewModel.
- Placed in the Home `TopAppBar` (or directly under it).

### 6.3 — UI state composables in `view/components/`
- `LoadingView.kt` — centered `CircularProgressIndicator`.
- `ErrorView.kt` — icon + message + `Retry` button.
- `EmptyView.kt` — illustration + "No movies found" text.

### 6.4 — Wire states in `HomeScreen`
```kotlin
when (val s = uiState) {
    is Loading -> LoadingView()
    is Error   -> ErrorView(message = s.message, onRetry = vm::loadMovies)
    is Success -> if (s.movies.isEmpty()) EmptyView() else Grid(s.movies)
}
```

---

## Phase 7 — Details Screen

**Goal:** Full movie details with genre names, share action, and favorite toggle. Renders full-screen above the bottom bar.

### 7.1 — `view/details/DetailsUiState.kt`
```kotlin
sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Success(val movie: MovieModel, val genres: List<String>) : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
}
```

### 7.2 — Extend domain
- Add `getMovieDetail(id: Int): Flow<MovieModel>` to `MovieRepository` + impl. Impl calls `/movie/{id}`, maps `MovieDetailDto`, cross-references with `getFavorites()` for `isFavorite`.
- `MovieDetailDto.genres: List<GenreDto>` is read directly — no separate `/genre/movie/list` call.

### 7.3 — `view/details/DetailsViewModel.kt`
- Reads `movieId` from `SavedStateHandle`.
- `loadDetail()` triggers on init.
- `toggleFavorite()` calls repository and optimistically updates local state.
- `share(context)` fires `Intent.ACTION_SEND` with text `"${title}\nhttps://www.themoviedb.org/movie/${id}"`.

### 7.4 — `view/details/DetailsScreen.kt` (replace stub)
- `TopAppBar` with back arrow + share icon.
- Large poster (`w780`) via `AsyncImage`.
- Title row with star rating.
- Genre chips (`AssistChip`).
- Scrollable overview text.
- Floating heart `FloatingActionButton` (filled when favorite, outlined when not).
- Loading and Error states reuse `LoadingView` / `ErrorView` from Phase 6.

### 7.5 — Confirm BottomBar visibility logic
Phase 3.4's `shouldShowBottomBar` rule must correctly hide the bar on Details. Manual test: open Details, confirm no bottom bar; press back, confirm bar reappears on Home/Favorites/Profile.

---

## Phase 8 — Favorites Screen & Cross-Screen Sync

**Goal:** Real-time favorites list, synced with Home, Details, and itself via Room's Flow guarantees.

### 8.1 — `view/favorites/FavoritesUiState.kt`
```kotlin
sealed class FavoritesUiState {
    object Loading : FavoritesUiState()
    data class Success(
        val movies: List<MovieModel>,
        val viewMode: ViewMode = ViewMode.GRID
    ) : FavoritesUiState()
}
```
No Error state — Room queries don't fail at runtime in the same way network calls do; loading-empty maps to `Success(emptyList())`.

### 8.2 — `view/favorites/FavoritesViewModel.kt`
- Collects `repository.getFavorites()` directly — Room re-emits on every insert/delete.
- `toggleFavorite(movie: MovieModel)` → `viewModelScope.launch { repository.toggleFavorite(movie) }`. Wired to the `onToggleFavorite` lambda passed into `MovieCard` and `MovieListItem`. On this screen, untoggling removes the row immediately via the Flow.
- `viewMode: MutableStateFlow<ViewMode>` defaulting to `ViewMode.GRID`. `setViewMode(mode: ViewMode)` writes it. Exposed via `FavoritesUiState.Success.viewMode`. State is independent from Home (each screen remembers its own mode).

### 8.3 — `view/favorites/FavoritesScreen.kt` (replace stub)
- `TopAppBar(title = "Favorites", actions = { ViewModeToggle(state.viewMode, vm::setViewMode) })` — same toggle icon as Home (5.4), top-right.
- Body branches on `state.viewMode`:
  - `ViewMode.GRID` → `LazyVerticalGrid(columns = Fixed(2), ...)` of `MovieCard` items — **same grid + same card** as Home (`docs/design/Filme Item.png` is shared between Home and Favorites per the design).
  - `ViewMode.LIST` → `LazyColumn` of `MovieListItem` items with dividers (same as Home list mode).
- `EmptyView` (from Phase 6) with copy `"No favorites yet — tap the heart on any movie to add it"` when list is empty (renders in place of the grid/list, but the `ViewModeToggle` stays in the TopAppBar).
- Tap card / row → navigates to Details via `onMovieClick(id)` lambda.
- Tap heart → calls `vm.toggleFavorite(movie)`; since the movie was favorited (by definition, it's on this screen), the toggle deletes it from Room and the row/card disappears on the next Flow emission.

### 8.4 — Sync verification
The sync is structural, not manual: `getFavorites()` is a `Flow<List<MovieEntity>>` from Room. Any of three writers — `HomeViewModel.toggleFavorite`, `FavoritesViewModel.toggleFavorite`, `DetailsViewModel.toggleFavorite` — write through `repository.toggleFavorite(...)`, which triggers the Flow and updates every active collector. No SharedViewModel or event bus needed. Manual test:
- On Home, tap the heart on a card → heart fills.
- Switch to Favorites tab → movie appears immediately.
- Tap the heart on the same card in Favorites → row vanishes; switch back to Home → heart is now outlined.
- Open Details for the same movie → tap heart FAB → both Home heart and Favorites grid update on next frame.

---

## Phase 9 — Observability, Performance, Tests & README

**Goal:** Production-grade observability covering render performance, frames/jank, crashes (fatal + non-fatal), key flow timings, and a CI performance regression gate. Plus unit test coverage and delivery-ready README.

**Stack chosen (from decisions on 2026-05-16):**

| Layer | Tool | Role |
|---|---|---|
| Frame/render jank | **JankStats** (Jetpack) | Per-frame attribution to {screen, viewMode, scrolling}; in-app, free |
| Cloud perf telemetry | **Firebase Performance Monitoring** | Auto screen-render + network traces, custom traces, app-start auto trace |
| CI perf regression gate | **Macrobenchmark + Baseline Profile** | Startup/scroll benchmarks on real device in CI; baseline profile for AOT compile |
| Recomposition hygiene | **Compose Compiler Stability Reports** | Build-time JSON flagging unstable params; zero runtime cost |
| Fatal crashes + ANRs | **Firebase Crashlytics** | Industry standard, free, ANR detection |
| Non-fatals + perf traces | **Sentry** | Compose screen tracking, breadcrumbs, transactions |
| Structured logging | **Timber** | Local dev + breadcrumbs for both crash SDKs |
| Sampling | **Sampled in production** | 100% in debug; 15% in release for high-volume events; 100% for errors |

### 9.1 — Logging & analytics core

**Timber + structured tags** — already planted in Phase 1.
- Tag conventions: `[NETWORK]`, `[AUTH]`, `[BIOMETRIC]`, `[DB]`, `[NAV]`, `[PROFILE]`, `[RENDER]`, `[JANK]`, `[FUNNEL]`.
- Custom `Timber.Tree` per build flavor: `DebugTree` in debug, `ReleaseTree` in release that forwards `WARN`/`ERROR` to Crashlytics + Sentry breadcrumbs and drops `VERBOSE`/`DEBUG`.

**`analytics/AnalyticsTracker.kt`** — interface (unchanged):
```kotlin
interface AnalyticsTracker {
    fun trackScreen(name: String)
    fun trackEvent(name: String, params: Map<String, Any> = emptyMap())
    fun trackError(tag: String, throwable: Throwable, isFatal: Boolean = false)
}
```

**Implementations (composed in DI):**
- `TimberAnalyticsTracker` — always-on local logging.
- `FirebaseAnalyticsTracker` — forwards to Firebase Analytics + Performance custom attrs.
- `SentryAnalyticsTracker` — forwards to Sentry as breadcrumbs + events.
- `CrashlyticsErrorSink` — forwards `trackError` to Crashlytics `recordException` (or `log` for breadcrumbs).
- `CompositeAnalyticsTracker(sinks: List<AnalyticsTracker>)` — fans out every call to all sinks; never throws (each sink wrapped in try/catch so one vendor outage doesn't break others).
- `SampledAnalyticsTracker(delegate, policy: SamplingPolicy)` — outermost wrapper, decides per event whether to forward (see 9.10).

**`analytics/di/AnalyticsModule.kt`**:
```kotlin
@Provides @Singleton
fun provideTracker(
    timber: TimberAnalyticsTracker,
    firebase: FirebaseAnalyticsTracker,
    sentry: SentryAnalyticsTracker,
    crashlytics: CrashlyticsErrorSink,
    policy: SamplingPolicy
): AnalyticsTracker = SampledAnalyticsTracker(
    CompositeAnalyticsTracker(listOf(timber, firebase, sentry, crashlytics)),
    policy
)
```

### 9.2 — Crash reporting: Crashlytics + Sentry

**Why both:** Crashlytics has the best ANR detection and free fatal-crash pipeline. Sentry has the best Compose screen tracking and ties non-fatals to performance transactions in one UI. Together, every fatal crash lands in two places (cheap insurance), but they have distinct lanes for everything else.

**Crashlytics setup**
- Plugins: `com.google.gms.google-services` + `com.google.firebase.crashlytics`.
- `google-services.json` placed in `app/` (gitignored; CI provides via secret).
- Initialized automatically by the gradle plugin via `MovieFluxApp`.
- `FirebaseCrashlytics.getInstance().setUserId(hashedUserId)` set after login (SHA-256 of `"admin"` since it's mocked — still hash it for habit).
- Custom keys per session: `build_type`, `screen` (updated on every `trackScreen`), `view_mode` (updated when Home/Favorites toggles).
- Non-fatals: `crashlytics.recordException(throwable)` via `CrashlyticsErrorSink.trackError(...)` when `isFatal = false`.
- Debug builds: `crashlytics.isCrashlyticsCollectionEnabled = BuildConfig.DEBUG.not()` so dev crashes don't pollute the dashboard.

**Sentry setup**
- `io.sentry:sentry-android:7.x` + `io.sentry:sentry-compose-android:7.x` (auto-instruments screen transitions + UI lifecycle).
- DSN in `local.properties` → `BuildConfig.SENTRY_DSN`.
- `SentryAndroid.init(context) { it.dsn = BuildConfig.SENTRY_DSN; it.tracesSampleRate = if (DEBUG) 1.0 else 0.15 }`.
- Sentry auto-instruments OkHttp via its OkHttp integration → ties HTTP spans to user-facing transactions.
- Release tracking: `release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"`.

**Test crash affordance (debug only)**
- Hidden 5-tap gesture on Profile screen's version label → invokes `throw RuntimeException("Test crash")`. Disabled in release via `BuildConfig.DEBUG`.

### 9.3 — Render performance: JankStats

**Dependency:** `androidx.metrics:metrics-performance:1.0.0-beta01`.

**`performance/JankReporter.kt`**
```kotlin
class JankReporter @Inject constructor(
    private val tracker: AnalyticsTracker
) : JankStats.OnFrameListener {
    override fun onFrame(frameData: FrameData) {
        if (!frameData.isJank) return
        val states = frameData.states.associate { it.key to it.value }
        tracker.trackEvent("frame_jank", states + mapOf(
            "duration_ms" to frameData.frameDurationUiNanos / 1_000_000,
            "is_jank" to true
        ))
    }
}
```

**Activity-level wiring** — in `MainActivity.onCreate` after `setContent`:
```kotlin
val jankStats = JankStats.createAndTrack(window, jankReporter)
lifecycle.addObserver(LifecycleEventObserver { _, e ->
    jankStats.isTrackingEnabled = (e == ON_RESUME)
})
```

**Composable state attribution** — `view/components/JankStateEffect.kt`:
```kotlin
@Composable
fun JankStateEffect(vararg states: Pair<String, String>) {
    val view = LocalView.current
    DisposableEffect(states.toList()) {
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        states.forEach { (k, v) -> holder.state?.putState(k, v) }
        onDispose { states.forEach { (k, _) -> holder.state?.removeState(k) } }
    }
}
```

**Usage** — drop into every grid/list-bearing screen:
```kotlin
// HomeScreen
JankStateEffect(
    "screen"    to "home",
    "view_mode" to state.viewMode.name,
    "scrolling" to listState.isScrollInProgress.toString()
)
```

Same for `FavoritesScreen` (with `"favorites"`) and `DetailsScreen` (with `"details"`).

### 9.4 — Per-screen render timing

**`performance/ScreenRenderTracker.kt`**
- Modifier extension `Modifier.trackScreenRender(screen: String)`:
  - Captures `System.nanoTime()` on first `onPlaced`.
  - Hooks `Choreographer.getInstance().postFrameCallback` on the next frame.
  - On frame callback fired, computes `durationMs = (frameTime - startTime) / 1_000_000` and emits `screen_rendered` event with `{ screen, duration_ms, is_cold_start }`.
  - `is_cold_start` true only for the very first screen of the process (set a `@Singleton` flag on first call).

**Cold-start delta**
- `Process.getStartElapsedRealtime()` (API 26+) → captured in `MovieFluxApp.onCreate()` as `appStartElapsed`.
- First `trackScreen("home")` after login computes `now - appStartElapsed` → emits `cold_start_to_home` event with `duration_ms`.
- Cross-validated against Firebase Performance's auto `_app_start` trace.

**Wire-up locations**
- `LoginScreen`: `Modifier.trackScreenRender("login")` on root.
- `HomeScreen`, `FavoritesScreen`, `ProfileScreen`, `DetailsScreen`: same.

### 9.5 — Firebase Performance Monitoring

**Setup**
- Plugin `com.google.firebase.firebase-perf`.
- Already provisioned by `google-services.json` from 9.2.

**Auto-traces (no code)**
- `_app_start` — process start → first activity drawn.
- `_app_in_foreground_time` / `_app_in_background_time`.
- HTTP request traces for OkHttp (automatic via plugin).
- Screen rendering metrics (slow frames %, frozen frames %).

**Custom traces** (`com.google.firebase.perf.metrics.Trace`)
- `HomeViewModel.loadMovies()`:
  ```kotlin
  val trace = FirebasePerformance.getInstance().newTrace("home_load").apply { start() }
  trace.putAttribute("page", currentPage.toString())
  runCatching { useCase(currentPage) }
    .onSuccess { trace.putMetric("count", it.size.toLong()) }
    .also { trace.stop() }
  ```
- Same pattern for `DetailsViewModel.loadDetail`, `FavoritesViewModel` init, `LoginViewModel.login`.

**Custom HTTP attributes** — extend `ApiKeyInterceptor` to call `HttpMetric.putAttribute("endpoint", path)` before `start()`.

### 9.6 — Network + image + DB instrumentation

**Network (extend Phase 1's `ApiKeyInterceptor`)**
- Emit `tracker.trackEvent("network_request", { endpoint, status, duration_ms, response_bytes })`.
- On non-2xx: emit `trackError("[NETWORK]", HttpException(response))`.
- Sentry auto-instrumentation already wraps this in spans.

**Coil image load — `ImageLoader.eventListener`**
```kotlin
ImageLoader.Builder(context)
  .eventListener(object : EventListener {
      override fun onSuccess(request: ImageRequest, result: SuccessResult) {
          tracker.trackEvent("image_load", mapOf(
              "duration_ms" to result.metadata.diskCacheKey?.let { 0L } ?: -1L,  // approx
              "data_source" to result.dataSource.name,   // MEMORY_CACHE / DISK / NETWORK
              "from_cache"  to (result.dataSource != DataSource.NETWORK)
          ))
      }
      override fun onError(request: ImageRequest, result: ErrorResult) {
          tracker.trackError("[IMAGE]", result.throwable)
      }
  })
  .build()
```
- Provide this `ImageLoader` via Hilt (`@Provides @Singleton`) and pass to `AsyncImage(imageLoader = ...)` everywhere.

**Room — DAO method tracing**
- Wrap each `MovieDao` method call from the repository in `androidx.tracing.Trace.beginSection("dao_${methodName}")` / `endSection()`. Visible in Android Studio Profiler systrace.
- For slow queries: `RoomDatabase.QueryCallback` that emits `db_query` event when `executionTimeMs > 100`.

### 9.7 — User flow funnels

**`analytics/FunnelTracker.kt`**
- In-memory `Map<String, Long>` keyed by flow name → start timestamp.
- API: `start(flow: String)`, `step(flow: String, step: String)`, `complete(flow: String)`, `abandon(flow: String, reason: String)`.
- Every step emits `funnel_step` event with `{ flow, step, ms_since_start, ms_since_previous_step }`.
- `complete` emits `funnel_complete` with total duration; `abandon` emits `funnel_abandoned`.

**Auth funnel wiring**
- `LoginScreen` Sign-in button click → `funnel.start("auth"); funnel.step("auth", "login_clicked")`.
- `LoginViewModel` emits `Success` → `funnel.step("auth", "login_success")`.
- First `screen_rendered` event for `home` → `funnel.complete("auth")` (auth funnel total ms = login-to-home).
- `LoginViewModel` emits `Error` → `funnel.abandon("auth", reason = "invalid_credentials")`.

**Biometric outcome** (not strictly a funnel, but related)
- `BiometricHelper.authenticate` callbacks → `trackEvent("biometric_result", { outcome: success|cancel|error|no_hardware, duration_ms })`.

### 9.8 — Compose render diagnostics

**Compose Compiler Stability Reports**
- Add to `:app/build.gradle.kts`:
  ```kotlin
  kotlinOptions {
      freeCompilerArgs += listOf(
          "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${rootProject.layout.buildDirectory.get().asFile.absolutePath}/compose_reports",
          "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${rootProject.layout.buildDirectory.get().asFile.absolutePath}/compose_metrics"
      )
  }
  ```
- After `./gradlew assembleRelease`, review `build/compose_reports/app_release-classes.txt`.
- Pre-release checklist item: any composable with `runtime-determined stability` params should be promoted to `@Immutable`/`@Stable` data class or `ImmutableList` (kotlinx-immutable-collections) before shipping.

**Compose runtime trace markers** (for Android Studio Profiler)
- Wrap hot composables: `Trace.beginSection("HomeGrid")` ... `Trace.endSection()` inside `HomeScreen`'s grid block, `FavoritesGrid`, `MovieCard` (no — too granular; keep at screen level).
- Inspect via Profiler → CPU → System Trace.

**Recomposition counts (debug only)**
- Add `recompose-highlighter` dev modifier on suspect composables in debug builds:
  ```kotlin
  Modifier.then(if (BuildConfig.DEBUG) Modifier.recomposeHighlighter() else Modifier)
  ```

### 9.9 — Macrobenchmark + Baseline Profile (CI gate)

**New module:** `benchmark/`
- `androidx.benchmark:benchmark-macro-junit4:1.2.4`.
- Manifest declares benchmark `<profileable>` access.

**Tests**
- `StartupBenchmark` — `@RunWith(AndroidJUnit4::class)`; measures `StartupTimingMetric` for `COLD`, `WARM`, `HOT`. Target: cold start p95 < 1.5s on a Pixel 6.
- `HomeScrollBenchmark` — `FrameTimingMetric` while scrolling Home grid 10 pages. Target: p99 frame < 50ms.
- `HomeListScrollBenchmark` — same for LIST viewMode (compare against GRID).
- `FavoritesScrollBenchmark` — same for Favorites (after seeding 100 favorites via a test-only DAO entry point).
- `LoginToHomeBenchmark` — `TraceSectionMetric("home_load")` → measures network-bound first paint.

**Baseline Profile**
- `BaselineProfileGenerator` test runs a critical user journey: cold start → scroll Home 5 pages → tap movie → view Details → back. Generates `baseline-prof.txt` written into `:app/src/main/`.
- Reduces cold start by 20–40% typically.

**CI**
- `./gradlew :benchmark:connectedBenchmarkAndroidTest` runs on a managed device (Google's `Pixel 6 API 33` via `com.android.test` plugin).
- Output JSON parsed by a CI step (`scripts/check_benchmarks.sh`) — fails the PR if regressions exceed thresholds (configured in `benchmark/thresholds.yaml`).

### 9.10 — Sampling + privacy policy

**`analytics/SamplingPolicy.kt`**
```kotlin
data class SamplingPolicy(
    val errorRate: Float = 1.0f,            // never drop errors
    val funnelRate: Float = 1.0f,           // never drop funnel steps (low volume)
    val screenRenderRate: Float = if (DEBUG) 1.0f else 0.15f,
    val jankRate: Float = if (DEBUG) 1.0f else 0.15f,
    val networkRate: Float = if (DEBUG) 1.0f else 0.20f,
    val imageRate: Float = if (DEBUG) 1.0f else 0.05f,  // highest volume
    val defaultRate: Float = if (DEBUG) 1.0f else 0.20f
)
```

**`SampledAnalyticsTracker`** routes events by name → rate → `Random.nextFloat() < rate ? forward : drop`. Errors always forwarded. Sentry has its own `tracesSampleRate` for transactions — kept aligned.

**Privacy**
- User ID hashed (SHA-256) before reaching any vendor SDK.
- No PII in event params — movie titles OK; usernames/emails forbidden by lint rule (custom `AnalyticsParamLint` check, or just a code review checklist).
- Crashlytics + Sentry both configured `setCollectionEnabled(BuildConfig.DEBUG.not())` — debug builds don't send.
- README documents what telemetry is collected and how to opt out (future: Profile toggle).

### 9.11 — Analytics call-site map

| Where | Event / call | Why |
|---|---|---|
| Every ViewModel `init` | `trackScreen(name)` | Screen funnel + Crashlytics + Sentry custom key |
| `LoginViewModel.login` start | `funnel.start("auth"); funnel.step("auth", "login_clicked")` | Auth funnel start |
| `LoginViewModel` Success | `funnel.step("auth", "login_success")` + Firebase trace `login` stop | Step 2 of funnel |
| First `screen_rendered("home")` after Success | `funnel.complete("auth")` | Auth flow timing |
| `LoginViewModel` Error | `funnel.abandon("auth", reason)` + `trackError` | Error visibility |
| `BiometricHelper` callback | `trackEvent("biometric_result", { outcome, duration_ms })` | Device-issue surfacing |
| `Profile.confirmLogout` | `trackEvent("logout")` | User-flow metric |
| `Profile.setBiometricEnabled` | `trackEvent("biometric_toggled", { enabled })` | Settings telemetry |
| `Home/Favorites.setViewMode` | `trackEvent("view_mode_changed", { screen, mode })` | Feature usage |
| `Home/Favorites/Details.toggleFavorite` | `trackEvent("toggle_favorite", { movie_id, is_favorite })` | Engagement |
| `ApiKeyInterceptor` | `trackEvent("network_request", { endpoint, status, duration_ms })` | Per-call telemetry |
| Coil EventListener | `trackEvent("image_load", { data_source, duration_ms })` | Image perf |
| JankStats `onFrame` | `trackEvent("frame_jank", { duration_ms, screen, view_mode, scrolling })` | Render perf |
| `Modifier.trackScreenRender` | `trackEvent("screen_rendered", { screen, duration_ms, is_cold_start })` | Render perf |
| Any catch block | `trackError(tag, throwable, isFatal = false)` | Non-fatal capture |
| `UncaughtExceptionHandler` (default chain) | Crashlytics + Sentry handle automatically | Fatal capture |

### 9.12 — Unit Tests

**`HomeViewModelTest`**
- `loadMovies()` emits `Success` with movie list.
- `loadMovies()` on network error emits `Error` state.
- `loadNextPage()` appends results to the existing list and increments `currentPage`.
- Search query triggers `searchMovies`, not `getPopularMovies`.
- `toggleFavorite(movie)` calls `repository.toggleFavorite(...)`.
- An external favorite write (Room insert from Favorites or Details) is reflected in `isFavorite` flags across all loaded pages.
- `setViewMode(LIST)` updates `UiState.Success.viewMode` and does not refetch.

**`FavoritesViewModelTest`** *(new — heart on the card lives here too)*
- `getFavorites()` emission updates `UiState.Success(movies)`.
- `toggleFavorite(movie)` calls `repository.toggleFavorite(...)`, and since the movie was favorited, the next Flow emission drops the row from `UiState.Success.movies`.
- `setViewMode(LIST)` updates `UiState.Success.viewMode` independently of Home (verify by writing to one and asserting the other is unchanged — uses two separate VMs).

**`DetailsViewModelTest`** *(new)*
- `loadDetail(id)` emits `Success` with merged `isFavorite` from local favorites.
- `toggleFavorite()` calls `repository.toggleFavorite(...)` and flips `isFavorite` in local state optimistically.
- `loadDetail()` on network error emits `Error`.

**`ProfileViewModelTest`** *(new)*
- `setBiometricEnabled(true)` writes to `AuthPreferences` when biometric available.
- `setBiometricEnabled(true)` emits `BiometricUnavailable` and does NOT write when no hardware.
- `confirmLogout()` calls `AuthPreferences.clear()` and emits `LogoutComplete`.
- `requestLogout()` / `dismissLogoutDialog()` flip `showLogoutDialog` correctly.

**`MovieRepositoryImplTest`**
- `getPopularMovies()` joins remote results with local favorites.
- `toggleFavorite()` calls `dao.insert()` when `movie.isFavorite == false`.
- `toggleFavorite()` calls `dao.delete()` when `movie.isFavorite == true`.
- `searchMovies()` returns mapped domain models.

**`LoginViewModelTest`**
- `login("admin", "1234")` emits `Success` and sets `isLoggedIn = true`.
- `login("admin", "wrong")` emits `Error`.

**`BottomNavBarTest`** *(Compose UI test, instrumented)*
- Tapping each tab navigates to the right route.
- Selected state reflects current route.
- Bottom bar is hidden on Details route, visible on Home/Favorites/Profile.

**`AnalyticsTrackerTest`** *(new — guards the observability core)*
- `CompositeAnalyticsTracker` forwards a call to every sink even if one throws.
- `SampledAnalyticsTracker` always forwards `trackError` regardless of `errorRate < 1.0`.
- `SampledAnalyticsTracker` drops a `frame_jank` event when `Random.nextFloat() >= jankRate`.
- `FunnelTracker` emits `funnel_complete` with the correct total duration; double-completing a flow is a no-op.

**`JankReporterTest`** *(new — unit)*
- `onFrame(isJank = false)` emits nothing.
- `onFrame(isJank = true, states = [screen=home, view_mode=GRID])` emits `frame_jank` with all state attributes merged.

Use `kotlinx-coroutines-test` (`runTest`, `TestDispatcher`) + MockK + Turbine for unit tests; Compose UI testing rule for the BottomNavBar test.

**Macrobenchmark suite (from 9.9, run in CI, not part of `:app`'s `test` source set):**
- `StartupBenchmark` — cold/warm/hot timing.
- `HomeScrollBenchmark` (GRID + LIST).
- `FavoritesScrollBenchmark`.
- `LoginToHomeBenchmark`.
- All ship JSON metrics; thresholds asserted in `scripts/check_benchmarks.sh`.

### 9.13 — README.md
Sections:
1. **API Key setup** — add `TMDB_API_KEY=your_key` and `SENTRY_DSN=your_dsn` to `local.properties`. `google-services.json` must be placed in `app/` (CI injects via secret, dev gets it from the shared 1Password vault).
2. **Biometric testing** — enroll a fingerprint in the emulator (`Settings > Security > Fingerprint`), log in, open Profile, toggle "Biometric login" on, restart app.
3. **Navigation structure** — diagram of the nested NavHost (AuthGraph + MainGraph with tabs).
4. **Architecture decisions** — MVVM + Clean Architecture, Hilt, Room + Flow for cross-screen sync.
5. **Observability stack** — what each tool does (table from Phase 9 intro), how events flow through `CompositeAnalyticsTracker`, where dashboards live (Firebase + Sentry URLs). Includes the analytics call-site map (9.11).
6. **Running benchmarks** — `./gradlew :benchmark:connectedBenchmarkAndroidTest`; how to read the JSON; current thresholds; how the Baseline Profile is regenerated.
7. **Reading Compose Compiler reports** — where `build/compose_reports/` lives, how to spot unstable params, how to fix with `@Immutable` / `ImmutableList`.
8. **Sampling + privacy** — what telemetry is collected, sampling rates per event type, hashing of user IDs, how debug builds are excluded from production sinks.
9. **AI usage** — document how Claude Code was used for scaffolding and plan generation.

---

## Dependency Map

```
Phase 1 (infra)          <- blocks everything else
Phase 2 (auth)           <- needs Phase 1
Phase 3 (nav restructure) <- needs Phase 2 (login navigates into MainGraph)
Phase 4 (profile)        <- needs Phase 3 (Profile tab must exist) + Phase 2 (AuthPreferences, BiometricHelper)
Phase 5 (home grid)      <- needs Phase 3 (Home tab) + Phase 1 (Retrofit)
Phase 6 (search/states)  <- needs Phase 5
Phase 7 (details)        <- needs Phase 3 + Phase 5 (MovieCard reuse)
Phase 8 (favorites)      <- needs Phase 5 (MovieCard) + Phase 7 (Details nav target)
Phase 9 (obs + tests)    <- needs all of the above
```

Phases 4 and 5 can run in parallel after Phase 3 (different files, no shared code). Phases 7 and 8 can run in parallel after Phase 5 if two people split work.

---

## Summary of changes vs. previous plan

| Area | Before | After |
|---|---|---|
| Navigation | Single flat NavHost | Nested NavHost: AuthGraph + MainGraph (with inner NavHost for tabs) |
| Tab bar | None | Material3 `NavigationBar` with 3 tabs (Home / Favorites / Profile), hidden on Details |
| Profile screen | Did not exist | New screen with user header, biometric toggle, app version, About, Logout |
| Logout | Button on Home, instant logout | Button on Profile, confirmation dialog, clears `AuthPreferences` |
| Biometric opt-in | First-login `AlertDialog` after Success | Toggle row on Profile screen (Day 2 dialog removed) |
| Phase count | 7 days | 9 phases — new Phase 3 (nav restructure) + new Phase 4 (profile) inserted between auth and feature screens |
| `Screen` sealed class | 4 routes (Login, Home, Favorites, Details) | 6 routes + 2 graph routes: AuthGraph, MainGraph, Login, Home, Favorites, Profile, Details |
| Tests | Home / Repository / Login VM tests | + Profile / Favorites / Details VM tests + BottomNavBar instrumented + AnalyticsTracker + JankReporter + 5 Macrobenchmarks |
| Movie grid item | Original spec: heart overlay, star rating | Matches `docs/design/Filme Item.png`: poster + title (1-line) + `X/5` rating + heart `IconButton` at bottom-right of poster (semi-transparent black circle) |
| Home / Favorites list mode | Grid only | Toggle in TopAppBar switches between Grid (`MovieCard`) and List (`MovieListItem`); per-screen independent state |
| Login screen | Stub | Full design built from `docs/design/Login.png` using existing color tokens (`BackgroundDark`, `TealGreenLight`, `TealGreen`); SVG logo + "MovieFlux" wordmark; pill button |
| Observability | Timber + AnalyticsTracker interface | Full stack: Timber + JankStats + Firebase Performance + Crashlytics + Sentry + Macrobenchmark/Baseline Profile + Compose Compiler reports; sampled at 15% in release; funnel tracker; per-screen render timing; cold-start delta |
| Crash reporting | Not addressed | Crashlytics (fatal + ANR) + Sentry (non-fatal + perf transactions); both wired through `CompositeAnalyticsTracker` |
