# MovieFlux — 9-Phase Implementation Plan

## Current Baseline

The codebase has Clean Architecture scaffolding (domain models, repository interface, Room DAO, Retrofit interface, navigation routes, theme) but **no wiring**: no DI, no Room `@Database`, no Retrofit client, no ViewModels, and all screens are stubs.

Navigation today is a single flat `NavHost`: `Login → Home → (Details | Favorites)`, with logout living inside Home as a button. This plan restructures navigation into a **nested NavHost** (Auth graph + Main graph) with a **bottom tab bar** in the Main graph hosting three tabs: **Home**, **Favorites**, **Profile**. Logout moves out of Home and into a new **Profile** screen with a confirmation dialog.

---

## Implementation Status Audit (2026-05-16)

A walk of the current source tree confirms how much of the plan is already on disk. This audit is informational — it lets later phases skip work that is already done and lets reviewers spot drift between code and plan.

| Phase | Status | Evidence |
|---|---|---|
| **Phase 1 — Infrastructure** | **Done** | `MovieFluxApp.kt` has `@HiltAndroidApp` and `Timber.plant`; `di/NetworkModule.kt`, `di/DatabaseModule.kt`, `di/RepositoryModule.kt` exist; `data/local/MovieDatabase.kt` declares `@Database`; `RemoteDataSource` includes `getMovieDetail`; `AndroidManifest.xml` registers `.MovieFluxApp`. **Note:** §1.1–1.8 of the plan were rewritten retroactively to match the actual import shape — the real wiring already compiles. |
| **Phase 2 — Auth & Security (mocked)** | **Done (mocked)** | `data/preferences/AuthPreferences.kt` uses `EncryptedSharedPreferences` with `AES256_SIV` / `AES256_GCM`; `data/biometric/BiometricHelper.kt` exists; `view/login/LoginScreen.kt` matches the Login.png spec; `LoginViewModel.login()` validates `admin / 1234`. **Gap:** authentication is local-only — there is no real backend, no account creation, and no password recovery. **Resolved by new Phase 2B below.** |
| **Phase 3 — Nested Navigation** | **Done** | `navigation/Screen.kt` declares `AuthGraph`, `MainGraph`, `Login`, `Home`, `Favorites`, `Profile`, `Details`; `MainScaffold.kt` + `BottomNavBar.kt` exist; `AppNavHost.kt` wires the nested graph. **Gap (introduced by Phase 2B):** `Screen.kt` and `AuthGraph` need a new `Register` destination. |
| **Phase 4 — Profile / Logout** | **Done** | `view/profile/{ProfileScreen,ProfileViewModel,ProfileUiState}.kt`, `LogoutConfirmDialog.kt`, `ProfileHeader.kt`, `SettingsSwitchRow.kt` all exist. **Gap (introduced by Phase 2B):** Profile header should show the real Firebase email, not the hard-coded `"admin@movieflux.app"`. |
| **Phase 5+ — Home / Details / Favorites** | **Partial** | Home, Details, Favorites screens + ViewModels + UiStates exist (`feat/observability` branch). Pagination, search, and Room favorites integration to be verified per their own phases. |

**Drift summary:** the codebase is ahead of the plan in some areas (analytics — see `analytics/AnalyticsTracker`, `FunnelTracker`, `LogRecompositions` are referenced but not in the plan) and the plan is ahead of the code in one area (Firebase auth + registration, added below).

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

### 1.0 — Root cause: why imports currently fail

A baseline audit of the current `gradle/libs.versions.toml`, `build.gradle.kts` (root), and `app/build.gradle.kts` reveals that the project is missing several plugins and dependencies that the existing source files already reference. Every file under `di/`, `data/preferences/`, `data/biometric/`, `data/local/MovieDatabase.kt`, `MovieFluxApp.kt`, and all `view/**/ViewModel.kt` files currently fails to resolve imports because of the gaps below. Phase 1 must fix **all** of them before any other phase runs.

| # | Missing piece | Symptom in IDE / build |
|---|---|---|
| 1 | `org.jetbrains.kotlin.android` plugin | Kotlin sources under `app/src/main/java/**` are not compiled as a Kotlin module; annotation processors (KSP) refuse to attach. |
| 2 | `com.google.devtools.ksp` plugin | `@Database`, `@Dao`, `@HiltAndroidApp`, `@Inject`, `@Module` generate nothing → `DaggerMovieFluxApp_HiltComponents` and `MovieDatabase_Impl` never appear → unresolved references at every injection site. |
| 3 | `com.google.dagger.hilt.android` plugin | Hilt component generation skipped even if dependencies are added. |
| 4 | Hilt runtime + compiler artifacts | `import dagger.hilt.*`, `import javax.inject.Inject` resolve, but app crashes/fails to compile at codegen. |
| 5 | `androidx.hilt:hilt-navigation-compose` | `hiltViewModel()` in Compose screens cannot be resolved. |
| 6 | Room compiler (KSP) | `MovieDatabase`, `MovieDao` produce "cannot find implementation" at runtime / no generated `_Impl`. |
| 7 | `androidx.lifecycle:lifecycle-viewmodel-compose` and `lifecycle-viewmodel-ktx` | `ViewModel`, `viewModelScope`, `viewModel()`/`hiltViewModel()` unresolved in `*ViewModel.kt` files. |
| 8 | `androidx.compose.material:material-icons-extended` | `Icons.Default.Person`, `Icons.Default.Favorite`, `Icons.Default.Visibility`, etc. used by `BottomNavBar`, `LoginScreen`, `ProfileScreen` → unresolved. |
| 9 | `androidx.security:security-crypto` | `EncryptedSharedPreferences`, `MasterKey` in `AuthPreferences.kt` unresolved. |
| 10 | `androidx.biometric:biometric` | `BiometricManager`, `BiometricPrompt` in `BiometricHelper.kt` unresolved. |
| 11 | `com.jakewharton.timber:timber` | `Timber.plant(...)` in `MovieFluxApp.kt` unresolved. |
| 12 | `buildFeatures.buildConfig = true` + `buildConfigField` | `BuildConfig.TMDB_API_KEY` referenced by `NetworkModule` does not exist. |
| 13 | `<application android:name=".MovieFluxApp">` in `AndroidManifest.xml` | App runs with the default `Application`, so Hilt never initialises → runtime `IllegalStateException`. |
| 14 | Compose BOM `2024.09.00` is stale and `kotlin-compose` plugin requires Kotlin `2.2.10` paired with a current BOM | Compose compiler / runtime drift causes `@Composable` resolution warnings and missing APIs (e.g. `WindowInsets` helpers used by Login). |
| 15 | `JavaVersion.VERSION_11` with Kotlin 2.2 + KSP 2.2.10 | KSP 2.2.x and Hilt 2.51+ require **JVM 17**. Leaving 11 produces `Unsupported class file major version` at KSP run. |
| 16 | Gson vs. Moshi conflict in earlier draft | `libs.versions.toml` already declares Gson + `converter-gson`. Decision: **keep Gson** (already imported, less churn). Remove the Moshi mention from `NetworkModule` notes. |

The substeps below resolve every row above in order. Do not skip a substep — each is a real failing import in the current tree.

---

### 1.1 — Bump JVM target to 17

In `app/build.gradle.kts`:

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    jvmToolchain(17)
}
```

Also bump the Compose BOM in `libs.versions.toml` to the current release at implementation time (must be compatible with Kotlin 2.2.10 + AGP 9.x). Check `https://developer.android.com/jetpack/compose/bom/mapping` for the latest version — do **not** pin a specific version in this plan, since the BOM is published monthly and any literal will drift.

### 1.2 — Declare new versions in `gradle/libs.versions.toml`

Add under `[versions]`:

```toml
hilt = "2.51.1"
hiltNavigationCompose = "1.2.0"
ksp = "2.2.10-1.0.29"           # MUST match Kotlin version exactly
lifecycleViewmodel = "2.10.0"
securityCrypto = "1.1.0-alpha06"
biometric = "1.2.0-alpha05"
timber = "5.0.1"
coroutinesTest = "1.8.1"
mockk = "1.13.12"
turbine = "1.1.0"
```

### 1.3 — Declare new libraries in `libs.versions.toml`

Add under `[libraries]`:

```toml
# Hilt
hilt-android            = { group = "com.google.dagger",  name = "hilt-android",            version.ref = "hilt" }
hilt-compiler           = { group = "com.google.dagger",  name = "hilt-android-compiler",   version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt",      name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Lifecycle / ViewModel for Compose
androidx-lifecycle-viewmodel-ktx     = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx",     version.ref = "lifecycleViewmodel" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodel" }

# Room compiler (KSP)
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Compose extras
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

# Security / Biometric
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
androidx-biometric       = { group = "androidx.biometric", name = "biometric",       version.ref = "biometric" }

# Logging
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

# Test
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
mockk                   = { group = "io.mockk",              name = "mockk",                   version.ref = "mockk" }
turbine                 = { group = "app.cash.turbine",      name = "turbine",                 version.ref = "turbine" }
```

### 1.4 — Declare new plugins in `libs.versions.toml`

