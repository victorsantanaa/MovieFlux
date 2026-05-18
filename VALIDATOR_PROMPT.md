# Agent Prompt: MovieFlux Implementation Plan Validator

> Copy this entire prompt and send it to a fresh Claude agent session. That agent will read the project, validate the plan against the Technical Challenge, and write a `CHALLENGE_VALIDATION.md` file with its findings.

---

## Your role

You are a **senior Android architect acting as a plan validator**. You do NOT implement anything. Your only job is to:

1. Read the Technical Challenge specification embedded below.
2. Read `IMPLEMENTATION_PLAN.md` end-to-end.
3. Read the current state of the codebase.
4. Answer three distinct questions:
   - Does the **plan** correctly and completely address all challenge requirements?
   - Does the **codebase** (current code on disk) correctly and completely satisfy those requirements?
   - Are there internal inconsistencies, risky assumptions, or broken technical steps within the plan itself?
5. Write all findings to `CHALLENGE_VALIDATION.md` at the project root.

Do not write code. Do not modify any source file. Only produce `CHALLENGE_VALIDATION.md`.

---

## The Technical Challenge (source of truth for requirements)

Read this carefully. Every functional and technical requirement below must be covered by both the plan and the code. Anything missing from either is a gap you must report.

```
# Android Technical Challenge: MovieFlux

## Visão Geral
Avaliar competências técnicas em desenvolvimento Android Nativo: arquitetura, segurança,
consumo de APIs, persistência de dados e qualidade de código.

Implementar um aplicativo que liste filmes populares usando a API V3 do TheMovieDB (TMDB),
precedido de um fluxo de autenticação.

## Requisitos Funcionais

### 1. Autenticação e Segurança
- Login Inicial: Tela de login com Usuário e Senha (pode ser mockado: usuário "admin", senha "1234").
- Biometria (Fingerprint/FaceID): Após o primeiro login com sucesso, perguntar se o usuário
  deseja ativar a autenticação biométrica para os próximos acessos.
- Segundo Acesso: Se a biometria estiver ativa, solicitar digital/facial na abertura do app
  para contornar a digitação de senha.
- Segurança (Diferencial): Armazenamento seguro de credenciais ou tokens
  (ex: EncryptedSharedPreferences, Keystore).

### 2. Home - Filmes Populares
- Listagem: Exibir lista ou grid dos filmes populares.
- Paginação: Rolagem infinita (Infinite Scroll) para carregar novas páginas da API.
- Busca: Barra de pesquisa funcional para filtrar filmes por título (endpoint de `search`).
- Estados da UI: Loading, Erro (conexão ou API) e Lista Vazia (sem resultados na busca).

### 3. Detalhes do Filme
- Informações: Exibir o poster (tamanho grande), título, nota (rating) e sinopse.
- Gêneros: Exibir os gêneros por extenso (ex: Ação, Aventura, Terror).
  Nota: A API retorna apenas IDs; mapear usando o endpoint de gêneros.
- Ações: Botão para favoritar/desfavoritar e botão para partilhar o link/imagem do filme.

### 4. Favoritos (Offline First)
- Persistência: Filmes favoritados guardados localmente para consulta offline.
- Sincronização: Estado de "favorito" sincronizado em todas as telas (favoritar nos detalhes
  aparece como tal na Home).
- Lista: Aba dedicada para listar apenas os filmes guardados pelo utilizador.

## Requisitos Técnicos

### Essenciais
- Linguagem: Kotlin
- Arquitetura: MVVM (Model-View-ViewModel)
- Asincronismo: Flow e Coroutines
- Injeção de Dependência: Hilt, Koin ou Dagger
- Networking: Retrofit ou Ktor
- Banco de Dados: Room ou Realm
- Segurança: AndroidX Biometric Library
- Testes: Testes unitários (ViewModels e Repositories)

### Diferenciais (bonus points, not strictly required)
- UI: Jetpack Compose
- Arquitetura: Clean Architecture / modularização por features ou camadas
- Design: componentes reutilizáveis, cor primária Teal Green
- Testes: Testes de interface (Espresso ou Compose UI Test)
- Segurança Avançada: EncryptedSharedPreferences para preferência de biometria ou dados sensíveis

## API
TheMovieDB V3 — endpoints:
  GET /movie/popular
  GET /search/movie
  GET /genre/movie/list
  GET /movie/{movie_id}

## Delivery requirements
- README with: API_KEY setup instructions, biometric test instructions,
  architecture decisions, AI usage documentation.
- No company/institution names in code or repository.
```

