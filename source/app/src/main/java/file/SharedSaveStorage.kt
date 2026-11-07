package file

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Shared engine user data; APK resources and caches remain app-specific. */
object SharedSaveStorage {
    private val userDataEntry = Regex("^\\s*user-data\\s*=")

    /** An explicit custom setting always wins over the release default. */
    fun defaultUserData(configDirectory: File): String? {
        val config = File(configDirectory, "openmw.cfg")
        val entries = if (config.exists()) config.readLines().filter { userDataEntry.containsMatchIn(it) }
            else emptyList()
        if (entries.isEmpty()) return configDirectory.canonicalPath
        if (entries.size == 1 && entries.single().substringAfter('=').trim() in listOf(".", "\".\""))
            return configDirectory.canonicalPath
        return null
    }

    /** Copy only; original saves and existing destination files are never removed. */
    fun copyExisting(sources: List<File>, destination: File): Int {
        val target = destination.canonicalFile
        if (isLink(destination))
            throw IOException("A link blocks the shared saves folder: " + destination.path)
        if (!target.isDirectory && !target.mkdirs())
            throw IOException("Cannot create shared saves folder: " + target.path)
        val roots = sources.map { it.canonicalFile }.distinct()
        var copied = 0
        for (root in roots) {
            if (root == target || !root.exists()) continue
            if (!root.isDirectory)
                throw IOException("The old saves location is not a directory: " + root.path)
            if (target.path.startsWith(root.path + File.separator) ||
                root.path.startsWith(target.path + File.separator))
                throw IOException("Old and shared save folders must not overlap.")
            fun visit(directory: File) {
                val entries = directory.listFiles()
                    ?: throw IOException("Cannot read old saves folder: " + directory.path)
                for (input in entries) {
                    // Do not follow links out of a save tree or recurse through links.
                    if (isLink(input))
                        throw IOException("A link blocks safe save copying: " + input.path)
                    val output = File(target, input.relativeTo(root).path)
                    if (isLink(output))
                        throw IOException("A link blocks the destination save: " + output.path)
                    if (input.isDirectory) {
                        if (!output.isDirectory && !output.mkdirs())
                            throw IOException("Cannot create shared character folder: " + output.path)
                        visit(input)
                    } else if (input.isFile && copyFile(input, output)) {
                        copied++
                    } else if (!input.isFile) {
                        throw IOException("Cannot read old save: " + input.path)
                    }
                }
            }
            visit(root)
        }
        return copied
    }

    private fun copyFile(source: File, requested: File): Boolean {
        val sourceHash = digest(source)
        var destination = requested
        if (destination.exists()) {
            if (destination.isFile && digest(destination) == sourceHash) return false
            // The displayed save description is inside the save, so a new
            // filename retains the original description in the Load menu.
            val suffix = source.extension.let { if (it.isEmpty()) "" else "." + it }
            destination = File(requested.parentFile, "imported-" + sourceHash + suffix)
            if (destination.exists()) {
                if (destination.isFile && digest(destination) == sourceHash) return false
                throw IOException("A file blocks an imported save: " + destination.path)
            }
        }
        val parent = destination.parentFile!!
        if (!parent.isDirectory && !parent.mkdirs())
            throw IOException("Cannot create shared saves folder: " + parent.path)
        val temporary = File.createTempFile(".openmw-save-", ".tmp", parent)
        try {
            source.copyTo(temporary, overwrite = true)
            if (digest(temporary) != sourceHash)
                throw IOException("Save copy verification failed: " + source.path)
            temporary.setLastModified(source.lastModified())
            if (destination.exists())
                throw IOException("The shared save changed during copying. Please retry: " + destination.path)
            if (!temporary.renameTo(destination))
                throw IOException("Cannot install copied save: " + destination.path)
        } finally {
            temporary.delete()
        }
        return true
    }

    private fun isLink(file: File): Boolean {
        // Resolve parent aliases first (Android storage aliases and Windows
        // short temporary paths are legitimate), then inspect this entry.
        val parent = file.absoluteFile.parentFile?.canonicalFile ?: return false
        val entry = File(parent, file.name)
        return entry.canonicalFile != entry.absoluteFile.normalize()
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}