The current `[plugins]` block is missing the Kotlin Android plugin entirely — that is the single biggest reason imports fail. Replace `[plugins]` with:

```toml
[plugins]
android-application = { id = "com.android.application",                   version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android",              version.ref = "kotlin" }
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose",       version.ref = "kotlin" }
ksp                 = { id = "com.google.devtools.ksp",                   version.ref = "ksp" }
hilt                = { id = "com.google.dagger.hilt.android",            version.ref = "hilt" }
```

### 1.5 — Register plugins in the root `build.gradle.kts`

Replace the contents of the root `build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.hilt)                apply false
}
```

### 1.6 — Apply plugins in `app/build.gradle.kts`

Replace the `plugins { … }` block at the top of `app/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
```

Order matters: `kotlin-android` must precede `kotlin-compose`, `ksp`, and `hilt`.

### 1.7 — Enable `buildConfig` and emit `TMDB_API_KEY`

In `app/build.gradle.kts`:

1. Add `TMDB_API_KEY=your_real_key_here` to `local.properties` (this file is gitignored).
2. At the top of the file, read it:
   ```kotlin
   import java.util.Properties
   import java.io.FileInputStream

   val localProps = Properties().apply {
       val f = rootProject.file("local.properties")
       if (f.exists()) load(FileInputStream(f))
   }
   val tmdbApiKey: String = localProps.getProperty("TMDB_API_KEY") ?: ""
   ```
3. Inside `android { defaultConfig { … } }`:
   ```kotlin
   buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
   ```
4. Inside `android { buildFeatures { … } }` add:
   ```kotlin
   buildConfig = true
   ```

### 1.8 — Wire dependencies in `app/build.gradle.kts`

Append to the existing `dependencies { … }` block:

```kotlin
// Hilt
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(libs.hilt.navigation.compose)

// Lifecycle / ViewModel
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.lifecycle.viewmodel.compose)

// Room compiler
ksp(libs.androidx.room.compiler)

// Compose extras
implementation(libs.androidx.compose.material.icons.extended)

// Security / Biometric
implementation(libs.androidx.security.crypto)
implementation(libs.androidx.biometric)

// Logging
implementation(libs.timber)

// Test
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.mockk)
testImplementation(libs.turbine)
```

### 1.9 — Register the Application class in `AndroidManifest.xml`

Edit `app/src/main/AndroidManifest.xml` and add `android:name=".MovieFluxApp"` on the `<application>` tag:

```xml
<application
    android:name=".MovieFluxApp"
    android:allowBackup="true"
    ... >
```

Without this line, `@HiltAndroidApp` is never initialised and every `@Inject` site throws at runtime even after the build succeeds.

### 1.10 — `MovieFluxApp.kt` — Application class

```kotlin
@HiltAndroidApp
class MovieFluxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
```

Required imports:
```kotlin
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import com.example.movieflux.BuildConfig
```

### 1.11 — Annotate `MainActivity` with `@AndroidEntryPoint`

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() { … }
```

Without this annotation, `hiltViewModel()` cannot resolve the activity's component.

### 1.12 — `di/NetworkModule.kt`

- `ApiKeyInterceptor` appends `?api_key=<BuildConfig.TMDB_API_KEY>` to every request URL (use `HttpUrl.newBuilder().addQueryParameter("api_key", …)`).
- `HttpLoggingInterceptor` at `BODY` level on debug, `BASIC` on release.
- `OkHttpClient` with both interceptors, 15s connect/read/write timeouts.
- `Retrofit` with base URL `https://api.themoviedb.org/3/` + **Gson** converter (`GsonConverterFactory.create()`) — Gson is already in `libs.versions.toml`; do not introduce Moshi.
- Provide `RemoteDataSource` via `retrofit.create(RemoteDataSource::class.java)`.
- Module annotated `@Module @InstallIn(SingletonComponent::class)`.

### 1.13 — `di/DatabaseModule.kt`

- Provide `MovieDatabase` via `Room.databaseBuilder(context, MovieDatabase::class.java, "movieflux.db").build()`.
- Provide `MovieDao` from `db.movieDao()`.
- `@ApplicationContext` from Hilt for the context.
- Module annotated `@Module @InstallIn(SingletonComponent::class)`.

### 1.14 — `di/RepositoryModule.kt`

- `@Binds` `MovieRepositoryImpl` → `MovieRepository`.
- Abstract class, annotated `@Module @InstallIn(SingletonComponent::class)`.

### 1.15 — Extend `RemoteDataSource`

- Add `@GET("movie/{movie_id}") suspend fun getMovieDetail(@Path("movie_id") id: Int): MovieDetailDto`.
- `MovieDetailDto` fields: `id`, `title`, `overview`, `poster_path`, `vote_average`, `genres: List<GenreDto>` (detail endpoint returns full genre objects, no separate `/genre/movie/list` call needed for Details).

### 1.16 — `MovieDatabase.kt` body

```kotlin
@Database(entities = [MovieEntity::class], version = 1)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
}
```

### 1.17 — Sync + smoke-compile gate

1. In Android Studio: **File → Sync Project with Gradle Files**. Verify the IDE no longer flags red imports in `MovieFluxApp.kt`, `di/*.kt`, `data/preferences/AuthPreferences.kt`, `data/biometric/BiometricHelper.kt`, and the `view/**/ViewModel.kt` files.
2. From the command line:
   ```bash
   ./gradlew clean assembleDebug
   ```
   (On Windows: `gradlew.bat clean assembleDebug`.)
3. Must finish with `BUILD SUCCESSFUL`. If KSP fails with `Unsupported class file major version`, recheck step 1.1 (JVM 17). If Hilt fails with `expected @HiltAndroidApp`, recheck step 1.9. Do **not** start Phase 2 until this gate is green.

---

## Phase 2 — Authentication & Security

**Goal:** Working login (mocked), biometric infrastructure ready, session persistence. (Biometric **UI toggle** moves to the Profile screen in Phase 4 — Phase 2 only builds the plumbing.)

### 2.1 — `data/preferences/AuthPreferences.kt`
Wraps `EncryptedSharedPreferences` (AES256_SIV key encryption, AES256_GCM value encryption). Exposes:
- `isLoggedIn: Boolean` (read/write) — **superseded by §2B.5 if Phase 2B is shipped**: `FirebaseAuth.currentUser != null` becomes the source of truth and `isLoggedIn` is removed in favour of `cachedEmail`.
- `biometricEnabled: Boolean` (read/write)
- `clear()` — wipes all keys (used by logout)
- Phase 11.1 adds `biometricPrompted: Boolean` for the post-login opt-in flag.

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

> **Superseded by §2B.7 if Phase 2B is shipped:** `Error` payload changes from `val message: String` to `@StringRes val messageRes: Int` for localisation. Update every `LoginUiState.Error("…")` call site to `LoginUiState.Error(R.string.…)` when 2B lands.

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

### 2.10 — Biometric gate in `MainActivity`

> **Superseded by §2B.12 if Phase 2B is shipped** (Firebase becomes the source of truth, `isLoggedIn` is replaced by `firebaseAuth.currentUser != null`). The version below is the mock-flavor implementation.
> **Superseded by §11.5 for the failure UX** (fallback scaffold with Retry / Use password instead, instead of silently falling back to `AuthGraph`).

- In `onCreate`, read `AuthPreferences`:
  - If `isLoggedIn && biometricEnabled && BiometricHelper.canAuthenticate() == Available` → show prompt before composing `MainGraph`.
  - On success → render `MainGraph` with start destination `Home`.
  - On error/cancel → render `AuthGraph` (force re-login).
- If `isLoggedIn` and biometric not required → start at `MainGraph`.
- If `!isLoggedIn` → start at `AuthGraph`.

### 2.11 — Compile + manual smoke test
Run on emulator, log in with `admin / 1234`, confirm `Success`.

---

## Phase 2B — Firebase Authentication & Registration

> **REMOVED FROM SCOPE.** Mocked `admin/1234` (Phase 2) is the only auth path. See README "Decisões fora do escopo" for rationale.

**Goal:** Replace the mocked `admin / 1234` login with **Firebase Authentication (Email/Password)** and introduce a **Registration** screen. After this phase, real accounts are created in Firebase, the session is driven by `FirebaseAuth.currentUser`, and `AuthPreferences.isLoggedIn` becomes a cached mirror of that state (not the source of truth).

### 2B.0 — Why Firebase

- Free tier covers small-to-medium apps (50k MAU on Spark).
- Email/password provider needs no backend code — `FirebaseAuth` ships SDK-side flows for sign-up, sign-in, password reset, and email verification.
- Plays well with Hilt: the SDK exposes a singleton `FirebaseAuth.getInstance()` that we can provide through `AuthModule`.
- Future expansion (Google sign-in, anonymous accounts, custom claims, Firestore) is incremental.

### 2B.1 — Create the Firebase project + register the Android app

