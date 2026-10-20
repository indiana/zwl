package com.indiana.zwl.data.tiledownload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/**
 * "Anuluj" action of the download notification. Cancels the unique job, which
 * stops the worker and drops the partial file.
 */
class TileDownloadCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(TileDownloadContract.WORK_NAME)
    }
}
