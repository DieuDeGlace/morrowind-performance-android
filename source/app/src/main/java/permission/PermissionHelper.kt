/*
    Copyright (C) 2016 sandstranger

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.libopenmw.openmw.BuildConfig

object PermissionHelper {
    const val STORAGE_PERMISSION_REQUEST = 23

    fun hasSharedStorageAccess(context: Context): Boolean =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun requestSharedStorageAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")))
            } catch (_: ActivityNotFoundException) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else getWriteExternalStoragePermission(activity)
    }

    /**
     * The Mobile flavor keeps the port's existing targetSdk 29 legacy-storage
     * behavior. The Automotive flavor targets API 35, where WRITE_EXTERNAL_STORAGE
     * can no longer grant broad filesystem access. Android/media/com.ast.openmw is
     * still directly usable by this app, so the chooser must not be blocked by an
     * obsolete permission check on AAOS.
     */
    fun canOpenLegacyStorageChooser(activity: Activity): Boolean {
        if (BuildConfig.IS_AUTOMOTIVE_BUILD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true

        return hasSharedStorageAccess(activity)
    }

    fun getWriteExternalStoragePermission(activity: Activity) {
        if (hasSharedStorageAccess(activity)) return
        if (BuildConfig.IS_AUTOMOTIVE_BUILD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST
        )
    }
}
