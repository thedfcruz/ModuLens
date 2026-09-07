package com.dfcruz.modulens

import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

data class ModuleDependency(
    val path: String,
    val projectDependencies: Set<ModuleDependencyDetails>,
    val declaredLibraryDependencies: Set<LibraryDependencyDetails>,
)

data class ModuleDependencyDetails(
    val path: String,
    val configuration: String,
    val scope: DependencyScope,
)

data class LibraryDependencyDetails(
    val group: String,
    val name: String,
    val version: String,
    val scope: DependencyScope,
) {
    val coordinate: String
        get() = "$group:$name:$version"

    val moduleIdentifier: String
        get() = "$group:$name"
}

object ProjectModuleGraph {

    fun build(project: Project): Map<String, ModuleDependency> =
        project.allprojects
            .filter { it != project }
            .filter { it.buildFile.exists() } // filter ":core", ":component" and other intermediate projects that gradle created
            .associate { subproject ->
                val projectDependencies = subproject.configurations
                    .getModuleDependencies()
                    .filterNot { it.path == subproject.path }
                    .toSet()
                val declaredLibraryDependencies = subproject.configurations
                    .getDeclaredLibraryDependencies()
                subproject.path to ModuleDependency(
                    path = subproject.path,
                    projectDependencies = projectDependencies,
                    declaredLibraryDependencies = declaredLibraryDependencies,
                )
            }

    fun ConfigurationContainer.getModuleDependencies(): Set<ModuleDependencyDetails> =
        this
            .flatMap { configuration ->
                val scope = DependencyScope.fromConfiguration(configuration.name)
                    ?: return@flatMap emptyList()

                configuration.dependencies
                    .filterIsInstance<ProjectDependency>()
                    .map { dependency ->
                        ModuleDependencyDetails(
                            path = dependency.path,
                            configuration = configuration.name,
                            scope = scope,
                        )
                    }
            }.toSet()

    fun ConfigurationContainer.getDeclaredLibraryDependencies(): Set<LibraryDependencyDetails> =
        this
            .flatMap { configuration ->
                val scope = DependencyScope.fromConfiguration(configuration.name)
                    ?: return@flatMap emptyList()

                configuration.dependencies
                    .filterIsInstance<ExternalModuleDependency>()
                    .mapNotNull { dependency ->
                        val group = dependency.group ?: return@mapNotNull null
                        LibraryDependencyDetails(
                            group = group,
                            name = dependency.name,
                            version = dependency.version ?: "unspecified",
                            scope = scope,
                        )
                    }
            }
            .toSet()

    fun resolveTransitiveLibraryDependencies(
        project: Project,
        declaredLibraryCoordinates: List<String>,
    ): List<String> {
        val declaredIdentifiers = declaredLibraryCoordinates
            .map { coordinate -> coordinate.substringBeforeLast(":") }
            .toSet()

        return resolveLibraryCoordinates(project, declaredLibraryCoordinates)
            .filterNot { coordinate ->
                coordinate.substringBeforeLast(":") in declaredIdentifiers
            }
    }

    fun resolveLibraryCoordinates(
        project: Project,
        libraryCoordinates: List<String>,
    ): List<String> {
        if (libraryCoordinates.isEmpty()) return emptyList()

        val dependencies = libraryCoordinates
            .map { coordinate -> project.dependencies.create(coordinate) }
            .toTypedArray()
        val configuration = project.configurations.detachedConfiguration(*dependencies)

        return configuration.incoming.resolutionResult.allComponents
            .mapNotNull { component ->
                val identifier = component.id as? ModuleComponentIdentifier
                    ?: return@mapNotNull null

                LibraryDependencyDetails(
                    group = identifier.group,
                    name = identifier.module,
                    version = identifier.version,
                    scope = DependencyScope.PRODUCTION,
                )
            }
            .map { it.coordinate }
            .distinct()
            .sorted()
    }
}
