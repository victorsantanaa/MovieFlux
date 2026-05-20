# MovieFlux — Implementation Plan: Detekt + JaCoCo + Konsist

## Context & Goal

The project has no static-analysis gate beyond `gradlew lint`, no measurable test-coverage signal, and no automated enforcement of the Clean Architecture layering rules documented in `CLAUDE.md`. Adding **Detekt** gives Kotlin-specific static analysis with a customisable rule set; **JaCoCo** produces a unit-test coverage report (HTML + XML) that a CI pipeline or a reviewer can read at a glance; **Konsist** runs as a normal JUnit test suite and asserts that the project's architectural conventions (layer boundaries, naming, Hilt annotations, repository contract) are still respected. Konsist is non-negotiable for this project: today the rules live only in `CLAUDE.md` prose and reviewer judgement — a single misplaced import (e.g. `view/` referencing `data/local/MovieEntity`) silently breaks the architecture. All three tools run locally via Gradle; none adds a runtime dependency.

## Scope

**In scope:**
- Detekt 1.23.x plugin wired at the project root, applied to the `:app` module.
- Project-checked-in Detekt config (`config/detekt/detekt.yml`) tuned for a Compose-heavy Kotlin 2.2 codebase.
- Baseline file (`config/detekt/baseline.xml`) so the initial introduction does not flood the build with pre-existing warnings.
- JaCoCo 0.8.12 plugin wired at `:app`, with a custom aggregate task `jacocoTestReport` that runs after `testDebugUnitTest`.
- Coverage exclusions for code that cannot or should not be unit-tested: generated Hilt/DI factories, Compose previews, navigation glue, `Application` and `Activity` shells, R/BuildConfig.
- Both reports produced under `app/build/reports/` in HTML and XML.
- **Konsist** 0.17.x added as `testImplementation`, exercised by a dedicated `KonsistArchitectureTest` class under `app/src/test/java/com/example/movieflux/architecture/`.
- The Konsist suite covers every layering and naming convention currently enforced only by `CLAUDE.md` prose (domain has no Android imports, `view/` never imports `data/`, ViewModels are `@HiltViewModel`-annotated and end with `ViewModel`, repository interfaces live in `domain/repository`, implementations live in `data/repository`, etc.).
- README documents how to run all three locally (`gradlew detekt`, `gradlew jacocoTestReport`, `gradlew testDebugUnitTest --tests "*KonsistArchitectureTest"`).

**Dependencies / preconditions:**
- `app/build.gradle.kts` plugins block must explicitly apply `alias(libs.plugins.kotlin.android)` first — tracked as **Fix-1** in `REVISED_IMPLEMENTATION_PLAN.md`. If not yet landed, this plan's Phase 1 lands it as a prerequisite step.
- JDK 17 toolchain already configured (`app/build.gradle.kts:50`); JaCoCo 0.8.12 supports JDK 17 bytecode without flags.

## Architecture Decisions

