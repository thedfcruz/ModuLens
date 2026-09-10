package com.dfcruz.modulens

import kotlinx.serialization.Serializable

@Serializable
data class ReportMetadata(
    val pluginId: String,
    val gradleVersion: String,
    val includedScopes: List<String>,
    val excludedModules: List<String>,
    val externalLibraryResolutionEnabled: Boolean,
)

@Serializable
data class DirectModuleDependencyDeclaration(
    val from: String,
    val to: String,
    val configuration: String,
    val scope: String,
)

@Serializable
data class DirectLibraryDependencyDeclaration(
    val module: String,
    val identifier: String,
    val declaredVersion: String,
    val configuration: String,
    val scope: String,
)

internal object ReportContractCodec {
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
        require(values.size == 4) { "Invalid module dependency report record." }
        return DirectModuleDependencyDeclaration(values[0], values[1], values[2], values[3])
    }

    fun libraryDependency(record: String): DirectLibraryDependencyDeclaration {
        val values = record.split(separator)
        require(values.size == 5) { "Invalid library dependency report record." }
        return DirectLibraryDependencyDeclaration(values[0], values[1], values[2], values[3], values[4])
    }
}
