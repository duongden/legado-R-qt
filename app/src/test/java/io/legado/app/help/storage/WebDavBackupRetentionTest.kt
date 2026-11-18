package io.legado.app.help.storage

import io.legado.app.lib.cloud.CloudStorageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBackupRetentionTest {
    private fun file(name: String, dir: Boolean = false, size: Long = 20) = CloudStorageFile(name, name, size, isDir = dir)
    private val newest = "backup2026-11-18-Phone.zip"
    @Test fun retainsNOnlyForExactDeviceAndKeepsSharedFiles() {
        val files = listOf(newest, "backup2026-11-17-Phone.zip", "backup2026-11-16-Phone.zip",
            "backup2026-11-16-Other.zip", "backup.zip", "backup2026-11-16.zip",
            "backup2026-11-16-BigPhone.zip", "backup2026-99-16-Phone.zip").map { file(it) }
        assertEquals(listOf("backup2026-11-16-Phone.zip"), WebDavBackupRetention.deletionCandidates(files, "Phone", 2, newest))
        assertTrue(WebDavBackupRetention.deletionCandidates(files, "Phone", 0, newest).isEmpty())
        assertTrue(WebDavBackupRetention.deletionCandidates(files, "", 1, newest).isEmpty())
    }
    @Test fun incompleteListingDirectoriesAndEmptyFilesNeverDelete() {
        val old = file("backup2026-11-16-Phone.zip")
        assertTrue(WebDavBackupRetention.deletionCandidates(listOf(old), "Phone", 1, newest).isEmpty())
        assertTrue(WebDavBackupRetention.deletionCandidates(listOf(file(newest, dir = true), old), "Phone", 1, newest).isEmpty())
        assertTrue(WebDavBackupRetention.deletionCandidates(listOf(file(newest, size = 0), old), "Phone", 1, newest).isEmpty())
    }
    @Test fun deviceRegexCharactersAreLiteralAndUploadWinsClockSkew() {
        val fresh = "backup2026-11-15-A(1).zip"
        assertEquals(listOf("backup2026-11-18-A(1).zip"), WebDavBackupRetention.deletionCandidates(
            listOf(file(fresh), file("backup2026-11-18-A(1).zip"), file("backup2026-11-18-A1.zip")), "A(1)", 1, fresh))
    }
}
