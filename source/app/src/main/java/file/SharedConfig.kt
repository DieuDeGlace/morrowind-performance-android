package file

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import constants.Constants
import permission.PermissionHelper
import java.io.File
import java.io.IOException

object SharedConfig {
    const val PREF_PATH = "performance_config_path"

    fun recommended(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "OpenMW-Performance/config"
    )

    fun ensureForLaunch(context: Context) {
        val saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_PATH, "").orEmpty()
        if (saved.isNotEmpty()) {
            activate(context, File(saved))
        } else if (PermissionHelper.hasSharedStorageAccess(context)) {
            activate(context, recommended())
        }
    }

    fun activate(context: Context, selected: File) {
        val target = selected.canonicalFile
        val path = target.path
        if (!selected.isAbsolute || path == File.separator ||
            path.contains("/Android/data/") || path.endsWith("/Android/data") ||
            path.contains("/Android/obb/") || path.endsWith("/Android/obb"))
            throw IOException("Choose a settings folder outside Android/data, such as Documents/OpenMW-Performance/config.")
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
        if (preferences.getString(PREF_PATH, "") != path && !preferences.edit().putString(PREF_PATH, path).commit())
            throw IOException("Could not save the selected configuration folder.")
        Constants.USER_CONFIG = path
        Constants.USER_OPENMW_CFG = userConfig.path
    }
}