1. Go to `https://console.firebase.google.com/` → **Add project** → name it `MovieFlux` (analytics optional; pick **Disable** to keep the dependency set minimal for now).
2. Inside the project: **Build → Authentication → Get started → Sign-in method → Email/Password → Enable** (leave Email link / passwordless **disabled** for this phase).
3. **Project settings (gear icon) → Your apps → Add Android app**:
   - Package name: `com.example.movieflux` (must match `applicationId` in `app/build.gradle.kts`).
   - App nickname: `MovieFlux Android`.
   - Debug signing certificate SHA-1: optional for email/password, **required** later for Google sign-in — fetch with `./gradlew signingReport` and paste it now to avoid revisiting.
4. **Download `google-services.json`** and drop it into `app/google-services.json` (project root of the `:app` module, **not** `app/src/main/`).
5. Add `app/google-services.json` to `.gitignore` if the project is public; otherwise commit it (it contains no secrets, just public client IDs).

### 2B.2 — Add Firebase + Google Services plugin to Gradle

In `gradle/libs.versions.toml`:

```toml
[versions]
googleServices = "4.4.2"
firebaseBom    = "33.5.1"   # use the current BoM at implementation time

[libraries]
firebase-bom  = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }   # version supplied by BoM

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

In the root `build.gradle.kts`, add `alias(libs.plugins.google.services) apply false` to the `plugins { … }` block.

In `app/build.gradle.kts`:

```kotlin
plugins {
    // … existing plugins …
    alias(libs.plugins.google.services)
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
}
```

### 2B.3 — `di/AuthModule.kt` — provide `FirebaseAuth`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
```

### 2B.4 — `data/auth/AuthRepository.kt` (interface) + `AuthRepositoryImpl`

Wrap `FirebaseAuth` behind a domain-shaped interface so the rest of the app does not couple to Firebase types.

```kotlin
sealed class AuthResult {
    data class Success(val userId: String, val email: String) : AuthResult()
    data class Error(val type: AuthError) : AuthResult()
}

enum class AuthError {
    InvalidCredentials,         // wrong password / no account
    EmailAlreadyInUse,
    WeakPassword,
    InvalidEmail,
    NetworkError,
    UserDisabled,
    Unknown
}

interface AuthRepository {
    val currentUserId: String?
    val currentUserEmail: String?
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun register(email: String, password: String): AuthResult
    suspend fun sendPasswordReset(email: String): AuthResult
    fun signOut()
}
```

`AuthRepositoryImpl` maps Firebase exceptions to `AuthError`:

| Firebase exception | `AuthError` |
|---|---|
| `FirebaseAuthInvalidCredentialsException` | `InvalidCredentials` *or* `InvalidEmail` (inspect `errorCode`) |
| `FirebaseAuthInvalidUserException` | `InvalidCredentials` |
| `FirebaseAuthUserCollisionException` | `EmailAlreadyInUse` |
| `FirebaseAuthWeakPasswordException` | `WeakPassword` |
| `FirebaseNetworkException` | `NetworkError` |
| `FirebaseAuthException` (other) | `Unknown` |

All suspend calls wrap `.await()` (`kotlinx-coroutines-play-services` — add `kotlinx-coroutines-play-services:1.8.1` to `libs.versions.toml`).

Bind in a new `@Module` or extend `RepositoryModule`:
```kotlin
@Binds
abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
```

### 2B.5 — Refactor `AuthPreferences`

`isLoggedIn` is no longer the source of truth — `FirebaseAuth.currentUser != null` is. Keep `AuthPreferences` only for **cached display** and **biometric toggle**:

- Remove the `isLoggedIn` write paths from `LoginViewModel`.
- Add `cachedEmail: String?` (so the biometric gate can show the email before Firebase is initialised).
- Keep `biometricEnabled`.
- `clear()` continues to wipe the cache on logout.

### 2B.6 — Strings: error messages

Add to `res/values/strings.xml`:

```xml
<string name="auth_error_invalid_credentials">Invalid email or password.</string>
<string name="auth_error_email_in_use">An account with this email already exists.</string>
<string name="auth_error_weak_password">Password must be at least 6 characters.</string>
<string name="auth_error_invalid_email">Please enter a valid email address.</string>
<string name="auth_error_network">No internet connection. Please try again.</string>
<string name="auth_error_user_disabled">This account has been disabled.</string>
<string name="auth_error_unknown">Something went wrong. Please try again.</string>

<string name="register_title">Create your account</string>
<string name="register_email_placeholder">Email</string>
<string name="register_password_placeholder">Password</string>
<string name="register_password_confirm_placeholder">Confirm password</string>
<string name="register_submit">Create account</string>
<string name="register_have_account">Already have an account? Sign in</string>
<string name="login_no_account">New here? Create an account</string>
<string name="login_forgot_password">Forgot password?</string>
```

`AuthError.toStringRes()` extension maps each enum to its string id.

### 2B.7 — Update `LoginUiState` and `LoginViewModel`

Replace `admin/1234` with the Firebase flow. `LoginUiState` stays the same shape; `Error` carries a `@StringRes Int` instead of a raw string so messages are localizable.

```kotlin
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(@StringRes val messageRes: Int) : LoginUiState()
}
```

`LoginViewModel.login(email, password)`:
1. Client-side validation: non-blank, `Patterns.EMAIL_ADDRESS.matcher(email).matches()`. On fail → `Error(R.string.auth_error_invalid_email)`.
2. Set `Loading`.
3. `authRepository.signIn(email, password)` → on `Success`: cache email in `AuthPreferences.cachedEmail`, emit `Success`. On `Error(type)`: emit `Error(type.toStringRes())`.
4. Funnel/analytics calls stay; replace `"login_success"` step to fire only after Firebase confirms.

### 2B.8 — Update `LoginScreen` for the new fields + register link

Changes to `view/login/LoginScreen.kt`:

- First field placeholder becomes `"Email"`, `KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = Next)`, `leadingIcon = Icons.Default.Email`.
- Password field unchanged.
- Below the `PrimaryButton("Done")`, add a `TextButton` row:
  - "Forgot password?" → opens an `AlertDialog` (single email field) that calls `vm.sendPasswordReset(email)`.
  - `Spacer(8.dp)`.
  - "New here? Create an account" → calls `onNavigateToRegister()` (a new lambda parameter on `LoginScreen`).
- Pass `onNavigateToRegister` from the auth NavGraph (Phase 3 update — see §2B.11).

### 2B.9 — New `Register` screen

Files (mirror the Login feature):

| File | Responsibility |
|---|---|
| `view/register/RegisterUiState.kt` | sealed class `Idle / Loading / Success / Error(@StringRes Int)`, plus a `data class FormState(val email, val password, val confirm)` |
| `view/register/RegisterViewModel.kt` | `@HiltViewModel`, injects `AuthRepository` + `AuthPreferences` + analytics. `register(email, pw, confirm)` validates, calls `authRepository.register`, caches email on success. |
| `view/register/RegisterScreen.kt` | Composable described below |

`RegisterScreen` layout (matches the Login visual language so no new design tokens are required):

| # | Element | Spec |
|---|---|---|
| 1 | `TopAppBar` | Transparent background, back arrow (`Icons.AutoMirrored.Filled.ArrowBack`), title = `R.string.register_title`, on click → `onNavigateBack()` (pops to Login). |
| 2 | `MovieFluxLogo(size = 96.dp)` | Smaller than Login (header is now the title bar). |
| 3 | Spacer 32.dp | |
| 4 | Email `OutlinedTextField` | Same teal styling as Login email field, `KeyboardType.Email`, `imeAction = Next`. |
| 5 | Spacer 12.dp | |
| 6 | Password `OutlinedTextField` | Same as Login password field + show/hide toggle, `imeAction = Next`. |
| 7 | Spacer 12.dp | |
| 8 | Confirm password `OutlinedTextField` | Same styling, `imeAction = Done`, `keyboardActions.onDone = { vm.register(...) }`. |
| 9 | Spacer 24.dp | |
| 10 | Error text | Same styling as Login error row; shown when `uiState is Error`. |
| 11 | `PrimaryButton(stringResource(R.string.register_submit))` | Disabled when any field blank, password length < 6, password != confirm, or `uiState is Loading`. `isLoading = uiState is Loading`. |
| 12 | Spacer 16.dp | |
| 13 | `TextButton(stringResource(R.string.register_have_account))` | Calls `onNavigateBack()` (back to Login). |

Client-side validation order (fast-fail, single error shown at a time):
1. Email format → `R.string.auth_error_invalid_email`.
2. Password length ≥ 6 → `R.string.auth_error_weak_password`.
3. Password == confirm → new string `R.string.register_error_password_mismatch` ("Passwords do not match.").
4. Then call `authRepository.register(...)`.

On `Success`, the screen calls `onRegisterSuccess()` which navigates to `MainGraph` and clears the back stack (same `popUpTo(0) { inclusive = true }` pattern as Login).

### 2B.10 — Extend `navigation/Screen.kt`

Add a new auth destination:

```kotlin
object Register : Screen("register")
```

(No arguments — registration is form-driven, not deep-linked.)

### 2B.11 — Extend `AppNavHost.kt` (`AuthGraph`)

