# MovieFlux Diagramas

## 1. Fluxo de Autenticação, Biometria e Navegação

Fluxo real de login (mock `admin / 1234`), com a **opção de habilitar biometria** após o login e o
**gate biométrico no relançamento** do app. Sessão e preferências são persistidas em
`AuthPreferences`, que usa **EncryptedSharedPreferences**.

```mermaid
stateDiagram-v2
    direction TB

    [*] --> Idle : LoginScreen exibida<br/>tracker.trackScreen("login")

    Idle --> Validando : login(username, password)<br/>funnel.start("auth")<br/>funnel.step("login_clicked")

    Validando --> ErroCredenciais : Credenciais invalidas
    Validando --> LoginSucesso : admin / 1234

    ErroCredenciais --> Idle : funnel.abandon<br/>"invalid_credentials"<br/>trackError + mensagem na tela

    state LoginSucesso {
        [*] --> SalvandoSession
        SalvandoSession --> AvaliandoPrompt : isLoggedIn = true<br/>(EncryptedSharedPreferences)
        AvaliandoPrompt --> [*] : funnel.step("login_success")
    }

    LoginSucesso --> OptInBiometria : shouldPrompt =<br/>!biometricPrompted &&<br/>canAuthenticate() == Available
    LoginSucesso --> Navegando : shouldPrompt == false

    state OptInBiometria {
        [*] --> ExibindoDialogo : BiometricOptInDialog
        ExibindoDialogo --> Confirmado : onEnable / onSkip<br/>confirmBiometricOptIn(enable)
        Confirmado --> [*] : biometricEnabled = enable<br/>biometricPrompted = true
    }

    OptInBiometria --> Navegando : onLoginSuccess()

    state Navegando {
        [*] --> EmpilhamentoLimpo
        EmpilhamentoLimpo --> [*] : navigate(MainGraph)<br/>popUpTo(0) inclusive<br/>AuthGraph removido da pilha
    }

    Navegando --> [*] : MainScaffold / Home exibida

    classDef neutral fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef error fill:#E57373,stroke:#B71C1C,color:#FFFFFF,stroke-width:2px
    classDef success fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px

    class Idle neutral
    class Validando neutral
    class ErroCredenciais error
    class LoginSucesso success
    class OptInBiometria success
    class Navegando success
```

### 1.1. Gate Biométrico no Relançamento (MainActivity)

A biometria **não** desbloqueia durante o login — ela atua como um gate na reabertura do app.
Só quando `isLoggedIn && biometricEnabled && canAuthenticate() == Available` o `BiometricGate`
exige a digital antes de compor o `NavHost` em `MainGraph`.

```mermaid
stateDiagram-v2
    direction LR

    [*] --> Checking : needsBiometric == true
    [*] --> Passed : needsBiometric == false

    Checking --> Passed : authenticate() onSuccess<br/>effectiveStart = MainGraph
    Checking --> Failed : onError(errString)

    Failed --> Checking : onRetry
    Failed --> Passed : onUsePassword<br/>effectiveStart = AuthGraph

    Passed --> [*] : compõe AppNavHost(content)

    classDef neutral fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef error fill:#E57373,stroke:#B71C1C,color:#FFFFFF,stroke-width:2px
    classDef success fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px

    class Checking neutral
    class Failed error
    class Passed success
```

---

## 2. Estratégia Offline-First e Sincronização de Dados

Como `MovieRepositoryImpl.getPopularMovies()` combina o cache local (Room, tabela `movie_cache`) e a
API remota. A ordem reflete o código: os IDs de favoritos são lidos **primeiro**, o cache é emitido
imediatamente, e os dados frescos só são re-emitidos **se o conteúdo da página mudou**.

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'actorBkg':'#00897B','actorBorder':'#004D40','actorTextColor':'#FFFFFF','signalColor':'#004D40','signalTextColor':'#263238','noteBkgColor':'#B2DFDB','noteBorderColor':'#00897B','noteTextColor':'#004D40','sequenceNumberColor':'#FFFFFF'}}}%%
sequenceDiagram
    autonumber
    participant UI as UI (ViewModel)
    participant Repo as MovieRepositoryImpl
    participant DB as Room (movie_cache / favorites)
    participant API as TMDB API (Remote)

    UI->>Repo: getPopularMovies(page) [Flow]
    Repo->>DB: getFavoriteIds()
    DB-->>Repo: Set<Int>
    Repo->>DB: getCachedPage(page)
    DB-->>Repo: List<CachedMovieEntity>
    alt cache não vazio
        Repo-->>UI: emit (cache + isFavorite)
    end

    Note over Repo, API: Busca de rede (try/catch)
    Repo->>Repo: getGenres() (cache em memória, TTL 24h)
    Repo->>API: getPopular(page)
    API-->>Repo: MovieResponse (DTOs)
    Repo->>DB: upsertCache(entities)
    Repo->>DB: evictCacheBeyond(500)

    alt remoteIds != cachedIds
        Repo-->>UI: emit (dados frescos + isFavorite)
    end

    Note over Repo: IOException/HttpException só propaga<br/>se o cache estava vazio
