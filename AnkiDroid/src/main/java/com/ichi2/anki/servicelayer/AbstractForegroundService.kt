package com.ichi2.anki.servicelayer

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import com.ichi2.anki.Channel

abstract class AbstractForegroundService : LifecycleService() {
    abstract val channel: Channel
    abstract val notificationId: Int
    abstract val notificationBuilder: NotificationCompat.Builder

    protected val channelName
        get() = channel.getName(resources)

    protected val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(this)
    }

    protected fun sendNotification() {
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    protected fun notifyProgress(progress: Int, max: Int, indeterminate: Boolean = false) {
        notificationBuilder.setProgress(max, progress, indeterminate)
        sendNotification()
    }
}
