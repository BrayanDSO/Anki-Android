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
package com.ichi2.anki.servicelayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MigrationForegroundService : AbstractForegroundService() {
    override val channel: Channel = Channel.SCOPED_STORAGE_MIGRATION
    // TODO: centralize the existing notification IDs somewhere so they are unique. Not sure what happens if they aren't
    override val notificationId = 97 // random number I chose
    override val notificationBuilder by lazy {
        NotificationCompat.Builder(this, channel.id)
            .setContentTitle(channelName)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOnlyAlertOnce(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.i("Starting migration service")

        // API 31+ requires a PendingIntent mutability flag, and FLAG_IMMUTABLE is only for API ≥ 23
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val notificationIntent = Intent(this, DeckPicker::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flag)
        notificationBuilder.setContentIntent(pendingIntent)

        startForeground(notificationId, notificationBuilder.build())
        startMigration()

        return START_NOT_STICKY
    }

    private fun startMigration() {
        // TODO: using thread {} instead of launch {} works too. See which is better/faster/safer
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val prefs = AnkiDroidApp.getSharedPrefs(this@MigrationForegroundService)
                val migrateUserData = MigrateUserData.createInstance(prefs)!!

                // TODO: get total bytes and report progress accurately
                notifyProgress(0, 0, true)
                migrateUserData.migrateFiles {
                    // TODO
                }
                // TODO: find and call the method that finishes the migration and makes "migrationInProgress" false
                stopSelf()
            }
        }
    }
}
