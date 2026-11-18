package io.legado.app.service

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsCacheFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun onlyTemporarySynthesisAudioIsDeleted() {
        val cache = temporary.newFolder("tts")
        val audio = cache.resolve("cue.mp3").apply { writeText("audio") }
        val download = temporary.newFile("download.mp3").apply { writeText("keep") }
        assertFalse(TtsCacheFiles.deleteFailedAudio(cache, download))
        assertTrue(download.exists())
        assertTrue(TtsCacheFiles.deleteFailedAudio(cache, audio))
        assertFalse(audio.exists())
    }

    @Test fun nestedFilesAndTraversalCannotDeleteDownloads() {
        val cache = temporary.newFolder("tts")
        val nested = cache.resolve("downloads").apply { mkdir() }.resolve("keep.mp3").apply { writeText("keep") }
        val outside = temporary.newFile("outside.mp3").apply { writeText("keep") }
        assertFalse(TtsCacheFiles.deleteFailedAudio(cache, nested))
        assertFalse(TtsCacheFiles.deleteFailedAudio(cache, cache.resolve("../outside.mp3")))
        assertTrue(nested.exists())
        assertTrue(outside.exists())
    }
}
