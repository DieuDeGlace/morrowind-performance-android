package mods

import android.content.Context
import android.preference.PreferenceManager
import file.GameInstaller
import java.io.File
import java.io.IOException

/** Registered mod folders stay separate from the base game and survive missing SD cards. */
object ModsPaths {
    private const val PREF_DIRECTORIES = "performance_mod_directories"
    private const val PREF_IMPORTED = "performance_mod_directories_imported"

    fun gameRoot(ctx: Context): File = GameInstaller.getDataFilesFile(ctx).parentFile ?: File("")
    fun directoryRoots(ctx: Context): ArrayList<String> = arrayListOf(gameRoot(ctx).absolutePath)

    fun extraDirectories(ctx: Context): ArrayList<String> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (!preferences.getBoolean(PREF_IMPORTED, false)) {
            val siblings = gameRoot(ctx).listFiles()
            if (siblings != null) {
                val imported = ModDirectoryLayout.registeredPaths(
                    GameInstaller.getDataFilesFile(ctx),
                    siblings.filter { it.isDirectory }.map { it.path }
                )
                if (!preferences.edit().putString(PREF_DIRECTORIES, imported.joinToString("\n"))
                        .putBoolean(PREF_IMPORTED, true).commit())
                    throw IOException("Could not save the mod folder list.")
            }
        }
        return preferences.getString(PREF_DIRECTORIES, "").orEmpty().lines()
            .filter { it.isNotBlank() }.distinct().toCollection(arrayListOf())
    }

    fun directoryCollection(ctx: Context, db: ModsDatabaseOpenHelper = ModsDatabaseOpenHelper.getInstance(ctx)) =
        ModsCollection(ModType.Dir, directoryRoots(ctx), db, extraDirectories(ctx))

    fun allDataDirectories(ctx: Context): ArrayList<String> =
        (listOf(GameInstaller.getDataFilesFile(ctx).canonicalPath) + extraDirectories(ctx))
            .distinct().toCollection(arrayListOf())

    fun activeDataDirectories(ctx: Context, db: ModsDatabaseOpenHelper = ModsDatabaseOpenHelper.getInstance(ctx)):
        ArrayList<String> = ModDirectoryLayout.activePaths(
            GameInstaller.getDataFilesFile(ctx),
            directoryCollection(ctx, db).mods.map { it.filename to it.enabled }
        )

    fun addDirectory(ctx: Context, selected: File) {
        val path = ModDirectoryLayout.validate(selected, GameInstaller.getDataFilesFile(ctx)).path
        val paths = extraDirectories(ctx)
        if (path !in paths) paths += path
        if (!PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(PREF_DIRECTORIES, paths.joinToString("\n")).commit())
            throw IOException("Could not save the mod folder list.")
        val collection = directoryCollection(ctx)
        collection.mods.first { it.filename == path }.apply { enabled = true; dirty = true }
        collection.update()
    }

    /** Only unregisters a path; no mod files are changed or deleted. */
    fun removeDirectory(ctx: Context, path: String) {
        val paths = extraDirectories(ctx).filterNot { it == path }
        if (!PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(PREF_DIRECTORIES, paths.joinToString("\n")).commit())
            throw IOException("Could not save the mod folder list.")
        ModsDatabaseOpenHelper.getInstance(ctx).writableDatabase.delete(
            "mod", "type = ? AND filename = ?", arrayOf(ModType.Dir.v.toString(), path)
        )
    }

    fun fileExistsIn(filename: String, dataDirectories: Collection<String>): Boolean =
        dataDirectories.any { File(it, filename).isFile }
}
