package com.dfcruz.modulens.task

import com.dfcruz.modulens.dashboard.HtmlDashboardRenderer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DashboardTask : DefaultTask() {

    @get:InputFile
    abstract val reportFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        HtmlDashboardRenderer.write(
            directory = outputDirectory.get().asFile,
            dashboardData = reportFile.get().asFile.readText(),
        )
        logger.lifecycle("ModuLens HTML dashboard: ${outputDirectory.get().asFile.resolve("index.html")}")
    }
}