- **Decision:** apply Detekt only to `:app` (single-module project), via the `io.gitlab.arturbosch.detekt` plugin. **Reason:** the project has one module; an aggregate root task would add ceremony for no benefit. **Alternative considered:** root-level `detekt` task scanning every subproject — rejected as premature; revisit if/when a `:domain` or `:data` module is extracted.
- **Decision:** check in a project-tuned `detekt.yml` derived from `detekt --generate-config`, with Compose-aware deviations (`FunctionNaming` allows PascalCase for `@Composable`, `LongMethod` threshold relaxed to 80 lines for screen composables). **Reason:** the default Detekt rule set flags `@Composable` functions as naming violations and Compose screens as "too long" — both are false positives in idiomatic Compose code. **Alternative considered:** depend on `io.nlopez.compose.rules:detekt` for Compose-specific rules — deferred; the built-in rules with targeted thresholds cover today's needs without a second plugin.
- **Decision:** generate a Detekt baseline (`baseline.xml`) at introduction time so existing findings do not block the first build. New code is held to the full rule set. **Reason:** lets the gate start at "zero new warnings" instead of forcing a big-bang cleanup PR. **Alternative considered:** fix every existing finding up front — rejected; scope creep into a tooling-only PR.
- **Decision:** wire JaCoCo as a **custom Gradle task** (`jacocoTestReport`) rather than relying on the AGP-bundled coverage flag (`enableUnitTestCoverage`). **Reason:** the AGP flag pre-9.x produced incomplete reports for Kotlin source sets and is undergoing churn in AGP 9.x; the custom task using `JacocoReport` with explicit `classDirectories` / `sourceDirectories` gives deterministic, well-documented behaviour today. **Alternative considered:** `testCoverage { jacocoVersion = "0.8.12" }` block — kept available for a future swap, but not the first cut.
- **Decision:** exclude from coverage: `**/di/**`, `**/*Module*.*`, `**/*_Factory*.*`, `**/*_HiltModules*.*`, `**/databinding/**`, `**/BuildConfig.*`, `**/R.class`, `**/R$*.class`, `**/Manifest*.*`, `**/Hilt_*.*`, `MainActivity*`, `**/ui/theme/**`, `**/view/**/*Preview*.*`, `**/navigation/**`. **Reason:** these are either generated, are framework shells with no logic worth asserting, or are pure Compose UI/preview code outside unit-test scope (covered separately by `LoginScreenTest`). **Alternative considered:** include everything — rejected; would produce artificially low coverage numbers driven by uncoverable files.
- **Decision:** do NOT enforce a coverage threshold (no `jacocoTestCoverageVerification`) in this first cut. **Reason:** producing the report is the goal; enforcement turns a useful signal into a blocker without baseline data. Revisit once two or three reports have been read.
- **Decision:** introduce Konsist as a `testImplementation`-only dependency and write architecture assertions as a regular JUnit 4 test class (`KonsistArchitectureTest`), grouped by concern (`Layering`, `Naming`, `Hilt`, `Compose`, `Room`, `Retrofit`). **Reason:** Konsist runs inside the existing `testDebugUnitTest` task — no new Gradle plumbing, no separate report consumer; failures show up alongside ViewModel/repository test failures in the standard JUnit report. **Alternative considered:** ArchUnit — rejected; ArchUnit is JVM-bytecode-based and works on Kotlin source only via fragile name conventions; Konsist parses the Kotlin source AST directly and understands `@HiltViewModel`, `@Composable`, `suspend`, sealed classes, and package-by-feature layout without ceremony.
- **Decision:** Konsist tests are **enforced from day one** with NO baseline. **Reason:** the architecture rules in `CLAUDE.md` describe today's state — if any current file violates them, the test failing is itself the signal that the architecture has drifted and must be repaired before merge. A baseline would defeat the purpose. **Alternative considered:** soft-launch with a `withSuppress` list — rejected; the same argument as for baseline. If a rule turns out to be wrong, change the rule, not the suppress list.
- **Decision:** scope Konsist's source-set scan via `Konsist.scopeFromProject().files` filtered to exclude test sources, generated sources (`build/generated`), and Hilt-generated files (`Hilt_*.kt`, `*_HiltModules.kt`). **Reason:** generated code follows different rules; including it produces false positives that drown the signal. **Alternative considered:** `Konsist.scopeFromProduction()` — usable but slightly broader; the explicit filter is documented inline and easier for a future contributor to adjust.

## Phases

### Phase 1 — Catalog entries and root-build wiring

**Goal:** Declare Detekt and JaCoCo in the version catalog and register them at the root project so any module can opt in.

**Touches:**
- `gradle/libs.versions.toml` (EDIT)
- `build.gradle.kts` (root, EDIT)

**Steps:**
1. In `[versions]` of `libs.versions.toml`, add `detekt = "1.23.7"` and `jacoco = "0.8.12"`. Reason: Detekt 1.23.7 is the latest 1.x stable compatible with Kotlin 2.2; JaCoCo 0.8.12 supports JDK 17 bytecode and Kotlin coroutines instrumentation. Pin both for reproducibility.
2. In `[plugins]`, add `detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }`. JaCoCo ships with Gradle, so no plugin alias is needed — it will be applied by string id in the app module.
3. In `[libraries]`, add `detekt-formatting = { group = "io.gitlab.arturbosch.detekt", name = "detekt-formatting", version.ref = "detekt" }`. Reason: the formatting rule set (ktlint-backed) lives in a separate artifact and ships disabled by default — wire it as a `detektPlugins(...)` dependency in Phase 2.
4. In the root `build.gradle.kts` plugins block, add `alias(libs.plugins.detekt) apply false`. Reason: declares the version once at the root; the `:app` module applies it. Mirrors the existing `apply false` pattern for `android.application`, `kotlin.android`, etc.

