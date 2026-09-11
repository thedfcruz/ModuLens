package com.dfcruz.modulens.analysis

data class VerificationPolicy(
    val failOnCycles: Boolean,
    val maxDependencyDepth: Int,
    val maxDirectDependencies: Int,
    val failOnVersionConflicts: Boolean,
    val failOnRedundantDependencies: Boolean = false,
)

data class VerificationViolation(
    val rule: String,
    val message: String,
    val finding: Finding,
)

object ProjectVerificationEngine {
    fun verify(
        graph: ModuleGraph,
        libraryInventory: LibraryInventoryAnalysis,
        policy: VerificationPolicy,
    ): List<VerificationViolation> {
        return ProjectFindingsAnalyzer
            .analyzeForVerification(FindingAnalysisSnapshot(graph, libraryInventory), policy)
            .filter { finding -> policy.shouldFail(finding) }
            .map { finding -> VerificationViolation(finding.id.ruleName, finding.message, finding) }
    }

    private fun VerificationPolicy.shouldFail(finding: Finding): Boolean = when (finding.id) {
        FindingId.DEPENDENCY_CYCLE -> failOnCycles
        FindingId.VERSION_CONFLICT -> failOnVersionConflicts
        FindingId.REDUNDANT_MODULE_DEPENDENCY -> failOnRedundantDependencies
        FindingId.MAXIMUM_DEPENDENCY_DEPTH,
        FindingId.MAXIMUM_DIRECT_DEPENDENCIES,
            -> true
    }
}
