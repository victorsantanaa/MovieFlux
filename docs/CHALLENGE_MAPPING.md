# MovieFlux — Mapeamento Requisitos do Desafio → Código

> Análise somente leitura. Este documento mapeia cada requisito do **Android Technical Challenge: MovieFlux** para o local exato no código que o atende. Nenhum código foi alterado.
>
> Convenção de caminhos: todos os caminhos são relativos a `app/src/main/java/com/example/movieflux/`, salvo indicação. Os testes ficam em `app/src/test/...` e `app/src/androidTest/...`.

---

## 1. Autenticação e Segurança

| Requisito | Onde no código | Observações |
|---|---|---|
| **Login inicial** (mock `admin` / `1234`) | `view/login/LoginViewModel.kt:38` (`username == "admin" && password == "1234"`); UI em `view/login/LoginScreen.kt`; estados em `view/login/LoginUiState.kt` | Credenciais fixas no ViewModel, conforme permitido. |
| **Pergunta de opt-in biométrico após 1º login** | `LoginViewModel.kt:41-43` decide `shouldPromptBiometric` (só se ainda não perguntado e biometria disponível); UI do diálogo `view/login/BiometricOptInDialog.kt`; confirmação `LoginViewModel.confirmBiometricOptIn()` `:52-56` | Opt-in persistido via `AuthPreferences.biometricEnabled` + `biometricPrompted`. |
| **Segundo acesso via biometria** (pular senha no relaunch) | `MainActivity.kt:57-113` — lê `isLoggedIn && biometricEnabled`, verifica disponibilidade e exibe `BiometricGate`. Lógica do gate em `view/biometric/BiometricGate.kt` + `view/biometric/BiometricGateState.kt` | Comentário em `MainActivity.kt:64-68`: biometria é a **única** forma de pular o login no relaunch. |
| **AndroidX Biometric Library + fallback** | `data/biometric/BiometricHelper.kt` — checagem de disponibilidade `BiometricManager` (`:24-33`), `BiometricPrompt` (`:35-59`). UI de fallback ("usar senha" / "tentar novamente") em `BiometricGate.kt:44-80` | Dependência declarada em `app/build.gradle.kts:127` (`androidx.biometric`). Critério de avaliação (fallback) atendido por `onUsePassword` / `onRetry`. |
| **Armazenamento seguro (EncryptedSharedPreferences / Keystore)** | `data/preferences/AuthPreferences.kt` — `EncryptedSharedPreferences` + `MasterKey` (AES256_GCM/SIV), recuperação de corrupção `:48-64`, warm-up fora da main thread `awaitReady()` `:27-29` | Dependência `androidx.security.crypto` em `build.gradle.kts:126`. É o diferencial "Segurança Avançada". |
| Wiring de DI para auth/biometria | `di/PreferencesModule.kt` (AuthPreferences); `BiometricHelper` é `@Singleton` construído via `@Inject` | |
| **Testes** | Unitários: `app/src/test/.../LoginViewModelTest.kt`. Instrumentados: `androidTest/.../view/biometric/BiometricGateTest.kt`, `view/login/BiometricOptInDialogTest.kt`, `view/login/LoginScreenTest.kt` | |

---

## 2. Home — Filmes Populares

| Requisito | Onde no código | Observações |
|---|---|---|
| **Listagem (lista ou grid)** | `view/home/HomeScreen.kt`; toggle de modo de visualização `view/components/ViewModeToggle.kt` + `ViewMode.kt`; card `view/components/MovieCard.kt`, linha `MovieListItem.kt` | Preferência grid/lista persistida via `data/preferences/UiPreferences.kt`. |
| **Paginação (infinite scroll)** | `view/home/HomeViewModel.kt` — `loadNextPage()` `:138-162`, contadores `currentPage`/`canLoadMore` `:47-48`. Repositório: `MovieRepositoryImpl.getPopularMovies(page)` `:39-77` | `currentPage` só é incrementado após anexar a página com sucesso (`:152`) — sem rollback de contador. UseCase: `domain/usecase/GetPopularMoviesUseCase.kt`. |
| **Busca por título** | `HomeViewModel.kt` — `_searchQuery` + debounce 300ms `:67-82`, `setSearchQuery()` `:177`. UseCase `domain/usecase/SearchMoviesUseCase.kt`. Repositório `MovieRepositoryImpl.searchMovies()` `:120-131`. API `RemoteDataSource.search()` `:14-17`. UI `view/components/SearchBar.kt` | Usa TMDB `/search/movie`. |
| **Estado: Loading** | `view/home/HomeUiState.kt` (`Loading`); renderizado em `HomeScreen.kt:165` (`MovieCardSkeleton`) | Componente skeleton `view/components/MovieCardSkeleton.kt`. |
| **Estado: Erro** | `HomeUiState.Error`; `HomeScreen.kt:169` (`ErrorView`). Mapeamento de exceções → mensagem: `view/common/ErrorMapper.kt` (`toUserMessageRes`). Evento de erro de paginação `HomeViewModel.kt:171-175` | `view/components/ErrorView.kt`. |
| **Estado: Lista vazia** | `HomeScreen.kt:177-184` — distingue busca vazia (`isQueryActive`) de lista vazia via `EmptyView` (`view/components/EmptyView.kt`) | |
| **Testes** | `app/src/test/.../HomeViewModelTest.kt`, `view/common/ErrorMapperTest.kt`, `data/repository/MovieRepositoryImplTest.kt` | |