**Tests:**
- None directly. Catalog/plugin declarations are validated by Gradle sync.

**Acceptance criterion:** `gradlew help` succeeds; `gradlew tasks --all | grep -i detekt` returns nothing (plugin not yet applied to :app) but Gradle does not error on resolution. The `detekt` and `jacoco` versions appear in `libs.versions.toml`.

---

### Phase 2 — Apply Detekt to `:app` with a tuned config and baseline

**Goal:** Run Detekt against the production source set with project-tuned rules; freeze existing findings into a baseline so the build is green.

**Touches:**
- `app/build.gradle.kts` (EDIT — plugins block + new `detekt { }` block + `detektPlugins(...)` dependency)
- `config/detekt/detekt.yml` (NEW)
- `config/detekt/baseline.xml` (NEW — generated by Gradle)

**Steps:**
1. In `app/build.gradle.kts` plugins block, ensure `alias(libs.plugins.kotlin.android)` is the second entry (prerequisite from `REVISED_IMPLEMENTATION_PLAN.md` Fix-1); then add `alias(libs.plugins.detekt)` as the last entry. Reason: Detekt needs Kotlin metadata from `kotlin-android` to type-resolve.
2. Add a top-level configuration block at the end of `app/build.gradle.kts`:
   ```
   detekt {
       toolVersion = libs.versions.detekt.get()
       config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
       baseline = file("$rootDir/config/detekt/baseline.xml")
       buildUponDefaultConfig = true
       parallel = true
       autoCorrect = false
   }
   ```
   Reason: `buildUponDefaultConfig = true` makes `detekt.yml` an override layer, not a full replacement — keeps the diff small.
3. In the `dependencies { }` block, add `detektPlugins(libs.detekt.formatting)`. Reason: enables the formatting (ktlint) rule set; without this line the `formatting` section in `detekt.yml` is ignored.
4. Configure the default `Detekt` task to write both HTML and XML reports:
   ```
   tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
       reports {
           html.required.set(true)
           xml.required.set(true)
           sarif.required.set(false)
           md.required.set(false)
       }
       jvmTarget = "17"
   }
   ```
   Reason: HTML is for humans, XML is for CI integrations (e.g. SonarQube). Explicit `jvmTarget` avoids a Kotlin 2.2 warning.
5. Create `config/detekt/detekt.yml` by running `gradlew detektGenerateConfig` once and then applying the following overrides (do not check in the full generated file — only the deltas the project actually wants):
   ```
   build:
     maxIssues: 0
   complexity:
     LongMethod:
       threshold: 80          # Compose screens often exceed default 60
     LongParameterList:
       functionThreshold: 8   # ViewModels with multiple injected deps
   naming:
     FunctionNaming:
       ignoreAnnotated: ['Composable']   # @Composable uses PascalCase
   style:
     MagicNumber:
       ignorePropertyDeclaration: true
       ignoreAnnotation: true
       ignoreEnums: true
   formatting:
     active: true
     android: true
     autoCorrect: false
   ```
   Reason: each override targets a known idiomatic Kotlin-Compose pattern that the default config flags as a violation.
6. Generate the baseline with `gradlew detektBaseline`. This writes `config/detekt/baseline.xml` capturing every current finding. Check the file into the repo. Reason: keeps the build green on day one; new violations introduced by future code still fail.

**Tests:**
- None directly. Detekt itself is the test.

**Acceptance criterion:** `gradlew detekt` succeeds. `app/build/reports/detekt/detekt.html` exists. Removing a single line from `baseline.xml` (e.g. a previously-baselined `MagicNumber` finding) causes the next `gradlew detekt` run to fail, proving the gate is live.

---

### Phase 3 — Wire JaCoCo into `:app` with a `jacocoTestReport` task

**Goal:** Produce HTML + XML coverage reports for unit tests in the `debug` variant, excluding generated and uncoverable code.

**Touches:**
- `app/build.gradle.kts` (EDIT — apply jacoco plugin + new task)

**Steps:**
1. In the `app/build.gradle.kts` plugins block, append `id("jacoco")`. Reason: JaCoCo is a built-in Gradle plugin; no catalog alias is needed.
2. Add a configuration block:
   ```
   jacoco {
       toolVersion = libs.versions.jacoco.get()
   }
   ```
   Reason: pins the JaCoCo runtime to 0.8.12 across all coverage tasks.