```kotlin
navigation(route = Screen.AuthGraph.route, startDestination = Screen.Login.route) {
    composable(Screen.Login.route) {
        LoginScreen(
            onLoginSuccess = { rootNavController.navigate(Screen.MainGraph.route) {
                popUpTo(0) { inclusive = true }
            } },
            onNavigateToRegister = { rootNavController.navigate(Screen.Register.route) }
        )
    }
    composable(Screen.Register.route) {
        RegisterScreen(
            onRegisterSuccess = { rootNavController.navigate(Screen.MainGraph.route) {
                popUpTo(0) { inclusive = true }
            } },
            onNavigateBack = { rootNavController.popBackStack() }
        )
    }
}
```

### 2B.12 — Update the biometric / auto-login gate in `MainActivity`

Replace the `AuthPreferences.isLoggedIn` check (Phase 2.10) with Firebase as the source of truth:

```kotlin
val signedIn = firebaseAuth.currentUser != null
when {
    !signedIn -> startDestination = Screen.AuthGraph.route
    signedIn && authPreferences.biometricEnabled
        && biometricHelper.canAuthenticate() == Available -> showBiometricPrompt { ok ->
            startDestination = if (ok) Screen.MainGraph.route else Screen.AuthGraph.route
        }
    else -> startDestination = Screen.MainGraph.route
}
```

Inject `FirebaseAuth` into `MainActivity` via `@AndroidEntryPoint` + `@Inject lateinit var firebaseAuth: FirebaseAuth`.

### 2B.13 — Update logout in `ProfileViewModel`

`confirmLogout()` now does, in order:
1. `authRepository.signOut()` (calls `firebaseAuth.signOut()`).
2. `authPreferences.clear()`.
3. Emit `LogoutComplete` event.

### 2B.14 — Update `ProfileHeader` to show the real email

`ProfileViewModel.init` now reads `authRepository.currentUserEmail` (fallback to `authPreferences.cachedEmail`) instead of the hard-coded `"admin@movieflux.app"`. Username defaults to the local part of the email (`email.substringBefore("@")`).

### 2B.15 — Forgot password flow (minimal)

In the "Forgot password?" dialog opened from §2B.8:
- Single `OutlinedTextField` (email).
- `PrimaryButton("Send reset email")` → calls `LoginViewModel.sendPasswordReset(email)` → `authRepository.sendPasswordReset(email)`.
- Dismiss dialog and show a `Snackbar` with "If an account exists for this email, a reset link has been sent." (intentionally vague to avoid account enumeration).

### 2B.16 — Tests (Phase 2B-specific)

- `AuthRepositoryImplTest` (unit) — mock `FirebaseAuth` with MockK; verify each Firebase exception maps to the correct `AuthError`.
- `RegisterViewModelTest` (unit) — Turbine on `uiState`; cover: invalid email → `WeakPassword` error; mismatched confirm → mismatch error; successful path → `Success`; `EmailAlreadyInUse` from repo → mapped error.
- `LoginViewModelTest` updates: replace `admin/1234` cases with mocked `AuthRepository` returning each `AuthResult` variant.

### 2B.17 — Manual smoke gate

1. Fresh install on emulator with internet access.
2. Tap "Create an account" → register with `test@movieflux.app / hunter22` → land on Home.
3. Force-stop the app → re-open → land on Home (no re-login, because Firebase persists the session).
4. Profile → Log out → land on Login.
5. Log in with the same credentials → land on Home.
6. Log in with wrong password → see "Invalid email or password."
7. Try to register the same email again → see "An account with this email already exists."
8. Verify in the Firebase console that the user appears under **Authentication → Users**.

### 2B.18 — Open questions

1. **Email verification**: enforce verified email before allowing access to `MainGraph`? Adds a verification screen between Register and Main. Defer to a later phase unless required by stakeholders.
2. **Google / Apple sign-in**: enable now, or follow up in a dedicated phase? Requires SHA-1 (already collected in §2B.1) and a OneTap implementation.
3. **Anonymous accounts**: useful for trying the app before committing. Out of scope here unless prioritised.
4. **`google-services.json` distribution**: commit to the repo (public client data, simpler CI) or inject via CI secret (cleaner for a public OSS repo)?

---

## Phase 2C — Google Sign-In (Credential Manager + Firebase)

> **REMOVED FROM SCOPE.** Mocked `admin/1234` (Phase 2) is the only auth path. See README "Decisões fora do escopo" for rationale.

