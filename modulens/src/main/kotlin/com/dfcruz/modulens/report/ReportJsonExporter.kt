package com.dfcruz.modulens.report

import com.dfcruz.modulens.analysis.Finding
import com.dfcruz.modulens.analysis.FindingSeverity
import com.dfcruz.modulens.analysis.LibraryInventoryAnalysis
import com.dfcruz.modulens.analysis.ModuleEdgeReference
import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.analysis.ProjectGraphAnalysis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ReportJsonExporter {
    private val json = Json {
        prettyPrint = true
    }

    fun fullReport(snapshot: ReportAnalysisSnapshot): String = fullReport(
        graph = snapshot.graph,
        project = snapshot.project,
        libraries = snapshot.libraries,
        findings = snapshot.findings,
        moduleLibraries = snapshot.moduleLibraries,
        metadata = snapshot.metadata,
        directModuleDependencies = snapshot.directModuleDependencies,
        directLibraryDeclarations = snapshot.directLibraryDeclarations,
    )

    private fun fullReport(
        graph: ModuleGraph,
        project: ProjectGraphAnalysis,
        libraries: LibraryInventoryAnalysis,
        findings: List<Finding>,
        moduleLibraries: Map<String, List<String>>,
        metadata: ReportMetadata,
        directModuleDependencies: List<DirectModuleDependencyDeclaration>,
        directLibraryDeclarations: List<DirectLibraryDependencyDeclaration>,
    ): String {
        val usageModulesByLibrary = if (metadata.options.includeLibraryUsageModules) {
            buildMap<String, MutableSet<String>> {
                moduleLibraries.forEach { (module, coordinates) ->
                    coordinates.forEach { coordinate ->
                        getOrPut(libraryIdentifier(coordinate)) { linkedSetOf() }.add(module)
                    }
                }
            }.mapValues { (_, modules) -> modules.sorted() }
        } else {
            emptyMap()
        }

        return json.encodeToString(
            FullReport(
            schemaVersion = 1,
            reportType = "modulens-full-report",
            metadata = ReportMetadataDto(
                pluginId = metadata.pluginId,
                gradleVersion = metadata.gradleVersion,
                includedScopes = metadata.includedScopes.sorted(),
                excludedModules = metadata.excludedModules.sorted(),
                externalLibraryResolution = ExternalLibraryResolutionReport(
                    enabled = metadata.externalLibraryResolutionEnabled,
                    status = if (metadata.externalLibraryResolutionEnabled) "completed" else "skipped",
                ),
                options = ReportOptionsDto(
                    minimumFindingSeverity = metadata.options.minimumFindingSeverity.name,
                    includeResolvedLibraries = metadata.options.includeResolvedLibraries,
                    includeTransitiveModuleRelationships = metadata.options.includeTransitiveModuleRelationships,
                    includeLibraryUsageModules = metadata.options.includeLibraryUsageModules,
                    ignoredLibraryPatterns = metadata.options.ignoredLibraryPatterns.sorted(),
                ),
            ),
            project = ProjectReport(
                modules = project.totalModules,
                dependencyEdges = project.totalDependencies,
                averageDependencies = project.averageDependencies,
                maximumDependencyDepth = project.maximumDependencyDepth,
                cycles = project.cycles.sortedBy { it.joinToString("\u0000") },
            ),
            directModuleDependencies = directModuleDependencies.sortedWith(
                compareBy(
                    DirectModuleDependencyDeclaration::from,
                    DirectModuleDependencyDeclaration::to,
                    DirectModuleDependencyDeclaration::configuration,
                    DirectModuleDependencyDeclaration::scope,
                ),
            ),
            directLibraryDeclarations = directLibraryDeclarations.sortedWith(
                compareBy(
                    DirectLibraryDependencyDeclaration::module,
                    DirectLibraryDependencyDeclaration::identifier,
                    DirectLibraryDependencyDeclaration::configuration,
                    DirectLibraryDependencyDeclaration::declaredVersion,
                ),
            ),
            modules = graph.modules.sorted().map { module ->
                graph.analyzeModule(module).let { analysis ->
                    ModuleReport(
                        path = module,
                        directDependencies = analysis.directDependencies.sorted(),
                        transitiveDependencyCount = analysis.transitiveDependencies.size,
                        transitiveDependencies = if (metadata.options.includeTransitiveModuleRelationships) {
                            analysis.transitiveDependencies.sorted()
                        } else {
                            emptyList()
                        },
                        directDependents = analysis.directDependents.sorted(),
                        affectedModuleCount = analysis.allDependents.size,
                        affectedModules = if (metadata.options.includeTransitiveModuleRelationships) {
                            analysis.allDependents.sorted()
                        } else {
                            emptyList()
                        },
                        libraries = if (metadata.options.includeResolvedLibraries) {
                            moduleLibraries[module].orEmpty().sorted()
                        } else {
                            emptyList()
                        },
                        dependencyDepth = analysis.dependencyDepth,
                        dependentDepth = analysis.dependentDepth,
                        projectCoverage = analysis.projectCoverage,
                    )
                }
            },
            libraries = LibraryInventoryReport(
                directDeclarations = libraries.directDeclarations,
                versionConflicts = libraries.versionConflicts.map { it.identifier }.sorted(),
                libraries = libraries.entries.sortedBy { it.identifier }.map { entry ->
                    val usageModules = usageModulesByLibrary[entry.identifier].orEmpty()
                    LibraryReport(
                        identifier = entry.identifier,
                        declaredVersions = entry.declaredVersions.sorted(),
                        resolvedVersion = entry.resolvedVersion,
                        modules = usageModules,
                        directModules = entry.modules.sorted(),
                    )
                },
            ),
            findings = FindingsReport(
                findings = findings
                    .filter { it.severity.isIncludedBy(metadata.options.minimumFindingSeverity) }
                    .sortedWith(compareBy<Finding>({ it.id.name }, { it.subject }, { it.message }))
                    .map(::findingReport),
            ),
            ),
        )
    }

private fun findingReport(finding: Finding): FindingReport = FindingReport(
        id = finding.id.name,
        severity = finding.severity.name,
        subject = finding.subject,
        message = finding.message,
        dependencyPath = finding.evidence.dependencyPath,
        declarations = finding.evidence.declarations.sorted(),
        references = FindingReferencesReport(
            modules = finding.references.modules.sorted(),
            libraries = finding.references.libraries.sorted(),
            moduleEdges = finding.references.moduleEdges
                .sortedWith(compareBy(ModuleEdgeReference::from, ModuleEdgeReference::to))
                .map { ModuleEdgeReport(it.from, it.to) },
        ),
        suggestedFix = finding.suggestedFix,
    )

    private fun libraryIdentifier(coordinate: String): String = coordinate.substringBeforeLast(":")
}

