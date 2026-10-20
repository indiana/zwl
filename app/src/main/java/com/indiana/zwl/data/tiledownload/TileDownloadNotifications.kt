package com.indiana.zwl.data.tiledownload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.indiana.zwl.MainActivity
import com.indiana.zwl.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ongoing notification for the tile download foreground service: progress,
 * a way back into the app and a cancel action.
 */
@Singleton
class TileDownloadNotifications @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun build(text: String, progress: Float): Notification {
        ensureChannel()
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Pobieranie mapy offline")
            .setContentText(text)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Anuluj", cancelIntent())
            .build()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pobieranie map offline",
                // Progress updates must never buzz the user's pocket.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Postęp pobierania obszaru mapy do użytku offline"
                setShowBadge(false)
            }
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CANCEL,
        Intent(context, TileDownloadCancelReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val CHANNEL_ID = "tile_download"
        const val REQUEST_OPEN = 1
        const val REQUEST_CANCEL = 2
    }
}
