package com.dfcruz.modulens

import java.io.File

internal object ReportFileWriter {
    fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
