package com.dfcruz.modulens.dashboard

import java.io.File

internal object HtmlDashboardRenderer {

    fun write(directory: File, dashboardData: String) {
        val assetsDirectory = directory.resolve("assets")
        directory.mkdirs()
        assetsDirectory.mkdirs()

        writeResource("dashboard/index.html", directory.resolve("index.html"))
        writeResource("dashboard/modulens.css", assetsDirectory.resolve("modulens.css"))
        writeResource("dashboard/modulens.js", assetsDirectory.resolve("modulens.js"))
        assetsDirectory.resolve("modulens-data.js")
            .writeText("window.MODULENS_DATA = $dashboardData;")
    }

    private fun writeResource(resourcePath: String, destination: File) {
        destination.writeText(readResource(resourcePath))
    }

    private fun readResource(resourcePath: String): String =
        checkNotNull(HtmlDashboardRenderer::class.java.classLoader.getResourceAsStream(resourcePath)) {
            "Missing bundled dashboard resource: $resourcePath"
        }.bufferedReader().use { it.readText() }
}
