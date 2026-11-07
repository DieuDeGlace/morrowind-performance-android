package file

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SharedSaveStorageTest {
    @get:Rule val temp = TemporaryFolder()

    private fun save(root: File, name: String, text: String): File =
        File(root, name).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun defaultsToSelectedConfigFolderWithoutEditingIt() {
        val config = temp.newFolder("Documents config")
        val file = File(config, "openmw.cfg")
        val content = "# user-data is chosen by the launcher\ncontent=Morrowind.esm\n"
        file.writeText(content)
        assertEquals(config.canonicalPath, SharedSaveStorage.defaultUserData(config))
        assertEquals(content, file.readText())
    }

    @Test fun explicitCustomPathIsRespected() {
        val config = temp.newFolder()
        val file = File(config, "openmw.cfg")
        file.writeText("user-data=\"/storage/My custom saves\"\n")
        assertNull(SharedSaveStorage.defaultUserData(config))
        assertEquals("user-data=\"/storage/My custom saves\"\n", file.readText())
    }

    @Test fun existingDotOverrideUsesTheSharedFolder() {
        val config = temp.newFolder()
        File(config, "openmw.cfg").writeText("  user-data = \".\"\n")
        assertEquals(config.canonicalPath, SharedSaveStorage.defaultUserData(config))
    }

    @Test fun commentsDoNotCountAsOverrides() {
        val config = temp.newFolder()
        File(config, "openmw.cfg").writeText("# user-data=\"private\"\n")
        assertEquals(config.canonicalPath, SharedSaveStorage.defaultUserData(config))
    }

    @Test fun copiesNestedCharacterSavesAndKeepsOriginals() {
        val source = temp.newFolder("app saves")
        val original = save(source, "Character/quicksave.omwsave", "saved character")
        val target = File(temp.root, "Documents/config/saves")
        assertEquals(1, SharedSaveStorage.copyExisting(listOf(source), target))
        assertEquals("saved character", original.readText())
        assertEquals("saved character", File(target, "Character/quicksave.omwsave").readText())
    }

    @Test fun conflictingSaveIsRetainedAsAnotherLoadableFile() {
        val source = temp.newFolder("source")
        val target = temp.newFolder("target")
        save(source, "Character/save.omwsave", "older save")
        val existing = save(target, "Character/save.omwsave", "newer save")
        assertEquals(1, SharedSaveStorage.copyExisting(listOf(source), target))
        assertEquals("newer save", existing.readText())
        val saves = File(target, "Character").listFiles()!!
        assertEquals(2, saves.size)
        assertTrue(saves.any { it.extension == "omwsave" && it.readText() == "older save" })
        assertEquals(0, SharedSaveStorage.copyExisting(listOf(source), target))
    }

    @Test fun retryAndDuplicateSourcesDoNotDuplicateSaves() {
        val source = temp.newFolder("source")
        save(source, "Character/save.omwsave", "save")
        val target = temp.newFolder("target")
        assertEquals(1, SharedSaveStorage.copyExisting(listOf(source, source), target))
        assertEquals(0, SharedSaveStorage.copyExisting(listOf(source), target))
        assertEquals(1, File(target, "Character").listFiles()!!.size)
    }

    @Test fun switchingSharedConfigFolderCopiesItsSaves() {
        val privateSaves = temp.newFolder("private saves")
        val sharedSaves = temp.newFolder("previous shared saves")
        save(privateSaves, "Old/old.omwsave", "old")
        save(sharedSaves, "Current/current.omwsave", "current")
        val target = temp.newFolder("new shared saves")
        assertEquals(2, SharedSaveStorage.copyExisting(listOf(privateSaves, sharedSaves), target))
        assertTrue(File(privateSaves, "Old/old.omwsave").isFile)
        assertTrue(File(sharedSaves, "Current/current.omwsave").isFile)
        assertEquals("current", File(target, "Current/current.omwsave").readText())
    }

    @Test fun failedDestinationKeepsAllOriginalSaves() {
        val source = temp.newFolder("source")
        val original = save(source, "Character/save.omwsave", "keep me")
        val target = temp.newFile("blocked")
        try {
            SharedSaveStorage.copyExisting(listOf(source), target)
            fail("Expected a blocked destination")
        } catch (_: IOException) {
            assertEquals("keep me", original.readText())
        }
    }

    @Test(expected = IOException::class) fun overlappingSaveFoldersAreRejected() {
        val source = temp.newFolder("source")
        save(source, "Character/save.omwsave", "keep")
        SharedSaveStorage.copyExisting(listOf(source), File(source, "nested"))
    }

    @Test fun sameFolderIsLeftUntouched() {
        val source = temp.newFolder("source")
        val original = save(source, "Character/save.omwsave", "keep")
        assertEquals(0, SharedSaveStorage.copyExisting(listOf(source), source))
        assertEquals("keep", original.readText())
    }

    @Test fun emptyOldStorageStillCreatesTheSharedSavesDirectory() {
        val target = File(temp.root, "new shared saves")
        assertEquals(0, SharedSaveStorage.copyExisting(listOf(File(temp.root, "missing")), target))
        assertTrue(target.isDirectory)
    }

    @Test fun linksCannotRedirectAnImportedSaveOutsideTheSharedFolder() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        val source = temp.newFolder("source")
        save(source, "Character/save.omwsave", "source")
        val target = temp.newFolder("target")
        val outside = temp.newFolder("outside")
        java.nio.file.Files.createSymbolicLink(File(target, "Character").toPath(), outside.toPath())
        try {
            SharedSaveStorage.copyExisting(listOf(source), target)
            fail("Expected a link to be rejected")
        } catch (_: IOException) {
            assertTrue(outside.listFiles()!!.isEmpty())
            assertEquals("source", File(source, "Character/save.omwsave").readText())
        }
    }

    @Test fun sourceLinksAreNotFollowed() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        val source = temp.newFolder("source")
        val outside = temp.newFolder("outside")
        save(outside, "private.omwsave", "private")
        java.nio.file.Files.createSymbolicLink(File(source, "linked").toPath(), outside.toPath())
        val target = temp.newFolder("target")
        try {
            SharedSaveStorage.copyExisting(listOf(source), target)
            fail("Expected a source link to be rejected")
        } catch (_: IOException) {
            assertTrue(target.listFiles()!!.isEmpty())
            assertEquals("private", File(outside, "private.omwsave").readText())
        }
    }
}