**Goal:** Add **"Continue with Google"** to the Login and Register screens, using **Credential Manager** (Google's current recommended API — `GoogleSignInClient` is deprecated as of late 2024) and exchanging the returned Google ID token for a Firebase credential. After this phase a user can either email/password-register or one-tap into the app with their Google account; both paths land in the same `FirebaseAuth.currentUser` and the rest of the app behaves identically.

### 2C.0 — Why Credential Manager (not `GoogleSignInClient`)

- `com.google.android.gms.auth.api.signin.GoogleSignIn*` is deprecated; Google's official guidance is **`androidx.credentials:credentials` + `com.google.android.libraries.identity.googleid`**.
- Credential Manager unifies passkeys, saved passwords, and "Sign in with Google" behind one bottom-sheet UI — fewer screens to design, better autofill behaviour.
- Works on API 26+ (our min SDK), with a Play Services fallback below API 34.

### 2C.1 — Firebase console: enable Google provider

1. Firebase console → **Authentication → Sign-in method → Google → Enable**.
2. Pick a **Project support email** (usually your developer email).
3. Save. The console will automatically generate an **OAuth 2.0 Web client ID** under **Project settings → Your apps → SDK setup and configuration → Web client ID**. **Copy this Web client ID** — it is what we pass to Credential Manager, **not** the Android client ID. (Common pitfall: passing the Android client ID returns `IdToken null`.)
4. Confirm the **debug SHA-1** from §2B.1 is registered under the Android app. If it is missing, add it now (Project settings → Your apps → Android app → Add fingerprint), then **re-download `google-services.json`** and replace `app/google-services.json`. For release builds, also register the **release SHA-1** from the upload key.

### 2C.2 — Gradle: Credential Manager + Google ID helper

In `gradle/libs.versions.toml`:

```toml
[versions]
credentials      = "1.3.0"
googleid         = "1.1.1"

[libraries]
androidx-credentials               = { group = "androidx.credentials", name = "credentials",                       version.ref = "credentials" }
androidx-credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth",    version.ref = "credentials" }
googleid                           = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
```

In `app/build.gradle.kts` `dependencies { … }`:

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services)
implementation(libs.googleid)
```

### 2C.3 — Store the Web client ID safely

The Web client ID is **not** a secret (it ships in the APK), but pinning it in source pollutes diffs. Put it in `local.properties` and surface it via `BuildConfig`, the same pattern as `TMDB_API_KEY` in §1.7:

`local.properties`:
```
GOOGLE_WEB_CLIENT_ID=123456789012-abcdefg.apps.googleusercontent.com
```

`app/build.gradle.kts` (`defaultConfig`):
```kotlin
val googleWebClientId = localProps.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
```

### 2C.4 — Extend `AuthRepository`

Add a new method that takes a Google ID token and exchanges it for a Firebase session:

```kotlin
interface AuthRepository {
    // … existing methods …
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult
}
```

`AuthRepositoryImpl.signInWithGoogleIdToken(idToken)`:
1. Build credential: `val credential = GoogleAuthProvider.getCredential(idToken, null)`.
2. `firebaseAuth.signInWithCredential(credential).await()`.
3. Map result the same way as email sign-in. New error mapping:

| Firebase exception | `AuthError` |
|---|---|
| `FirebaseAuthInvalidCredentialsException` (ID token rejected) | `InvalidCredentials` |
| `FirebaseAuthUserCollisionException` (email already linked to a different provider) | `EmailAlreadyInUse` *(reuse — Profile screen can later expose an account-linking flow)* |
| Other | same mappings as §2B.4 |

### 2C.5 — `data/auth/GoogleSignInHelper.kt`

A thin Android-only wrapper around Credential Manager so ViewModels can stay free of Android types. **Must be invoked from an `Activity` context** because Credential Manager attaches its bottom sheet to a window.

```kotlin
class GoogleSignInHelper @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    sealed class Result {
        data class Success(val idToken: String) : Result()
        data class Error(val type: AuthError) : Result()
        object Cancelled : Result()
        object NoCredentials : Result()   // no Google accounts on device
    }

    suspend fun signIn(activity: Activity): Result {
        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)   // false = also offer never-used accounts
            .setAutoSelectEnabled(true)             // returning users get one-tap
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential
                && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                Result.Success(googleCred.idToken)
            } else {
                Result.Error(AuthError.Unknown)
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.NoCredentials
        } catch (e: GetCredentialException) {
            Result.Error(AuthError.Unknown)
        }
    }

    suspend fun signOut(activity: Activity) {
        // Clears Credential Manager's cached selection so the next call shows the picker
        // again. Independent from firebaseAuth.signOut().
        CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
    }
}
```

Provide as `@Singleton` in `AuthModule` (§2B.3).

### 2C.6 — `LoginViewModel` / `RegisterViewModel` — `signInWithGoogle(activity)`

Both ViewModels grow the same method (factor into a shared `BaseAuthViewModel` only if a third caller appears — premature for two):

```kotlin
fun signInWithGoogle(activity: Activity) {
    viewModelScope.launch {
        _uiState.value = LoginUiState.Loading
        when (val r = googleSignInHelper.signIn(activity)) {
            is GoogleSignInHelper.Result.Success -> {
                when (val auth = authRepository.signInWithGoogleIdToken(r.idToken)) {
                    is AuthResult.Success -> {
                        authPreferences.cachedEmail = auth.email
                        _uiState.value = LoginUiState.Success
                    }
                    is AuthResult.Error -> _uiState.value = LoginUiState.Error(auth.type.toStringRes())
                }
            }
            GoogleSignInHelper.Result.Cancelled    -> _uiState.value = LoginUiState.Idle
            GoogleSignInHelper.Result.NoCredentials -> _uiState.value = LoginUiState.Error(R.string.auth_error_no_google_account)
            is GoogleSignInHelper.Result.Error     -> _uiState.value = LoginUiState.Error(r.type.toStringRes())
        }
    }
}
```

Inject `GoogleSignInHelper` alongside `AuthRepository`.

### 2C.7 — UI: Google button on Login and Register

Add a reusable component `view/components/GoogleSignInButton.kt`:

- Material3 `OutlinedButton` with the multi-colour Google "G" logo (`res/drawable/ic_google_logo.xml` — download from Google's brand guidelines and import via Vector Asset).
- Label: `stringResource(R.string.auth_continue_with_google)` ("Continue with Google").
- Height 52.dp, full width minus 24.dp horizontal padding (matches `PrimaryButton`).
- Border colour `TealGreenLight`, container `BackgroundDark`, label `TealGreenLight`.

Place on each auth screen **below** the primary button + above the "divider":

| # | Element |
|---|---|
| n | `PrimaryButton("Done"/"Create account")` |
| n+1 | Spacer 16.dp |
| n+2 | Row: `Divider(weight=1f)` + `Text("or", padding 8.dp)` + `Divider(weight=1f)`, horizontal padding 24.dp |
| n+3 | Spacer 16.dp |
| n+4 | `GoogleSignInButton(onClick = { vm.signInWithGoogle(activity) })` |

Get the `Activity` reference with:
```kotlin
val activity = LocalContext.current as Activity
```
(`MainActivity` is the only Activity; the cast is safe.)

Add strings:
```xml
<string name="auth_continue_with_google">Continue with Google</string>
<string name="auth_or_divider">or</string>
<string name="auth_error_no_google_account">No Google accounts found on this device. Add one in Settings to continue.</string>
```

### 2C.8 — Logout: clear Credential Manager state too

`ProfileViewModel.confirmLogout()` (§2B.13) now also calls `googleSignInHelper.signOut(activity)` so the next "Continue with Google" tap shows the account picker instead of silently re-signing-in the same user. Pass the activity into `confirmLogout(activity: Activity)` from `ProfileScreen` (`LocalContext.current as Activity`).

### 2C.9 — Manifest / ProGuard

- **Manifest:** no additions — Credential Manager has no required `<meta-data>` for Google sign-in (different from the legacy `GoogleSignInClient`, which needed `com.google.android.gms.version`).
- **ProGuard / R8:** the Credential Manager + GoogleId libraries ship their own consumer rules, so no manual `proguard-rules.pro` edits are required for a debug build. Validate again before the first release build.

### 2C.10 — Tests

- `GoogleSignInHelperTest` (unit) — mock `CredentialManager` (interface) with MockK; verify each thrown exception maps to the right `Result` variant.
- `AuthRepositoryImplTest` — add cases for `signInWithGoogleIdToken`: success, invalid token → `InvalidCredentials`, collision → `EmailAlreadyInUse`.
- `LoginViewModelTest` — add `signInWithGoogle` paths: success, cancelled (stays `Idle`), no credentials (error string), repo error.

### 2C.11 — Manual smoke gate

1. Fresh install on an emulator with a Google account configured (Settings → Passwords & accounts → Add account → Google).
2. Login screen → tap **Continue with Google** → bottom sheet appears → pick account → land on Home.
3. Force-stop → reopen → still on Home (Firebase session persists; Google credential cached).
4. Profile → Log out → land on Login. Tap **Continue with Google** again → the picker reappears (not auto-selected), confirming §2C.8 cleared the state.
5. Repeat on a device with **no** Google account → expect the "No Google accounts found" error.
6. In the Firebase console (Authentication → Users) the user has **google.com** listed under **Providers**, and email/password and Google variants of the same email collapse into one user record.
7. Register screen → tap **Continue with Google** with a brand-new account → user is created, no email-verification step required.

### 2C.12 — Open questions

1. **Account linking**: if a user first registered with email/password using `jane@gmail.com` and later taps Google for the same email, Firebase returns `EmailAlreadyInUse`. Do we want to *link* the providers (one `User` with both methods) or *block* the duplicate? Linking requires re-auth with the original method first.
2. **One Tap / auto sign-in on Login screen launch**: should the picker auto-show when Login mounts for returning users, or always require a tap? (`setAutoSelectEnabled(true)` already covers silent re-auth; auto-showing the sheet is a separate UX decision.)
3. **Release signing**: confirm the release SHA-1 is registered in Firebase before the first internal-test build, otherwise Google sign-in fails silently on release APKs.
4. **Sign in with Apple**: parallel implementation if iOS parity is required — different OAuth flow, separate phase.

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

> **REMOVED FROM SCOPE.** Mocked `admin/1234` (Phase 2) is the only auth path. See README "Decisões fora do escopo" for rationale.

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

> **Superseded by §10.4** — the README spec lives in Phase 10 (delivery blocker) so the reviewer-friendliness sections (`mockAuth` flavor, placeholder `google-services.json`) sit alongside the rest of the build-hardening work. Do not write the README from this section; follow §10.4.

---

## Phase 10 — Reviewer-Friendly Build + README (Delivery Blocker)

**Goal:** A fresh clone with only `TMDB_API_KEY` set must `./gradlew assembleDebug` and run. Today the project hard-depends on `google-services.json`, Sentry DSN, and (after Phase 2C) a Google Web Client ID — none of which a reviewer will have. This phase removes those build-time dependencies and ships the README that the challenge mandates.

Closes: CHALLENGE_VALIDATION §2.4 (README), §3.10 (Firebase reviewer risk), §3.11 (Sentry/Crashlytics reviewer risk), §3.12 (`Screen.Register` drift).

### 10.1 — Choice: placeholder secrets vs. Gradle flavors

Pick **A** (placeholder secrets) unless the reviewer experience is the explicit deliverable; **B** (flavors) is cleaner but doubles the CI matrix.

**A. Placeholder secrets (recommended for this delivery):**
- Commit a `google-services.json.sample` (with `your-app-id` / `your-project-id`) and a `setup.sh` (or README step) instructing the reviewer to copy it to `google-services.json`. The Firebase SDK initialises but no events leave the device (no real project).
- `local.properties` reads default to empty string; downstream code treats empty-string secrets as "disabled".
- Crashlytics, Sentry, and Firebase Performance call sites become no-ops when their secret is blank.

**B. `mockAuth` vs `firebaseAuth` flavors:**
- `productFlavors { mockAuth { … }; firebaseAuth { … } }`.
- `mockAuth` provides `AuthRepository = MockAuthRepository` (the original `admin / 1234`), skips Google Services plugin, builds without `google-services.json`.
- `firebaseAuth` requires the full setup from Phase 2B/2C.
- Default variant: `mockAuthDebug`.

Document the chosen path in the README.

### 10.2 — Make Crashlytics / Sentry / Firebase Perf safe when secrets are absent

- `CrashlyticsErrorSink` — at construction, read `FirebaseApp.getApps(context).isEmpty()`. If empty, set an `enabled = false` flag and short-circuit every method. Same for `FirebaseAnalyticsTracker` and any `FirebasePerformance.newTrace(...)` call.
- `SentryAnalyticsTracker` — in `MovieFluxApp.onCreate`, only call `SentryAndroid.init { … }` when `BuildConfig.SENTRY_DSN.isNotBlank()`. Otherwise inject a `NoopSentryTracker`.
- `CompositeAnalyticsTracker` already swallows per-sink exceptions (per Phase 9.1) — verify with a test that boots with no `google-services.json` present and asserts no crash, no exception in logcat.
- `MovieFluxApp.onCreate` order matters: Timber first, then Sentry (if enabled), then any Firebase code path. Firebase auto-init from the Gradle plugin happens earlier — guarded by the placeholder/flavor choice in §10.1.

### 10.3 — `Screen.Register` drift fix (carry-forward from Phase 2B.10)

If Phase 2B is shipped, ensure the corresponding edits actually landed:
- `Screen.Register : Screen("register")` exists in `navigation/Screen.kt`.
- `AuthGraph` includes the `composable(Screen.Register.route) { RegisterScreen(...) }` entry from §2B.11.
- `LoginScreen` accepts and forwards `onNavigateToRegister`.

**Phase 2B and Phase 2C are coupled** — Phase 2C (Google sign-in) extends `AuthRepository` and reuses the `RegisterScreen` introduced in 2B. If Phase 2B is **not** shipped (flavor A path with `MockAuthRepository`), explicitly drop **both Phase 2B and Phase 2C** from the delivery scope and remove `Screen.Register` from `navigation/Screen.kt`. Keeping 2C without 2B is not a valid configuration.

### 10.4 — `README.md` at project root

The README required by the challenge — sections taken from Phase 9.13 plus the reviewer-friendliness notes added here:

1. **Project overview** — one paragraph: what MovieFlux does, target SDK, stack one-liner.
2. **API_KEY + secrets setup** — exact lines for `local.properties` (`TMDB_API_KEY`, optional `SENTRY_DSN`, optional `GOOGLE_WEB_CLIENT_ID`); how to obtain a TMDB key; what each optional secret unlocks.
3. **`google-services.json`** — explain placeholder vs. real Firebase project, link to "How to create your own" steps from Phase 2B.1.
4. **Build & run** — `./gradlew assembleDebug` (Windows: `gradlew.bat`); minimum JDK 17; Android Studio version.
5. **Biometric testing** — how to enrol a fingerprint on the emulator (`Settings → Security → Fingerprint`) and how to test the post-login opt-in (Phase 11).
6. **Login credentials** — `admin / 1234` (mock flavor) or "create an account" (Firebase flavor).
7. **Navigation diagram** — ASCII version of the diagram in "Navigation Architecture (target state)" section above.
8. **Architecture decisions** — MVVM + Clean Architecture rationale; why Hilt; why Room+Flow; why nested NavHost.
9. **Observability stack** — table from Phase 9 intro.
10. **Tests** — how to run unit tests (`./gradlew test`); how to run benchmarks (`./gradlew :benchmark:connectedBenchmarkAndroidTest`); current coverage targets.
11. **AI usage disclosure** — which parts were scaffolded with Claude Code (plan generation, boilerplate); which decisions were human-made.
12. **Known limitations / out-of-scope** — single explicit list (e.g. no offline mode beyond favorites, no pull-to-refresh on Home, no deep-links).

### 10.5 — Smoke gate

1. `git clone` the repo to a clean directory.
2. Add only `TMDB_API_KEY=...` to `local.properties` (no Sentry, no Google Web Client ID).
3. Copy `google-services.json.sample` → `google-services.json` (flavor A) or select `mockAuthDebug` (flavor B).
4. `./gradlew clean assembleDebug` — must succeed.
5. Install and open the app — must reach Login screen with no crash.
6. Logcat — must show no `FATAL EXCEPTION`, no `Default FirebaseApp is not initialized`, no Sentry init error.

---

## Phase 11 — Auth Polish: Post-Login Biometric Prompt + Fallback UX

**Goal:** Close the biometric usability gaps the challenge explicitly evaluates. Today the user can only discover biometrics by visiting Profile, and a failed biometric attempt drops the user on Login with no explanation.

Closes: CHALLENGE_VALIDATION §1 "biometric opt-in after first login" (❌), §2.1, §3.1 (`onAuthenticationFailed` misuse), §3.2 (fallback UX), §3.13 (`biometricPrompted` flag).

### 11.1 — Add `biometricPrompted` flag to `AuthPreferences`

```kotlin
var biometricPrompted: Boolean
    get() = prefs.getBoolean(KEY_BIOMETRIC_PROMPTED, false)
    set(v) = prefs.edit().putBoolean(KEY_BIOMETRIC_PROMPTED, v).apply()