---

## 3. Detalhes do Filme

| Requisito | Onde no código | Observações |
|---|---|---|
| **Poster grande, título, nota, sinopse** | `view/details/DetailsScreen.kt` — poster `AsyncImage` `:166`, nota `:194` (`%.1f / 10`). URL base da imagem de detalhe `w780` em `data/mapper/MovieDetailMapper.kt:6` | Imagem maior que na listagem (`w500`). |
| **Gêneros por extenso (mapear IDs → nomes)** | `MovieRepositoryImpl.getGenres()` `:103-118` (cache com TTL de 24h, protegido por mutex) chamando `RemoteDataSource.genres()` (`/genre/movie/list`). Nomes anexados: populares `:57`, busca `:128`, detalhe via `MovieDetailMapper.kt:16`. Campo `MovieModel.genreNames` (`domain/model/MovieModel.kt:11`). Renderizado em `DetailsScreen.kt` (`genreNames`) | O DTO de detalhe já traz objetos de gênero completos (`MovieDetailDto.genres`), então o detalhe mapeia nomes direto; populares/busca mapeiam IDs pelo mapa de gêneros em cache. É o critério de avaliação "mapeamento de IDs de gêneros". |
| **Favoritar / desfavoritar** | `view/details/DetailsViewModel.kt:70-85` — atualização otimista + rollback em caso de falha. UseCase `domain/usecase/ToggleFavoriteUseCase.kt` | |
| **Partilhar link/imagem** | `DetailsViewModel.share()` `:87-100` emite `DetailsEvent.Share(title, url)`; intent construído na UI `DetailsScreen.kt:91-97` (`Intent.ACTION_SEND` + `createChooser`) | ViewModel mantido livre de `Context`; URL `https://www.themoviedb.org/movie/{id}`. |
| **Testes** | `app/src/test/.../view/details/DetailsViewModelTest.kt` | |

---

## 4. Favoritos (Offline First)

| Requisito | Onde no código | Observações |
|---|---|---|
| **Persistência local (Room)** | `data/local/MovieDao.kt` (tabela `favorites`), `data/local/MovieEntity.kt`, DB `data/local/MovieDatabase.kt`. Entity↔domain `data/mapper/EntityMapper.kt`. DI `di/DatabaseModule.kt` | Nomes de gênero persistidos nas linhas de favoritos (comentário `MovieRepositoryImpl.kt:84-92`), então o cold-start offline direto em Favoritos ainda exibe os gêneros. |
| **Sincronização de estado entre telas** | A Home recomputa `isFavorite` combinando com o flow de favoritos: `HomeViewModel.kt:84-105` (`combine(... getFavorites() ...)`, `:95-97`). O detalhe lê um flow ciente de favoritos. Fonte única da verdade = flow `dao.getFavorites()` (`MovieDao.kt:15-16`) | Favoritar em Detalhes/Favoritos/Home re-emite pelo mesmo Room Flow → todas as telas atualizam. É o critério de avaliação "eficiência na sincronização". |
| **Aba dedicada de favoritos** | `view/favorites/FavoritesScreen.kt`, `view/favorites/FavoritesViewModel.kt`, `FavoritesUiState.kt`. Wiring da aba `navigation/TopLevelTab.kt`, `view/main/MainScaffold.kt`, `view/components/BottomNavBar.kt` | Favoritos também tem filtro de busca local `FavoritesViewModel.kt:39-43`. |
| **Leitura offline-first de populares** | `MovieRepositoryImpl.getPopularMovies()` `:42-76` — emite o cache primeiro, depois a rede; engole erros IO/HTTP quando há cache (`:72-76`). Tabela de cache `movie_cache` (`CachedMovieEntity.kt`), evicção `MovieDao.evictCacheBeyond` `:44-49` | |
| **Testes** | `app/src/test/.../FavoritesViewModelTest.kt`, `MovieRepositoryImplTest.kt` | |

