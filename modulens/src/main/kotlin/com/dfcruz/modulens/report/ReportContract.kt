package com.dfcruz.modulens.report

import com.dfcruz.modulens.analysis.FindingSeverity
import kotlinx.serialization.Serializable

data class ReportMetadata(
    val pluginId: String,
    val gradleVersion: String,
    val includedScopes: List<String>,
    val excludedModules: List<String>,
    val externalLibraryResolutionEnabled: Boolean,
    val options: ReportOptions = ReportOptions(),
)

data class ReportOptions(
    val minimumFindingSeverity: FindingSeverity = FindingSeverity.WARNING,
    val includeResolvedLibraries: Boolean = false,
    val includeTransitiveModuleRelationships: Boolean = false,
    val includeLibraryUsageModules: Boolean = false,
    val ignoredLibraryPatterns: Set<String> = emptySet(),
)

internal fun ReportOptions.includesLibrary(coordinate: String): Boolean =
    ignoredLibraryPatterns.none { pattern -> pattern.matchesLibrary(coordinate) }

private fun String.matchesLibrary(coordinate: String): Boolean {
    val expression = split("*")
        .joinToString(".*") { Regex.escape(it) }
    return Regex("^$expression$").matches(coordinate) ||
        Regex("^$expression$").matches(coordinate.substringBeforeLast(":"))
}

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
