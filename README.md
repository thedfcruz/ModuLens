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
| `moduLensAnalyze` | Summarises the complete module graph, including depth, hotspots, classifications, and cycles. |
| `moduLensModule --module=:path` | Shows dependencies, dependents, impact, libraries, scopes, cycles, and potential redundant declarations for one module. |
| `moduLensLibraries` | Lists external library declarations, resolved versions, and version-alignment conflicts. |
| `moduLensFindings` | Reports actionable dependency findings with evidence and suggested fixes. |
| `moduLensDashboard` | Generates an interactive HTML dashboard for module, finding, impact, and library analysis. |
| `moduLensVerify` | Applies configured policies and fails the build for violations; intended for CI. |

### Project-wide tasks

```bash
./gradlew moduLensAnalyze
./gradlew moduLensLibraries
./gradlew moduLensFindings
./gradlew moduLensDashboard
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
        exportText.set(true)
        exportJson.set(true)
        exportHtml.set(true)
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

Report export is opt-in. Enable text, JSON, HTML, or any combination in `moduLens.analysis`.

Reports are generated under the root project's `build/reports/modulens` directory:

| Task | Text report | JSON report |
| --- | --- | --- |
| `moduLensAnalyze` | `project.txt` | `project.json` |
| `moduLensModule` | `modules/<module-path>.txt` | `modules/<module-path>.json` |
| `moduLensLibraries` | `libraries.txt` | `libraries.json` |
| `moduLensFindings` | `findings.txt` | `findings.json` |

### Interactive HTML dashboard

Enable `exportHtml` to generate the dashboard automatically after any local analysis task, or run it directly:

```bash
./gradlew moduLensDashboard
```

Open `build/reports/modulens/html/index.html` in a browser. The dashboard works without a network connection and includes a summary, filterable findings, module dependency and impact explorer, and library inventory.

The JSON files are intended for CI artifacts or later integration with pull-request reporting.

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
