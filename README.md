# MovieFlux

Aplicativo Android de catálogo de filmes desenvolvido como desafio técnico. Utiliza a API do TMDB para listar filmes populares, busca, detalhes, favoritos offline e autenticação biométrica.

---

## Configuração (Setup)

### Pré-requisitos

- Android Studio Hedgehog (2023.1.1) ou superior
- JDK 17
- Android SDK 36 (API Level 36)

### Chave da API TMDB

1. Crie uma conta gratuita em https://www.themoviedb.org/settings/api e obtenha uma chave de API v3.
2. Na raiz do projeto, crie o arquivo `local.properties` (se ainda não existir) e adicione:

   ```
   TMDB_API_KEY=sua_chave_aqui
   ```

3. Esse valor é lido em `app/build.gradle.kts` e exposto via `BuildConfig.TMDB_API_KEY`. Sem ele, o projeto não compila.

### Compilar o APK de debug

```bash
# Linux / macOS
./gradlew clean assembleDebug

# Windows
gradlew.bat clean assembleDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`.

---

## Como testar o login biométrico

### No emulador

1. Crie um AVD com API 26 ou superior no Android Virtual Device Manager.
2. No emulador, acesse **Configurações → Segurança → Impressão digital** e cadastre uma impressão usando:

   ```bash
   adb -e emu finger touch 1
   ```

3. No primeiro acesso ao app, insira as credenciais:
   - **Usuário:** `admin`
   - **Senha:** `1234`
4. Após o login bem-sucedido, um diálogo de opt-in biométrico aparecerá perguntando se você deseja habilitar a biometria para acessos futuros. Toque em **Habilitar**.
5. Force-stop no app (`adb shell am force-stop com.example.movieflux`) e abra novamente — o prompt biométrico aparecerá antes da tela principal.

### Resetar o estado para re-testar

```bash
adb shell pm clear com.example.movieflux
```

Isso limpa os dados do app (incluindo a flag `biometricPrompted`), permitindo testar o fluxo de opt-in novamente.

---

## Arquitetura

O projeto segue **MVVM + Clean Architecture** em três camadas bem definidas:

```
Composable (Screen)
    └── ViewModel  (StateFlow<UiState>)
          └── UseCase
                └── Repository (interface)
                      ├── Remote — Retrofit (TMDB API)
                      └── Local  — Room (favorites + cache)
```

### Camada de Domínio (`domain/`)

Sem dependências Android. Contém:
- `MovieModel` — entidade canônica usada em toda a aplicação.
- `MovieRepository` — interface de repositório.
- `UseCase`s — wrappers finos sobre chamadas ao repositório.

### Camada de Dados (`data/`)

Implementa as interfaces do domínio:
- `remote/` — Retrofit (`RemoteDataSource`), DTOs e wrappers de resposta.
- `local/` — Room (`MovieDao`, `MovieEntity`, `CachedMovieEntity`).
- `repository/MovieRepositoryImpl` — política **cache-first**: emite o cache do Room antes de atualizar via rede. O mapa de gêneros (`Map<Int, String>`) é mantido em memória com `Mutex`.
- `mapper/` — `MovieMapper` (DTO → Domínio), `EntityMapper` (Room ↔ Domínio), `CacheMapper`, `MovieDetailMapper`.

### Camada de UI (`ui/` + `view/` + `navigation/`)

**Jetpack Compose + Material 3.** Cada tela tem um `ViewModel` pareado que expõe uma `StateFlow<UiState>`:
- `view/home/` — lista/grade de filmes populares, paginação infinita, busca com debounce, estados Loading/Error/Empty.
- `view/details/` — detalhes do filme com toggle de favorito otimista e share intent.
- `view/favorites/` — filmes favoritos do Room, acessíveis offline.
- `view/login/` — autenticação mockada (`admin/1234`) com opt-in biométrico pós-login.
- `view/profile/` — configurações do perfil e logout.
- `navigation/` — `Screen` sealed class com quatro rotas; `AppNavHost` gerencia toda a navegação.

### Observabilidade

O subsistema de analytics é composto por `CompositeAnalyticsTracker` (delega para múltiplos sinks), `SampledAnalyticsTracker` (amostragem configurável por evento) e `FunnelTracker` (rastreia funis de conversão como o fluxo de login), todos backed por `TimberAnalyticsTracker` em debug. O `JankReporter` integra `JankStats` da Jetpack para detectar frames lentos e registrá-los via Timber; `JankStateEffect` expõe isso como um side-effect Compose reutilizável.

### Autenticação

A autenticação é **intencionalmente mockada** (`admin/1234`), conforme definido no briefing do desafio. As implementações de Firebase Authentication e Google Sign-In (fases 2B e 2C do plano original) foram explicitamente descartadas do escopo de entrega.

---

## Uso de IA

Este projeto utilizou **Claude Code (Anthropic)** como ferramenta de assistência ao desenvolvimento:

- Geração e refatoração de código Kotlin/Compose.
- Auditoria do plano de implementação (`CHALLENGE_VALIDATION.md`).
- Elaboração do plano revisado de implementação (`REVISED_IMPLEMENTATION_PLAN.md`).
- Revisão de testes unitários e identificação de lacunas de cobertura.

Todos os commits passaram por revisão humana. Nenhum código foi aplicado de forma automática sem avaliação prévia.

---

## Limitações conhecidas

- **Perfil hard-coded:** e-mail e nome exibidos na tela de perfil (`admin@movieflux.app` / `admin`) são fixos, pois a autenticação é mockada. Isso é intencional.
- **Firebase Authentication, Google Sign-In e Sentry/Crashlytics** não estão implementados. Foram explicitamente removidos do escopo deste desafio para evitar dependências de arquivos de configuração específicos de máquina (`google-services.json`, DSN do Sentry).
- **Testes de UI (Compose UI Test):** existe um teste de UI para o fluxo de login (`LoginScreenTest`) como demonstração. Testes de UI adicionais estão fora do escopo desta entrega.
