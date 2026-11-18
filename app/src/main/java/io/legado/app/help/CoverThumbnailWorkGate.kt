package io.legado.app.help

/** Bounds queued owned thumbnails and coalesces repeated requests for the same cover. */
internal class CoverThumbnailWorkGate(private val limit: Int) {
    private val keys = hashSetOf<String>()
    @Synchronized fun acquire(key: String): Boolean {
        if (keys.size >= limit || key in keys) return false
        keys.add(key)
        return true
    }
    @Synchronized fun release(key: String) { keys.remove(key) }
}
