package io.legado.app.help.storage

import io.legado.app.lib.cloud.CloudStorageFile
import java.time.LocalDate

/** Only dated backups owned by this exact device may be pruned. Legacy/shared files stay. */
internal object WebDavBackupRetention {
    fun deletionCandidates(files: List<CloudStorageFile>, device: String?, keep: Int, uploaded: String): List<String> {
        if (keep <= 0 || device.isNullOrBlank()) return emptyList()
        val pattern = Regex("^backup(\\d{4}-\\d{2}-\\d{2})-${Regex.escape(device)}\\.zip$")
        fun owned(name: String): Boolean {
            val date = pattern.matchEntire(name)?.groupValues?.get(1) ?: return false
            return runCatching { LocalDate.parse(date) }.isSuccess
        }
        if (!owned(uploaded)) return emptyList()
        val owned = files.filter { !it.isDir && it.size > 0 && owned(it.displayName) }
            .distinctBy { it.displayName }
        // A truncated or eventually consistent PROPFIND must never trigger deletion.
        if (owned.none { it.displayName == uploaded }) return emptyList()
        return owned.sortedWith(compareByDescending<CloudStorageFile> { it.displayName == uploaded }
            .thenByDescending { it.displayName }).drop(keep).map { it.displayName }
    }
}
