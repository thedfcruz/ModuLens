package com.dfcruz.modulens.report

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
