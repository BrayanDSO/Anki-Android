/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.ichi2.anki.R
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.canManageExternalStorage
import com.ichi2.utils.hasPermissionBeenDenied

// TODO After #14129 (targetSdkVersion = 33) is done, separate 'Photos and videos' from
//  'Music and audio' permissions
/**
 * Permissions screen for requesting permissions in API 33+,
 * if the user [canManageExternalStorage], which isn't possible in the play store.
 *
 * Requested permissions:
 * 1. All files access: [Permissions.MANAGE_EXTERNAL_STORAGE].
 *   Used for saving the collection in a public directory
 *   which isn't deleted when the app is uninstalled
 * 2. Media access: [Permissions.legacyStorageAccessPermissions].
 *   Starting from API 33, there are new permissions for accessing media
 *   ([Permissions.tiramisuAudioPermission] and [Permissions.tiramisuPhotosAndVideosPermissions]),
 *   which are necessary to sync and check media in public directories.
 *   Since `targetSdkVersion` isn't >= 33 yet (#14129), the new permissions can't be used properly,
 *   and can be get by requesting [Manifest.permission.READ_EXTERNAL_STORAGE]
 *   (https://developer.android.com/about/versions/13/behavior-changes-13#granular-media-permissions).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TiramisuPermissionsFragment : PermissionsFragment() {
    private val accessAllFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private val mediaAccessLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.permissions_tiramisu, container, false)

        val allFilesPermission = view.findViewById<PermissionItem>(R.id.all_files_permission)
        allFilesPermission.permissions = listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        allFilesPermission.setButtonClickListener { _, permission ->
            if (!permission.isGranted) {
                accessAllFilesLauncher.showManageAllFilesScreen()
            }
        }

        val mediaPermission = view.findViewById<PermissionItem>(R.id.media_permission)
        // with targetSdkVersion < 33, the legacy storage access permissions work to get media access
        mediaPermission.permissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        mediaPermission.setButtonClickListener { _, permission ->
            if (permission.isGranted) return@setButtonClickListener

            if (!hasPermissionBeenDenied(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                mediaAccessLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                showToastAndOpenAppSettingsScreen(R.string.startup_photos_and_videos_permission)
            }
        }

        return view
    }
}
