package com.example.movieflux.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

class KonsistArchitectureTest {

    // ── Layering ──────────────────────────────────────────────────────────────

    @Test
    fun `domain layer has no android imports`() {
        Konsist.scopeFromPackage("com.example.movieflux.domain..").files
            .assertFalse {
                it.hasImport { i ->
                    i.name.startsWith("android.") || i.name.startsWith("androidx.")
                }
            }
    }

    @Test
    fun `domain layer has no data layer imports`() {
        Konsist.scopeFromPackage("com.example.movieflux.domain..").files
            .assertFalse {
                it.hasImport { i -> i.name.startsWith("com.example.movieflux.data.") }
            }
    }

    @Test
    fun `domain layer has no view layer imports`() {
        Konsist.scopeFromPackage("com.example.movieflux.domain..").files
            .assertFalse {
                it.hasImport { i -> i.name.startsWith("com.example.movieflux.view.") }
            }
    }

    @Test
    fun `view layer never imports data local or data remote directly`() {
        // UiPreferences and AuthPreferences live in data.preferences and are
        // intentional cross-layer dependencies; only data.local and data.remote
        // are off-limits to the view layer (MovieDao, MovieEntity, DTOs).
        Konsist.scopeFromPackage("com.example.movieflux.view..").files
            .assertFalse {
                it.hasImport { i ->
                    i.name.startsWith("com.example.movieflux.data.local.") ||
                        i.name.startsWith("com.example.movieflux.data.remote.")
                }
            }
    }

    @Test
    fun `navigation layer never imports data layer`() {
        Konsist.scopeFromPackage("com.example.movieflux.navigation..").files
            .assertFalse {
                it.hasImport { i -> i.name.startsWith("com.example.movieflux.data.") }
            }
    }

    // ── Naming + Hilt ─────────────────────────────────────────────────────────

    @Test
    fun `classes annotated HiltViewModel must end with ViewModel`() {
        productionClasses()
            .filter { cls -> cls.annotations.any { it.name == "HiltViewModel" } }
            .forEach { cls ->
                assert(cls.name.endsWith("ViewModel")) {
                    "Expected @HiltViewModel class '${cls.name}' to end with 'ViewModel'"
                }
            }
    }

    @Test
    fun `classes ending with ViewModel must be annotated HiltViewModel`() {
        productionClasses()
            .filter { cls -> cls.name.endsWith("ViewModel") && !cls.name.startsWith("Fake") }
            .forEach { cls ->
                assert(cls.annotations.any { it.name == "HiltViewModel" }) {
                    "Expected '${cls.name}' to be annotated with @HiltViewModel"
                }
            }
    }

    @Test
    fun `classes ending with UseCase live under domain usecase`() {
        productionClasses()
            .filter { cls -> cls.name.endsWith("UseCase") }
            .forEach { cls ->
                val path = cls.containingFile.path.normalizePath()
                assert(path.contains("/domain/usecase/")) {
                    "Expected UseCase '${cls.name}' under domain/usecase but was: $path"
                }
            }
    }

    @Test
    fun `repository interfaces live under domain repository`() {
        productionInterfaces()
            .filter { iface -> iface.name.endsWith("Repository") }
            .forEach { iface ->
                val path = iface.containingFile.path.normalizePath()
                assert(path.contains("/domain/repository/")) {
                    "Expected Repository '${iface.name}' under domain/repository but was: $path"
                }
            }
    }

    @Test
    fun `repository implementations live under data repository`() {
        productionClasses()
            .filter { cls -> cls.name.endsWith("RepositoryImpl") }
            .forEach { cls ->
                val path = cls.containingFile.path.normalizePath()
                assert(path.contains("/data/repository/")) {
                    "Expected RepositoryImpl '${cls.name}' under data/repository but was: $path"
                }
            }
    }

    @Test
    fun `Hilt modules in di package are annotated Module and InstallIn`() {
        productionClasses()
            .filter { cls ->
                cls.containingFile.path.normalizePath().contains("/di/") &&
                    cls.name.endsWith("Module")
            }
            .forEach { cls ->
                val names = cls.annotations.map { it.name }
                assert("Module" in names) { "Expected '${cls.name}' to have @Module annotation" }
                assert("InstallIn" in names) { "Expected '${cls.name}' to have @InstallIn annotation" }
            }
    }

