package com.dfcruz.modulens.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HtmlDashboardRendererTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `writes bundled dashboard assets and data`() {
        val outputDirectory = temporaryFolder.newFolder("dashboard")

        HtmlDashboardRenderer.write(outputDirectory, "{\"project\":{}}")

        assertTrue(outputDirectory.resolve("index.html").isFile)
        assertTrue(outputDirectory.resolve("assets/modulens.css").isFile)
        assertTrue(outputDirectory.resolve("assets/modulens.js").isFile)
        assertEquals(
            "window.MODULENS_DATA = {\"project\":{}};",
            outputDirectory.resolve("assets/modulens-data.js").readText(),
        )
    }
}
