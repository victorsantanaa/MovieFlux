package com.example.movieflux.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration

internal fun String.normalizePath() = replace('\\', '/')

internal fun productionClasses(): List<KoClassDeclaration> =
    Konsist.scopeFromProduction().classes()
        .filterNot { it.containingFile.path.normalizePath().contains("/build/generated/") }
        .filterNot { it.containingFile.name.startsWith("Hilt_") }
        .filterNot { it.containingFile.name.endsWith("_HiltModules.kt") }
        .filterNot { it.containingFile.name.endsWith("_Factory.kt") }

internal fun productionInterfaces(): List<KoInterfaceDeclaration> =
    Konsist.scopeFromProduction().interfaces()
        .filterNot { it.containingFile.path.normalizePath().contains("/build/generated/") }
        .filterNot { it.containingFile.name.startsWith("Hilt_") }

internal fun productionFunctions(): List<KoFunctionDeclaration> =
    Konsist.scopeFromProduction().functions()
        .filterNot { it.containingFile.path.normalizePath().contains("/build/generated/") }

internal fun String.resideIn(packagePrefix: String) =
    this.startsWith(packagePrefix)