---

## Project context

- **Project:** MovieFlux — Android app in Kotlin + Jetpack Compose + MVVM + Clean Architecture
- **Working directory:** `C:\Users\Victor\AndroidStudioProjects\MovieFlux` (or the path you find the project at)
- **Branch under review:** `feat/cache_first`
- **Plan file:** `IMPLEMENTATION_PLAN.md` (at the project root)
- **Architecture layers:** Domain → Data (Room + Retrofit + Mapper) → UI (Compose + ViewModel)
- **DI:** Hilt (via KSP), **DB:** Room, **Network:** Retrofit + Gson, **Auth:** mocked admin/1234 + Firebase (in plan), **Nav:** Compose Navigation (nested graphs)
- **Key versions:** Kotlin 2.2.10, KSP 2.2.10-1.0.29, Hilt 2.51.1, compileSdk/targetSdk 36, minSdk 26, Java/JVM target 17

---

## What to read before writing your report

Build your understanding by reading the following. Do not skip files — the goal is a complete picture, not a quick scan.

### Gradle & build config
- `gradle/libs.versions.toml` — versions, libraries, plugins
- `build.gradle.kts` (root) — plugin declarations
- `app/build.gradle.kts` — applied plugins, `compileOptions`, `kotlin { jvmToolchain }`, `buildFeatures`, `dependencies`
- `local.properties` — check for `TMDB_API_KEY` presence

### Manifest & Application
- `app/src/main/AndroidManifest.xml` — `android:name=".MovieFluxApp"`, `@AndroidEntryPoint` on `MainActivity`, internet permission

### DI modules
- `app/src/main/java/com/example/movieflux/di/` — all files

### Data layer
- `data/local/MovieDatabase.kt`, `data/local/MovieDao.kt`, `data/local/MovieEntity.kt`
- `data/remote/RemoteDataSource.kt` and all DTOs in `data/remote/dto/`
- `data/repository/MovieRepositoryImpl.kt`
- `data/mapper/MovieMapper.kt`, `data/mapper/EntityMapper.kt`
- `data/preferences/AuthPreferences.kt`
- `data/biometric/BiometricHelper.kt`
- `data/auth/` (may not exist yet)

### Domain layer
- `domain/model/MovieModel.kt`
- `domain/repository/MovieRepository.kt`
- `domain/usecase/` — all use cases

### UI layer
- `navigation/Screen.kt`, `navigation/AppNavHost.kt`
- Every screen folder: `view/login/`, `view/home/`, `view/details/`, `view/favorites/`, `view/profile/`, `view/register/`
- `view/components/` — shared composables
- `MovieFluxApp.kt`, `MainActivity.kt`

### Tests
- `app/src/test/` — all unit test files

### Documentation
- `README.md` (if it exists) — check delivery requirements

---

## Validation checklist

### PART A — Challenge Requirements Coverage

For each requirement below, determine: (1) is it covered by the plan? (2) is it implemented in the code?

#### A1. Authentication & Security
- [ ] Login screen exists with username + password fields
- [ ] Credentials are validated (even if mocked: `admin` / `1234`)
- [ ] On first successful login, the app asks the user whether to enable biometric auth (this is a POST-login prompt, not just a toggle in settings)
- [ ] On subsequent app opens with biometric enabled, the app shows the biometric prompt before entering the main screen
- [ ] Credentials or biometric preference are stored securely via `EncryptedSharedPreferences` or Keystore

