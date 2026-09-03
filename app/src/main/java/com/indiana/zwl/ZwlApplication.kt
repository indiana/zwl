package com.indiana.zwl

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Environment
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.indiana.zwl.data.sync.SyncWorker
import com.indiana.zwl.presentation.map.util.clearParsedGeometryCache
import com.indiana.zwl.shared.di.androidModule
import com.indiana.zwl.shared.di.databaseModule
import com.indiana.zwl.shared.di.repositoryModule
import com.indiana.zwl.shared.di.sharedModule
import com.indiana.zwl.shared.offline.OfflineAreaJanitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZwlApplication : Application(), androidx.work.Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun writeCrashLog(t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val msg = "=== CRASH ${System.currentTimeMillis()} ===\n${sw.toString()}"
        // Write to internal storage — always accessible via app settings
        try {
            File(filesDir, "crash_log.txt").writeText(msg)
        } catch (_: Exception) {}
        // Also write to Downloads — accessible from device file manager
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dir.exists() || dir.mkdirs()) {
                File(dir, "zwl_crash_log.txt").writeText(msg)
            }
        } catch (_: Exception) {}
        try {
            Toast.makeText(this, "CRASH: ${t.message}", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            clearParsedGeometryCache()
        }
    }

    override fun onCreate() {
        try {
            super.onCreate()
        } catch (t: Throwable) {
            writeCrashLog(t)
            throw t
        }

        startKoin {
            androidLogger()
            androidContext(this@ZwlApplication)
            modules(sharedModule, databaseModule, androidModule, repositoryModule)
        }

        // One-shot offline store housekeeping (legacy map.mbtiles + orphans);
        // fire-and-forget so it never slows down app startup.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                org.koin.java.KoinJavaComponent.getKoin().get<OfflineAreaJanitor>().run()
            } catch (_: Exception) {
            }
        }

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(throwable)
            prev?.uncaughtException(thread, throwable)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZwlDataSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
