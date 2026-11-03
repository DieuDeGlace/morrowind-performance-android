package ui.storage

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.preference.PreferenceFragment
import android.widget.Toast
import com.codekidlabs.storagechooser.Content
import com.codekidlabs.storagechooser.StorageChooser
import com.libopenmw.openmw.R
import constants.Constants
import file.SharedConfig
import permission.PermissionHelper
import java.io.File

object StorageUi {
    fun ensureAccess(activity: Activity): Boolean {
        if (PermissionHelper.hasSharedStorageAccess(activity)) return true
        AlertDialog.Builder(activity).setTitle(R.string.performance_storage_access)
            .setMessage(R.string.performance_storage_access_explanation)
            .setPositiveButton(R.string.performance_grant_access) { _, _ ->
                PermissionHelper.requestSharedStorageAccess(activity)
            }.setNegativeButton(android.R.string.cancel, null).show()
        return false
    }

    fun chooseDirectory(activity: Activity, selected: (File) -> Unit) {
        if (!ensureAccess(activity)) return
        val content = Content().apply {
            selectLabel = activity.getString(R.string.chooser_select)
            createLabel = activity.getString(R.string.chooser_create)
            newFolderLabel = activity.getString(R.string.chooser_new_folder)
            cancelLabel = activity.getString(R.string.chooser_cancel)
            overviewHeading = activity.getString(R.string.chooser_heading)
            internalStorageText = activity.getString(R.string.chooser_internal_storage)
            freeSpaceText = activity.getString(R.string.chooser_free_space)
            folderCreatedToastText = activity.getString(R.string.chooser_folder_created)
            folderErrorToastText = activity.getString(R.string.chooser_folder_error)
            textfieldHintText = activity.getString(R.string.chooser_folder_name)
            textfieldErrorText = activity.getString(R.string.chooser_empty_folder_name)
        }
        val chooser = StorageChooser.Builder().withActivity(activity)
            .withFragmentManager(activity.fragmentManager).withMemoryBar(true)
            .withContent(content).allowCustomPath(true)
            .setType(StorageChooser.DIRECTORY_CHOOSER).build()
        chooser.setOnSelectListener { selected(File(it)) }
        chooser.show()
    }

    fun bindPreferences(fragment: PreferenceFragment) {
        val activity = fragment.activity
        fragment.findPreference("performance_storage_access")?.setOnPreferenceClickListener {
            PermissionHelper.requestSharedStorageAccess(activity)
            true
        }
        fragment.findPreference("performance_config_folder")?.setOnPreferenceClickListener {
            if (ensureAccess(activity)) {
                AlertDialog.Builder(activity).setTitle(R.string.performance_config_folder)
                    .setMessage(activity.getString(R.string.performance_config_folder_explanation,
                        SharedConfig.recommended().path))
                    .setPositiveButton(R.string.performance_use_documents) { _, _ ->
                        activate(activity, SharedConfig.recommended()) { refresh(fragment) }
                    }
                    .setNeutralButton(R.string.performance_browse) { _, _ ->
                        chooseDirectory(activity) { folder -> activate(activity, folder) { refresh(fragment) } }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            true
        }
        refresh(fragment)
    }

    fun refresh(fragment: PreferenceFragment) {
        val activity = fragment.activity ?: return
        fragment.findPreference("performance_config_folder")?.summary = Constants.USER_CONFIG
        fragment.findPreference("performance_storage_access")?.summary = activity.getString(
            if (PermissionHelper.hasSharedStorageAccess(activity)) R.string.performance_storage_granted
            else R.string.performance_storage_access_summary
        )
    }

    private fun activate(activity: Activity, target: File, done: () -> Unit) {
        val progress = ProgressDialog.show(activity, "",
            activity.getString(R.string.performance_copying_config), true, false)
        Thread {
            try {
                SharedConfig.activate(activity, target)
                activity.runOnUiThread {
                    progress.dismiss()
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        done()
                        Toast.makeText(activity, activity.getString(R.string.performance_config_ready, target.path),
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: Exception) {
                activity.runOnUiThread { progress.dismiss(); showError(activity, error) }
            }
        }.start()
    }

    fun showError(activity: Activity, error: Exception) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity).setTitle(R.string.performance_storage_error)
            .setMessage(error.message ?: activity.getString(R.string.performance_storage_access_explanation))
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
