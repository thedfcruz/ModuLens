package com.dfcruz.modulens.gradle

import com.dfcruz.modulens.DependencyScope
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

object ExternalLibraryResolver {
    fun resolveTransitiveLibraryDependencies(
        project: Project,
        declaredLibraryCoordinates: List<String>,
    ): List<String> {
        val declaredIdentifiers = declaredLibraryCoordinates
            .map { it.substringBeforeLast(":") }
            .toSet()

        return resolveLibraryCoordinates(project, declaredLibraryCoordinates)
            .filterNot { it.substringBeforeLast(":") in declaredIdentifiers }
    }

    fun resolveLibraryCoordinates(
        project: Project,
        libraryCoordinates: List<String>
    ): List<String> {
        if (libraryCoordinates.isEmpty()) return emptyList()
        val dependencies = libraryCoordinates.map(project.dependencies::create).toTypedArray()
        val configuration = project.configurations.detachedConfiguration(*dependencies)
        return configuration.incoming.resolutionResult.allComponents
            .mapNotNull { component ->
                val identifier =
                    component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                LibraryDependencyDeclaration(
                    group = identifier.group,
                    name = identifier.module,
                    version = identifier.version,
                    configuration = "detached",
                    scope = DependencyScope.PRODUCTION,
                ).coordinate
            }
            .distinct()
            .sorted()
    }
}
