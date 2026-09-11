package com.dfcruz.modulens.gradle

import com.dfcruz.modulens.DependencyScope
import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.report.DirectLibraryDependencyDeclaration
import com.dfcruz.modulens.report.DirectModuleDependencyDeclaration

/**
 * Immutable snapshot of dependency declarations collected from a Gradle project.
 *
 * It retains the Gradle-facing details required for reporting (configuration and scope), while
 * exposing scope-aware views for tasks. Graph algorithms remain in [ModuleGraph].
 */
class ProjectDependencyGraph internal constructor(
    private val modulesByPath: Map<String, CollectedModule>,
) {
    val modulePaths: Set<String> = modulesByPath.keys

    fun excluding(modulePaths: Set<String>): ProjectDependencyGraph = ProjectDependencyGraph(
        modulesByPath.filterKeys { it !in modulePaths },
    )

    fun moduleGraph(scopes: Set<DependencyScope>): ModuleGraph = ModuleGraph(
        modulesByPath.mapValues { (_, module) ->
            module.projectDependencies
                .filter { it.scope in scopes && it.path in this.modulePaths }
                .map { it.path }
                .distinct()
                .sorted()
        },
    )

    fun declaredLibraries(scopes: Set<DependencyScope>): Map<String, List<String>> =
        modulesByPath.mapValues { (_, module) ->
            module.declaredLibraryDependencies
                .filter { it.scope in scopes }
                .map { it.coordinate }
                .distinct()
                .sorted()
        }

    fun moduleDependencies(modulePath: String, scope: DependencyScope): List<String> =
        modulesByPath[modulePath]
            ?.projectDependencies
            .orEmpty()
            .filter { it.scope == scope && it.path in modulePaths }
            .map { it.path }
            .distinct()
            .sorted()

    fun libraryDependencies(modulePath: String, scope: DependencyScope): List<String> =
        modulesByPath[modulePath]
            ?.declaredLibraryDependencies
            .orEmpty()
            .filter { it.scope == scope }
            .map { it.coordinate }
            .distinct()
            .sorted()

    fun directModuleDeclarations(scopes: Set<DependencyScope>): List<DirectModuleDependencyDeclaration> =
        modulesByPath.values
            .flatMap { module ->
                module.projectDependencies
                    .filter { it.scope in scopes && it.path in modulePaths }
                    .map { dependency ->
                        DirectModuleDependencyDeclaration(
                            from = module.path,
                            to = dependency.path,
                            configuration = dependency.configuration,
                            scope = dependency.scope.optionName,
                        )
                    }
            }
            .distinct()
            .sortedWith(compareBy({ it.from }, { it.to }, { it.configuration }, { it.scope }))

    fun directLibraryDeclarations(scopes: Set<DependencyScope>): List<DirectLibraryDependencyDeclaration> =
        modulesByPath.values
            .flatMap { module ->
                module.declaredLibraryDependencies
                    .filter { it.scope in scopes }
                    .map { dependency ->
                        DirectLibraryDependencyDeclaration(
                            module = module.path,
                            identifier = dependency.moduleIdentifier,
                            declaredVersion = dependency.version,
                            configuration = dependency.configuration,
                            scope = dependency.scope.optionName,
                        )
                    }
            }
            .distinct()
            .sortedWith(compareBy({ it.module }, { it.identifier }, { it.declaredVersion }, { it.configuration }, { it.scope }))

    fun libraryCoordinates(scopes: Set<DependencyScope>): List<String> =
        declaredLibraries(scopes).values.flatten().distinct().sorted()

    fun libraryCoordinatesUsedBy(modulePath: String, scopes: Set<DependencyScope>): List<String> {
        val libraries = declaredLibraries(scopes)
        val moduleGraph = moduleGraph(scopes)
        return buildList {
            addAll(libraries[modulePath].orEmpty())
            moduleGraph.transitiveDependencies(modulePath)
                .forEach { dependency -> addAll(libraries[dependency].orEmpty()) }
        }.distinct().sorted()
    }
}
