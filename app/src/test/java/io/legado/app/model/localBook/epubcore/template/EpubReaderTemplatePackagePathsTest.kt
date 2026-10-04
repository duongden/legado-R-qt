package io.legado.app.model.localBook.epubcore.template

import io.legado.app.help.reader.ReaderAssetFixtures
import io.legado.app.help.reader.ReaderAssetStore
import io.legado.app.help.reader.ReaderTemplateAssetStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Android cache paths can traverse aliases such as /data/user/0 -> /data/data. */
class EpubReaderTemplatePackagePathsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun singleExportRoundTripsThroughAliasedCacheDirectory() {
        val template = exampleTemplate().copy(css = "/* 作者代码 */\r\n.页 { color: #123; }  ")
        val output = ByteArrayOutputStream()
        EpubReaderTemplatePackageArchive.writeTemplate(template, output)
        withAliasedCache { cache ->
            val restored = EpubReaderTemplatePackageArchive.read(output.toByteArray().inputStream(), cache)
            assertEquals(EpubReaderTemplateLibrary(listOf(template)), restored)
        }
    }

    @Test
    fun backupWithImagesAndFontsRoundTripsThroughAliasedCacheDirectory() {
        val assets = ReaderAssetStore(temporary.newFolder())
        val image = assets.import(ReaderAssetFixtures.png().inputStream(), "背景")
        val font = assets.import(ReaderAssetFixtures.sfnt().inputStream(), "字体")
        val template = exampleTemplate().copy(css = ReaderTemplateAssetStyle.font(
            ReaderTemplateAssetStyle.background("/* author */", image.id), font.id
        ))
        val library = EpubReaderTemplateLibrary(
            listOf(template, exampleTemplate("builtin.hidden")), setOf("builtin.hidden")
        )
        val output = ByteArrayOutputStream()
        EpubReaderTemplatePackageArchive.writeLibrary(library, output, assets)
        val restoredAssets = ReaderAssetStore(temporary.newFolder())
        withAliasedCache { cache ->
            assertEquals(library, EpubReaderTemplatePackageArchive.read(
                output.toByteArray().inputStream(), cache, restoredAssets
            ))
        }
        assertEquals(setOf(image.id, font.id), restoredAssets.library().assets.map { it.id }.toSet())
        assertArrayEquals(ReaderAssetFixtures.png(), restoredAssets.verifiedBytes(image.id))
        assertArrayEquals(ReaderAssetFixtures.sfnt(), restoredAssets.verifiedBytes(font.id))
    }

    @Test
    fun nestedManifestAndAssetsUseArchivePathsThroughAliasedCacheDirectory() {
        val assets = ReaderAssetStore(temporary.newFolder())
        val image = assets.import(ReaderAssetFixtures.png().inputStream(), "背景")
        val template = exampleTemplate().copy(css = ReaderTemplateAssetStyle.background("", image.id))
        val exported = ByteArrayOutputStream()
        EpubReaderTemplatePackageArchive.writeTemplate(template, exported, assets)
        val nested = ByteArrayOutputStream()
        ZipOutputStream(nested).use { output ->
            ZipInputStream(exported.toByteArray().inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry("theme/" + entry.name))
                    input.copyTo(output)
                    output.closeEntry()
                    input.closeEntry()
                }
            }
        }
        val restoredAssets = ReaderAssetStore(temporary.newFolder())
        withAliasedCache { cache ->
            assertEquals(listOf(template), EpubReaderTemplatePackageArchive.read(
                nested.toByteArray().inputStream(), cache, restoredAssets
            ).templates)
        }
        assertArrayEquals(ReaderAssetFixtures.png(), restoredAssets.verifiedBytes(image.id))
    }

    @Test
    fun corruptedExportStillFailsChecksumThroughAliasedCacheDirectory() {
        val marker = "zip-path-checksum-A".toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        EpubReaderTemplatePackageArchive.writeTemplate(exampleTemplate().copy(css = marker.toString(Charsets.UTF_8)), output)
        val bytes = output.toByteArray()
        val start = bytes.indices.first { index ->
            index + marker.size <= bytes.size && marker.indices.all { bytes[index + it] == marker[it] }
        }
        bytes[start + marker.lastIndex] = 'B'.code.toByte()
        withAliasedCache { cache ->
            assertThrows(IOException::class.java) {
                EpubReaderTemplatePackageArchive.read(bytes.inputStream(), cache)
            }
        }
    }

    @Test
    fun relativeCacheDirectorySupportsSingleAndBackupArchives() {
        val cache = Files.createTempDirectory(File(".").toPath(), "template-relative-cache-").toFile()
        try {
            assertFalse(cache.isAbsolute)
            val template = exampleTemplate()
            val library = EpubReaderTemplateLibrary(listOf(template))
            val single = ByteArrayOutputStream()
            val backup = ByteArrayOutputStream()
            EpubReaderTemplatePackageArchive.writeTemplate(template, single)
            EpubReaderTemplatePackageArchive.writeLibrary(library, backup)
            listOf(single, backup).forEach { output ->
                assertEquals(library, EpubReaderTemplatePackageArchive.read(output.toByteArray().inputStream(), cache))
                assertTrue(cache.listFiles().orEmpty().isEmpty())
            }
        } finally {
            Files.delete(cache.toPath())
        }
    }

    private fun withAliasedCache(block: (File) -> Unit) {
        val physical = temporary.newFolder()
        val linkRoot = Files.createTempDirectory(File(".").toPath(), "template-cache-alias-").toFile()
        val alias = File(linkRoot, "cache-alias")
        try {
            try {
                Files.createSymbolicLink(alias.toPath(), physical.toPath())
            } catch (error: UnsupportedOperationException) {
                assumeNoException("The filesystem must support symbolic links", error)
            } catch (error: IOException) {
                assumeNoException("The test account must be able to create symbolic links", error)
            }
            // Unix/Android canonicalize absolute links. Windows java.io retains the link
            // name, so use a relative link there to exercise the same root mismatch.
            val absoluteAlias = alias.absoluteFile.normalize()
            val cache = if (absoluteAlias == alias.canonicalFile) alias else absoluteAlias
            assertNotEquals(cache, cache.canonicalFile)
            assertTrue(Files.isSameFile(cache.toPath(), physical.toPath()))
            if (cache.isAbsolute) assertEquals(physical.canonicalFile, cache.canonicalFile)
            println("Template cache alias: " + if (cache.isAbsolute) "absolute symbolic link" else "relative symbolic link")
            block(cache)
        } finally {
            // Remove the link itself before JUnit cleans the physical test directory.
            Files.deleteIfExists(alias.toPath())
            Files.delete(linkRoot.toPath())
            assertTrue("Import must clean its staging files", physical.listFiles().orEmpty().isEmpty())
        }
    }
}