@Serializable
private data class FullReport(
    val schemaVersion: Int,
    val reportType: String,
    val metadata: ReportMetadataDto,
    val project: ProjectReport,
    val directModuleDependencies: List<DirectModuleDependencyDeclaration>,
    val directLibraryDeclarations: List<DirectLibraryDependencyDeclaration>,
    val modules: List<ModuleReport>,
    val libraries: LibraryInventoryReport,
    val findings: FindingsReport,
)

private fun FindingSeverity.isIncludedBy(minimum: FindingSeverity): Boolean =
    severityRank >= minimum.severityRank

private val FindingSeverity.severityRank: Int
    get() = when (this) {
        FindingSeverity.ERROR -> 2
        FindingSeverity.WARNING -> 1
    }

@Serializable
private data class ReportMetadataDto(
    val pluginId: String,
    val gradleVersion: String,
    val includedScopes: List<String>,
    val excludedModules: List<String>,
    val externalLibraryResolution: ExternalLibraryResolutionReport,
    val options: ReportOptionsDto,
)

@Serializable
private data class ExternalLibraryResolutionReport(
    val enabled: Boolean,
    val status: String,
)

@Serializable
private data class ReportOptionsDto(
    val minimumFindingSeverity: String,
    val includeResolvedLibraries: Boolean,
    val includeTransitiveModuleRelationships: Boolean,
    val includeLibraryUsageModules: Boolean,
    val ignoredLibraryPatterns: List<String>,
)

@Serializable
private data class ProjectReport(
    val modules: Int,
    val dependencyEdges: Int,
    val averageDependencies: Double,
    val maximumDependencyDepth: Int,
    val cycles: List<List<String>>,
)

@Serializable
private data class ModuleReport(
    val path: String,
    val directDependencies: List<String>,
    val transitiveDependencyCount: Int,
    val transitiveDependencies: List<String>,
    val directDependents: List<String>,
    val affectedModuleCount: Int,
    val affectedModules: List<String>,
    val libraries: List<String>,
    val dependencyDepth: Int,
    val dependentDepth: Int,
    val projectCoverage: Double,
)

@Serializable
private data class LibraryInventoryReport(
    val directDeclarations: Int,
    val versionConflicts: List<String>,
    val libraries: List<LibraryReport>,
)

@Serializable
private data class LibraryReport(
    val identifier: String,
    val declaredVersions: List<String>,
    val resolvedVersion: String?,
    val modules: List<String>,
    val directModules: List<String>,
)

@Serializable
private data class FindingsReport(
    val findings: List<FindingReport>,
)

@Serializable
private data class FindingReport(
    val id: String,
    val severity: String,
    val subject: String,
    val message: String,
    val dependencyPath: List<String>,
    val declarations: List<String>,
    val references: FindingReferencesReport,
    val suggestedFix: String?,
)

@Serializable
private data class FindingReferencesReport(
    val modules: List<String>,
    val libraries: List<String>,
    val moduleEdges: List<ModuleEdgeReport>,
)

@Serializable
private data class ModuleEdgeReport(
    val from: String,
    val to: String,
)
