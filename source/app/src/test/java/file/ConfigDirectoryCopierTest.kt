package file

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ConfigDirectoryCopierTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun copiesSettingsAndBindingsWhileKeepingOriginals() {
        val old = temp.newFolder("old config")
        File(old, "settings.cfg").writeText("[Camera]\nviewing distance = 32768\n")
        File(old, "input_v3.xml").writeText("bindings")
        val target = File(temp.root, "Documents/OpenMW-Performance/config")
        assertEquals(2, ConfigDirectoryCopier().copyMissing(old, target))
        assertEquals(File(old, "settings.cfg").readText(), File(target, "settings.cfg").readText())
        assertTrue(File(old, "input_v3.xml").exists())
        assertEquals("bindings", File(target, "input_v3.xml").readText())
    }

    @Test fun existingUserEditsWinAndRetryIsIdempotent() {
        val old = temp.newFolder("old")
        val target = temp.newFolder("new")
        File(old, "settings.cfg").writeText("old settings")
        File(target, "settings.cfg").writeText("user edits")
        File(old, "openmw.cfg").writeText("user config")
        val copier = ConfigDirectoryCopier()
        assertEquals(1, copier.copyMissing(old, target))
        assertEquals(0, copier.copyMissing(old, target))
        assertEquals("user edits", File(target, "settings.cfg").readText())
        assertEquals("old settings", File(old, "settings.cfg").readText())
    }

    @Test fun logFilesAreNotCopiedIntoEditableConfiguration() {
        val old = temp.newFolder("old")
        File(old, "openmw.log").writeText("log")
        File(old, "crash.dmp").writeText("dump")
        File(old, "settings.cfg").writeText("settings")
        val target = temp.newFolder("new")
        assertEquals(1, ConfigDirectoryCopier().copyMissing(old, target))
        assertFalse(File(target, "openmw.log").exists())
    }

    @Test(expected = IOException::class) fun overlappingFoldersAreRejected() {
        val old = temp.newFolder("old")
        ConfigDirectoryCopier().copyMissing(old, File(old, "nested"))
    }

    @Test fun failedDestinationDoesNotRemoveOriginal() {
        val old = temp.newFolder("old")
        File(old, "settings.cfg").writeText("keep")
        val target = temp.newFile("not a directory")
        try {
            ConfigDirectoryCopier().copyMissing(old, target)
            fail("Expected an error")
        } catch (_: IOException) {
            assertEquals("keep", File(old, "settings.cfg").readText())
        }
    }

    @Test fun sameFolderIsANoOp() {
        val old = temp.newFolder("old")
        File(old, "settings.cfg").writeText("keep")
        assertEquals(0, ConfigDirectoryCopier().copyMissing(old, old))
        assertEquals("keep", File(old, "settings.cfg").readText())
    }

    @Test fun unrelatedDocumentsAreNeverCopied() {
        val old = temp.newFolder("old")
        File(old, "personal.txt").writeText("private document")
        File(old, "global_storage.bin").writeText("Lua settings")
        val target = temp.newFolder("new")
        assertEquals(1, ConfigDirectoryCopier().copyMissing(old, target))
        assertFalse(File(target, "personal.txt").exists())
        assertEquals("Lua settings", File(target, "global_storage.bin").readText())
    }

    @Test fun resetKeepsSharedFolderAndUnrelatedFiles() {
        val folder = temp.newFolder("Documents")
        File(folder, "settings.cfg").writeText("settings")
        File(folder, "personal.txt").writeText("keep")
        File(folder, "mod folder").mkdirs()
        ConfigDirectoryCopier.resetKnownFiles(folder)
        assertTrue(folder.isDirectory)
        assertFalse(File(folder, "settings.cfg").exists())
        assertEquals("keep", File(folder, "personal.txt").readText())
        assertTrue(File(folder, "mod folder").isDirectory)
    }
}
