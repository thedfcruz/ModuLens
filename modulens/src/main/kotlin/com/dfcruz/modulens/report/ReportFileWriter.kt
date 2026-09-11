package com.dfcruz.modulens.report

import java.io.File

internal object ReportFileWriter {
    fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
