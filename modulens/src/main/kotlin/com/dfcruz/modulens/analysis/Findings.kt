package com.dfcruz.modulens.analysis

enum class FindingId(val ruleName: String) {
    DEPENDENCY_CYCLE("cycle"),
    MAXIMUM_DEPENDENCY_DEPTH("maximum dependency depth"),
    MAXIMUM_DIRECT_DEPENDENCIES("maximum direct dependencies"),
    REDUNDANT_MODULE_DEPENDENCY("redundant module dependency"),
    VERSION_CONFLICT("version conflict"),
}

enum class FindingSeverity { ERROR, WARNING }

data class FindingEvidence(
    val dependencyPath: List<String> = emptyList(),
    val declarations: List<String> = emptyList(),
)

data class ModuleEdgeReference(
    val from: String,
    val to: String,
)

data class FindingReferences(
    val modules: List<String> = emptyList(),
    val libraries: List<String> = emptyList(),
    val moduleEdges: List<ModuleEdgeReference> = emptyList(),
)

data class Finding(
    val id: FindingId,
    val severity: FindingSeverity,
    val subject: String,
    val message: String,
    val evidence: FindingEvidence = FindingEvidence(),
    val references: FindingReferences = FindingReferences(),
    val suggestedFix: String? = null,
)

data class FindingAnalysisSnapshot(
    val graph: ModuleGraph,
    val libraryInventory: LibraryInventoryAnalysis,
)

fun interface FindingAnalyzer {
    fun analyze(snapshot: FindingAnalysisSnapshot): List<Finding>
}

object ProjectFindingsAnalyzer {
    private val defaultAnalyzers = listOf(
        CycleFindingAnalyzer,
        RedundantModuleDependencyFindingAnalyzer,
        VersionConflictFindingAnalyzer,
    )

    fun analyze(snapshot: FindingAnalysisSnapshot): List<Finding> =
        defaultAnalyzers.flatMap { it.analyze(snapshot) }.sortedWith(findingOrder)

    fun analyzeForVerification(
        snapshot: FindingAnalysisSnapshot,
        policy: VerificationPolicy
    ): List<Finding> =
        (analyze(snapshot) + ThresholdFindingAnalyzer(policy).analyze(snapshot)).sortedWith(
            findingOrder
        )

    private val findingOrder = compareBy<Finding>({ it.id.ordinal }, { it.subject }, { it.message })
}

object CycleFindingAnalyzer : FindingAnalyzer {
    override fun analyze(snapshot: FindingAnalysisSnapshot): List<Finding> =
        snapshot.graph.cycles().map { cycle ->
            Finding(
                id = FindingId.DEPENDENCY_CYCLE,
                severity = FindingSeverity.ERROR,
                subject = cycle.first(),
                message = "Dependency cycle: ${cycle.joinToString(" → ")}",
                evidence = FindingEvidence(dependencyPath = cycle),
                references = FindingReferences(
                    modules = cycle.distinct().sorted(),
                    moduleEdges = cycle.zipWithNext()
                        .map { (from, to) -> ModuleEdgeReference(from, to) },
                ),
                suggestedFix = "Remove one dependency in this cycle or introduce an abstraction that breaks it.",
            )
        }
}

object RedundantModuleDependencyFindingAnalyzer : FindingAnalyzer {
    override fun analyze(snapshot: FindingAnalysisSnapshot): List<Finding> = buildList {
        snapshot.graph.modules.sorted().forEach { module ->
            val directDependencies = snapshot.graph.directDependencies(module)
            directDependencies.forEach { dependency ->
                directDependencies
                    .asSequence()
                    .filterNot { it == dependency }
                    .mapNotNull { alternative ->
                        snapshot.graph.dependencyPath(
                            alternative,
                            dependency
                        )
                    }
                    .firstOrNull()
                    ?.let { alternativePath ->
                        add(
                            Finding(
                                id = FindingId.REDUNDANT_MODULE_DEPENDENCY,
                                severity = FindingSeverity.WARNING,
                                subject = "$module → $dependency",
                                message = "$module directly depends on $dependency, which is already reachable through ${alternativePath.first()}.",
                                evidence = FindingEvidence(dependencyPath = listOf(module) + alternativePath),
                                references = FindingReferences(
                                    modules = (listOf(module) + alternativePath).distinct()
                                        .sorted(),
                                    moduleEdges = listOf(ModuleEdgeReference(module, dependency)),
                                ),
                                suggestedFix = "Remove the direct dependency unless it intentionally documents or exposes a required relationship.",
                            ),
                        )
                    }
            }
        }
    }
}

object VersionConflictFindingAnalyzer : FindingAnalyzer {
    override fun analyze(snapshot: FindingAnalysisSnapshot): List<Finding> =
        snapshot.libraryInventory.versionConflicts.map { library ->
            Finding(
                id = FindingId.VERSION_CONFLICT,
                severity = FindingSeverity.WARNING,
                subject = library.identifier,
                message = "${library.identifier} is declared with versions ${library.declaredVersions.joinToString()}.",
                evidence = FindingEvidence(declarations = library.modules),
                references = FindingReferences(
                    modules = library.modules.sorted(),
                    libraries = listOf(library.identifier),
                ),
                suggestedFix = "Align this dependency to one version, preferably through the version catalog or a platform/BOM.",
            )
        }
}

private class ThresholdFindingAnalyzer(private val policy: VerificationPolicy) : FindingAnalyzer {
    override fun analyze(snapshot: FindingAnalysisSnapshot): List<Finding> = buildList {
        val project = snapshot.graph.analyzeProject()
        if (project.maximumDependencyDepth > policy.maxDependencyDepth) {
            add(
                Finding(
                    id = FindingId.MAXIMUM_DEPENDENCY_DEPTH,
                    severity = FindingSeverity.ERROR,
                    subject = "project",
                    message = "Dependency depth ${project.maximumDependencyDepth} exceeds the configured maximum of ${policy.maxDependencyDepth}.",
                    references = FindingReferences(modules = snapshot.graph.modules.sorted()),
                    suggestedFix = "Reduce the longest module dependency chain or raise the configured limit deliberately.",
                ),
            )
        }
        snapshot.graph.modules.sorted().forEach { module ->
            val count = snapshot.graph.directDependencies(module).size
            if (count > policy.maxDirectDependencies) {
                add(
                    Finding(
                        id = FindingId.MAXIMUM_DIRECT_DEPENDENCIES,
                        severity = FindingSeverity.ERROR,
                        subject = module,
                        message = "$module has $count direct dependencies; the configured maximum is ${policy.maxDirectDependencies}.",
                        references = FindingReferences(modules = listOf(module)),
                        suggestedFix = "Remove unnecessary direct dependencies or split this module's responsibilities.",
                    ),
                )
            }
        }
    }
}