```

---

## 3. Fluxo de Interação: Home e Detalhes

Navegação entre listagem e detalhes, com busca (debounce: 0ms se vazia, 300ms com texto), paginação
(desabilitada durante busca) e favoritar. As telas ficam dentro do `MainScaffold` com `BottomNavBar`
(Home, Favoritos, Perfil); Detalhes esconde a barra inferior.

```mermaid
graph TD
    A[HomeScreen] -->|setSearchQuery| B{debounce<br/>0ms vazia / 300ms}
    B -->|Query vazia| C[getPopularMovies - cache/API]
    B -->|Com query| D[searchMovies via API]

    A -->|Scroll final| E[loadNextPage<br/>desabilitado se buscando]
    A -->|Clique no filme| F[navigate details/movieId]

    subgraph Detalhes ["DetailsScreen (esconde BottomNav)"]
        F --> G[getMovieDetail id]
        G --> H[Exibir Poster/Sinopse/Gêneros]
        H -->|toggleFavorite| I[ToggleFavoriteUseCase - Room]
        H -->|share| J[DetailsEvent.Share → UI monta chooser]
    end

    I -.->|getFavorites Flow| A
    I -.->|getFavorites Flow| K[FavoritesScreen]

    classDef screen fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef decision fill:#FFB74D,stroke:#E65100,color:#263238,stroke-width:2px
    classDef action fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px

    class A,K screen
    class B decision
    class C,D,E,F,G,H,I,J action
```

---

## 4. Gerenciamento de Estados da UI

Cada ViewModel expõe sua própria `sealed class UiState` via `StateFlow`. **Não existe** um estado
`Empty` genérico — uma busca sem resultados é um `Success` com lista vazia. As telas diferem: Login
tem `Idle`, Favoritos **não tem** `Error`.

```mermaid
stateDiagram-v2
    direction LR

    state "HomeUiState" as Home {
        [*] --> H_Loading : Loading
        H_Loading --> H_Success : Success(movies, isLoadingMore, errorOnPage)
        H_Loading --> H_Error : Error(messageRes)
        H_Success --> H_Loading : loadMovies() / refresh
        H_Error --> H_Loading : retry
    }

    state "DetailsUiState" as Details {
        [*] --> D_Loading : Loading
        D_Loading --> D_Success : Success(movie, genres)
        D_Loading --> D_Error : Error(messageRes)
        D_Error --> D_Loading : loadDetail()
    }

    state "FavoritesUiState" as Fav {
        [*] --> F_Loading : Loading
        F_Loading --> F_Success : Success(movies, viewMode)
    }

    state "LoginUiState" as Login {
        [*] --> L_Idle : Idle
        L_Idle --> L_Loading : Loading
        L_Loading --> L_Success : Success(shouldPromptBiometric)
        L_Loading --> L_Error : Error(messageRes)
        L_Error --> L_Idle
    }

    classDef loading fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef success fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef error fill:#E57373,stroke:#B71C1C,color:#FFFFFF,stroke-width:2px
    classDef idle fill:#B0BEC5,stroke:#455A64,color:#263238,stroke-width:2px

    class H_Loading,D_Loading,F_Loading,L_Loading loading
    class H_Success,D_Success,F_Success,L_Success success
    class H_Error,D_Error,L_Error error
    class L_Idle idle
```

---

## 5. C4 Model

Esta seção utiliza o modelo C4 para decompor a arquitetura do MovieFlux em diferentes níveis de abstração.

### Nível 1: Contexto (Context)
Descreve como o MovieFlux interage com usuários e sistemas externos.

```mermaid
graph LR
    User[Usuário Mobile] -->|Usa| App[MovieFlux App]
    App -->|Consome Dados| TMDB[TMDB API]
    App -->|Biometria| Android[Android Biometric / Keystore]

    classDef person fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef system fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef external fill:#90A4AE,stroke:#37474F,color:#FFFFFF,stroke-width:2px

    class User person
    class App system
    class TMDB,Android external
