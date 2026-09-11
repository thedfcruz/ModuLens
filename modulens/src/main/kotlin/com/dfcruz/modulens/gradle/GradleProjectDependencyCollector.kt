package com.dfcruz.modulens.gradle

import com.dfcruz.modulens.DependencyScope
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

data class CollectedModule(
    val path: String,
    val projectDependencies: Set<ProjectDependencyDeclaration>,
    val declaredLibraryDependencies: Set<LibraryDependencyDeclaration>,
)

data class ProjectDependencyDeclaration(
    val path: String,
    val configuration: String,
    val scope: DependencyScope,
)

data class LibraryDependencyDeclaration(
    val group: String,
    val name: String,
    val version: String,
    val configuration: String,
    val scope: DependencyScope,
) {
    val coordinate: String
        get() = "$group:$name:$version"

    val moduleIdentifier: String
        get() = "$group:$name"
}

object GradleProjectDependencyCollector {

    fun collect(project: Project): ProjectDependencyGraph = ProjectDependencyGraph(
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
                subproject.path to CollectedModule(
                    path = subproject.path,
                    projectDependencies = projectDependencies,
                    declaredLibraryDependencies = declaredLibraryDependencies,
                )
            },
    )

    private fun ConfigurationContainer.getModuleDependencies(): Set<ProjectDependencyDeclaration> =
        this
            .flatMap { configuration ->
                val scope = DependencyScope.fromConfiguration(configuration.name)
                    ?: return@flatMap emptyList()

                configuration.dependencies
                    .filterIsInstance<ProjectDependency>()
                    .map { dependency ->
                        ProjectDependencyDeclaration(
                            path = dependency.path,
                            configuration = configuration.name,
                            scope = scope,
                        )
                    }
            }.toSet()

    private fun ConfigurationContainer.getDeclaredLibraryDependencies(): Set<LibraryDependencyDeclaration> =
        this
            .flatMap { configuration ->
                val scope = DependencyScope.fromConfiguration(configuration.name)
                    ?: return@flatMap emptyList()

                configuration.dependencies
                    .filterIsInstance<ExternalModuleDependency>()
                    .mapNotNull { dependency ->
                        val group = dependency.group ?: return@mapNotNull null
                        LibraryDependencyDeclaration(
                            group = group,
                            name = dependency.name,
                            version = dependency.version ?: "unspecified",
                            configuration = configuration.name,
                            scope = scope,
                        )
                    }
            }
            .toSet()

}
