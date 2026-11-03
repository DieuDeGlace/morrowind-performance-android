package file

import java.io.File
import java.io.IOException

/** Copies editable configuration without removing originals or replacing edits. */
class ConfigDirectoryCopier {
    companion object {
        val CONFIG_FILES = setOf("openmw.cfg", "settings.cfg", "input_v2.xml", "input_v3.xml",
            "gamecontrollerdb.txt", "shaders.yaml", "global_storage.bin", "player_storage.bin", "console_history.txt")

        /** A shared folder may contain unrelated documents: never delete the folder itself. */
        fun resetKnownFiles(folder: File) {
            CONFIG_FILES.map { File(folder, it) }.filter { it.isFile }.forEach { it.delete() }
        }
    }

    fun copyMissing(source: File, destination: File): Int {
        val from = source.canonicalFile
        val to = destination.canonicalFile
        if (from == to) return 0
        if (from.path.startsWith(to.path + File.separator) ||
            to.path.startsWith(from.path + File.separator))
            throw IOException("Choose a configuration folder separate from the current folder.")
        if (!to.isDirectory && !to.mkdirs())
            throw IOException("Could not create configuration folder: ${to.path}")
        if (!from.exists()) return 0
        if (!from.isDirectory || from.list() == null)
            throw IOException("Could not read existing configuration: ${from.path}")

        var copied = 0
        val entries = from.listFiles() ?: throw IOException("Could not read ${from.path}")
        entries.filter { it.isFile && it.name in CONFIG_FILES }.forEach { input ->
            if (!input.canonicalPath.startsWith(from.path + File.separator))
                throw IOException("Configuration contains a link outside its folder: ${input.path}")
            val output = File(to, input.relativeTo(from).path)
            if (output.exists()) {
                if (!output.isFile) throw IOException("A directory blocks configuration file: ${output.path}")
                return@forEach
            }
            val parent = output.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs())
                throw IOException("Could not create ${parent.path}")
            val temporary = File.createTempFile(".openmw-config-", ".tmp", parent)
            try {
                input.copyTo(temporary, overwrite = true)
                if (temporary.length() != input.length())
                    throw IOException("Incomplete copy of ${input.path}")
                temporary.setLastModified(input.lastModified())
                // A pre-existing destination always wins, including after an interrupted copy.
                if (!output.exists()) {
                    if (!temporary.renameTo(output))
                        throw IOException("Could not install configuration file: ${output.path}")
                    copied++
                }
            } finally {
                temporary.delete()
            }
        }
        return copied
    }
}