3. Register the report task:
   ```
   tasks.register<JacocoReport>("jacocoTestReport") {
       dependsOn("testDebugUnitTest")
       group = "verification"
       description = "Generates JaCoCo coverage report for the debug unit tests."

       reports {
           html.required.set(true)
           xml.required.set(true)
           csv.required.set(false)
       }

       val fileFilter = listOf(
           "**/R.class",
           "**/R$*.class",
           "**/BuildConfig.*",
           "**/Manifest*.*",
           "**/*_Factory*.*",
           "**/*_HiltModules*.*",
           "**/Hilt_*.*",
           "**/*Module*.*",
           "**/di/**",
           "**/ui/theme/**",
           "**/navigation/**",
           "**/view/**/*Preview*.*",
           "**/MainActivity*.*",
           "**/MovieFluxApp*.*"
       )

       val kotlinClasses = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
           exclude(fileFilter)
       }
       classDirectories.setFrom(files(kotlinClasses))
       sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
       executionData.setFrom(
           fileTree(layout.buildDirectory.get()) {
               include("jacoco/testDebugUnitTest.exec", "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
           }
       )
   }
   ```
   Reason: `tmp/kotlin-classes/debug` is the AGP 9.x output directory for Kotlin bytecode; the `executionData` covers both pre-AGP-9 (`jacoco/...`) and AGP-9 (`outputs/unit_test_code_coverage/...`) paths so the task works across local and CI Gradle runs.
4. In the `android { buildTypes { debug { } } }` block (add `debug { }` if absent), set `enableUnitTestCoverage = true`. Reason: tells AGP to instrument `testDebugUnitTest` classes for JaCoCo so the `.exec` file is actually produced.
5. Do NOT register a `jacocoTestCoverageVerification` task in this cut. Defer threshold enforcement until at least two reports have been read.

**Tests:**
- None directly. JaCoCo's report is the artefact under test.

**Acceptance criterion:** `gradlew jacocoTestReport` succeeds and produces `app/build/reports/jacoco/jacocoTestReport/html/index.html` and `.../jacocoTestReport.xml`. Opening the HTML shows non-zero coverage on `MovieRepositoryImpl`, `HomeViewModel`, `DetailsViewModel`, `LoginViewModel`, `FavoritesViewModel`, `ProfileViewModel`, and the analytics package. Excluded packages (`di`, `navigation`, `ui/theme`) do not appear.

---

### Phase 4 — Konsist architecture tests (REQUIRED — main test layer)

**Goal:** Make every architectural convention currently described only in `CLAUDE.md` mechanically enforced by a JUnit test. The suite is the project's contract; a violation fails CI.

**Touches:**
- `gradle/libs.versions.toml` (EDIT — add Konsist version + library alias)
- `app/build.gradle.kts` (EDIT — `testImplementation(libs.konsist)`)
- `app/src/test/java/com/example/movieflux/architecture/KonsistArchitectureTest.kt` (NEW)
- `app/src/test/java/com/example/movieflux/architecture/ArchitectureScope.kt` (NEW — shared filtered scope)

**Steps:**

1. **Catalog wiring.** In `libs.versions.toml`, add `konsist = "0.17.3"` under `[versions]` and `konsist = { group = "com.lemonappdev", name = "konsist", version.ref = "konsist" }` under `[libraries]`. In `app/build.gradle.kts` add `testImplementation(libs.konsist)` under the existing JUnit/MockK/Turbine block. Reason: Konsist is a test-only dependency; pinning via the catalog matches the project convention.

2. **Shared scope helper.** Create `ArchitectureScope.kt` in package `com.example.movieflux.architecture`, importing `com.lemonappdev.konsist.api.Konsist`. Expose `internal val productionScope = Konsist.scopeFromProduction().files` chained through four `filterNot` calls to exclude `it.path.contains("/build/generated/")`, `it.name.startsWith("Hilt_")`, `it.name.endsWith("_HiltModules.kt")`, and `it.name.endsWith("_Factory.kt")`. Reason: every test reuses the same filtered scope; generated/Hilt files are out of bounds.

