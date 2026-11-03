package mods

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ModDirectoryLayoutTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun sameNamedFoldersOnDifferentPathsStaySeparate() {
        val base = temp.newFolder("Morrowind", "Data Files")
        val first = temp.newFolder("mods one", "Textures")
        val second = temp.newFolder("mods two", "Textures")
        assertEquals(listOf(first.canonicalPath, second.canonicalPath),
            ModDirectoryLayout.registeredPaths(base, listOf(first.path, second.path)))
    }

    @Test fun packageFolderWithDataFilesSelectsTheDataRoot() {
        val root = temp.newFolder("mod")
        val data = File(root, "data files").apply { mkdirs() }
        assertEquals(data.canonicalFile, ModDirectoryLayout.dataDirectory(root))
    }

    @Test fun baseAndDuplicatePathsAreNotAddedTwice() {
        val base = temp.newFolder("Data Files")
        val mod = temp.newFolder("mod")
        assertEquals(listOf(mod.canonicalPath), ModDirectoryLayout.registeredPaths(base,
            listOf(base.path, mod.path, File(mod, "../mod").path)))
    }

    @Test fun activePathsKeepBaseFirstAndRespectOverrideOrder() {
        val base = temp.newFolder("Data Files")
        val first = temp.newFolder("first")
        val second = temp.newFolder("second")
        val disabled = File(temp.root, "disconnected")
        assertEquals(listOf(base.canonicalPath, second.canonicalPath, first.canonicalPath),
            ModDirectoryLayout.activePaths(base, listOf(second.path to true,
                disabled.path to false, first.path to true, second.path to true)))
    }

    @Test(expected = IOException::class) fun missingEnabledFolderBlocksAnUnmoddedLaunch() {
        ModDirectoryLayout.activePaths(temp.newFolder("base"), listOf(File(temp.root, "missing").path to true))
    }

    @Test fun missingDisabledFolderDoesNotBlockLaunch() {
        val base = temp.newFolder("base")
        assertEquals(listOf(base.canonicalPath),
            ModDirectoryLayout.activePaths(base, listOf(File(temp.root, "missing").path to false)))
    }

    @Test(expected = IOException::class) fun baseCannotBeRegisteredAsAMod() {
        val base = temp.newFolder("base")
        ModDirectoryLayout.validate(base, base)
    }

    @Test fun textureOnlyModCanBeRegisteredWithoutAPlugin() {
        val mod = temp.newFolder("独立した mod")
        File(mod, "textures").mkdirs()
        assertEquals(mod.canonicalFile, ModDirectoryLayout.validate(mod, temp.newFolder("base")))
    }

    @Test(expected = IOException::class) fun relativeFolderIsRejected() {
        ModDirectoryLayout.validate(File("relative"), temp.newFolder("base"))
    }
}
