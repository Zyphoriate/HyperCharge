package cn.buffcow.hypersc.receiver

import android.annotation.SuppressLint
import android.content.Context
import cn.buffcow.hypersc.utils.ProtectNotificationHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * Module entry for the .remote process of Security Center.
 *
 * Registers battery change receiver to show/hide the charge protection
 * notification based on charging state.
 *
 * The remote process receives cross-process events from the main process
 * via RemoteEventHelper when the user changes the charge protection value.
 *
 * @author qingyu
 */
class ModuleReceiver(base: XposedInterface, modulePath: String) : XposedModule(base, modulePath) {

    @SuppressLint("PrivateApi")
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != "com.miui.securitycenter") return
        if (param.processName != "com.miui.securitycenter.remote") return

        // Obtain Application Context via reflection on ActivityThread
        val context = getApplicationContext()
        if (context != null) {
            ProtectNotificationHelper.registerBatteryReceiver(context)
        }
    }

    /**
     * Obtain the Application Context via reflection.
     * Since libxposed API 101's XposedModule does not provide a direct Context
     * getter, we use the standard ActivityThread.currentApplication() approach.
     */
    @SuppressLint("PrivateApi")
    private fun getApplicationContext(): Context? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread")
            val activityThread = currentActivityThreadMethod.invoke(null)
            val getApplicationMethod = activityThreadClass.getMethod("getApplication")
            getApplicationMethod.invoke(activityThread) as? Context
        } catch (e: Exception) {
            android.util.Log.e("HyperSmartCharge", "Failed to get ApplicationContext", e)
            null
        }
    }
}