3. **`KonsistArchitectureTest.kt` — Layering rules** (the most important block; these encode the Clean Architecture contract from `CLAUDE.md`):
   - `domain layer has no android imports`: `Konsist.scopeFromPackage("com.example.movieflux.domain..").files.assertFalse { it.hasImport { i -> i.name.startsWith("android.") || i.name.startsWith("androidx.") } }`. Reason: domain must remain framework-free.
   - `domain layer has no data layer imports`: `...assertFalse { it.hasImport { i -> i.name.startsWith("com.example.movieflux.data.") } }`. Reason: dependency inversion — data depends on domain, never the reverse.
   - `domain layer has no view layer imports`: same shape, target `com.example.movieflux.view.`. Reason: keeps domain pure.
   - `view layer never imports data layer directly`: `Konsist.scopeFromPackage("com.example.movieflux.view..").files.assertFalse { it.hasImport { i -> i.name.startsWith("com.example.movieflux.data.") } }`. Reason: ViewModels go through `MovieRepository`, never touch `MovieDao`, `MovieEntity`, or DTOs.
   - `navigation layer never imports data or domain implementation details`: assert no imports of `com.example.movieflux.data.` from `com.example.movieflux.navigation..`. Reason: navigation is a UI concern only.

4. **`KonsistArchitectureTest.kt` — Naming + Hilt rules:**
   - `classes annotated @HiltViewModel must end with ViewModel and extend ViewModel`: `productionScope.classes().withAnnotationOf(HiltViewModel::class).assertTrue { it.name.endsWith("ViewModel") && it.hasParentWithName("ViewModel") }`. Reason: enforces consistent VM discovery in tests, screens, and analytics.
   - `every class ending with ViewModel is annotated @HiltViewModel`: inverse of the above (excluding test fakes via `withoutNameEndingWith("FakeViewModel")` if any). Reason: catches a developer who creates a VM without Hilt injection.
   - `classes ending with UseCase live under domain.usecase`: `productionScope.classes().withNameEndingWith("UseCase").assertTrue { it.resideInPackage("com.example.movieflux.domain.usecase..") }`. Reason: locks down the package-by-feature convention.
   - `repository interfaces live under domain.repository, implementations under data.repository`: assert any interface ending with `Repository` is in `domain.repository`; any class ending with `RepositoryImpl` is in `data.repository`. Reason: enforces the seam used by `MovieRepositoryImpl`.
   - `Hilt modules end with Module and are annotated @Module @InstallIn`: scope `com.example.movieflux.di..` (and any nested `di/` packages), `assertTrue { it.hasAnnotationOf(Module::class) && it.hasAnnotationOf(InstallIn::class) }`. Reason: prevents an unannotated provider from silently breaking the graph.

5. **`KonsistArchitectureTest.kt` — Room rules:**
   - `Room entity classes are annotated @Entity and reside in data.local`: `productionScope.classes().withAnnotationOf(Entity::class).assertTrue { it.resideInPackage("com.example.movieflux.data.local..") }`. Reason: prevents leakage of Room types into other packages.
   - `every DAO is an interface, annotated @Dao, residing in data.local`: scope filter on `withAnnotationOf(Dao::class)`, assert `isInterface && resideInPackage("com.example.movieflux.data.local..")`. Reason: enforces the Room DAO convention.
   - `no Room entity is imported by view or domain`: scoped `assertFalse` checking imports do not reference any `@Entity`-annotated class FQN. Reason: backstop for the layering rule above, expressed at the symbol level.

6. **`KonsistArchitectureTest.kt` — Retrofit + Compose rules:**
   - `Retrofit service interfaces (any interface annotated with @GET/@POST/@PUT/@DELETE) reside in data.remote`: `productionScope.interfaces().assertTrue { iface -> iface.functions().none { it.hasAnnotation { a -> a.name in setOf("GET","POST","PUT","DELETE") } } || iface.resideInPackage("com.example.movieflux.data.remote..") }`. Reason: locks the Retrofit interface location.
   - `Composable functions are top-level or member of objects, never of classes` and `Composable functions use PascalCase`: `productionScope.functions().withAnnotationOf(Composable::class).assertTrue { it.name.first().isUpperCase() }`. Reason: matches the Detekt `FunctionNaming` override and gives a second, source-level enforcement.
   - `screens live under view/<feature>/ and end with Screen`: `productionScope.functions().withAnnotationOf(Composable::class).withNameEndingWith("Screen").assertTrue { it.resideInPackage("com.example.movieflux.view..") }`. Reason: aligns with `CLAUDE.md` package-by-feature rule.

