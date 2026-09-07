package com.dfcruz.modulens

object ReportJsonExporter {
    fun dashboard(
        graph: ModuleGraph,
        project: ProjectGraphAnalysis,
        libraries: LibraryInventoryAnalysis,
        findings: List<Finding>,
        moduleLibraries: Map<String, List<String>>,
    ): String = """
        {
          "project": {
            "modules": ${project.totalModules},
            "dependencyEdges": ${project.totalDependencies},
            "averageDependencies": ${project.averageDependencies},
            "maximumDependencyDepth": ${project.maximumDependencyDepth},
            "cycles": ${nestedArrays(project.cycles)}
          },
          "modules": [${graph.modules.sorted().joinToString(",") { module ->
              val analysis = graph.analyzeModule(module)
              "{\"path\":${string(module)},\"directDependencies\":${arrays(analysis.directDependencies)},\"transitiveDependencies\":${arrays(analysis.transitiveDependencies)},\"directDependents\":${arrays(analysis.directDependents)},\"affectedModules\":${arrays(analysis.allDependents)},\"libraries\":${arrays(moduleLibraries[module].orEmpty())},\"dependencyDepth\":${analysis.dependencyDepth},\"dependentDepth\":${analysis.dependentDepth},\"projectCoverage\":${analysis.projectCoverage}}"
          }}],
          "libraries": ${dashboardLibraries(libraries, moduleLibraries)},
          "findings": ${findings(findings)}
        }
    """.trimIndent()

    fun findings(findings: List<Finding>): String = """
        {
          "findings": [${findings.joinToString(",") { finding ->
              "{\"id\":${string(finding.id.name)},\"severity\":${string(finding.severity.name)},\"subject\":${string(finding.subject)},\"message\":${string(finding.message)},\"dependencyPath\":${arrays(finding.evidence.dependencyPath)},\"declarations\":${arrays(finding.evidence.declarations)},\"suggestedFix\":${finding.suggestedFix?.let(::string) ?: "null"}}"
          }}]
        }
    """.trimIndent()

    fun project(analysis: ProjectGraphAnalysis): String = """
        {
          "modules": ${analysis.totalModules},
          "dependencyEdges": ${analysis.totalDependencies},
          "averageDependencies": ${analysis.averageDependencies},
          "maximumDependencyDepth": ${analysis.maximumDependencyDepth},
          "cycles": ${nestedArrays(analysis.cycles)}
        }
    """.trimIndent()

    fun module(analysis: FocusModuleAnalysis): String = """
        {
          "module": ${string(analysis.module)},
          "directDependencies": ${arrays(analysis.directDependencies)},
          "transitiveDependencies": ${arrays(analysis.transitiveDependencies)},
          "directDependents": ${arrays(analysis.directDependents)},
          "affectedModules": ${arrays(analysis.allDependents)},
          "dependencyDepth": ${analysis.dependencyDepth},
          "projectCoverage": ${analysis.projectCoverage},
          "cycles": ${nestedArrays(analysis.cycles)}
        }
    """.trimIndent()

    fun libraries(analysis: LibraryInventoryAnalysis): String = """
        {
          "directDeclarations": ${analysis.directDeclarations},
          "versionConflicts": ${arrays(analysis.versionConflicts.map { it.identifier })},
          "libraries": [${analysis.entries.joinToString(",") { entry ->
              "{\"identifier\":${string(entry.identifier)},\"declaredVersions\":${arrays(entry.declaredVersions)},\"resolvedVersion\":${entry.resolvedVersion?.let(::string) ?: "null"},\"modules\":${arrays(entry.modules)}}"
          }}]
        }
    """.trimIndent()

    private fun dashboardLibraries(
        analysis: LibraryInventoryAnalysis,
        moduleLibraries: Map<String, List<String>>,
    ): String = """
        {
          "directDeclarations": ${analysis.directDeclarations},
          "versionConflicts": ${arrays(analysis.versionConflicts.map { it.identifier })},
          "libraries": [${analysis.entries.joinToString(",") { entry ->
              val modules = moduleLibraries
                  .filterValues { coordinates -> entry.identifier in coordinates.map(::libraryIdentifier) }
                  .keys
                  .sorted()
              "{\"identifier\":${string(entry.identifier)},\"declaredVersions\":${arrays(entry.declaredVersions)},\"resolvedVersion\":${entry.resolvedVersion?.let(::string) ?: "null"},\"modules\":${arrays(modules)}}"
          }}]
        }
    """.trimIndent()

    private fun libraryIdentifier(coordinate: String): String = coordinate.substringBeforeLast(":")

    private fun arrays(values: Iterable<String>): String = values.joinToString(prefix = "[", postfix = "]") { string(it) }
    private fun nestedArrays(values: Iterable<List<String>>): String = values.joinToString(prefix = "[", postfix = "]") { arrays(it) }
    private fun string(value: String): String = "\"${value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")}\""
}