```

Cleared by `clear()` (logout resets the prompt so a re-login asks again).

### 11.2 — `BiometricOptInDialog`

`view/components/BiometricOptInDialog.kt` — Material3 `AlertDialog`:
- Title: "Enable biometric login?"
- Body: "Use your fingerprint or face to sign in faster next time."
- Confirm: "Enable" → `onEnable()` → writes `biometricEnabled = true`, `biometricPrompted = true`, then navigates to `MainGraph`.
- Dismiss: "Not now" → `onSkip()` → writes `biometricPrompted = true` only, then navigates to `MainGraph`.

### 11.3 — Wire dialog after successful login

The "should we prompt?" decision lives in `LoginViewModel` so it is unit-testable without Compose.

**ViewModel** — extend `LoginUiState.Success` to carry the flag and add `onBiometricChoice` to apply the user's answer:

```kotlin
sealed class LoginUiState {
    // … Idle, Loading, Error …
    data class Success(val shouldPromptBiometric: Boolean) : LoginUiState()
}

// inside LoginViewModel, after authRepository.signIn(...) returns Success:
val shouldPrompt = !authPreferences.biometricPrompted
    && biometricHelper.canAuthenticate() is Available
_uiState.value = LoginUiState.Success(shouldPromptBiometric = shouldPrompt)

