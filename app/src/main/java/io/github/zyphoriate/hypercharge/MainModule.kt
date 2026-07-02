package io.github.zyphoriate.hypercharge

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.zyphoriate.hypercharge.dexkit.DexQueries
import io.github.zyphoriate.hypercharge.hook.ProtectFragmentHook
import org.luckypray.dexkit.DexKitBridge

/**
 * libxposed API 101 entry point for the main process.
 *
 * Uses DexKit to locate target classes/methods by structural characteristics,
 * eliminating reliance on hard-coded class name strings.
 *
 * @author qingyu
 */
class MainModule : XposedModule() {

    companion object {
        init {
            System.loadLibrary("dexkit")
        }
    }

    @SuppressLint("PrivateApi")
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != "com.miui.securitycenter") return
        if (!param.isFirstPackage) return

        val apkPath = param.applicationInfo.sourceDir
        val classLoader = param.defaultClassLoader

        val bridge = DexKitBridge.create(apkPath)

        try {
            val fragmentClassData = DexQueries.findChargeProtectFragment(bridge)
            val fragmentClass = fragmentClassData.getInstance(classLoader)

            val onCreatePrefs = DexQueries.findOnCreatePreferencesMethod(bridge)
            val onCreatePrefsMethod = onCreatePrefs.getMethodInstance(classLoader)

            val onPreferenceClick = DexQueries.findOnPreferenceClickMethod(bridge)
            val onPreferenceClickMethod = onPreferenceClick.getMethodInstance(classLoader)

            val getPreferenceScreen = DexQueries.findGetPreferenceScreenMethod(bridge)
            val getPreferenceScreenMethod = getPreferenceScreen.getMethodInstance(classLoader)

            val findPreference = DexQueries.findFindPreferenceMethod(bridge)
            val findPreferenceMethod = findPreference.getMethodInstance(classLoader)

            val requireContext = DexQueries.findRequireContextMethod(bridge)
            val requireContextMethod = requireContext.getMethodInstance(classLoader)

            ProtectFragmentHook.apply(
                xposedInterface = this,
                onCreatePrefsMethod = onCreatePrefsMethod,
                onPreferenceClickMethod = onPreferenceClickMethod,
                getPreferenceScreenMethod = getPreferenceScreenMethod,
                findPreferenceMethod = findPreferenceMethod,
                requireContextMethod = requireContextMethod,
                fragmentClass = fragmentClass,
                packageName = param.packageName
            )
        } catch (e: Exception) {
            android.util.Log.e("HyperSmartCharge", "Failed to locate target methods", e)
        } finally {
            bridge.close()
        }
    }
}
