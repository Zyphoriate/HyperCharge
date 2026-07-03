package io.github.zyphoriate.hypercharge.hook

import android.content.Context
import android.util.Log
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.zyphoriate.hypercharge.BuildConfig
import io.github.zyphoriate.hypercharge.utils.ChargeProtectionUtils
import io.github.zyphoriate.hypercharge.utils.RemoteEventHelper
import java.lang.reflect.Method

object ProtectFragmentHook {

    private const val TAG = "HyperSmartCharge"
    private const val PREF_KEY_INTELLECT = "cb_intellect_charge_protect"
    private const val PREF_KEY_CATEGORY = "category_features_battery_protect"
    private const val PREF_KEY_CUTOFF = "cb_smart_charge_cutoff"
    private const val PREF_KEY_CUTOFF_SETTING = "pref_smart_charge_cutoff_value"

    private var appContext: Context? = null
    private lateinit var xposed: XposedInterface

    fun apply(
        xposedInterface: XposedInterface,
        onCreatePrefsMethod: Method,
        onPreferenceClickMethod: Method,
        onPreferenceChangeMethod: Method,
        getPreferenceScreenMethod: Method,
        requireContextMethod: Method,
        @Suppress("UNUSED_PARAMETER") packageName: String,
    ) {
        xposed = xposedInterface
        Log.i(TAG, "Hooking ...")

        // Hook 1: onCreatePreferences — inject cutoff radio + settings button
        xposed.hook(onCreatePrefsMethod).intercept { chain ->
            val fragment = chain.thisObject
            val result = chain.proceed()
            try {
                appContext = requireContextMethod.invoke(fragment) as? Context
                val ctx = appContext ?: return@intercept result
                if (isSmartChargeAvailable(fragment)) {
                    injectCutoffUI(fragment, getPreferenceScreenMethod, requireContextMethod)
                    if (BuildConfig.DEBUG) Toast.makeText(ctx, "HyperSmartCharge OK", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "inject error", e)
            }
            result
        }

        // Hook 2: onPreferenceChange — detect when cutoff radio is toggled
        xposed.hook(onPreferenceChangeMethod).intercept { chain ->
            val preference = chain.args[0]
            val newValue = chain.args[1]
            val key = try { preference?.javaClass?.getMethod("getKey")?.invoke(preference) as? String }
                catch (_: Exception) { null }

            if (key == PREF_KEY_CUTOFF) {
                val enabled = (newValue as? Boolean) ?: false
                Log.d(TAG, "Cutoff toggled: $enabled")
                val ctx = appContext
                if (ctx != null) {
                    if (enabled) {
                        val cur = ChargeProtectionUtils.getSmartChargePercentValue(ctx)
                        if (cur == null) {
                            ChargeProtectionUtils.openCommonProtectMode(70)
                            ChargeProtectionUtils.putSmartChargePercentValue(ctx, 70)
                        }
                    } else {
                        ChargeProtectionUtils.closeSmartCharge()
                        ChargeProtectionUtils.putSmartChargePercentValue(ctx, null)
                        RemoteEventHelper.sendEvent(ctx, RemoteEventHelper.Event.UnregisterBatteryReceiver)
                    }
                    // Update settings button
                    updateSettingsButton(ctx, enabled)
                }
            }

            chain.proceed()
        }

        // Hook 3: onPreferenceClick — launch SettingsActivity on button click
        xposed.hook(onPreferenceClickMethod).intercept { chain ->
            val preference = chain.args[0]
            val key = try { preference?.javaClass?.getMethod("getKey")?.invoke(preference) as? String }
                catch (_: Exception) { null }

            if (key == PREF_KEY_CUTOFF_SETTING) {
                launchSettingsActivity(appContext ?: return@intercept chain.proceed())
                return@intercept true
            }
            chain.proceed()
        }

        Log.i(TAG, "Hooks installed")
    }

    private fun injectCutoffUI(
        fragment: Any,
        getPreferenceScreenMethod: Method,
        requireContextMethod: Method,
    ) {
        val cl = fragment.javaClass.classLoader
        val context = requireContextMethod.invoke(fragment) as Context
        val radioClass = cl.loadClass("miuix.preference.SingleChoicePreference")
        val prefBaseClass = cl.loadClass("androidx.preference.Preference")
        val textPrefClass = cl.loadClass("miuix.preference.TextPreference")
        val category = findPreference(fragment, PREF_KEY_CATEGORY) ?: return
        val hasCutoff = ChargeProtectionUtils.getSmartChargePercentValue(context) != null

        // 1. Add cutoff radio (unchecked)
        val radioPref = radioClass.getConstructor(Context::class.java).newInstance(context).apply {
            radioClass.getMethod("setKey", String::class.java).invoke(this, PREF_KEY_CUTOFF)
            radioClass.getMethod("setTitle", CharSequence::class.java)
                .invoke(this, modString(context, "smart_charge_cutoff_title", "Charge Cutoff"))
            // Ensure unchecked before adding (avoids "Already has a checked item")
            try { radioClass.getMethod("setChecked", Boolean::class.java).invoke(this, false) }
            catch (_: Exception) {}
        }
        category.javaClass.getMethod("addPreference", prefBaseClass).invoke(category, radioPref)

        // 2. Add settings button below category (on PreferenceScreen)
        val settingButton = textPrefClass.getConstructor(Context::class.java).newInstance(context).apply {
            textPrefClass.methods.find { it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1 }
                ?.invoke(this, fragment)
            textPrefClass.getMethod("setKey", String::class.java).invoke(this, PREF_KEY_CUTOFF_SETTING)
            textPrefClass.getMethod("setTitle", CharSequence::class.java)
                .invoke(this, modString(context, "smart_charge_value_title", "Cutoff Value"))
            textPrefClass.getMethod("setText", String::class.java)
                .invoke(this, getValueText(context))
            textPrefClass.getMethod("setVisible", Boolean::class.java).invoke(this, hasCutoff)
        }
        val screen = getPreferenceScreenMethod.invoke(fragment)
        screen.javaClass.getMethod("addPreference", prefBaseClass).invoke(screen, settingButton)

        // 3. If cutoff was previously enabled, select it now
        if (hasCutoff) {
            try {
                val getCount = category.javaClass.getMethod("getPreferenceCount")
                val getPref = category.javaClass.getMethod("getPreference", Int::class.java)
                val count = getCount.invoke(category) as Int
                for (i in 0 until count) {
                    val p = getPref.invoke(category, i)
                    val k = try { radioClass.getMethod("getKey").invoke(p) as? String }
                        catch (_: Exception) { null }
                    if (k == PREF_KEY_CUTOFF) continue
                    try { radioClass.getMethod("setChecked", Boolean::class.java).invoke(p, false) }
                    catch (_: Exception) {}
                }
                radioClass.getMethod("setChecked", Boolean::class.java).invoke(radioPref, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to select cutoff", e)
            }
        }

        Log.i(TAG, "UI injected, hasCutoff=$hasCutoff")
    }

    private fun updateSettingsButton(context: Context, visible: Boolean) {
        try {
            val pref = findPreference(appContext, PREF_KEY_CUTOFF_SETTING)
                ?: return
            pref.javaClass.getMethod("setVisible", Boolean::class.java).invoke(pref, visible)
            pref.javaClass.getMethod("setText", String::class.java)
                .invoke(pref, getValueText(context))
        } catch (e: Exception) {
            Log.e(TAG, "updateSettingsButton error", e)
        }
    }

    private fun launchSettingsActivity(context: Context) {
        try {
            context.startActivity(android.content.Intent().apply {
                setClassName("io.github.zyphoriate.hypercharge", ".ui.SettingsActivity")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "launch failed", e)
        }
    }

    private fun findPreference(who: Any?, key: String): Any? {
        if (who == null) return null
        var clazz: Class<*>? = who.javaClass
        while (clazz != null) {
            try {
                val m = clazz.getDeclaredMethod("findPreference", CharSequence::class.java)
                return m.invoke(who, key)
            } catch (_: NoSuchMethodException) {
                try {
                    val m = clazz.getDeclaredMethod("findPreference", String::class.java)
                    return m.invoke(who, key)
                } catch (_: NoSuchMethodException) { clazz = clazz.superclass }
            }
        }
        return null
    }

    private fun isSmartChargeAvailable(fragment: Any): Boolean {
        val cat = findPreference(fragment, PREF_KEY_CATEGORY) ?: return false
        return findPreference(cat, PREF_KEY_INTELLECT) != null
    }

    private fun getValueText(context: Context): String {
        val p = ChargeProtectionUtils.getSmartChargePercentValue(context)
        return if (p != null && ChargeProtectionUtils.isSmartChargePercentValueValid(p)) "$p%" else "Close"
    }

    @Suppress("DiscouragedApi")
    private fun modString(context: Context, name: String, fallback: String): String {
        return try {
            val id = context.resources.getIdentifier(name, "string", "io.github.zyphoriate.hypercharge")
            if (id != 0) context.resources.getString(id) else fallback
        } catch (_: Exception) { fallback }
    }
}