fun onBiometricChoice(enabled: Boolean) {
    authPreferences.biometricEnabled = enabled
    authPreferences.biometricPrompted = true
}
```

**Screen** — `LoginScreen` only reads the flag and dispatches user input:

```kotlin
val state = uiState
if (state is LoginUiState.Success) {
    if (state.shouldPromptBiometric) {
        BiometricOptInDialog(
            onEnable = { vm.onBiometricChoice(enabled = true);  onLoginSuccess() },
            onSkip   = { vm.onBiometricChoice(enabled = false); onLoginSuccess() }
        )
    } else {
        LaunchedEffect(Unit) { onLoginSuccess() }
    }
}
```

Same pattern in `RegisterScreen` if Phase 2B shipped (reuse `RegisterUiState.Success(shouldPromptBiometric)` + `RegisterViewModel.onBiometricChoice`).

### 11.4 — Fix `BiometricHelper` callback semantics

Per AndroidX docs:
- `onAuthenticationError(errorCode, errString)` — **terminal** (lockout, cancel, no hardware). Call `onError(...)`.
- `onAuthenticationFailed()` — **transient** (single mis-scan; user retries on the same prompt). Do **not** call `onError(...)`. Either omit the override or `Timber.d("[BIOMETRIC] transient failed attempt")`.
- `onAuthenticationSucceeded(result)` — call `onSuccess()`.

### 11.5 — Biometric fallback UI in `MainActivity`

When `BiometricHelper.authenticate` returns `onError`, do not silently drop to `AuthGraph`. Instead:

1. Set a `BiometricGateState` (`Pending`, `Authenticated`, `Failed(reason: String)`).
2. On `Failed`, render a full-screen scaffold with:
   - Logo + "Biometric authentication required" headline.
   - Reason text (`"You cancelled"`, `"Too many attempts — try again later"`, etc., mapped from `BiometricPrompt.ERROR_*`).
   - **Retry biometric** button (re-runs `biometricHelper.authenticate`).
   - **Use password instead** button → navigates to `AuthGraph` (`FirebaseAuth.signOut()` or `authPreferences.clear()` first, depending on flavor).
3. Errors `ERROR_NO_BIOMETRICS`, `ERROR_HW_NOT_PRESENT`, `ERROR_HW_UNAVAILABLE` skip the retry button (only "Use password instead").

### 11.6 — Tests

- `LoginViewModelTest`: after `Success`, `shouldPromptBiometric` reflects `biometricPrompted == false && canAuthenticate == Available`.
- `LoginViewModelTest`: `biometricPrompted == true` → never prompts again.
- `AuthPreferencesTest` (new — instrumented): `clear()` resets `biometricPrompted` to `false`.
- `BiometricHelperTest`: assert `onAuthenticationFailed` does **not** invoke the error callback (use a fake `BiometricPrompt.AuthenticationCallback`).

### 11.7 — Smoke gate

1. Fresh install → log in → dialog appears once → tap "Enable" → restart app → biometric prompts at launch.
2. Fresh install → log in → tap "Not now" → restart app → goes straight to Home (no prompt).
3. With biometric enabled, fail the prompt three times → see "Too many attempts" fallback scaffold with "Use password instead" button.
4. Cancel the biometric prompt → see "You cancelled" with **Retry biometric** + **Use password instead** buttons.

---

## Phase 12 — Test Coverage Completion

**Goal:** Close the test coverage gaps the challenge evaluates ("Testes unitários — ViewModels **e Repositories**").

**Scope vs. Phase 9.12.** Phase 9.12 is the *comprehensive* test catalogue (all VMs, repository, observability core, Compose UI tests, macrobenchmarks). This phase is the *focused delivery push* for the rows of 9.12 that CHALLENGE_VALIDATION.md flagged as still missing on disk:

| Test target | Phase 9.12 row | Status in audit | Owner here |
|---|---|---|---|
| `MovieRepositoryImplTest` | 9.12 row 5 | ❌ (biggest gap) | §12.1 — extended scenarios |
| `DetailsViewModelTest` | 9.12 row 3 | ❌ | §12.2 |
| `ProfileViewModelTest` | 9.12 row 4 | ❌ | §12.3 |
| `AuthRepositoryImplTest` | 9.12 (not listed — added by 2B/2C) | ❌ | §12.4 (only if 2B shipped) |
| Everything else in 9.12 (HomeVM, FavoritesVM, LoginVM, BottomNavBar, AnalyticsTracker, JankReporter) | various | ✅ or partial | already on disk per audit — verify, don't re-author |

Closes: CHALLENGE_VALIDATION §1 "Unit tests — Repositories" (❌), §1 "Unit tests — ViewModels" (⚠️), §2.2, §2.3.

### 12.1 — `MovieRepositoryImplTest` (the biggest gap)

`app/src/test/java/com/example/movieflux/data/repository/MovieRepositoryImplTest.kt`. MockK on `RemoteDataSource` + `MovieDao` + mappers. Use `runTest` + Turbine.

| # | Scenario | Assert |
|---|---|---|
| 1 | `getPopularMovies(page=1)` returns network results joined with favorites | DAO `getAllFavorites()` returns `[1, 3]` → result has `isFavorite = true` only for movies with id 1 and 3 |
| 2 | `getPopularMovies` — cache-first behaviour | Second call within TTL returns cached values; `remoteDataSource` called only once |
| 3 | `getPopularMovies` — network failure with no cache | Throws `IOException` |
| 4 | `getPopularMovies` — network failure with cache | Returns cached values; no exception propagated |
| 5 | `searchMovies(query)` happy path | Delegates to `/search/movie`; results mapped to domain models with favorite flags |
| 6 | `searchMovies` — `HttpException` from network | Exception propagates so ViewModel can show `Error` |
| 7 | `toggleFavorite(movie)` with `isFavorite=false` | Calls `dao.insertFavorite(entity)`; no `delete` call |
| 8 | `toggleFavorite(movie)` with `isFavorite=true` | Calls `dao.deleteFavoriteById(id)`; no `insert` call |
| 9 | `getMovieDetail(id)` — happy path | Returns mapped domain model with `isFavorite` joined from DAO |
| 10 | `getMovieDetail(id)` — DAO query first, then network | Verify order: `dao.getFavoriteById(id)` called before `remoteDataSource.getMovieDetail(id)` |
| 11 | `getGenres()` — cached `Map<Int,String>` behind a mutex | Second call within process lifetime returns cached map; `remoteDataSource.getGenres()` called only once (drives Phase 13) |

### 12.2 — `DetailsViewModelTest`

`app/src/test/java/com/example/movieflux/view/details/DetailsViewModelTest.kt`:

- `loadDetail(id)` → `Success` with movie + genres mapped (genres come from `MovieDetailDto.genres` directly, per §3.9 fix in Phase 14).
- `loadDetail(id)` → repository throws → `Error(message)`.
- `toggleFavorite()` — optimistic flip, then persists; on persist failure (DAO throws) the UI reverts (Phase 14.1).
- `share(context)` — verify `Intent.ACTION_SEND`, `type = "text/plain"`, extra `EXTRA_TEXT` contains title and TMDB URL.

### 12.3 — `ProfileViewModelTest`

`app/src/test/java/com/example/movieflux/view/profile/ProfileViewModelTest.kt`:

- `setBiometricEnabled(true)` when `canAuthenticate == Available` → writes to `AuthPreferences`.
- `setBiometricEnabled(true)` when `canAuthenticate == NoneEnrolled` → emits `BiometricUnavailable(NoneEnrolled)` event, does **not** write.
- `confirmLogout()` (flavor B: Firebase) → calls `authRepository.signOut()` then `authPreferences.clear()` then emits `LogoutComplete`.
- `confirmLogout()` (flavor A: mock) → calls `authPreferences.clear()` and emits `LogoutComplete`.
- `requestLogout()` / `dismissLogoutDialog()` flip `showLogoutDialog`.

### 12.4 — `AuthRepositoryImplTest` (only if Phase 2B shipped)

Already specified in §2B.16 and §2C.10 — confirm those files exist and run green.

### 12.5 — Coverage gate (optional but recommended)

Add `jacoco` to `:app/build.gradle.kts`. Target: ≥ 70 % line coverage on `view/**/*ViewModel.kt` and `data/repository/**`. Fail CI under threshold.

---

## Phase 13 — Genre Mapping on Home + Cached Genres

**Goal:** Surface 1–2 genre chips on every Home card (and Favorites/Search), and cache the `/genre/movie/list` response so each Details/Home interaction does not re-fetch it.

Closes: CHALLENGE_VALIDATION §1 "Genre mapping on Home cards" (❌), §2.5, §3.8 (`getGenres()` cache).

### 13.1 — Repository-level genre cache

In `MovieRepositoryImpl`:

```kotlin
private val genresMutex = Mutex()
@Volatile private var cachedGenres: Map<Int, String>? = null

override suspend fun getGenres(): Map<Int, String> {
    cachedGenres?.let { return it }
    return genresMutex.withLock {
        cachedGenres ?: remoteDataSource.getGenres().genres
            .associate { it.id to it.name }
            .also { cachedGenres = it }
    }
}
```

Single network call per process lifetime. Invalidated only if the process is killed (acceptable — genres change ~yearly).

### 13.2 — Enrich `MovieModel` with genre **names** (not just IDs)

**Domain change (touches the domain layer — not just data).** Add a new field to `domain/model/MovieModel.kt`:

```kotlin
val genreNames: List<String> = emptyList()
```

Default value keeps the change backward-compatible — existing code paths (Details `MovieDetailMapper`, fakes in tests, hard-coded fixtures) continue to compile. Verify the repository populates it for every list response; downstream UIs (`MovieCard`, `MovieListItem`, future filters) can rely on the field being present, just sometimes empty.

`MovieModel` already has `genreIds: List<Int>`. The repository populates `genreNames` when building list responses:

```kotlin
override suspend fun getPopularMovies(page: Int): List<MovieModel> {
    val genres = getGenres()                          // cached
    val favorites = dao.getAllFavoriteIds().toSet()
    return remoteDataSource.getPopularMovies(page).results.map { dto ->
        movieMapper.toDomain(dto).copy(
            isFavorite  = dto.id in favorites,
            genreNames  = dto.genreIds.mapNotNull { genres[it] }
        )
    }
}
```

Same treatment in `searchMovies()`.

### 13.3 — Render chips on `MovieCard` / `MovieListItem`

`view/components/MovieCard.kt`:
- Show the first **2** genre names as small `AssistChip`s in a `Row` directly under the title (or overlaid on the poster gradient — verify against the design).
- Chip styling: `MaterialTheme.colorScheme.primaryContainer` background, `bodySmall` label, height 24.dp, no leading icon.
- `MovieListItem.kt`: same chips in a `Row` to the right of the poster, under the title.

### 13.4 — Tests

- Extend `MovieRepositoryImplTest` (§12.1 row 11) to assert `genreNames` is populated on returned models when the genres cache has data.
- Compose UI test (instrumented): `MovieCard` renders 2 chips when `movie.genreNames = ["Action", "Sci-Fi"]`.

---

## Phase 14 — Error-Handling Polish

**Goal:** Wire the small but visible robustness gaps the validation report flagged. Each item is a single-file change.

Closes: CHALLENGE_VALIDATION §3.3 (favorite rollback), §3.4 (pagination errors), §3.5 (search exceptions), §3.6 (`movieId` parsing), §3.7 (empty-search distinct copy), §3.9 (drop redundant genre fetch in Details).

### 14.1 — `DetailsViewModel.toggleFavorite` rollback on failure

```kotlin
fun toggleFavorite() {
    val current = (uiState.value as? DetailsUiState.Success)?.movie ?: return
    val optimistic = current.copy(isFavorite = !current.isFavorite)
    _uiState.value = DetailsUiState.Success(optimistic)
    viewModelScope.launch {
        runCatching { repository.toggleFavorite(current) }
            .onFailure {
                _uiState.value = DetailsUiState.Success(current)   // revert
                _events.send(DetailsEvent.ShowError(R.string.error_toggle_favorite))
            }
    }
}
```

Add `DetailsEvent` channel for one-shot snackbar messages.

### 14.2 — `HomeViewModel.loadNextPage` surfaces pagination errors

- Catch block emits a one-shot `HomeEvent.PaginationError(message)` via `Channel<HomeEvent>`.
- `HomeScreen` collects events and shows a `Snackbar` ("Couldn't load more. Tap to retry.") with a `Retry` action that re-invokes `loadNextPage()`.
- Footer item in the LazyGrid/LazyColumn renders a retry button when the last page failed (use a small `errorOnPage: Int?` field in `HomeUiState.Success`).

### 14.3 — `HomeViewModel.activeMovies` catches search exceptions

`flatMapLatest` chain wraps the search branch:

```kotlin
flatMapLatest { query ->
    if (query.isBlank()) popularFlow
    else flow { emit(repository.searchMovies(query)) }
        .catch { emit(emptyList()); _events.trySend(HomeEvent.SearchError(it.localizedMessage)) }
}
```

Without `.catch`, the StateFlow collector dies silently and the UI hangs on the previous state.

### 14.4 — Distinguish "no search results" from "no popular movies"

- `HomeUiState.Success` already holds `query: String`. Add (or surface) `isQueryActive: Boolean`.
- `EmptyView` accepts a `message: String` parameter; `HomeScreen` passes either `stringResource(R.string.empty_no_popular)` or `stringResource(R.string.empty_no_results_for, query)`.
- New strings:
  ```xml
  <string name="empty_no_popular">No popular movies right now. Pull to refresh.</string>
  <string name="empty_no_results_for">No results for "%1$s".</string>
  ```

### 14.5 — `DetailsViewModel` fails loudly on missing `movieId`

Replace:
```kotlin
val movieId = savedStateHandle.get<String>("movieId")?.toInt() ?: 0
```
with:
```kotlin
val movieId = checkNotNull(savedStateHandle.get<String>("movieId")?.toIntOrNull()) {
    "Details route requires a valid integer movieId argument"
}
```

The nav contract guarantees the arg exists (`Screen.Details("details/{movieId}")` + `navArgument("movieId") { type = NavType.IntType }`). If the assertion fires in QA, the bug is in navigation, not data — failing loud is correct.

While here: declare `movieId` as `NavType.IntType` in the `composable(Screen.Details.route, arguments = listOf(navArgument("movieId") { type = NavType.IntType }))` definition so the framework parses the int instead of leaving the cast to the ViewModel.

> **Breaking change to §3.5 / §3.6.** The current navigation snippets register `composable(Screen.Details.route)` without `arguments = …`, and `Screen.Details.createRoute(id: Int)` interpolates the int into the path string. When §14.5 lands, update §3.5 / §3.6 to add the `navArgument("movieId") { type = NavType.IntType }` entry, and update `DetailsScreen`/`DetailsViewModel` to read the argument as `Int` (not `String?.toInt()`).

### 14.6 — Drop redundant `/genre/movie/list` fetch in Details

`MovieDetailDto.genres` already returns full `GenreDto` objects. `DetailsViewModel.loadDetail` currently calls `repository.getGenres()` and intersects on IDs — redundant.

Change `MovieDetailMapper` to read `dto.genres.map { it.name }` directly. Remove the `getGenres()` call from `DetailsViewModel.loadDetail`. Saves one HTTP round-trip per Details open. The repository cache from §13.1 is still useful for Home (the `/movie/popular` endpoint returns IDs only).

### 14.7 — Tests

- `DetailsViewModelTest`: rollback test — when DAO throws on `toggleFavorite`, `uiState` reverts to the pre-optimistic value and a `ShowError` event fires.
- `HomeViewModelTest`: `loadNextPage` on `IOException` emits `PaginationError`; `errorOnPage` is set; retry re-attempts.
- `HomeViewModelTest`: search query that throws — `_events` emits `SearchError`; `uiState` stays valid (empty list, not crashed).
- `HomeViewModelTest`: assert `isQueryActive` toggles correctly with `setSearchQuery("")` vs `setSearchQuery("foo")`.

---

## Dependency Map

```
Phase 1 (infra)             <- blocks everything else
Phase 2 (auth — mock)       <- needs Phase 1
Phase 2B (Firebase auth)    <- needs Phase 2 (replaces mocked sign-in path)
Phase 2C (Google sign-in)   <- needs Phase 2B
Phase 3 (nav restructure)   <- needs Phase 2 (login navigates into MainGraph)
Phase 4 (profile)           <- needs Phase 3 + Phase 2 (AuthPreferences, BiometricHelper)
Phase 5 (home grid)         <- needs Phase 3 + Phase 1 (Retrofit)
Phase 6 (search/states)     <- needs Phase 5
Phase 7 (details)           <- needs Phase 3 + Phase 5 (MovieCard reuse)
Phase 8 (favorites)         <- needs Phase 5 + Phase 7 (Details nav target)
Phase 9 (obs + tests)       <- needs all of the above
Phase 10 (reviewer build + README) <- needs Phase 9 (analytics sinks must already exist to be no-op'd); blocks delivery
Phase 11 (auth polish)      <- needs Phase 2 (BiometricHelper, AuthPreferences) + Phase 4 (Profile reuses the same prefs)
Phase 12 (test completion)  <- needs Phase 7, 8, 4 (all VMs must exist); Phase 11 adds biometric-prompt tests
Phase 13 (genre chips)      <- needs Phase 5 (MovieCard) + Phase 1 (RemoteDataSource.getGenres)
Phase 14 (error polish)     <- needs Phase 5, 6, 7 (touches Home + Details VMs)
```

Phases 4 and 5 can run in parallel after Phase 3 (different files, no shared code). Phases 7 and 8 can run in parallel after Phase 5 if two people split work. **Phases 11, 13, 14 are all parallelisable** after their dependencies above are met (different files); Phase 12 should land last so it covers the behaviour changes from 11/13/14. **Phase 10 is the delivery blocker** — even though it can technically run earlier, it depends on every analytics sink existing so it can no-op them safely.

---

## Summary of changes vs. previous plan

| Area | Before | After |
|---|---|---|
| Navigation | Single flat NavHost | Nested NavHost: AuthGraph + MainGraph (with inner NavHost for tabs) |
| Tab bar | None | Material3 `NavigationBar` with 3 tabs (Home / Favorites / Profile), hidden on Details |
| Profile screen | Did not exist | New screen with user header, biometric toggle, app version, About, Logout |
| Logout | Button on Home, instant logout | Button on Profile, confirmation dialog, clears `AuthPreferences` (+ `firebaseAuth.signOut()` if Phase 2B shipped, + `clearCredentialState` if Phase 2C shipped) |
| Biometric opt-in | First-login `AlertDialog` after Success | Removed in original Phase 4; **reinstated in Phase 11** with a `biometricPrompted` flag so the prompt fires exactly once on first login, plus a Profile toggle for later changes |
| Biometric fallback UX | Silent drop to Login on failure | **Phase 11.5** adds a full-screen scaffold with Retry / Use password instead, and §11.4 fixes the `onAuthenticationFailed` vs `onAuthenticationError` misuse |
| Authentication | Mocked `admin / 1234` only | Mocked **or** Firebase Email/Password (Phase 2B) **or** Firebase + Google sign-in via Credential Manager (Phase 2C); selectable per delivery via flavor / placeholder choice in Phase 10 |
| Registration | No flow | New `RegisterScreen` + `Screen.Register` route (Phase 2B), reused by Google sign-in (Phase 2C) |
| Phase count | 7 days → 9 phases | **14 phases**: 1, 2, 2B, 2C, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 (Phases 2B/2C optional, 10–14 close CHALLENGE_VALIDATION.md gaps) |
| `Screen` sealed class | 4 routes | 7 routes + 2 graph routes: AuthGraph, MainGraph, Login, Register (2B), Home, Favorites, Profile, Details |
| Tests | Home / Repository / Login VM tests | + Profile / Favorites / Details VM tests + AuthRepository (2B/2C) + BottomNavBar instrumented + AnalyticsTracker + JankReporter + 5 Macrobenchmarks; Phase 12 is the focused delivery push for the rows still missing on disk |
| Movie grid item | Original spec: heart overlay, star rating | Matches `docs/design/Filme Item.png`: poster + title (1-line) + `X/5` rating + heart `IconButton` at bottom-right of poster; **Phase 13** adds genre chips |
| Home / Favorites list mode | Grid only | Toggle in TopAppBar switches between Grid (`MovieCard`) and List (`MovieListItem`); per-screen independent state |
| Login screen | Stub | Full design built from `docs/design/Login.png` using existing color tokens; SVG logo + "MovieFlux" wordmark; pill button; + "Forgot password?" + "Create an account" links (2B); + "Continue with Google" (2C) |
| Genre mapping | IDs only on Home; Details refetches `/genre/movie/list` | **Phase 13** caches `Map<Int,String>` in repo; `MovieModel.genreNames` populated for list responses; **Phase 14.6** drops the Details refetch (uses embedded `MovieDetailDto.genres`) |
| Error handling | Optimistic toggle without rollback; pagination errors swallowed; search exceptions kill the StateFlow; `movieId` silently coerces to 0 | **Phase 14**: rollback on favorite failure; pagination snackbar + retry; `.catch { }` on search; loud `checkNotNull` on `movieId`; `NavType.IntType` declared on the Details route |
| Observability | Timber + AnalyticsTracker interface | Full stack: Timber + JankStats + Firebase Performance + Crashlytics + Sentry + Macrobenchmark/Baseline Profile + Compose Compiler reports; sampled at 15% in release; funnel tracker; per-screen render timing; cold-start delta |
| Crash reporting | Not addressed | Crashlytics (fatal + ANR) + Sentry (non-fatal + perf transactions); both wired through `CompositeAnalyticsTracker`; **Phase 10.2** makes all SDKs no-op when their secrets are blank so a clean clone still builds |
| Reviewer build | Hard dependency on real `google-services.json` + Sentry DSN | **Phase 10**: choose placeholder secrets or `mockAuth`/`firebaseAuth` flavors; fresh clone with only `TMDB_API_KEY` must build & launch |
| README | Did not exist | **Phase 10.4** (supersedes 9.13): 12-section spec including API key setup, biometric testing, navigation diagram, architecture decisions, observability stack, AI-usage disclosure |