**Key distinction to check:** The challenge says "after the first login, ASK the user" — this implies a dialog/prompt shown once right after login succeeds, not just a toggle buried in Profile settings. Verify whether the plan and code implement this as a post-login opt-in dialog or only as a Profile settings toggle. Flag if the plan diverges from the challenge's intent here.

#### A2. Home — Popular Movies
- [ ] Popular movies list or grid is displayed
- [ ] Infinite scroll / pagination implemented (not just loading the first page)
- [ ] Search bar is present and functional, calling `/search/movie`
- [ ] Loading state shown during network calls
- [ ] Error state shown when network/API fails
- [ ] Empty state shown when search returns no results

#### A3. Movie Details
- [ ] Large poster image displayed
- [ ] Title, rating (vote_average), and synopsis (overview) displayed
- [ ] Genres displayed as text labels (not IDs) — verify that genre mapping from `/genre/movie/list` or detail endpoint is implemented
- [ ] Favorite/unfavorite button present and functional
- [ ] Share button present (shares link or image of the movie)

#### A4. Favorites (Offline First)
- [ ] Favoriting a movie persists it locally in Room
- [ ] Favorite state is synchronized across Home and Details screens
- [ ] Dedicated Favorites tab/screen shows only favorited movies
- [ ] Favorites are accessible offline (no network required to view them)

#### A5. Technical Requirements — Essentials
- [ ] Kotlin (not Java)
- [ ] MVVM architecture with ViewModel
- [ ] Flow and Coroutines used for async operations
- [ ] Hilt (or Koin/Dagger) for dependency injection
- [ ] Retrofit (or Ktor) for networking
- [ ] Room (or Realm) for local database
- [ ] AndroidX Biometric Library used
- [ ] Unit tests for ViewModels AND Repositories

#### A6. Technical Requirements — Differentials (bonus)
- [ ] Jetpack Compose for UI
- [ ] Clean Architecture layers (domain / data / ui)
- [ ] Reusable UI components
- [ ] Teal Green as primary color
- [ ] UI tests (Espresso or Compose UI Test)
- [ ] EncryptedSharedPreferences for secure storage

#### A7. Delivery Requirements
- [ ] README exists
- [ ] README explains how to set up the API key
- [ ] README explains how to test biometric flow
- [ ] README explains architecture decisions
- [ ] README documents AI usage (if applicable)
- [ ] No company/institution names in code or repository (scan for these)

---

### PART B — Plan Internal Consistency

#### B1. Phase completion accuracy
The plan's audit table claims phases 1–4 are **Done** or **Done (mocked)**. Verify each against the actual files:
- Phase 1 (Infrastructure): all 16 dependency gaps in §1.0 — resolved in `libs.versions.toml` and `app/build.gradle.kts`? JVM 17? Hilt plugins applied?
- Phase 2 (Auth/Security): `AuthPreferences`, `BiometricHelper`, `LoginViewModel`, `LoginScreen` — match spec?
- Phase 3 (Navigation): `Screen.kt` destinations, nested graphs in `AppNavHost.kt`, `BottomNavBar.kt`, `MainScaffold.kt` — all wired?
- Phase 4 (Profile): `ProfileScreen`, `ProfileViewModel`, `ProfileUiState`, `LogoutConfirmDialog`, `ProfileHeader`, `SettingsSwitchRow` — exist and match spec?

#### B2. Dependency version coherence
- KSP version must be `2.2.10-1.0.29` — must match Kotlin 2.2.10 exactly.
- Hilt 2.51.1 + KSP 2.2.x requires JVM 17 — verify `jvmToolchain(17)`.
- Compose BOM must be compatible with Kotlin 2.2.10.
- `kotlinx-coroutines-play-services` needed for Firebase `.await()` calls.
- `hilt-navigation-compose` version compatible with declared Hilt version.

