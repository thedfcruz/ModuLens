package com.dfcruz.modulens.analysis

data class ProjectGraphAnalysis(
    val totalModules: Int,
    val totalDependencies: Int,
    val averageDependencies: Double,
    val maximumDependencyDepth: Int,
    val leafModules: Int,
    val rootModules: Int,
    val isolatedModules: Int,
    val topDependencies: List<Pair<String, Int>>,
    val topDependents: List<Pair<String, Int>>,
    val moduleWithMostDependencies: Pair<String, Int>?,
    val moduleWithMostDependents: Pair<String, Int>?,
    val cycles: List<List<String>>,
)

data class FocusModuleAnalysis(
    val module: String,
    val directDependencies: List<String>,
    val transitiveDependencies: Set<String>,
    val allDependencies: Set<String>,
    val directDependents: List<String>,
    val allDependents: Set<String>,
    val dependencyDepth: Int,
    val dependentDepth: Int,
    val projectCoverage: Double,
    val cycles: List<List<String>>,
) {
    val isLeaf: Boolean
        get() = directDependencies.isEmpty()
    val isRoot: Boolean
        get() = directDependents.isEmpty()
    val isIsolated: Boolean
        get() = isLeaf && isRoot
}

class ModuleGraph(graph: Map<String, List<String>>) {

    private val adjacency =
        graph.mapValues { (_, dependencies) -> dependencies.distinct().sorted() }

    private val reverseAdjacency by lazy {
        val reversed = adjacency.keys.associateWith { mutableListOf<String>() }.toMutableMap()
        adjacency.forEach { (module, dependencies) ->
            dependencies.forEach { dependency ->
                reversed.getOrPut(dependency) { mutableListOf() }.add(module)
            }
        }
        reversed.mapValues { (_, dependents) -> dependents.distinct().sorted() }
    }

    val modules: Set<String> get() = adjacency.keys
    val dependenciesByModule: Map<String, List<String>> get() = adjacency

    fun directDependencies(module: String): List<String> = adjacency[module].orEmpty()

    fun directDependents(module: String): List<String> = reverseAdjacency[module].orEmpty()

    fun transitiveDependencies(module: String): Set<String> = transitive(adjacency, module)

    fun transitiveDependents(module: String): Set<String> = transitive(reverseAdjacency, module)

    fun redundantDirectDependencies(module: String): Map<String, List<String>> {
        val directDependencies = directDependencies(module)
        return directDependencies
            .mapNotNull { dependency ->
                val alternatives = directDependencies
                    .filterNot { it == dependency }
                    .filter { alternative -> dependency in transitiveDependencies(alternative) }
                dependency.takeIf { alternatives.isNotEmpty() }?.let { it to alternatives }
            }
            .toMap()
    }

    fun dependencyPath(from: String, to: String): List<String>? {
        if (from !in adjacency || to !in adjacency) return null
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf(from)
        queue.add(listOf(from))
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()
            if (current == to) return path
            adjacency[current].orEmpty().forEach { dependency ->
                if (visited.add(dependency)) queue.add(path + dependency)
            }
        }
        return null
    }

    fun cycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        adjacency.keys.sorted().forEach { start ->
            fun visit(module: String, path: List<String>, visited: Set<String>) {
                adjacency[module].orEmpty().forEach { dependency ->
                    when (dependency) {
                        start -> cycles.add(path + start)
                        !in visited if dependency >= start -> visit(
                            dependency,
                            path + dependency,
                            visited + dependency
                        )
                    }
                }
            }
            visit(start, listOf(start), setOf(start))
        }
        return cycles.distinct()
    }

    fun analyzeProject(): ProjectGraphAnalysis {
        val totalModules = adjacency.size
        val totalDependencies = adjacency.values.sumOf { it.size }
        val topDependencies = ranked(adjacency)
        val topDependents = ranked(reverseAdjacency)
        return ProjectGraphAnalysis(
            totalModules,
            totalDependencies,
            if (totalModules == 0) 0.0 else totalDependencies.toDouble() / totalModules,
            adjacency.keys.maxOfOrNull { dependencyDepth(it) } ?: 0,
            adjacency.count { it.value.isEmpty() },
            reverseAdjacency.count { it.value.isEmpty() },
            adjacency.keys.count {
                adjacency.getValue(it).isEmpty() && reverseAdjacency.getValue(it).isEmpty()
            },
            topDependencies.take(5),
            topDependents.take(5),
            topDependencies.firstOrNull(),
            topDependents.firstOrNull(), cycles(),
        )
    }

    fun analyzeModule(module: String): FocusModuleAnalysis {
        require(module in adjacency) { "Module '$module' was not found." }
        val directDependencies = directDependencies(module)
        val allDependencies = transitiveDependencies(module)
        val directDependents = directDependents(module)
        val allDependents = transitiveDependents(module)
        return FocusModuleAnalysis(
            module,
            directDependencies,
            allDependencies - directDependencies.toSet(),
            allDependencies,
            directDependents,
            allDependents,
            dependencyDepth(module),
            dependentDepth(module),
            if (adjacency.size <= 1) 0.0 else allDependents.size.toDouble() / (adjacency.size - 1) * 100,
            cycles().filter { module in it },
        )
    }

    private fun transitive(source: Map<String, List<String>>, start: String): Set<String> {
        val visited = mutableSetOf<String>()
        fun visit(module: String) {
            source[module].orEmpty().forEach { if (visited.add(it)) visit(it) }
        }
        visit(start)
        visited.remove(start)
        return visited
    }

    private fun ranked(source: Map<String, List<String>>): List<Pair<String, Int>> =
        source.map { (module, dependencies) -> module to dependencies.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    private fun dependencyDepth(module: String): Int = depth(adjacency, module)

    private fun dependentDepth(module: String): Int = depth(reverseAdjacency, module)

    private fun depth(source: Map<String, List<String>>, start: String): Int {
        fun visit(module: String, visited: Set<String>): Int {
            if (module in visited) return 0
            val neighbours = source[module].orEmpty()
            return if (neighbours.isEmpty()) 0 else 1 + neighbours.maxOf {
                visit(
                    it,
                    visited + module
                )
            }
        }
        return visit(start, emptySet())
    }
}
