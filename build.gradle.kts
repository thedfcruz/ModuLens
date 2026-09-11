import com.dfcruz.modulens.DependencyScope

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.kotlin.multiplatform) apply false
    alias(libs.plugins.modulens)
}

moduLens {
    scopes.set(DependencyScope.entries.toSet())

    analysis {
        resolveExternalLibraries.set(true)
        exportText.set(true)
        exportHtml.set(true)
    }

    // Keep the sample dashboard fully explorable. Real projects can keep these compact by default.
    report {
        includeResolvedLibraries.set(true)
        includeLibraryUsageModules.set(true)
    }

    verification {
        failOnCycles.set(true)
        failOnVersionConflicts.set(true)
        // The sample deliberately contains a redundant direct dependency for analysis demonstrations.
        failOnRedundantDependencies.set(false)
        maxDependencyDepth.set(3)
        maxDirectDependencies.set(5)
    }
}
