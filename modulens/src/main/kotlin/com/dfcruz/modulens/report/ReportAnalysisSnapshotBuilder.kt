package com.dfcruz.modulens.report

import com.dfcruz.modulens.analysis.Finding
import com.dfcruz.modulens.analysis.FindingAnalysisSnapshot
import com.dfcruz.modulens.analysis.LibraryInventoryAnalysis
import com.dfcruz.modulens.analysis.LibraryInventoryAnalyzer
import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.analysis.ProjectFindingsAnalyzer
import com.dfcruz.modulens.analysis.ProjectGraphAnalysis

data class ReportAnalysisInput(
    val graph: Map<String, List<String>>,
    val libraryDeclarations: Map<String, List<String>>,
    val resolvedLibraries: List<String>,
    val moduleLibraries: Map<String, List<String>>,
    val metadata: ReportMetadata,
    val directModuleDependencies: List<DirectModuleDependencyDeclaration>,
    val directLibraryDeclarations: List<DirectLibraryDependencyDeclaration>,
)

data class ReportAnalysisSnapshot(
    val graph: ModuleGraph,
    val project: ProjectGraphAnalysis,
    val libraries: LibraryInventoryAnalysis,
    val findings: List<Finding>,
    val moduleLibraries: Map<String, List<String>>,
    val metadata: ReportMetadata,
    val directModuleDependencies: List<DirectModuleDependencyDeclaration>,
    val directLibraryDeclarations: List<DirectLibraryDependencyDeclaration>,
)

object ReportAnalysisSnapshotBuilder {
    fun build(input: ReportAnalysisInput): ReportAnalysisSnapshot {
        val graph = ModuleGraph(input.graph)
        val libraries = LibraryInventoryAnalyzer.analyze(
            declarations = input.libraryDeclarations,
            resolvedLibraries = input.resolvedLibraries,
        )
        return ReportAnalysisSnapshot(
            graph = graph,
            project = graph.analyzeProject(),
            libraries = libraries,
            findings = ProjectFindingsAnalyzer.analyze(FindingAnalysisSnapshot(graph, libraries)),
            moduleLibraries = input.moduleLibraries.mapValues { (_, libraries) ->
                libraries.distinct().sorted()
            },
            metadata = input.metadata,
            directModuleDependencies = input.directModuleDependencies,
            directLibraryDeclarations = input.directLibraryDeclarations,
        )
    }
}
