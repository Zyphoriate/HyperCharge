package io.github.zyphoriate.hypercharge.receiver

import android.annotation.SuppressLint
import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.zyphoriate.hypercharge.utils.ProtectNotificationHelper

/**
 * Module entry for the .remote process of Security Center.
 *
 * Registers battery change receiver to show/hide the charge protection
 * notification based on charging state.
 *
 * @author qingyu
 */
class ModuleReceiver : XposedModule() {

    private var isRemoteProcess = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        isRemoteProcess = param.processName == "com.miui.securitycenter.remote"
    }

    @SuppressLint("PrivateApi")
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (!isRemoteProcess) return
        if (param.packageName != "com.miui.securitycenter") return

        val context = getApplicationContext()
        if (context != null) {
            ProtectNotificationHelper.registerBatteryReceiver(context)
        }
    }

    @SuppressLint("PrivateApi")
    private fun getApplicationContext(): Context? {
        return try {
            val cls = Class.forName("android.app.ActivityThread")
            val at = cls.getMethod("currentActivityThread").invoke(null)
            cls.getMethod("getApplication").invoke(at) as? Context
        } catch (e: Exception) {
            android.util.Log.e("HyperSmartCharge", "Failed to get ApplicationContext", e)
            null
        }
    }
}