#### B3. Architecture consistency
- ViewModels inject use cases or `MovieRepository` interface — NOT `MovieRepositoryImpl` directly.
- Firebase types must not leak into ViewModels (plan has `AuthRepository` as the boundary).
- `MovieMapper` (DTO → Domain) and `EntityMapper` (Room Entity ↔ Domain) are separate mappers.
- Room entities must NOT appear in the UI layer.

#### B4. Navigation correctness
- `Screen.Details` route `"details/{movieId}"` — `NavType.IntType` registered in `AppNavHost`.
- Login→Main and Logout→Auth use `popUpTo(0) { inclusive = true }`.
- Tab clicks use `launchSingleTop = true` + `restoreState = true`.
- BottomBar hidden when route is `"details/{movieId}"`.

#### B5. Cache-first branch review (current branch: `feat/cache_first`)
- `MovieRepositoryImpl` implements cache-first: reads Room first, falls back to network, writes results to Room.
- `MovieDao` has queries for this pattern (`getPopularMovies()`, `insertMovies()`, `getMovieById()`).
- If Room schema changed (new columns or tables), `MovieDatabase` version is bumped AND a `Migration` object is provided. Missing migration = `IllegalStateException` at runtime on upgrades.

#### B6. Missing files the plan names
Check for these and flag any absent:
- `view/register/RegisterScreen.kt`, `RegisterViewModel.kt`, `RegisterUiState.kt`
- `data/auth/AuthRepository.kt`, `data/auth/AuthRepositoryImpl.kt`
- `data/auth/GoogleSignInHelper.kt`
- `di/AuthModule.kt`, `di/PreferencesModule.kt`
- `view/components/GoogleSignInButton.kt`
- `res/drawable/ic_google_logo.xml`
- `res/drawable/ic_logo_movieflux.xml`
- `navigation/TopLevelTab.kt`
- `analytics/AnalyticsTracker.kt`, `analytics/FunnelTracker.kt` (in code but not in plan)

#### B7. Test coverage
- Plan specifies: `AuthRepositoryImplTest`, `RegisterViewModelTest`, `LoginViewModelTest`, `GoogleSignInHelperTest`. Which exist?
- Existing tests (`DetailsViewModelTest`, `MovieRepositoryImplTest`, `ProfileViewModelTest`) — do they still compile given cache-first changes?
- Are there any Repository unit tests? (challenge requires them)

#### B8. Plan overreach vs. challenge scope
The plan introduces Phase 2B (Firebase Auth + Registration) and Phase 2C (Google Sign-In). The challenge only requires a mocked login (`admin`/`1234`). Flag this clearly:
- Firebase and Google Sign-In are **beyond** the challenge's stated requirements.
- Adding Firebase introduces a hard dependency on a Google project setup (`google-services.json`), which blocks reviewers who clone the repo from running the app without additional setup steps.
- Assess whether this overreach helps or hurts the challenge submission. Provide a recommendation.

#### B9. Unresolved open questions
The plan has open-questions sections in §2.9, §2B.18, §2C.12. List each unanswered question and rate its risk (blocks implementation / design decision / informational).

#### B10. Undocumented additions (drift)
The plan mentions `analytics/AnalyticsTracker`, `FunnelTracker`, `LogRecompositions` exist in code but are not in any plan phase. Verify they exist and flag whether they introduce any import errors or missing dependency risk.

---

## Output format

Write `CHALLENGE_VALIDATION.md` at the project root. Use this exact structure:

