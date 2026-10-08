package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashLogStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun createsPrivateDirectoryAndPreservesFullReport() {
        val directory = File(temporaryFolder.root, "private/crash")
        val report = "IllegalStateException\n\tat BookshelfFragment1.renderGroupSelector\nCaused by: test"
        CrashLogStore.write(directory, "crash-new.log", report)
        assertEquals(report, File(directory, "crash-new.log").readText())
    }

    @Test fun boundsReportsWithoutDeletingUnrelatedFilesOrNewestReportAfterClockRollback() {
        val directory = temporaryFolder.newFolder()
        repeat(15) { index ->
            File(directory, "crash-$index.log").apply {
                writeText("old")
                setLastModified(System.currentTimeMillis() + 60_000 + index)
            }
        }
        File(directory, "notes.txt").writeText("keep")
        CrashLogStore.write(directory, "crash-current.log", "current")
        assertEquals(10, directory.listFiles()!!.count { it.extension == "log" })
        assertEquals("current", File(directory, "crash-current.log").readText())
        assertTrue(File(directory, "notes.txt").exists())
    }
}
