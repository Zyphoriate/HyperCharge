package io.github.zyphoriate.hypercharge

import android.annotation.SuppressLint
import io.github.zyphoriate.hypercharge.dexkit.DexQueries
import io.github.zyphoriate.hypercharge.hook.ProtectFragmentHook
import io.github.zyphoriate.hypercharge.utils.RemoteEventHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge

/**
 * libxposed API 101 entry point.
 *
 * Uses DexKit to locate target classes/methods by structural characteristics,
 * eliminating reliance on hard-coded class name strings.
 *
 * @author qingyu
 */
class MainModule(base: XposedInterface, modulePath: String) : XposedModule(base, modulePath) {

    companion object {
        init {
            System.loadLibrary("dexkit")
        }
    }

    @SuppressLint("PrivateApi")
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != "com.miui.securitycenter") return
        if (!param.isFirstPackage) return

        val apkPath = param.applicationInfo.sourceDir
        val classLoader = param.defaultClassLoader

        val bridge = DexKitBridge.create(apkPath)

        try {
            // Locate ChargeProtectFragment class
            val fragmentClassData = DexQueries.findChargeProtectFragment(bridge)
            val fragmentClass = fragmentClassData.getInstance(classLoader)

            // Locate onCreatePreferences method
            val onCreatePrefs = DexQueries.findOnCreatePreferencesMethod(bridge)
            val onCreatePrefsMethod = onCreatePrefs.getMethodInstance(classLoader)

            // Locate onPreferenceClick method
            val onPreferenceClick = DexQueries.findOnPreferenceClickMethod(bridge)
            val onPreferenceClickMethod = onPreferenceClick.getMethodInstance(classLoader)

            // Locate getPreferenceScreen method
            val getPreferenceScreen = DexQueries.findGetPreferenceScreenMethod(bridge)
            val getPreferenceScreenMethod = getPreferenceScreen.getMethodInstance(classLoader)

            // Locate findPreference method
            val findPreference = DexQueries.findFindPreferenceMethod(bridge)
            val findPreferenceMethod = findPreference.getMethodInstance(classLoader)

            // Locate requireContext method
            val requireContext = DexQueries.findRequireContextMethod(bridge)
            val requireContextMethod = requireContext.getMethodInstance(classLoader)

            // Register hooks
            ProtectFragmentHook.apply(
                base = base,
                onCreatePrefsMethod = onCreatePrefsMethod,
                onPreferenceClickMethod = onPreferenceClickMethod,
                getPreferenceScreenMethod = getPreferenceScreenMethod,
                findPreferenceMethod = findPreferenceMethod,
                requireContextMethod = requireContextMethod,
                fragmentClass = fragmentClass,
                packageName = param.packageName
            )
        } catch (e: Exception) {
            // DexKit location failed — module may not apply to this version
            android.util.Log.e("HyperSmartCharge", "Failed to locate target methods", e)
        } finally {
            bridge.close()
        }
    }
}
