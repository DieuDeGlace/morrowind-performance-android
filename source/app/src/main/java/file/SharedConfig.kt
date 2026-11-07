package file

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import constants.Constants
import java.io.File
import java.io.IOException

object SharedConfig {
    const val PREF_PATH = "performance_config_path"
    private const val PREF_SAVES_COPIED_TO = "shared_saves_copied_to_v1"

    fun recommended(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "OpenMW-Physics-Test/config"
    )

    fun selectedOrRecommended(context: Context): File {
        val saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_PATH, "").orEmpty()
        return if (saved.isNotEmpty()) File(saved) else recommended()
    }

    fun ensureForLaunch(context: Context) {
        activate(context, selectedOrRecommended(context))
    }
    @Synchronized
    fun activate(context: Context, selected: File) {
        val target = selected.canonicalFile
        val path = target.path
        if (!selected.isAbsolute || path == File.separator ||
            path.contains("/Android/data/") || path.endsWith("/Android/data") ||
            path.contains("/Android/obb/") || path.endsWith("/Android/obb"))
            throw IOException("Choose a settings folder outside Android/data, such as Documents/OpenMW-Physics-Test/config.")
        val game = GameInstaller.getDataFilesFile(context).canonicalFile
        if (target == game || path.startsWith(game.path + File.separator))
            throw IOException("Choose a settings folder outside the base game's Data Files.")
        if (!target.isDirectory && !target.mkdirs())
            throw IOException("Cannot create $path. Enable Shared storage access in the launcher.")
        val probe = try { File.createTempFile(".openmw-write-", ".tmp", target) }
        catch (error: IOException) {
            throw IOException("Cannot write to $path. Enable Shared storage access or choose another folder.", error)
        }
        probe.delete()
        ConfigDirectoryCopier().copyMissing(File(Constants.USER_CONFIG), target)
        val userConfig = File(target, "openmw.cfg")
        if (!userConfig.exists()) userConfig.writeText("# User OpenMW configuration. Add your overrides here.\n")
        if (!userConfig.isFile) throw IOException("The selected folder must contain an openmw.cfg file, not a directory with that name.")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val sharedUserData = SharedSaveStorage.defaultUserData(target)
        val customSavePath = parser.LaunchArguments.hasCustomUserData(
            preferences.getString("commandLine", "").orEmpty())
        if (sharedUserData != null && !customSavePath &&
            (preferences.getString(PREF_SAVES_COPIED_TO, "") != path || !File(target, "saves").isDirectory)) {
            SharedSaveStorage.copyExisting(
                listOf(File(Constants.USER_FILE_STORAGE, "saves"), File(Constants.USER_CONFIG, "saves"),
                    File(context.filesDir, "user/OpenMW/saves")),
                File(target, "saves")
            )
            if (!preferences.edit().putString(PREF_SAVES_COPIED_TO, path).commit())
                throw IOException("Could not record the completed save copy. Original saves are unchanged.")
        }
        if (preferences.getString(PREF_PATH, "") != path && !preferences.edit().putString(PREF_PATH, path).commit())
            throw IOException("Could not save the selected configuration folder.")
        Constants.USER_CONFIG = path
        Constants.USER_OPENMW_CFG = userConfig.path
    }
}