```

### Nível 2: Containers
Componentes técnicos principais dentro do app Android. O armazenamento é dividido entre
**EncryptedSharedPreferences** (auth), **SharedPreferences** (`ui_prefs`: tema e view mode) e **Room**.

```mermaid
graph TD
    User[Usuário Mobile]

    subgraph App ["Android Application"]
        UI[Jetpack Compose UI<br/>+ ViewModels]
        Auth[EncryptedSharedPreferences<br/>AuthPreferences]
        UiPrefs[SharedPreferences<br/>UiPreferences / ui_prefs]
        Database[(Room Database<br/>favorites + movie_cache)]
        Network[Retrofit Client<br/>RemoteDataSource]
        Analytics[AnalyticsTracker<br/>+ FunnelTracker]
        Biometric[BiometricHelper]
    end

    TMDB[TheMovieDB API]

    User --> UI
    UI --> Auth
    UI --> UiPrefs
    UI --> Network
    UI --> Database
    UI --> Analytics
    UI --> Biometric
    Network --> TMDB
    Database -.->|Cache offline-first| UI

    classDef person fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef container fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef external fill:#90A4AE,stroke:#37474F,color:#FFFFFF,stroke-width:2px

    class User person
    class UI,Auth,UiPrefs,Database,Network,Analytics,Biometric container
    class TMDB external
```

### Nível 3: Componentes (Components)
Decompõe a aplicação nas camadas Clean Architecture. Os ViewModels dependem dos UseCases, que
delegam ao `MovieRepositoryImpl`, único ponto que combina remoto e local.

```mermaid
graph TB
    subgraph Presentation ["Presentation Layer"]
        VM[HomeViewModel / DetailsViewModel<br/>FavoritesViewModel / LoginViewModel / ProfileViewModel]
    end

    subgraph Domain ["Domain Layer"]
        UC[GetPopularMoviesUseCase · SearchMoviesUseCase<br/>GetMovieDetailUseCase · GetFavoritesUseCase<br/>ToggleFavoriteUseCase]
        RepoIf[MovieRepository - interface]
    end

    subgraph Data ["Data Layer"]
        Repo[MovieRepositoryImpl]
        Remote[RemoteDataSource - Retrofit]
        Local[MovieDao - Room]
    end

    VM --> UC
    UC --> RepoIf
    RepoIf -.implementado por.-> Repo
    Repo --> Remote
    Repo --> Local

    classDef component fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef iface fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    class VM,UC,Repo,Remote,Local component
    class RepoIf iface
```

### Nível 4: Código (Code)
Diagrama de classes com as relações principais, alinhado às assinaturas reais.

```mermaid
classDiagram
    class MovieRepository {
        <<interface>>
        +getPopularMovies(page) Flow~List_MovieModel~
        +searchMovies(query) Flow~List_MovieModel~
        +getMovieDetail(id) Flow~MovieModel~
        +getFavorites() Flow~List_MovieModel~
        +toggleFavorite(movie) suspend
        +getGenres() Map~Int_String~
    }
    class MovieRepositoryImpl {
        -api: RemoteDataSource
        -dao: MovieDao
        -cachedGenres: Map~Int,String~
    }
    class HomeViewModel {
        -getPopularMovies: GetPopularMoviesUseCase
        -searchMovies: SearchMoviesUseCase
        -getFavorites: GetFavoritesUseCase
        -toggleFavoriteUseCase: ToggleFavoriteUseCase
        -tracker: AnalyticsTracker
        -uiPreferences: UiPreferences
        +uiState: StateFlow~HomeUiState~
        +loadNextPage()
    }
    class MovieDao {
        <<interface>>
        +getFavorites() Flow
        +getFavoriteIds() suspend
        +insert(movie) suspend
        +delete(movie) suspend
        +getCachedPage(page) suspend
        +upsertCache(movies) suspend
        +evictCacheBeyond(keep) suspend
    }

    MovieRepository <|.. MovieRepositoryImpl
    HomeViewModel --> MovieRepository : via UseCases
    MovieRepositoryImpl --> MovieDao
    MovieRepositoryImpl --> RemoteDataSource
```
