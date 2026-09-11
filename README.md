# ModuLens

ModuLens is a Gradle plugin for finding actionable module and dependency-structure issues in Android and Kotlin Multiplatform projects.

It is deliberately focused on dependency health: it explains structural problems, shows the dependency path that caused them, and can enforce selected rules in CI. It is not a general build-performance profiler.

## What it analyses

- Project module graph: direct and transitive dependencies, dependents, dependency depth, cycles, and leaf/root/isolated modules.
- Module impact: the modules affected when a selected module changes.
- External libraries: direct declarations, resolved versions, and version-alignment conflicts.
- Dependency findings: cycles, redundant direct module dependencies, and version conflicts.
- Dependency scopes: production, debug, release, unit-test, and Android-test dependencies. Kotlin Multiplatform `commonMain` dependencies are treated as production dependencies; test source sets are treated as test dependencies.

## Add the plugin

### Maven Central

The first Maven Central release is configured as version `1.0.0` with the plugin ID `com.dfcruz.modulens`. Once it is published, apply it in the root `build.gradle.kts`:

```kotlin
plugins {
    id("com.dfcruz.modulens") version "1.0.0"
}
```

### Development from this repository

To use the plugin source directly while working with a checkout next to your project, include its build in `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../ModuLens/modulens")
}
```

Then apply it without a version:

```kotlin
plugins {
    id("com.dfcruz.modulens")
}
```

Apply it only to the root project. The plugin discovers all included modules after Gradle has evaluated the build.

## Tasks

All tasks appear under the `ModuLens` group in `./gradlew tasks`.

| Task | Purpose |
| --- | --- |
| `moduLensAnalyze` | Summarises the complete module graph, including depth, hotspots, classifications, and cycles. Generates the dashboard too when `exportHtml` is enabled. |
| `moduLensModule --module=:path` | Shows dependencies, dependents, impact, libraries, scopes, cycles, and potential redundant declarations for one module. |
| `moduLensLibraries` | Lists external library declarations, resolved versions, and version-alignment conflicts. |
| `moduLensFindings` | Reports actionable dependency findings with evidence and suggested fixes. |
| `moduLensDashboard` | Generates only the interactive HTML dashboard for module, finding, impact, and library analysis. |
| `moduLensReport` | Exports the complete graph, module analysis, library inventory, and findings as one JSON report for automation or AI review. |
| `moduLensVerify` | Applies configured policies and fails the build for violations; intended for CI. |

### Project-wide tasks

```bash
./gradlew moduLensAnalyze
./gradlew moduLensLibraries
./gradlew moduLensFindings
./gradlew moduLensDashboard
./gradlew moduLensReport
./gradlew moduLensVerify
```

### Analyse one module

`moduLensModule` requires the exact Gradle project path through `--module`. The path always begins with `:`.

```bash
./gradlew moduLensModule --module=:core:database
./gradlew moduLensModule --module=:feature:dashboard
./gradlew moduLensModule --module=:shared
```

Use `./gradlew projects` to list the available paths. `--module` is currently supported only by `moduLensModule`; the other analysis tasks intentionally inspect the complete selected project graph.

## Findings

`moduLensFindings` currently reports:

- Dependency cycles.
- Redundant direct module dependencies. The report shows an alternative path that already provides the dependency.
- External-library version conflicts across modules.

For example:

```text
WARNING: redundant module dependency
Subject: :app → :core:domain
:app directly depends on :core:domain, which is already reachable through :feature.
Path: :app → :feature → :core:domain
Suggested fix: Remove the direct dependency unless it intentionally documents or exposes a required relationship.
```

Local analysis marks cycles as errors and the other current findings as warnings. CI decides which types should fail the build.

## Configuration

