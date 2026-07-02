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
            // DexKit: locate obfuscated class and hook-target methods
            val fragmentClassData = DexQueries.findChargeProtectFragment(bridge)
            val fragmentClass = fragmentClassData.getInstance(classLoader)

            val onCreatePrefsMethod = DexQueries.findOnCreatePreferencesMethod(bridge)
                .getMethodInstance(classLoader)

            val onPreferenceClickMethod = DexQueries.findOnPreferenceClickMethod(bridge)
                .getMethodInstance(classLoader)

            // Standard framework methods — use simple reflection
            val getPreferenceScreenMethod = fragmentClass.getMethod("getPreferenceScreen")
            val findPreferenceMethod = run {
                try { fragmentClass.getMethod("findPreference", String::class.java) }
                catch (_: NoSuchMethodException) { fragmentClass.getMethod("findPreference", CharSequence::class.java) }
            }
            val requireContextMethod = fragmentClass.getMethod("requireContext")

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