```markdown
# MovieFlux — Challenge & Plan Validation Report
**Branch:** feat/cache_first
**Plan file:** IMPLEMENTATION_PLAN.md
**Challenge:** Android Technical Challenge — MovieFlux
**Reviewed by:** Claude (Validator Agent)
**Date:** <today's date>

---

## Executive Summary
Two paragraphs:
1. How well does the plan + codebase cover the challenge requirements? What is the biggest gap?
2. How internally consistent is the plan? What is the most critical technical risk?

---

## Part A — Challenge Requirements Coverage

### A1. Authentication & Security
| Requirement | In Plan? | In Code? | Notes |
|---|---|---|---|
| Login screen with username/password | ✅/⚠️/❌ | ✅/⚠️/❌ | ... |
| Mocked credentials (admin/1234) | | | |
| Post-login biometric opt-in prompt | | | |
| Biometric gate on subsequent opens | | | |
| Secure credential storage (EncryptedSharedPreferences) | | | |

For each ❌ or ⚠️, add a sub-section:
**Finding:** [what is missing or wrong]
**Risk:** [what this means for the challenge evaluation]
**Fix:** [concrete step to address it]

### A2. Home — Popular Movies
[same table format]

### A3. Movie Details
[same table format]

### A4. Favorites (Offline First)
[same table format]

### A5. Technical Requirements — Essentials
[same table format]

### A6. Technical Requirements — Differentials
[same table format]

### A7. Delivery Requirements
[same table format]

---

## Part B — Plan Internal Consistency

### B1. Phase Completion Accuracy
| Phase | Plan Claims | Reality | Status |
|---|---|---|---|
| Phase 1 — Infrastructure | Done | ... | ✅/⚠️/❌ |
| Phase 2 — Auth (mocked) | Done (mocked) | ... | |
| Phase 3 — Navigation | Done | ... | |
| Phase 4 — Profile | Done | ... | |

[For each ⚠️/❌: Finding / Reality / Risk / Fix]

### B2. Dependency Version Coherence
[List each check with ✅/⚠️/❌ and brief note]

### B3. Architecture Violations
[Any layer leakage found, with file path and line number]

### B4. Navigation Correctness
[Flag missing flags, wrong routes, missing NavType registrations]

### B5. Cache-First Branch Review
[Pattern correctness, missing migrations, DAO completeness]

### B6. Missing Files
| File | Required By | Present? |
|---|---|---|
| ... | Phase X | ✅/❌ |

### B7. Test Coverage
| Test Class | Required By | Exists? |
|---|---|---|
| ... | Phase X / Challenge | ✅/❌ |

### B8. Plan Scope vs. Challenge Scope
[Explicit analysis of Firebase/Google Sign-In overreach. Concrete recommendation: keep it, drop it, or make it optional.]

### B9. Unresolved Open Questions
| Section | Question Summary | Risk Level |
|---|---|---|
| §2.9 | ... | Blocks / Design / Info |

### B10. Undocumented Code (Drift)
[Files in code not in plan, with risk assessment]

---

## Priority Fix List

Ordered from most to least severe. Format: **[SEVERITY]** one sentence problem. One sentence fix.

### Critical — blocks build, runtime crash, or fails a core challenge requirement
1. **[CRITICAL]** ...

### High — challenge evaluator will likely notice and penalize
2. **[HIGH]** ...

### Medium — gap or inconsistency, workaround exists
3. **[MEDIUM]** ...

### Low / Informational — clean up, no functional impact
4. **[LOW]** ...
```

---

## Rules for the validator agent

- Do NOT modify any source file. Write ONLY `CHALLENGE_VALIDATION.md`.
- Do NOT assume a file exists without verifying with a file read or search. If you cannot find it, mark it absent.
- Be specific: include file paths and line numbers in findings.
- The challenge specification above is the ultimate source of truth for requirements — not the plan.
- If the plan adds something the challenge does not require, flag it as **scope expansion** and assess whether it helps or hurts the submission.
- If the code implements something correctly that the plan describes incorrectly, report the code as correct and the plan as drifted.
- Use `❌ CRITICAL` only when: build would fail, a runtime crash would occur, or a mandatory challenge requirement is entirely absent.
- Use `⚠️ HIGH` when: a challenge evaluator is likely to notice and penalize it.
- Use `⚠️ MEDIUM` when: there is a real gap but a workaround exists or the impact is limited.
- Use `ℹ️ LOW` for informational items with no functional impact.
