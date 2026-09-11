package com.dfcruz.modulens.gradle

import com.dfcruz.modulens.DependencyScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectDependencyGraphTest {

    private val graph = ProjectDependencyGraph(
        mapOf(
            ":app" to CollectedModule(
                path = ":app",
                projectDependencies = setOf(
                    ProjectDependencyDeclaration(":feature", "implementation", DependencyScope.PRODUCTION),
                    ProjectDependencyDeclaration(":test-support", "testImplementation", DependencyScope.UNIT_TEST),
                ),
                declaredLibraryDependencies = setOf(
                    LibraryDependencyDeclaration("com.example", "network", "1.0", "implementation", DependencyScope.PRODUCTION),
                    LibraryDependencyDeclaration("com.example", "test-utils", "1.0", "testImplementation", DependencyScope.UNIT_TEST),
                ),
            ),
            ":feature" to CollectedModule(
                path = ":feature",
                projectDependencies = emptySet(),
                declaredLibraryDependencies = emptySet(),
            ),
            ":test-support" to CollectedModule(
                path = ":test-support",
                projectDependencies = emptySet(),
                declaredLibraryDependencies = emptySet(),
            ),
        ),
    )

    @Test
    fun `filters module and library views by scope`() {
        assertEquals(listOf(":feature"), graph.moduleGraph(setOf(DependencyScope.PRODUCTION)).directDependencies(":app"))
        assertEquals(
            listOf("com.example:network:1.0"),
            graph.declaredLibraries(setOf(DependencyScope.PRODUCTION)).getValue(":app"),
        )
    }

    @Test
    fun `exclusion removes nodes and edges pointing to them`() {
        val excluded = graph.excluding(setOf(":feature"))

        assertEquals(setOf(":app", ":test-support"), excluded.modulePaths)
        assertEquals(emptyList<String>(), excluded.moduleGraph(setOf(DependencyScope.PRODUCTION)).directDependencies(":app"))
    }

    @Test
    fun `produces report declarations with Gradle provenance`() {
        val declarations = graph.directModuleDeclarations(setOf(DependencyScope.PRODUCTION))

        assertEquals(1, declarations.size)
        assertEquals(":app", declarations.single().from)
        assertEquals("implementation", declarations.single().configuration)
    }
}
