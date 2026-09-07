package com.dfcruz.modulens

data class LibraryInventoryEntry(
    val identifier: String,
    val declaredVersions: List<String>,
    val modules: List<String>,
    val resolvedVersion: String?,
)

data class LibraryInventoryAnalysis(
    val entries: List<LibraryInventoryEntry>,
    val directDeclarations: Int,
) {
    val versionConflicts: List<LibraryInventoryEntry>
        get() = entries.filter { it.declaredVersions.size > 1 }
}

object LibraryInventoryAnalyzer {

    fun analyze(
        declarations: Map<String, List<String>>,
        resolvedLibraries: List<String>,
    ): LibraryInventoryAnalysis {
        val resolvedVersions = resolvedLibraries.associate { coordinate ->
            coordinate.substringBeforeLast(":") to coordinate.substringAfterLast(":")
        }
        val declaredOccurrences = declarations
            .flatMap { (module, coordinates) -> coordinates.map { it to module } }
            .groupBy({ it.first.substringBeforeLast(":") }, { it })
        val entries = (declaredOccurrences.keys + resolvedVersions.keys)
            .sorted()
            .map { identifier ->
                val occurrences = declaredOccurrences[identifier].orEmpty()
                LibraryInventoryEntry(
                    identifier = identifier,
                    declaredVersions = occurrences.map { it.first.substringAfterLast(":") }.distinct().sorted(),
                    modules = occurrences.map { it.second }.distinct().sorted(),
                    resolvedVersion = resolvedVersions[identifier],
                )
            }

        return LibraryInventoryAnalysis(entries, declarations.values.sumOf { it.size })
    }
}
