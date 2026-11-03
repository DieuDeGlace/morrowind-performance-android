package mods

import java.io.File
import java.io.IOException

/** Filesystem-only rules shared by directory registration and config output. */
object ModDirectoryLayout {
    fun dataDirectory(folder: File): File {
        val child = folder.listFiles()?.firstOrNull {
            it.isDirectory && it.name.equals("Data Files", ignoreCase = true)
        }
        return (child ?: folder).canonicalFile
    }

    fun registeredPaths(base: File, folders: Collection<String>): ArrayList<String> {
        val basePath = base.canonicalPath
        return folders.map { dataDirectory(File(it)).path }
            .filter { it != basePath }.distinct().toCollection(arrayListOf())
    }

    fun validate(folder: File, base: File): File {
        if (!folder.isAbsolute || folder.path.any { it == '\n' || it == '\r' || it == '"' })
            throw IOException("Choose a local folder with a valid absolute path.")
        val result = dataDirectory(folder)
        if (result == base.canonicalFile)
            throw IOException("The base Data Files folder is already included. Choose a separate mod folder.")
        if (!result.isDirectory || result.list() == null || !result.canRead())
            throw IOException("Cannot read mod folder: ${result.path}. Check shared storage access and that the storage is connected.")
        return result
    }

    fun activePaths(base: File, ordered: List<Pair<String, Boolean>>): ArrayList<String> {
        val result = arrayListOf(base.canonicalPath)
        ordered.filter { it.second }.forEach { (path, _) ->
            val folder = File(path)
            if (!folder.isDirectory || folder.list() == null || !folder.canRead())
                throw IOException("Enabled mod folder is unavailable: $path. Reconnect its storage or disable it in Mods > Directories.")
            val canonical = folder.canonicalPath
            if (canonical !in result) result += canonical
        }
        return result
    }
}