---

## 🛠 Requisitos Técnicos

### Essenciais

| Requisito | Onde |
|---|---|
| **Kotlin** | Todo o código; toolchain `build.gradle.kts:67-69` (JVM 17), Kotlin 2.2.10 (conforme CLAUDE.md). |
| **MVVM** | Um ViewModel + `UiState` por feature: `view/{home,login,details,favorites,profile}/`. |
| **Flow + Coroutines** | `StateFlow`/`Channel` em todos os ViewModels; retornos `Flow` em `MovieRepository.kt` / `MovieRepositoryImpl.kt`; `viewModelScope.launch` por toda parte. |
| **DI (Hilt)** | `di/` (`NetworkModule`, `DatabaseModule`, `RepositoryModule`, `PreferencesModule`), `analytics/di/AnalyticsModule.kt`; `@HiltViewModel`, `MovieFluxApp.kt` (`@HiltAndroidApp`), `MainActivity` `@AndroidEntryPoint`. Plugin `build.gradle.kts:14`. |
| **Networking (Retrofit)** | `data/remote/RemoteDataSource.kt`; client/key/base URL `di/NetworkModule.kt`. Deps `build.gradle.kts:89-93`. |
| **Banco de dados (Room)** | `data/local/`; deps `build.gradle.kts:96-98`. |
| **AndroidX Biometric** | `data/biometric/BiometricHelper.kt`; dep `build.gradle.kts:127`. |
| **Testes unitários (ViewModels e Repositories)** | `app/src/test/...` — `HomeViewModelTest`, `LoginViewModelTest`, `FavoritesViewModelTest`, `DetailsViewModelTest`, `ProfileViewModelTest`, `MovieRepositoryImplTest`, `UseCaseTest`, `ErrorMapperTest`, `AnalyticsTrackerTest`. Deps de teste: JUnit, coroutines-test, MockK, Turbine (`build.gradle.kts:132-136`). |

### Diferenciais (todos presentes)

| Diferencial | Onde |
|---|---|
| **Jetpack Compose** | Toda a camada de UI (`view/`, `ui/theme/`); plugin compose `build.gradle.kts:13`. |
| **Clean Architecture / camadas** | `domain/` (model, interface de repositório, usecases) ← `data/` (remote/local/repository/mapper) ← `view/`. Garantido pelo teste Konsist `app/src/test/.../architecture/KonsistArchitectureTest.kt`. |
| **Componentes reutilizáveis + Teal Green** | `view/components/` (PrimaryButton/SecondaryButton com `ButtonSize`, cards, SearchBar, etc.); cores `ui/theme/Color.kt` + `Theme.kt`. |
| **Testes de UI (Compose UI Test)** | `app/src/androidTest/...` — `LoginScreenTest`, `BiometricGateTest`, `BiometricOptInDialogTest`, `ComponentsUiTest`, `MovieCardTest`, `ThemeSelectorTest`. Deps `build.gradle.kts:138-147`. |
| **EncryptedSharedPreferences** | `data/preferences/AuthPreferences.kt` (ver §1). |

---

## 📡 Integração com API

| Endpoint | Onde (`data/remote/RemoteDataSource.kt`) |
|---|---|
| `GET /movie/popular` | `:9-12` `getPopular(page)` |
| `GET /search/movie` | `:14-17` `search(query)` |
| `GET /genre/movie/list` | `:19-20` `genres()` |
| `GET /movie/{movie_id}` | `:22-25` `getMovieDetail(id)` |

A API key é injetada por requisição via interceptor OkHttp `di/NetworkModule.kt:33-46`; base URL `https://api.themoviedb.org/3/` `:59`.

---

## 📝 Instruções de Entrega

