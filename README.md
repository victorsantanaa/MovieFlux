# MovieFlux

Aplicativo Android de catálogo de filmes desenvolvido como desafio técnico. Utiliza a [API do TMDB](https://developer.themoviedb.org/docs) para listar filmes populares, busca, detalhes, favoritos offline e autenticação biométrica.

---

## Configuração (Setup)

### Pré-requisitos

- [Android Studio](https://developer.android.com/studio) Hedgehog (2023.1.1) ou superior
- [JDK 17](https://adoptium.net/temurin/releases/?version=17) ([OpenJDK 17](https://openjdk.org/projects/jdk/17/))
- [Android SDK 36 (API Level 36)](https://developer.android.com/tools/releases/platforms) — instalável via [SDK Manager](https://developer.android.com/tools/sdkmanager)
- [Gradle](https://gradle.org/) (via wrapper `gradlew` incluído no projeto)

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

A autenticação é **intencionalmente mockada** (`admin/1234`), conforme definido no briefing do desafio.

---

## Bibliotecas e Referências

Principais bibliotecas utilizadas no projeto e seus links de documentação oficial:

### UI / Compose

- [Jetpack Compose](https://developer.android.com/jetpack/compose) — toolkit declarativo de UI.
- [Compose BOM](https://developer.android.com/jetpack/compose/bom) — Bill of Materials para alinhar versões do Compose.
- [Material 3](https://developer.android.com/jetpack/compose/designsystems/material3) — componentes e theming Material You.
- [Material Icons Extended](https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary) — conjunto estendido de ícones.
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) — navegação entre telas.
- [Coil](https://coil-kt.github.io/coil/) — carregamento de imagens assíncrono para Compose.

### Arquitetura / Lifecycle

- [Lifecycle / ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) — gerenciamento de estado com ciclo de vida.
- [Hilt](https://dagger.dev/hilt/) — injeção de dependência ([guia Android](https://developer.android.com/training/dependency-injection/hilt-android)).
- [Hilt Navigation Compose](https://developer.android.com/jetpack/compose/libraries#hilt) — integração Hilt + Navigation Compose.

### Rede

- [Retrofit](https://square.github.io/retrofit/) — cliente HTTP type-safe.
- [OkHttp](https://square.github.io/okhttp/) — cliente HTTP + logging interceptor.
- [Gson](https://github.com/google/gson) — serialização/desserialização JSON.
- [TMDB API](https://developer.themoviedb.org/docs) — fonte de dados de filmes.

### Persistência

- [Room](https://developer.android.com/jetpack/androidx/releases/room) — camada de persistência ORM sobre SQLite.
- [Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) — `EncryptedSharedPreferences` para dados sensíveis.

### Autenticação / Sistema

- [Biometric](https://developer.android.com/jetpack/androidx/releases/biometric) — `BiometricPrompt` para autenticação biométrica.
- [AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat) — compatibilidade (requerida pelo `FragmentActivity`).
- [Core SplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen) — API de splash do Android 12.
- [JankStats](https://developer.android.com/topic/performance/jankstats) — atribuição de jank de renderização.

### Build / Linguagem

- [Kotlin](https://kotlinlang.org/docs/home.html) — linguagem principal.
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) — concorrência assíncrona.
- [KSP](https://kotlinlang.org/docs/ksp-overview.html) — Kotlin Symbol Processing (processadores de anotação).
- [Android Gradle Plugin](https://developer.android.com/build) — sistema de build.

### Logging / Observabilidade

- [Timber](https://github.com/JakeWharton/timber) — logging estruturado.

### Qualidade / Testes

- [JUnit 4](https://junit.org/junit4/) — framework de testes unitários.
- [MockK](https://mockk.io/) — biblioteca de mocking para Kotlin.
- [Turbine](https://github.com/cashapp/turbine) — testes de `Flow`.
- [Kotlin Coroutines Test](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/) — utilitários de teste para coroutines.
- [Espresso](https://developer.android.com/training/testing/espresso) — testes instrumentados de UI.
- [Compose UI Test](https://developer.android.com/jetpack/compose/testing) — testes de UI para Compose.
- [Detekt](https://detekt.dev/) — análise estática de código Kotlin.
- [JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/) — cobertura de testes.
- [Konsist](https://docs.konsist.lemonappdev.com/) — regras de arquitetura como testes.

---

## Qualidade de Código

### Detekt — Análise estática Kotlin

```bash
gradlew detekt
```

Relatório gerado em `app/build/reports/detekt/detekt.html` (HTML para leitura humana) e `detekt.xml` (para integrações CI). A configuração de regras está em `config/detekt/detekt.yml`; o baseline de findings pré-existentes está em `config/detekt/baseline.xml`. Para regenerar o baseline após uma limpeza deliberada:

```bash
gradlew detektBaseline
```

### JaCoCo — Cobertura de testes unitários

```bash
gradlew jacocoTestReport
```

A task já depende de `testDebugUnitTest`. O relatório é gerado em `app/build/reports/jacoco/jacocoTestReport/html/index.html` e `.../jacocoTestReport.xml`. Os pacotes `di/`, `navigation/`, `ui/theme/`, `MainActivity` e `MovieFluxApp` são excluídos da cobertura — são shells de framework sem lógica testável por testes unitários.

#### Cobertura por componente-chave

A lógica testável por testes unitários — camada de domínio, repositório e ViewModels — tem cobertura alta. Os números baixos por pacote acima são puxados pelos Composables (telas), não pela lógica.

**Camadas:**

| Camada           | Instruções | Linhas |
|------------------|------------|--------|
| Domínio (`domain/`) | 100%    | 100%   |
| Dados (`data/`)¹    | 45,1%   | 34,7%  |

¹ A camada de dados inclui `data/local` (Room) e `data/biometric`, exercitados por testes instrumentados; o repositório, os mappers e o remote têm cobertura alta (ver abaixo e a tabela por pacote).

**Repositório:**

| Classe                | Instruções | Linhas |
|-----------------------|------------|--------|
| `MovieRepositoryImpl` | 94,6%      | 90,5%  |

**ViewModels:**

| ViewModel            | Instruções | Linhas |
|----------------------|------------|--------|
| `LoginViewModel`     | 100%       | 100%   |
| `HomeViewModel`      | 97,9%      | 100%   |
| `FavoritesViewModel` | 97,0%      | 100%   |
| `DetailsViewModel`   | 92,2%      | 97,1%  |
| `ProfileViewModel`   | 93,2%      | 93,9%  |

> **Importante:** o JaCoCo aqui mede **somente os testes unitários** (`testDebugUnitTest`). As camadas de domínio, mapeamento e rede — onde reside a lógica de negócio — têm cobertura alta (90–100%). Os pacotes `view/` têm cobertura baixa neste relatório porque os Composables são validados por **testes de UI instrumentados** (`androidTest`), que rodam em dispositivo/emulador e **não são contabilizados** nesta métrica. Veja a seção abaixo.

### Testes de UI (Compose UI Test)

```bash
gradlew connectedDebugAndroidTest   # requer device/emulador conectado
```

São **23 testes de UI instrumentados**, distribuídos em 6 arquivos sob `app/src/androidTest/`:

| Arquivo                   | Testes | Cobre                                                        |
|---------------------------|--------|--------------------------------------------------------------|
| `LoginScreenTest`         | 5      | Fluxo de login, validação de campos e mensagens de erro.     |
| `BiometricOptInDialogTest`| 3      | Diálogo de opt-in biométrico pós-login.                      |
| `BiometricGateTest`       | 3      | Gate biométrico exibido no relaunch.                         |
| `ComponentsUiTest`        | 6      | `PrimaryButton`/`SecondaryButton` e variações de `ButtonSize`.|
| `MovieCardTest`           | 4      | Renderização e interação do card de filme.                   |
| `ThemeSelectorTest`       | 2      | Seletor de tema na tela de perfil.                           |

Esses testes usam Hilt (`HiltTestRunner` + `HiltTestApp`) e exercitam os Composables das telas que aparecem com cobertura baixa no relatório JaCoCo de testes unitários.

### Konsist — Regras de arquitetura como testes JUnit

```bash
gradlew testDebugUnitTest --tests "*KonsistArchitectureTest"
```

Cada regra arquitetural documentada no `CLAUDE.md` é aplicada como um teste JUnit independente (`KonsistArchitectureTest`). O Konsist não tem baseline — qualquer violação quebra o build imediatamente. Se uma regra estiver errada, altere o teste; não adicione supressões.

### Atalho para CI

```bash
gradlew codeQuality
```

Roda Detekt, JaCoCo (que inclui os testes unitários) e os testes Konsist em sequência.

---

## Uso de IA

Este projeto utilizou diversas ferramentas de IA de forma complementar durante o desenvolvimento:

### [Claude Code](https://www.anthropic.com/claude-code) ([Anthropic](https://www.anthropic.com/)) — Desenvolvimento principal

- Geração e refatoração de código Kotlin/Compose.
- Auditoria do plano de implementação.
- Elaboração do plano revisado de implementação.
- Revisão de testes unitários e identificação de lacunas de cobertura.

### [ChatGPT](https://chatgpt.com/) ([OpenAI](https://openai.com/)) — Code Reviewer

- Revisão de código (code review), avaliando legibilidade, padrões e possíveis melhorias.

### [GitHub Copilot](https://github.com/features/copilot) — Code Reviewer e suporte a testes

- Revisão de código (code review).
- Auxílio na escrita de testes unitários e testes de UI.

### [Gemini](https://gemini.google.com/) ([Google](https://ai.google/)) — Code Reviewer e suporte a testes

- Revisão de código (code review).
- Auxílio na escrita de testes unitários e testes de UI.

### [Nano Banana](https://deepmind.google/models/gemini-image/) (Gemini Image) — Apoio ao fluxo do app

- Auxílio com o fluxo do aplicativo, geração de imagens e elaboração de cenários.

Todos os commits passaram por revisão humana. Nenhum código foi aplicado de forma automática sem avaliação prévia.

---

## Limitações conhecidas

- **Perfil hard-coded:** e-mail e nome exibidos na tela de perfil (`admin@movieflux.app` / `admin`) são fixos, pois a autenticação é mockada. Isso é intencional.