7. **`KonsistArchitectureTest.kt` — Mapper + DTO rules:**
   - `DTOs (classes ending with Dto) reside in data.remote and are not referenced outside data`: `productionScope.classes().withNameEndingWith("Dto").assertTrue { it.resideInPackage("com.example.movieflux.data.remote..") }`; companion assertion that no file outside `data.` imports a `Dto`. Reason: DTOs are network-transport types; leaking them into ViewModels is a clean-arch violation.
   - `Mappers reside in data.mapper`: `productionScope.classes().withNameContaining("Mapper").assertTrue { it.resideInPackage("com.example.movieflux.data.mapper..") }`. Reason: the mapper layer is the only place DTO ↔ Domain conversions live.

8. **Test class shape.** All assertions go in one `KonsistArchitectureTest` class, grouped into JUnit 4 `@Test fun` methods named after the rule (`fun domain layer has no android imports()`). Each test is independent so a single failure does not mask others. No setup needed beyond importing `productionScope`. Reason: keeps the architectural contract readable as a single document; a failure name pinpoints the violated rule.

**Tests:**
- The test class IS the test. No tests of tests.
- Verify locally by deliberately introducing a violation (e.g. add `import com.example.movieflux.data.local.MovieEntity` to a ViewModel) and confirm the corresponding Konsist test fails with a clear diagnostic; then revert.

**Acceptance criterion:** `gradlew testDebugUnitTest --tests "com.example.movieflux.architecture.KonsistArchitectureTest"` passes on the current branch (architecture is already correct per `CHALLENGE_VALIDATION.md §B3` — "No architecture violations found"). The full `testDebugUnitTest` task continues to pass with no regressions. Introducing a deliberate layering violation breaks the suite.

---

### Phase 5 — Aggregate convenience task and README docs

**Goal:** Give developers and CI a single entry point and a one-paragraph instruction on how to read the reports.

**Touches:**
- `app/build.gradle.kts` (EDIT)
- `README.md` (EDIT)

**Steps:**
1. Register a thin aggregate task in `app/build.gradle.kts`:
   ```
   tasks.register("codeQuality") {
       group = "verification"
       description = "Runs Detekt, the JaCoCo coverage report, and the Konsist architecture tests."
       dependsOn("detekt", "jacocoTestReport", "testDebugUnitTest")
   }
   ```
   Reason: `jacocoTestReport` already depends on `testDebugUnitTest`, but listing it explicitly documents that Konsist runs as part of the gate. A CI job can run `gradlew codeQuality` instead of listing each task.
2. In `README.md` under a new "Qualidade de Código" section (PT-BR, matching the existing tone), add four short paragraphs:
   - **Detekt:** `gradlew detekt` → relatório em `app/build/reports/detekt/detekt.html`. Configuração em `config/detekt/detekt.yml`, baseline em `config/detekt/baseline.xml`.
   - **JaCoCo:** `gradlew jacocoTestReport` → relatório em `app/build/reports/jacoco/jacocoTestReport/html/index.html`. A task já depende de `testDebugUnitTest`.
   - **Konsist:** `gradlew testDebugUnitTest --tests "*KonsistArchitectureTest"` valida as regras de arquitetura (camadas Clean, naming, anotações Hilt/Room/Retrofit). Sem baseline — qualquer violação quebra o build.
   - **Atalho:** `gradlew codeQuality` roda os três em sequência.
3. Mention in the same section that the Detekt baseline can be regenerated with `gradlew detektBaseline` after a deliberate cleanup PR, but that Konsist has no equivalent — a Konsist failure means the architecture has drifted and must be fixed.

**Tests:**
- None directly.

**Acceptance criterion:** `gradlew codeQuality` succeeds end-to-end on a clean clone; README "Qualidade de Código" section is present with the three paragraphs.

---

## Acceptance Checklist

### Build
- [ ] `gradlew detekt` succeeds on the current branch (baseline absorbs existing findings).
- [ ] `gradlew jacocoTestReport` succeeds and depends on `testDebugUnitTest`.
- [ ] `gradlew testDebugUnitTest --tests "*KonsistArchitectureTest"` succeeds (no architecture violations).
- [ ] `gradlew codeQuality` succeeds and runs detekt, JaCoCo, and the unit-test suite (which includes Konsist).
- [ ] `gradlew clean assembleDebug` continues to pass — new plugins do not break the existing build.

