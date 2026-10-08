package io.legado.app.help

import java.io.File

internal object CrashLogStore {
    fun write(directory: File, fileName: String, report: String) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create crash log directory" }
        val current = File(directory, fileName)
        current.writeText(report)
        // Keep the just-written report even if the device clock moved backwards.
        directory.listFiles()
            ?.filter { it.isFile && it != current && it.name.startsWith("crash-") && it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(9)
            ?.forEach { it.delete() }
    }
}