```kotlin
import com.dfcruz.modulens.DependencyScope
import com.dfcruz.modulens.analysis.FindingSeverity

moduLens {
    // Production is the default scope.
    scopes.set(
        setOf(
            DependencyScope.PRODUCTION,
            DependencyScope.UNIT_TEST,
            DependencyScope.ANDROID_TEST,
        ),
    )
    excludedModules.set(setOf(":sample:legacy"))

    analysis {
        // Resolves external dependency graphs. Disable for faster, declaration-only analysis.
        resolveExternalLibraries.set(true)
        // Writes the terminal output from local analysis tasks to text files.
        exportText.set(true)
        // Generates the HTML dashboard after local analysis tasks.
        exportHtml.set(true)
    }

    report {
        // The JSON report defaults to compact, direct-declaration data for AI review.
        minimumFindingSeverity.set(FindingSeverity.ERROR)
        includeResolvedLibraries.set(false)
        includeTransitiveModuleRelationships.set(false)
        includeLibraryUsageModules.set(false)
        // Glob patterns matched against group:name and group:name:version.
        ignoredLibraryPatterns.addAll("androidx.*", "com.android.tools.*")
    }

    verification {
        failOnCycles.set(true)
        failOnVersionConflicts.set(true)
        failOnRedundantDependencies.set(true)
        maxDependencyDepth.set(4)
        maxDirectDependencies.set(8)
    }
}
```

### Scope options

| Scope | Constant | Included by default |
| --- | --- | --- |
| Production | `DependencyScope.PRODUCTION` | Yes |
| Debug | `DependencyScope.DEBUG` | No |
| Release | `DependencyScope.RELEASE` | No |
| Unit test | `DependencyScope.UNIT_TEST` | No |
| Android test | `DependencyScope.ANDROID_TEST` | No |

`excludedModules` removes modules from the collected graph and all reports.

### Configure the graph analysed by every task

Use scopes and exclusions to focus all project-wide tasks and the selected module analysis on the part of the build that matters:

```kotlin
moduLens {
    scopes.set(setOf(DependencyScope.PRODUCTION, DependencyScope.DEBUG))
    excludedModules.set(
        setOf(
            ":legacy",
            ":benchmark",
        ),
    )
}
```

For example, `./gradlew moduLensModule --module=:feature:dashboard` then analyses `:feature:dashboard` against this filtered graph.

## Reports

Text and HTML export are opt-in. Enable either or both in `moduLens.analysis`.

Reports are generated under the root project's `build/reports/modulens` directory:

| Task | Text report |
| --- | --- |
| `moduLensAnalyze` | `project.txt` |
| `moduLensModule` | `modules/<module-path>.txt` |
| `moduLensLibraries` | `libraries.txt` |
| `moduLensFindings` | `findings.txt` |

### Interactive HTML dashboard

`moduLensAnalyze` provides the concise terminal summary; `moduLensDashboard` provides the visual explorer. The dashboard consumes the same complete JSON report generated by `moduLensReport`, so it does not run a separate analysis. When `exportHtml` is enabled, `moduLensAnalyze` and each focused local analysis task automatically generate the same dashboard after completing. Run `moduLensDashboard` directly when you only need to refresh the visual report:

```bash
./gradlew moduLensDashboard
```

Open `build/reports/modulens/html/index.html` in a browser. The dashboard works without a network connection and includes:

- A searchable module explorer with dependencies, dependents, impact, and resolved libraries.
- A searchable catalog of all resolved external libraries.
- Automatic library filtering when a module is selected.
- Module links on each library, so you can navigate from a library to every module that uses it.
- Filterable findings with dependency paths and suggested fixes.

### Full JSON report for automation and AI review

Run the aggregate report directly when a tool needs the complete project context in one file:

```bash
./gradlew moduLensReport
```

It writes `build/reports/modulens/report.json`. By default, this is a compact, AI-friendly report: project summary, direct module and library declarations, direct module relationships, and findings. Resolved library closures, transitive module relationships, and reverse library usage are opt-in through `report` configuration because they grow rapidly in large projects. Library glob patterns can remove known injected or irrelevant dependency families from the JSON report.

The report contract is documented in [the JSON Schema](docs/modulens-full-report.schema.json). `schemaVersion` follows a compatibility policy: additive fields remain in the current version; incompatible field changes require a new version. Findings include explicit module, library, and module-edge references so tools do not need to parse human-readable messages.

## CI verification

Use `moduLensVerify` in CI after configuring the verification policy. It checks the same underlying findings as local analysis, plus configured dependency limits.

```bash
./gradlew moduLensVerify
```

The defaults are conservative:

- Cycles fail the build.
- Version conflicts and redundant dependencies are reported locally but do not fail CI unless enabled.
- Depth and direct-dependency limits are disabled until configured.

When a rule fails, the output includes the rule, affected subject, dependency path where available, and a suggested fix.

## License

ModuLens is licensed under the [Apache License 2.0](LICENSE).
