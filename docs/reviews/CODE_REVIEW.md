# MovieFlux — Code Review

> Review date: 2026-05-20
> Scope: full `app/src/main` source. This document only **identifies** issues and suggests directions — no code was changed.

The architecture (MVVM + Clean, Hilt, Compose, Room cache + favorites, StateFlow `UiState`) is solid and the offline-first repository is genuinely well thought out. The issues below are ordered by severity.

---

## 🔴 Critical

### 1. Hardcoded credentials / fake authentication
`view/login/LoginViewModel.kt:37`
```kotlin
if (username == "admin" && password == "1234") {
```
Authentication is a hardcoded string comparison. There is no backend, no token, and the credentials are shipped in the APK. `isLoggedIn` is just a local boolean.
- **Risk:** anyone can "log in"; the credentials are trivially extractable from the APK.
- **Fix:** integrate a real auth backend (or, if this is intentionally a demo, document it loudly and gate it behind a debug flag). At minimum move the comparison out of a `String` literal.

### 2. API key leaked to logcat in release builds
`di/NetworkModule.kt:25-48`
```kotlin
level = if (BuildConfig.DEBUG) BODY else HttpLoggingInterceptor.Level.BASIC
```
The api-key interceptor is registered **before** the logging interceptor, so the logging interceptor sees the full URL including `?api_key=...`. `Level.BASIC` logs the request line — meaning the **TMDB API key is written to logcat in production**.
- **Fix:** use `Level.NONE` for release, or strip the `api_key` query param from logged URLs. Never log network at `BASIC`+ in release.

### 3. Release build has shrinking/obfuscation disabled
`app/build.gradle.kts:39-40`
```kotlin
release {
    isMinifyEnabled = false
```
No R8/ProGuard in release means: no obfuscation, no dead-code stripping, larger APK, and the (already-embedded) API key and credential strings are easier to recover.
- **Fix:** `isMinifyEnabled = true` and `isShrinkResources = true` for release; add keep rules as needed.

---

## 🟠 High

### 4. Non-null DTO fields will crash on unexpected JSON
`data/remote/MovieDto.kt`
```kotlin
data class MovieDto(
    val id: Int,
    val title: String,
    val overview: String,
    val vote_average: Double,
    val genre_ids: List<Int>,
)
```
Gson does not enforce Kotlin non-null. If TMDB omits any of these (e.g. a movie with no `genre_ids`, missing `overview`), the field is set to `null` via reflection and the first non-null access throws NPE — surfacing as a parse failure. `HomeViewModel` has a catch-all that converts this to an error state, but Details (`DetailsViewModel.loadDetail`) only catches `IOException`/`HttpException`, so a malformed body there is an **uncaught crash**.
- **Fix:** make optional fields nullable with defaults (`genre_ids: List<Int>? = null`, `overview: String? = null`) and default them in the mapper; or add a custom deserializer.

### 5. EncryptedSharedPreferences & SharedPreferences I/O on the main thread
`MainActivity.kt:51-53`, every ViewModel `init`, `data/preferences/*`
`AuthPreferences` builds `EncryptedSharedPreferences` (Keystore-backed, notoriously slow) at injection time, and `MainActivity.onCreate` reads it synchronously to compute `needsBiometric`. `UiPreferences`/`ThemeRepository` are also read synchronously in ViewModel `init`. This is blocking disk + crypto work on the UI thread → jank / ANR risk on cold start (ironic given the app ships JankStats).
- **Fix:** migrate preferences to **DataStore** (async, Flow-based). At minimum, do the initial reads off the main thread.

### 6. Favorites show no genre names on cold start
`data/repository/MovieRepositoryImpl.kt:65-72`
```kotlin
val genres = cachedGenres ?: emptyMap()
```
`getFavorites()` reads the in-memory `cachedGenres`, which is only populated after `getPopularMovies`/`getGenres` runs. If the user opens the app and goes straight to Favorites (or relaunches via biometric into the Favorites tab), `cachedGenres` is `null` → every favorite shows an empty genre list. It's also not reactive: the flow won't re-emit when genres later load.
- **Fix:** persist genres (Room table or DataStore) and join from there, or expose genres as a Flow and `combine` it.

### 7. `Context` passed into the ViewModel
`view/details/DetailsViewModel.kt:76-87`
```kotlin
fun share(context: Context) { ... context.startActivity(...) }
```
Starting activities / building intents belongs in the UI layer. Passing `Context` into a ViewModel is a leak hazard and breaks testability/clean-architecture boundaries.
- **Fix:** emit a `DetailsEvent.Share(title, url)` and let the Composable build the chooser intent.

---

## 🟡 Medium

