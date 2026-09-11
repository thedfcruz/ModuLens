package com.dfcruz.modulens.task

import com.dfcruz.modulens.report.DirectLibraryDependencyDeclaration
import com.dfcruz.modulens.report.DirectModuleDependencyDeclaration

internal object TaskInputCodec {
    private const val separator = "\u001F"

    fun moduleDependency(declaration: DirectModuleDependencyDeclaration): String = listOf(
        declaration.from,
        declaration.to,
        declaration.configuration,
        declaration.scope,
    ).joinToString(separator)

    fun libraryDependency(declaration: DirectLibraryDependencyDeclaration): String = listOf(
        declaration.module,
        declaration.identifier,
        declaration.declaredVersion,
        declaration.configuration,
        declaration.scope,
    ).joinToString(separator)

    fun moduleDependency(record: String): DirectModuleDependencyDeclaration {
        val values = record.split(separator)
        require(values.size == 4) { "Invalid module dependency task input." }
        return DirectModuleDependencyDeclaration(values[0], values[1], values[2], values[3])
    }

    fun libraryDependency(record: String): DirectLibraryDependencyDeclaration {
        val values = record.split(separator)
        require(values.size == 5) { "Invalid library dependency task input." }
        return DirectLibraryDependencyDeclaration(
            module = values[0],
            identifier = values[1],
            declaredVersion = values[2],
            configuration = values[3],
            scope = values[4]
        )
    }
}