### Configuration
- [ ] `gradle/libs.versions.toml` pins `detekt = "1.23.7"`, `jacoco = "0.8.12"`, and `konsist = "0.17.3"`.
- [ ] `config/detekt/detekt.yml` exists with the documented overrides only (not a full generated dump).
- [ ] `config/detekt/baseline.xml` exists and is checked into git.
- [ ] `app/build.gradle.kts` applies `detekt` plugin, `jacoco` plugin, and `kotlin-android` plugin (in that order after `android.application` and before `kotlin-compose`).
- [ ] `app/build.gradle.kts` declares `testImplementation(libs.konsist)`.

### Architecture tests (Konsist)
- [ ] `KonsistArchitectureTest` exists under `app/src/test/java/com/example/movieflux/architecture/`.
- [ ] Layering rules: domain has no `android.*` / `androidx.*` imports; domain does not import `data.*` or `view.*`; `view.*` never imports `data.*`; `navigation.*` never imports `data.*`.
- [ ] Naming + Hilt rules: every `@HiltViewModel` ends with `ViewModel` and extends `ViewModel`; every `*ViewModel` is `@HiltViewModel`-annotated; `*UseCase` lives in `domain.usecase`; repository interfaces in `domain.repository`, impls in `data.repository`; every Hilt module is `@Module @InstallIn`.
- [ ] Room rules: `@Entity` classes reside in `data.local`; every `@Dao` is an interface in `data.local`; no entity FQN is imported outside `data.`.
- [ ] Retrofit + Compose rules: Retrofit interfaces (using `@GET/@POST/@PUT/@DELETE`) reside in `data.remote`; `@Composable` functions use PascalCase; `*Screen` composables reside in `view.*`.
- [ ] Mapper + DTO rules: `*Dto` resides in `data.remote` and is not imported outside `data.*`; mappers reside in `data.mapper`.
- [ ] Deliberately introducing a layering violation (e.g. importing `MovieEntity` from a ViewModel) fails the relevant Konsist test with a clear diagnostic.

### Reports
- [ ] `app/build/reports/detekt/detekt.html` opens in a browser and lists zero failures.
- [ ] `app/build/reports/detekt/detekt.xml` is produced for CI consumption.
- [ ] `app/build/reports/jacoco/jacocoTestReport/html/index.html` opens and shows non-zero coverage on the ViewModel and repository packages.
- [ ] `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` is produced for CI consumption.
- [ ] No coverage rows appear for `di`, `navigation`, `ui/theme`, `MainActivity`, or `MovieFluxApp`.

### Documentation
- [ ] README "Qualidade de Código" section documents `gradlew detekt`, `gradlew jacocoTestReport`, the Konsist test command, and `gradlew codeQuality`.
- [ ] README notes how to regenerate the Detekt baseline and explicitly states that Konsist has no baseline.

## Out of Scope / Future Work

- **Coverage threshold enforcement.** Deferred — adding `jacocoTestCoverageVerification` with a min-coverage rule turns a useful signal into a CI blocker without baseline data. Revisit after two or three reports have been collected so the threshold reflects reality.
- **Compose-specific Detekt rules** via `io.nlopez.compose.rules:detekt`. Deferred — the built-in rules with the documented overrides cover today's needs; revisit if reviewers flag recurring Compose-idiom violations.
- **ktlint as a separate plugin.** Deferred — Detekt's `formatting` rule set is ktlint-backed and sufficient; introducing a standalone ktlint plugin would double the formatting noise.
- **SARIF output for GitHub code scanning.** Deferred — wire when a CI pipeline is added; the report is a one-line `sarif.required.set(true)` toggle.
- **`androidTest` coverage.** Deferred — the project has one instrumented test (`LoginScreenTest`); the unit-test report is the higher-signal artefact today. Add a `jacocoAndroidTestReport` task once instrumented coverage warrants it.
- **Multi-module setup.** Out of scope — the project is single-module. If `:domain` or `:data` are extracted later, promote both plugins to a `buildSrc` convention plugin to avoid duplication.
- **Konsist module-boundary rules** (e.g. `assertArchitecture { domain.dependsOnNothing(); data.dependsOn(domain); presentation.dependsOn(domain) }`). Deferred until the project is split into Gradle modules — Konsist's module API needs real module boundaries to be meaningful. The per-package layering tests in Phase 4 cover the same intent for the single-module case.
- **Konsist baseline / `withSuppress`.** Explicitly rejected — see the corresponding Architecture Decision. The architecture contract is enforced from day one.
