package io.github.zyphoriate.hypercharge

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.zyphoriate.hypercharge.dexkit.DexQueries
import io.github.zyphoriate.hypercharge.hook.ProtectFragmentHook
import org.luckypray.dexkit.DexKitBridge

class MainModule : XposedModule() {

    companion object {
        init { System.loadLibrary("dexkit") }
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

            // DexKit: onCreatePreferences (obfuscated, defined in MIUI code)
            val onCreatePrefsMethod = DexQueries.findOnCreatePreferencesMethod(bridge)
                .getMethodInstance(classLoader)

            // onPreferenceClick — inherited from PreferenceFragmentCompat, not obfuscated.
            // Must use fragmentClass.classLoader to resolve the Preference parameter type.
            val prefClass = fragmentClass.classLoader
                .loadClass("androidx.preference.Preference")
            val onPreferenceClickMethod = fragmentClass.getMethod("onPreferenceClick", prefClass)

            val onPreferenceChangeMethod = fragmentClass.getMethod("onPreferenceChange", prefClass, Any::class.java)
            val getPreferenceScreenMethod = fragmentClass.getMethod("getPreferenceScreen")
            val requireContextMethod = fragmentClass.getMethod("requireContext")

            ProtectFragmentHook.apply(
                xposedInterface = this,
                onCreatePrefsMethod = onCreatePrefsMethod,
                onPreferenceClickMethod = onPreferenceClickMethod,
                onPreferenceChangeMethod = onPreferenceChangeMethod,
                getPreferenceScreenMethod = getPreferenceScreenMethod,
                requireContextMethod = requireContextMethod,
                packageName = param.packageName
            )
        } catch (e: Exception) {
            android.util.Log.e("HyperSmartCharge", "Failed to locate target methods", e)
        } finally {
            bridge.close()
        }
    }
}