### 8. Use-case layer is inconsistent / largely bypassed
`view/home/HomeViewModel.kt:34-38` injects **both** `GetPopularMoviesUseCase` and `MovieRepository`, calling the repository directly for search, favorites, and toggle. Only one use case (`GetPopularMoviesUseCase`) exists and it's a trivial pass-through (`domain/usecase/GetPopularMoviesUseCase.kt`). The "Clean Architecture" boundary is half-applied.
- **Fix:** either commit to use cases for all domain operations, or drop the layer and let ViewModels depend on the repository consistently. Don't do both.

### 9. User-facing strings hardcoded (and in Portuguese) in ViewModels
`HomeViewModel.kt:119` `"Algo deu errado"`, `LoginViewModel.kt:46` `"Credenciais inválidas"`, `ProfileViewModel.kt:65-73` biometric messages.
These bypass `strings.xml`, can't be localized, and couple the ViewModel to copy. (Details already does this correctly with `R.string.error_toggle_favorite` + an event — follow that pattern everywhere.)
- **Fix:** emit string resource IDs / typed error enums; resolve in the Composable.

### 10. Raw exception messages shown to users
`HomeViewModel.kt:119-126`, `DetailsViewModel.kt:52-54` use `e.message ?: ...`. `HttpException.message` is like `"HTTP 404 Not Found"` and `IOException.message` is often a host/socket string — technical, untranslated, and occasionally leaking internal detail.
- **Fix:** map exception types to friendly, localized messages; keep the raw message only in logs.

### 11. Unbounded caches
- `movie_cache` (`MovieDao`) is upserted but **never evicted** — grows indefinitely as the user paginates over time.
- `cachedGenres` in the repository never expires; if TMDB adds/renames a genre the app never refreshes it within a process lifetime.
- **Fix:** add a TTL / max-rows eviction for the cache, and a refresh strategy for genres.

### 12. `allowBackup="true"`
`AndroidManifest.xml:9`
Auto-backup is on. Although `backup_rules.xml`/`data_extraction_rules.xml` are referenced, the encrypted-prefs comment in `AuthPreferences` acknowledges restore-to-new-device corruption. Confirm the auth prefs and Room DB are excluded from backup, otherwise a "logged in" flag (and cached data) can travel between devices.
- **Fix:** verify the backup rule files actually exclude `auth_prefs` and the database.

---

## 🟢 Low / Polish

- **`ProfileViewModel.confirmLogout` doesn't reset other prefs intent clearly** — `authPreferences.clear()` wipes `biometricEnabled`/`biometricPrompted` too, which may re-prompt biometric opt-in on next login. Confirm that's intended.
- **`HomeViewModel.loadNextPage`** collects a Flow that emits both cache and network for the next page; `distinctBy { it.id }` saves correctness but the page can briefly append twice. Consider `.take(...)`/`.last()` semantics for pagination, or a dedicated paginated endpoint method.
- **No retry/backoff or OkHttp cache** in `NetworkModule`; every transient failure is surfaced immediately.
- **`MovieDto` uses snake_case** with `@file:Suppress("ConstructorParameterNaming")`. A `@SerializedName` annotation + idiomatic Kotlin names would be cleaner than suppressing the lint rule project-wide for the file.
- **JaCoCo `fileFilter` excludes `**/view/**` only for `*Preview*`** but the whole `navigation`, `di`, `ui/theme` packages are excluded — fine, but Composable screens are counted; confirm coverage numbers aren't misleading.
- **`Timber` only planted in debug** (`MovieFluxApp.kt:11`). Good — but that means `trackError` does nothing in release. If analytics matters, wire a real reporting tree (Crashlytics, etc.) for release.
- **`getGenres()` double-checked locking** is correct, but the network call inside `withLock` serializes all callers behind one in-flight request — acceptable, just noting it blocks concurrent first-load callers.

---

## Summary table

| # | Severity | Area | Issue |
|---|----------|------|-------|
| 1 | 🔴 | Auth | Hardcoded `admin/1234` credentials |
| 2 | 🔴 | Security | API key logged to logcat in release |
| 3 | 🔴 | Build | R8/minify disabled in release |
| 4 | 🟠 | Parsing | Non-null DTO fields crash on bad JSON (Details has no catch-all) |
| 5 | 🟠 | Perf | Encrypted/SharedPreferences I/O on main thread |
| 6 | 🟠 | Bug | Favorites missing genre names on cold start |
| 7 | 🟠 | Arch | `Context` passed into ViewModel for sharing |
| 8 | 🟡 | Arch | Use-case layer inconsistent / bypassed |
| 9 | 🟡 | i18n | Hardcoded PT strings in ViewModels |
| 10 | 🟡 | UX | Raw exception messages shown to users |
| 11 | 🟡 | Data | Unbounded movie cache & genre cache |
| 12 | 🟡 | Security | `allowBackup=true` — verify exclusions |

### Suggested order of attack
1. Fix the release security trio (#1–3) before any public build.
2. Harden parsing (#4) and move prefs off the main thread (#5).
3. Address the favorites genre bug (#6) and ViewModel `Context` leak (#7).
4. Tackle the architecture/UX polish (#8–12) incrementally.
