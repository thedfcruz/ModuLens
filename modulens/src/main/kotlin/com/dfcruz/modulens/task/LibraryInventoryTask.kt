package com.dfcruz.modulens.task

import com.dfcruz.modulens.analysis.LibraryInventoryAnalyzer
import com.dfcruz.modulens.analysis.LibraryInventoryAnalysis
import com.dfcruz.modulens.report.ReportFileWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class LibraryInventoryTask : DefaultTask() {

    @get:Input
    abstract val libraryDeclarations: MapProperty<String, List<String>>

    @get:Input
    abstract val resolvedLibraries: ListProperty<String>

    @get:Optional
    @get:OutputFile
    abstract val textReport: RegularFileProperty

    @TaskAction
    fun action() {
        val analysis = LibraryInventoryAnalyzer.analyze(libraryDeclarations.get(), resolvedLibraries.get())
        val output = render(analysis)
        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
    }

    private fun render(analysis: LibraryInventoryAnalysis): String = buildString {
        appendLine()
        appendLine("External Library Inventory")
        appendLine("────────────────────────────────────────")
        appendLine("Libraries                    ${analysis.entries.size}")
        appendLine("Direct declarations          ${analysis.directDeclarations}")
        appendLine("Version alignment conflicts  ${analysis.versionConflicts.size}")
        appendLine()
        appendLine("Libraries")
        appendLine("────────────────────────────────────────")
        if (analysis.entries.isEmpty()) appendLine("None")
        else analysis.entries.forEach { entry ->
            appendLine(entry.identifier)
            appendLine("  Declared versions: ${entry.declaredVersions.joinToString()}")
            appendLine("  Resolved version:  ${entry.resolvedVersion ?: "not resolved"}")
            appendLine("  Modules: ${entry.modules.joinToString()}")
        }
        appendLine()
        appendLine("Version Alignment Conflicts")
        appendLine("────────────────────────────────────────")
        if (analysis.versionConflicts.isEmpty()) appendLine("None")
        else analysis.versionConflicts.forEach { entry ->
            appendLine(entry.identifier)
            appendLine("  Declared versions: ${entry.declaredVersions.joinToString()}")
            appendLine("  Modules: ${entry.modules.joinToString()}")
            entry.resolvedVersion?.let { appendLine("  Resolved $it") }
        }
    }
}
