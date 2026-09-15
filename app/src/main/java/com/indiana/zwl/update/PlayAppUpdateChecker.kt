package com.indiana.zwl.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class PlayAppUpdateChecker(
    private val activity: ComponentActivity,
    private val updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>
) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            manager.completeUpdate()
        }
    }

    fun check() {
        manager.registerListener(installListener)
        manager.appUpdateInfo
            .addOnSuccessListener { info -> handleInfo(info) }
            .addOnFailureListener { }
    }

    fun dispose() {
        manager.unregisterListener(installListener)
    }

    private fun handleInfo(info: AppUpdateInfo) {
        when {
            info.installStatus() == InstallStatus.DOWNLOADED -> manager.completeUpdate()
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> startUpdateFlow(info)
        }
    }

    private fun startUpdateFlow(info: AppUpdateInfo) {
        val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        manager.startUpdateFlowForResult(info, updateFlowLauncher, options)
    }
}
