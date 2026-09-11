package com.dfcruz.modulens

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import com.dfcruz.modulens.analysis.FindingSeverity
import javax.inject.Inject

abstract class ModuLensExtension @Inject constructor(objects: ObjectFactory) {
    val scopes: SetProperty<DependencyScope> = objects.setProperty(DependencyScope::class.java)
    val excludedModules: SetProperty<String> = objects.setProperty(String::class.java)
    val analysis: ModuLensAnalysisExtension = objects.newInstance(ModuLensAnalysisExtension::class.java)
    val report: ModuLensReportExtension = objects.newInstance(ModuLensReportExtension::class.java)
    val verification: ModuLensVerificationExtension = objects.newInstance(ModuLensVerificationExtension::class.java)

    init {
        scopes.convention(setOf(DependencyScope.PRODUCTION))
        excludedModules.convention(emptySet())
    }

    fun analysis(action: Action<in ModuLensAnalysisExtension>) = action.execute(analysis)

    fun report(action: Action<in ModuLensReportExtension>) = action.execute(report)

    fun verification(action: Action<in ModuLensVerificationExtension>) = action.execute(verification)
}

abstract class ModuLensReportExtension @Inject constructor(objects: ObjectFactory) {
    val minimumFindingSeverity: Property<FindingSeverity> =
        objects.property(FindingSeverity::class.java).convention(FindingSeverity.WARNING)
    val includeResolvedLibraries: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val includeTransitiveModuleRelationships: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
    val includeLibraryUsageModules: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val ignoredLibraryPatterns: SetProperty<String> = objects.setProperty(String::class.java).convention(emptySet())
}

abstract class ModuLensAnalysisExtension @Inject constructor(objects: ObjectFactory) {
    val resolveExternalLibraries: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val exportText: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val exportHtml: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class ModuLensVerificationExtension @Inject constructor(objects: ObjectFactory) {
    val failOnCycles: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val maxDependencyDepth: Property<Int> = objects.property(Int::class.java).convention(Int.MAX_VALUE)
    val maxDirectDependencies: Property<Int> = objects.property(Int::class.java).convention(Int.MAX_VALUE)
    val failOnVersionConflicts: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val failOnRedundantDependencies: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}
