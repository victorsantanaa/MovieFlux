# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build                  # Build the project
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew testDebugUnitTest --tests "com.example.movieflux.SomeTest"  # Run a single test class
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew lint                   # Run lint checks
./gradlew clean build            # Clean and rebuild
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Architecture

MovieFlux follows **MVVM + Clean Architecture** across three layers. The data flow is:

```
Composable Screen → ViewModel (StateFlow/UiState) → UseCase → Repository → Remote (Retrofit) / Local (Room)
```

### Domain Layer (`domain/`)
The innermost layer with no Android dependencies. Contains:
- `model/MovieModel.kt` — the canonical domain entity (not Room entities or Retrofit DTOs)
- `repository/MovieRepository.kt` — interface defining all data operations
- `usecase/` — thin wrappers over repository calls (e.g., `GetPopularMoviesUseCase`)

### Data Layer (`data/`)
Implements the domain interfaces. Never imported by the UI layer directly.
- `remote/` — Retrofit interface (`RemoteDataSource`), DTOs (`MovieDto`, `GenreDto`), and response wrappers
- `local/` — Room `MovieDao` and `MovieEntity` (table: `favorites`)
- `repository/MovieRepositoryImpl` — combines remote + local, owns all data-fetching logic
- `mapper/` — two distinct mappers: `MovieMapper` (DTO → Domain) and `EntityMapper` (Room Entity ↔ Domain)

The image base URL `https://image.tmdb.org/t/p/w500` is a constant in `MovieMapper`.

### UI Layer (`ui/` + `view/` + `navigation/`)
**MVVM with Jetpack Compose** and Material 3. Each screen has a paired ViewModel that exposes a single `UiState` sealed class via `StateFlow`.
- `view/<screen>/` — Composable screen + `ViewModel` per feature (`home/`, `login/`, `details/`, `favorites/`)
- `view/components/` — shared composables (`PrimaryButton`, `SecondaryButton` with `ButtonSize` enum: SMALL, MEDIUM, LARGE)
- `navigation/` — `Screen` sealed class defines the four routes: Login (start), Home, Details (with `movieId` param), Favorites; wired in `AppNavHost`
- `ui/theme/` — `MovieFluxTheme` wraps Material3 with Teal Green as primary color

## Key Technical Decisions

- **TMDB API** — The Movie Database endpoints: `/movie/popular`, `/search/movie`, `/genre/movie/list`, `/movie/{id}`.
- **Favorites** are persisted in Room; the `isFavorite` flag on `MovieModel` is set by joining remote results with the local favorites table.
- **Navigation** uses Compose Navigation (`navigation-compose`); `AppNavHost` owns all route declarations.
- **Kotlin version:** 2.2.10 | **Compile/Target SDK:** 36 | **Min SDK:** 26 | **Java:** 11