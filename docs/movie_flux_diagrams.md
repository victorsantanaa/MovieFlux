# MovieFlux Diagramas

## 1. Fluxo de Autenticação, Biometria e Navegação

Este diagrama detalha o processo de login mockado, a verificação de disponibilidade de biometria e a transição segura entre os grafos de navegação.

```mermaid
stateDiagram-v2
    direction TB

    [*] --> Idle : App iniciado

    Idle --> Validando : Usuario insere credenciais<br/>e clica em Login

    state Validando {
        [*] --> ChecandoCredenciais
        ChecandoCredenciais --> FunnelIniciado : FunnelTracker.start("auth")
    }

    Validando --> ErroCredenciais : Credenciais invalidas
    Validando --> LoginSucesso : Credenciais validas<br/>(admin / 1234)

    ErroCredenciais --> Idle : FunnelTracker.abandon<br/>"invalid_credentials"<br/>SnackBar exibido

    state LoginSucesso {
        [*] --> SalvandoSession
        SalvandoSession --> ChecandoBiometria : isLoggedIn = true<br/>(DataStore)
    }

    LoginSucesso --> BiometriaDisponivel : BiometricHelper<br/>canAuthenticate() = Available

    state BiometriaDisponivel {
        [*] --> ExibindoDialogo
        ExibindoDialogo --> BiometriaAceita : Usuario aceita
        ExibindoDialogo --> BiometriaRecusada : Usuario recusa
        BiometriaAceita --> SalvandoPreferencia : biometricEnabled = true<br/>(DataStore)
    }

    BiometriaDisponivel --> Navegando : FunnelTracker.step<br/>"login_success"

    state Navegando {
        [*] --> EmpilhamentoLimpo
        EmpilhamentoLimpo --> [*] : navigate(MainGraph)<br/>popUpTo(0)<br/>Login removido da pilha
    }

    Navegando --> [*] : HomeScreen exibida

    classDef neutral fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef error fill:#E57373,stroke:#B71C1C,color:#FFFFFF,stroke-width:2px
    classDef success fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px

    class Idle neutral
    class Validando neutral
    class ErroCredenciais error
    class LoginSucesso success
    class BiometriaDisponivel success
    class Navegando success
```

---

## 2. Estratégia Offline-First e Sincronização de Dados

Este diagrama ilustra como o `MovieRepositoryImpl` lida com o cache local (Room) e a API remota para garantir uma experiência fluida mesmo offline.

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'actorBkg':'#00897B','actorBorder':'#004D40','actorTextColor':'#FFFFFF','signalColor':'#004D40','signalTextColor':'#263238','noteBkgColor':'#B2DFDB','noteBorderColor':'#00897B','noteTextColor':'#004D40','sequenceNumberColor':'#FFFFFF'}}}%%
sequenceDiagram
    autonumber
    participant UI as UI (ViewModel)
    participant Repo as MovieRepositoryImpl
    participant DB as Room Database (Local)
    participant API as TMDB API (Remote)

    UI->>Repo: getPopularMovies(page)
    Repo->>DB: getCachedPage(page)
    DB-->>Repo: List<CachedMovieEntity>
    Repo-->>UI: Emit (Data from Cache)

    Note over Repo, API: Inicia busca em background
    Repo->>API: getPopular(page)
    API-->>Repo: MovieResponse (DTOs)

    Repo->>Repo: getGenres() (Map IDs to Names)
    Repo->>DB: upsertCache(freshData)

    Repo->>DB: getFavoriteIds()
    DB-->>Repo: Set<Int>

    Repo-->>UI: Emit (Fresh Data + Favorites Sync)