    // ── Room ─────────────────────────────────────────────────────────────────

    @Test
    fun `Room entity classes reside in data local`() {
        productionClasses()
            .filter { cls -> cls.annotations.any { it.name == "Entity" } }
            .forEach { cls ->
                val path = cls.containingFile.path.normalizePath()
                assert(path.contains("/data/local/")) {
                    "Expected @Entity class '${cls.name}' in data/local but was: $path"
                }
            }
    }

    @Test
    fun `DAO interfaces reside in data local`() {
        productionInterfaces()
            .filter { iface -> iface.annotations.any { it.name == "Dao" } }
            .forEach { iface ->
                val path = iface.containingFile.path.normalizePath()
                assert(path.contains("/data/local/")) {
                    "Expected @Dao interface '${iface.name}' in data/local but was: $path"
                }
            }
    }

    @Test
    fun `no Room entity is imported outside data package`() {
        val entityNames = productionClasses()
            .filter { cls -> cls.annotations.any { it.name == "Entity" } }
            .map { it.name }

        if (entityNames.isEmpty()) return

        Konsist.scopeFromProduction().files
            .filterNot { it.path.normalizePath().contains("/data/") }
            .assertFalse { file ->
                file.hasImport { i -> entityNames.any { name -> i.name.contains(name) } }
            }
    }

    // ── Retrofit ─────────────────────────────────────────────────────────────

    @Test
    fun `Retrofit service interfaces reside in data remote`() {
        val retrofitAnnotations = setOf("GET", "POST", "PUT", "DELETE", "PATCH")
        productionInterfaces()
            .filter { iface ->
                iface.functions().any { fn ->
                    fn.annotations.any { it.name in retrofitAnnotations }
                }
            }
            .forEach { iface ->
                val path = iface.containingFile.path.normalizePath()
                assert(path.contains("/data/remote/")) {
                    "Expected Retrofit interface '${iface.name}' in data/remote but was: $path"
                }
            }
    }

    // ── Compose ───────────────────────────────────────────────────────────────

    @Test
    fun `Composable functions use PascalCase`() {
        productionFunctions()
            .filter { fn -> fn.annotations.any { it.name == "Composable" } }
            .forEach { fn ->
                assert(fn.name.first().isUpperCase()) {
                    "Expected @Composable function '${fn.name}' to use PascalCase"
                }
            }
    }

    @Test
    fun `Screen composables reside in view package`() {
        productionFunctions()
            .filter { fn ->
                fn.annotations.any { it.name == "Composable" } && fn.name.endsWith("Screen")
            }
            .forEach { fn ->
                val path = fn.containingFile.path.normalizePath()
                assert(path.contains("/view/")) {
                    "Expected *Screen composable '${fn.name}' in view/ but was: $path"
                }
            }
    }

    // ── Mapper + DTO ──────────────────────────────────────────────────────────

    @Test
    fun `DTO classes reside in data remote`() {
        productionClasses()
            .filter { cls -> cls.name.endsWith("Dto") }
            .forEach { cls ->
                val path = cls.containingFile.path.normalizePath()
                assert(path.contains("/data/remote/")) {
                    "Expected Dto class '${cls.name}' in data/remote but was: $path"
                }
            }
    }

    @Test
    fun `DTO classes are not imported outside data package`() {
        val dtoNames = productionClasses()
            .filter { it.name.endsWith("Dto") }
            .map { it.name }

        if (dtoNames.isEmpty()) return

        Konsist.scopeFromProduction().files
            .filterNot { it.path.normalizePath().contains("/data/") }
            .assertFalse { file ->
                file.hasImport { i -> dtoNames.any { name -> i.name.contains(name) } }
            }
    }

    @Test
    fun `Mapper classes reside in data mapper`() {
        productionClasses()
            .filter { cls -> cls.name.contains("Mapper") }
            .forEach { cls ->
                val path = cls.containingFile.path.normalizePath()
                assert(path.contains("/data/mapper/")) {
                    "Expected Mapper class '${cls.name}' in data/mapper but was: $path"
                }
            }
    }
}