| Item | Onde / Status |
|---|---|
| **Repositório público** | (externo — verificar se o GitHub/GitLab está público). |
| **Configurar API_KEY** | ✅ **RESOLVIDO** — Mecanismo no código: `build.gradle.kts:4-8` lê `TMDB_API_KEY` de `local.properties` → `BuildConfig.TMDB_API_KEY` (`:35`). Documentado em `README.md:16-25`. |
| **Como testar biometria** | ✅ **RESOLVIDO** — `README.md:41-64` (setup do AVD, `adb emu finger touch 1`, login mock, diálogo de opt-in, relaunch via force-stop, reset via `pm clear`). |
| **Decisões de arquitetura / bibliotecas** | ✅ **RESOLVIDO** — `README.md:68-114` (diagrama em camadas + justificativa por camada) e `README.md:116-176` (bibliotecas categorizadas). |
| **Uso de IA (Prompt Engineering)** | ✅ **RESOLVIDO** — `README.md` seção "Uso de IA" lista ferramentas/papéis **e** a subseção "Metodologia: pipeline de 3 agentes (Prompt Engineering)" que aponta para `docs/prompts/` (Validator→Architect→Executor), `docs/plans/` e `docs/reviews/`. Ver "Evidência de Engenharia de IA" abaixo. |
| **Confidencialidade (sem nome de empresa)** | ✅ Genérico — `namespace = com.example.movieflux`, perfil `admin@movieflux.app`. Sem nome de empresa no README. ⚠️ `movieflux-release.jks` continua **não rastreado e não ignorado** — questão de higiene do repositório, não vazamento de confidencialidade (ver Gaps #2). |

> O `README.md` existe na raiz do repositório e **cobre os quatro itens de documentação**. Os gaps #1 (conteúdo do README) e #2 (documentação de IA) da lista original estão resolvidos.

### Evidência de Engenharia de IA (já no repositório)

O repositório contém um pipeline completo de prompt engineering multi-agente — material forte para o item "Uso de IA" da entrega:

| Artefato | Caminho | Papel |
|---|---|---|
| Prompt do Validator | `docs/prompts/VALIDATOR_PROMPT.md` | Audita plano + código vs. desafio → `CHALLENGE_VALIDATION.md` |
| Prompt do Architect | `docs/prompts/ARCHITECT_PROMPT.md` | Converte o relatório de validação em `REVISED_IMPLEMENTATION_PLAN.md` (só planeja, não escreve código) |
| Prompt do Architect (feature) | `docs/prompts/ARCHITECT_FEATURE_PROMPT.md` | Mesmo padrão, escopado por feature individual |
| Prompt do Executor | `docs/prompts/EXECUTOR_PROMPT.md` | Engenheiro de execução; implementa o plano fase a fase, um commit por fase |
| Relatório de validação | `docs/reviews/CHALLENGE_VALIDATION.md` | Saída do Validator |
| Planos | `docs/plans/IMPLEMENTATION_PLAN.md`, `REVISED_IMPLEMENTATION_PLAN.md`, planos de feature | Entradas/saídas do Architect |
| Code reviews | `docs/reviews/CODE_REVIEW.md` | Passada de revisão multi-modelo |

A separação Validator → Architect → Executor (com contratos rígidos "não escreva código" / "não tome decisões" por papel) **é** a engenharia de prompt.

**Status: exposto no README.** Foi adicionada a subseção "Metodologia: pipeline de 3 agentes (Prompt Engineering)" sob "Uso de IA" no `README.md`. Ela linka os três prompts em `docs/prompts/`, explica cada papel e seus guard-rails, e inclui um exemplo concreto antes/depois (o Validator detectou o uso incorreto de `onAuthenticationFailed` → o Architect especificou o `Fix-4A` → o Executor o tornou um no-op em `BiometricHelper.kt`). Nenhum trabalho adicional é necessário no item de IA.

---

## ⚖️ Critérios de Avaliação — Referência rápida

| Critério | Onde verificar |
|---|---|
| Biometria AndroidX + fallback | `BiometricHelper.kt`, `BiometricGate.kt:44-80`, `MainActivity.kt:86-108` |
| Mapeamento de IDs de gênero → string | `MovieRepositoryImpl.getGenres()` `:103-118`; `MovieDetailMapper.kt:16` |
| Sincronização de favoritos | `HomeViewModel.kt:84-105`; flow `MovieDao.getFavorites()` |
| Tratamento de exceções/rede | `MovieRepositoryImpl.kt:72-76,157-162`; `view/common/ErrorMapper.kt`; `HomeViewModel.kt:123-135` |
| Qualidade/cobertura de testes | `app/src/test/`, `app/src/androidTest/`; JaCoCo + Konsist + Detekt via `./gradlew codeQuality` (`build.gradle.kts:222-226`) |

---

## ➕ Funcionalidades além do escopo do desafio (e por que agregam valor)

O desafio não pede os itens abaixo — eles foram adicionados para demonstrar maturidade de engenharia (escalabilidade, observabilidade, qualidade e UX). Cada um é coeso com a arquitetura existente (MVVM + Clean Architecture) e não compromete os requisitos obrigatórios.

### Produto / UX

| Funcionalidade | Onde no código | Por que é bom |
|---|---|---|
| **Tela de Perfil** | `view/profile/ProfileScreen.kt`, `ProfileViewModel.kt`, `ProfileUiState.kt`; header `view/components/ProfileHeader.kt` | Dá um lar para logout, toggle de biometria e tema sem poluir as telas principais. Centraliza preferências do usuário num lugar previsível. Mostra a versão do app (`BuildConfig.VERSION_NAME`). |
| **Bottom navigation (TabBar)** | `navigation/TopLevelTab.kt`, `view/main/MainScaffold.kt`, `view/components/BottomNavBar.kt` | O desafio só exige "aba dedicada de favoritos"; a tab bar entrega isso de forma idiomática (Material 3) e ainda escala para Home/Favoritos/Perfil com `launchSingleTop` + restauração de estado. Esconde-se na tela de detalhe. |
| **Toggle de biometria no Perfil** | `ProfileViewModel.setBiometricEnabled()` `:59-77` | Permite ligar/desligar a biometria após o opt-in inicial, com mensagens específicas por motivo de indisponibilidade (`NoHardware`/`NoneEnrolled`/`Unavailable`). Complementa o requisito de opt-in pós-login. |
| **Tema claro/escuro/sistema** | `ui/theme/ThemeMode.kt` (`SYSTEM/LIGHT/DARK`), `data/preferences/ThemeRepository.kt`, seletor `view/profile/ThemeSelector.kt`, aplicado em `MainActivity.kt:75-77` | Acessibilidade e preferência do usuário; persistido e reativo via `StateFlow`. Demonstra theming Material 3 além do mínimo (Teal Green). |
| **Modos de visualização lista/grid persistidos** | `view/components/ViewMode.kt` + `ViewModeToggle.kt`; persistência `data/preferences/UiPreferences.kt`; usado em Home e Favoritos | O desafio aceita "lista **ou** grid"; oferecer ambos com preferência persistida por tela é um diferencial de UX. |
| **Busca dentro de Favoritos** | `FavoritesViewModel.kt:39-43` | Filtragem local instantânea (sem rede) sobre a lista offline — coerente com o princípio offline-first. |
| **Confirmação de logout** | `view/components/LogoutConfirmDialog.kt`; `ProfileViewModel.requestLogout/confirmLogout` | Evita logout acidental (que limpa as `EncryptedSharedPreferences`). Boa prática de UX para ação destrutiva. |
| **Splash screen + edge-to-edge** | `MainActivity.kt:46-55` (`installSplashScreen`, `setKeepOnScreenCondition`), `enableEdgeToEdge()` `:50` | A splash segura a tela enquanto o estado de auth (Keystore, lento) é lido fora da main thread — evita ANR de cold start. Edge-to-edge é o padrão moderno de Android. |
| **Logo / ícone adaptativo** | `view/components/MovieFluxLogo.kt`, `res/drawable/movie_flux_*`, `res/mipmap-anydpi-v26/` | Acabamento visual e identidade; ícone adaptativo segue as diretrizes atuais de launcher. |

### Robustez / dados

| Funcionalidade | Onde no código | Por que é bom |
|---|---|---|
| **Cache offline-first de populares** | tabela `movie_cache` (`data/local/CachedMovieEntity.kt`), `MovieRepositoryImpl.getPopularMovies()` `:42-76` | O desafio só exige favoritos offline; cachear a lista popular dá abertura instantânea e resiliência a falhas de rede em todas as telas, não só nos favoritos. |
| **Evicção de cache (cap de linhas)** | `MovieDao.evictCacheBeyond()` `:44-49`, `MAX_CACHE_ROWS = 500` (`MovieRepositoryImpl.kt:34`) | Impede o cache de crescer indefinidamente — higiene de armazenamento que muitos projetos esquecem. |
| **Cache do mapa de gêneros com TTL + Mutex** | `MovieRepositoryImpl.getGenres()` `:103-118` (TTL 24h, double-checked locking) | Evita refetch de `/genre/movie/list` a cada tela e é seguro sob concorrência. Resolve o critério de mapeamento de gêneros de forma eficiente. |
| **Recuperação de corrupção do EncryptedSharedPreferences** | `AuthPreferences.recreateAfterCorruption()` `:48-64` | Em backup/restore ou reset do Keystore, em vez de crashar no startup, recria o keyset (usuário só é deslogado uma vez). Tratamento de borda raro mas crítico. |
| **Mapeamento de erros localizado** | `view/common/ErrorMapper.kt` (`toUserMessageRes`), testado em `ErrorMapperTest.kt` | Converte exceções (IO/HTTP/parse) em mensagens amigáveis e localizadas; centraliza a política de erro fora dos ViewModels. |
| **Toggle de favorito otimista com rollback** | `DetailsViewModel.toggleFavorite()` `:70-85` | UI responde na hora e reverte em caso de falha de persistência, com evento de erro — boa prática de UX otimista. |
| **Workaround de gzip do CDN da TMDB** | interceptor `NetworkModule.kt:38-46` (`Accept-Encoding: identity`) | Contorna um bug real do CDN (cache HIT gzip sem header) que quebraria o parsing. Mostra diagnóstico de rede no mundo real. |

### Observabilidade

| Funcionalidade | Onde no código | Por que é bom |
|---|---|---|
| **Abstração de Analytics** | `analytics/AnalyticsTracker.kt` (interface), `TimberAnalyticsTracker`, `CompositeAnalyticsTracker` (multi-sink), `SampledAnalyticsTracker` + `SamplingPolicy` (amostragem) | Interface plugável: trocar/empilhar sinks sem tocar nos ViewModels. O composite isola falhas por sink; a amostragem controla volume. Pronto para um backend real sem refactor. |
| **Funnel de conversão** | `analytics/FunnelTracker.kt`; usado no fluxo de login (`LoginViewModel.kt:35-46`) | Rastreia start/step/abandon/success do funil de autenticação — instrumentação de produto que raramente aparece num desafio. |
| **Detecção de jank (JankStats)** | `performance/JankReporter.kt`, `JankStateEffect.kt`, `LogRecompositions.kt`; wiring `MainActivity.kt:115-120` | Reporta frames lentos via Jetpack `JankStats` para o tracker. Demonstra preocupação com performance de renderização e métricas de UI. |

### Qualidade de código / processo

| Funcionalidade | Onde no código | Por que é bom |
|---|---|---|
| **Testes de arquitetura (Konsist)** | `app/src/test/.../architecture/KonsistArchitectureTest.kt`, `ArchitectureScope.kt` | Transforma as regras do `CLAUDE.md` (separação de camadas, sem entidades Room na UI, etc.) em testes JUnit. Sem baseline — qualquer violação quebra o build. Garante que a Clean Architecture não erode com o tempo. |
| **Análise estática (Detekt)** | config `config/detekt/detekt.yml` + baseline; task `build.gradle.kts:150-168` | Padroniza estilo e detecta code smells automaticamente. |
| **Cobertura de testes (JaCoCo)** | task `jacocoTestReport` `build.gradle.kts:170-219` | Mede cobertura dos testes unitários com filtros sensatos (exclui shells de framework). README documenta os números por camada. |
| **Agregador de qualidade** | task `codeQuality` `build.gradle.kts:222-226` | Um comando (`./gradlew codeQuality`) roda Detekt + JaCoCo + Konsist — pronto para CI. |
| **Logging estruturado (Timber)** | usado em `BiometricHelper`, `JankReporter`, trackers | Logging limpo e taggeado, com nível BODY só em debug (`NetworkModule.kt:26-31`). |
| **Assinatura de release configurável** | `signingConfigs` `build.gradle.kts:38-48` (lê de `local.properties`) | Build de release pronto e sem secrets versionados — desde que o `.jks` não seja commitado (ver Gaps #2). |

> **Resumo para o avaliador:** nenhum desses extras desvia dos requisitos — eles os reforçam. Os mais relevantes para os critérios de avaliação são a **suíte de observabilidade**, os **testes de arquitetura Konsist** e o **cache offline-first**, que evidenciam pensamento de escalabilidade e manutenção a longo prazo.