```

---

## 3. Fluxo de Interação: Home e Detalhes

Detalha a navegação entre a listagem e os detalhes, incluindo a busca com debounce e a ação de favoritar.

```mermaid
graph TD
    A[HomeScreen] -->|Search Input| B{Debounce 300ms}
    B -->|Query Vazia| C[Listar Populares do DB/API]
    B -->|Com Query| D[Filtrar via API Search]

    A -->|Scroll Final| E[Carregar Próxima Página]
    A -->|Clique no Filme| F[Navegar para Detalhes]

    subgraph Detalhes ["Detalhes do Filme"]
        F --> G[Carregar Detalhes]
        G --> H[Exibir Poster/Sinopse/Gêneros]
        H -->|Clique Favorito| I[Toggle Local no Room]
        H -->|Clique Compartilhar| J[Intent de Compartilhamento]
    end

    I -.->|Flow Observer| A
    I -.->|Flow Observer| K[Tela de Favoritos]

    classDef screen fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef decision fill:#FFB74D,stroke:#E65100,color:#263238,stroke-width:2px
    classDef action fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px

    class A,K screen
    class B decision
    class C,D,E,F,G,H,I,J action
```

---

## 4. Gerenciamento de Estados da UI

Representação genérica de como os ViewModels (Home, Details, Favorites) expõem estados para as telas Compose.

```mermaid
stateDiagram-v2
    direction LR

    [*] --> Loading : Init ViewModel
    Loading --> Success : Dados recebidos
    Loading --> Error : Falha na rede/banco

    Success --> Loading : Refresh / Nova Página
    Success --> Empty : Busca sem resultados

    Error --> Loading : Retry action
    Empty --> Success : Clear Search

    classDef loading fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef success fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef error fill:#E57373,stroke:#B71C1C,color:#FFFFFF,stroke-width:2px
    classDef empty fill:#B0BEC5,stroke:#455A64,color:#263238,stroke-width:2px

    class Loading loading
    class Success success
    class Error error
    class Empty empty
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
    App -->|Biometria| Android[Android Biometric Library]

    classDef person fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef system fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef external fill:#90A4AE,stroke:#37474F,color:#FFFFFF,stroke-width:2px

    class User person
    class App system
    class TMDB,Android external
```

### Nível 2: Containers
Detalha os componentes técnicos principais dentro do aplicativo Android.

```mermaid
graph TD
    User[Usuário Mobile]

    subgraph App ["Android Application"]
        UI[Jetpack Compose UI]
        Storage[EncryptedSharedPreferences]
        Database[(Room Database)]
        Network[Retrofit Client]
    end

    TMDB[TheMovieDB API]

    User --> UI
    UI --> Storage
    UI --> Network
    UI --> Database
    Network --> TMDB
    Database -.->|Cache| UI

    classDef person fill:#26A69A,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef container fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    classDef external fill:#90A4AE,stroke:#37474F,color:#FFFFFF,stroke-width:2px

    class User person
    class UI,Storage,Database,Network container
    class TMDB external
```

### Nível 3: Componentes (Components)
Decompõe um container em seus componentes lógicos (focado na camada de dados e negócio).

```mermaid
graph TB
    subgraph Presentation ["Presentation Layer"]
        VM[ViewModels]
    end

    subgraph Domain ["Domain Layer"]
        UC[UseCases]
    end

    subgraph Data ["Data Layer"]
        Repo[MovieRepositoryImpl]
        Remote[RemoteDataSource]
        Local[MovieDao]
    end

    VM --> UC
    UC --> Repo
    Repo --> Remote
    Repo --> Local

    classDef component fill:#00897B,stroke:#004D40,color:#FFFFFF,stroke-width:2px
    class VM,UC,Repo,Remote,Local component
```

### Nível 4: Código (Code)
Diagrama de classes simplificado mostrando as relações principais entre as classes centrais do sistema.

```mermaid
classDiagram
    class MovieRepository {
        <<interface>>
        +getPopularMovies(page) Flow
        +searchMovies(query) Flow
        +getMovieDetail(id) Flow
    }
    class MovieRepositoryImpl {
        -api: RemoteDataSource
        -dao: MovieDao
        +getPopularMovies(page)
    }
    class HomeViewModel {
        -useCase: GetPopularMoviesUseCase
        +uiState: StateFlow
        +loadNextPage()
    }
    class MovieDao {
        <<interface>>
        +insert(movie)
        +getFavorites() Flow
    }

    MovieRepository <|.. MovieRepositoryImpl
    HomeViewModel --> MovieRepository : usa via UseCase
    MovieRepositoryImpl --> MovieDao
```
