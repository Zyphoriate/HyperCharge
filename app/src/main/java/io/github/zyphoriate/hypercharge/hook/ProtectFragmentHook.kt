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
    private const val PREF_KEY_CUTOFF_CHECK = "cb_smart_charge_cutoff"
    private const val PREF_KEY_CUTOFF_SETTING = "pref_smart_charge_cutoff_value"

    private var appContext: Context? = null
    private lateinit var xposedInterface: XposedInterface
    private var settingButton: Any? = null  // The "Cutoff Value" TextPreference
    private var textPrefClass: Class<*>? = null

    fun apply(
        xposedInterface: XposedInterface,
        onCreatePrefsMethod: Method,
        onPreferenceClickMethod: Method,
        getPreferenceScreenMethod: Method,
        requireContextMethod: Method,
        @Suppress("UNUSED_PARAMETER") packageName: String,
    ) {
        this.xposedInterface = xposedInterface
        Log.i(TAG, "Hooking onCreatePreferences in $packageName")

        // Hook onCreatePreferences: add "断冲" checkbox to the battery protect category
        xposedInterface.hook(onCreatePrefsMethod).intercept { chain ->
            val fragment = chain.thisObject
            val result = chain.proceed()

            try {
                appContext = requireContextMethod.invoke(fragment) as? Context
                val ctx = appContext ?: return@intercept result

                if (isSmartChargeAvailable(fragment)) {
                    injectCutoffCheckbox(fragment, getPreferenceScreenMethod, requireContextMethod)
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(ctx, "HyperSmartCharge hooked OK", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    RemoteEventHelper.sendEvent(ctx, RemoteEventHelper.Event.UnregisterBatteryReceiver)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onCreatePreferences hook error", e)
            }
            result
        }

        // Hook onPreferenceClick: handle radio selection AND settings button click
        xposedInterface.hook(onPreferenceClickMethod).intercept { chain ->
            val fragment = chain.thisObject
            val preference = chain.args[0]
            try {
                val prefKey = preference?.javaClass?.getMethod("getKey")?.invoke(preference) as? String
                Log.d(TAG, "onPreferenceClick key: $prefKey")

                when (prefKey) {
                    PREF_KEY_CUTOFF_SETTING -> {
                        // Settings button clicked → launch miuix Activity
                        val ctx = appContext ?: requireContextMethod.invoke(fragment) as? Context
                        if (ctx != null) launchSettingsActivity(ctx)
                        return@intercept true
                    }
                    PREF_KEY_CUTOFF_CHECK -> {
                        // "断冲" radio selected → show settings button + set default value
                        val ctx = appContext ?: return@intercept chain.proceed()
                        val cur = ChargeProtectionUtils.getSmartChargePercentValue(ctx)
                        if (cur == null) {
                            ChargeProtectionUtils.openCommonProtectMode(70)
                            ChargeProtectionUtils.putSmartChargePercentValue(ctx, 70)
                        }
                        showSettingButton(true, ctx)
                        return@intercept true
                    }
                    // Another radio was selected (智慧充电保护 / 正常满充) → hide button
                    PREF_KEY_INTELLECT -> {
                        val ctx = appContext ?: return@intercept chain.proceed()
                        ChargeProtectionUtils.closeSmartCharge()
                        RemoteEventHelper.sendEvent(ctx, RemoteEventHelper.Event.UnregisterBatteryReceiver)
                        showSettingButton(false, ctx)
                        // Pass through to original handler
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onPreferenceClick hook error", e)
            }
            chain.proceed()
        }

        Log.i(TAG, "HyperSmartCharge hooks installed successfully")
    }

    private fun injectCutoffCheckbox(
        fragment: Any,
        getPreferenceScreenMethod: Method,
        requireContextMethod: Method,
    ) {
        val cl = fragment.javaClass.classLoader
        val context = requireContextMethod.invoke(fragment) as Context

        // Find the category to add our option
        val category = findPreference(fragment, PREF_KEY_CATEGORY) ?: return
        val prefBaseClass = cl.loadClass("androidx.preference.Preference")

        // 1. Add "断冲" — use the same class as existing preferences
        val existingPref = findPreference(category, PREF_KEY_INTELLECT)
        val radioClass = existingPref!!.javaClass

        val hasCutoffSet = ChargeProtectionUtils.getSmartChargePercentValue(context) != null

        val radioPref = radioClass.getConstructor(Context::class.java).newInstance(context).apply {
            radioClass.getMethod("setKey", String::class.java).invoke(this, PREF_KEY_CUTOFF_CHECK)
            radioClass.getMethod("setTitle", CharSequence::class.java)
                .invoke(this, getModuleString(context, "smart_charge_cutoff_title", "Charge Cutoff"))
        }
        category.javaClass.getMethod("addPreference", prefBaseClass).invoke(category, radioPref)

        // If cutoff was previously set, switch selection to our radio
        if (hasCutoffSet) {
            try {
                val getPreferenceCount = category.javaClass.getMethod("getPreferenceCount")
                val getPreference = category.javaClass.getMethod("getPreference", Int::class.java)
                val count = getPreferenceCount.invoke(category) as Int
                for (i in 0 until count) {
                    val pref = getPreference.invoke(category, i)
                    val checked = radioClass.getMethod("isChecked").invoke(pref) as? Boolean ?: false
                    if (checked) {
                        radioClass.getMethod("setChecked", Boolean::class.java).invoke(pref, false)
                        break
                    }
                }
                radioClass.getMethod("setChecked", Boolean::class.java).invoke(radioPref, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch selection to cutoff radio", e)
            }
        }

        // 2. Add "断冲值" settings button
        val tpc = cl.loadClass("miuix.preference.TextPreference").also { textPrefClass = it }
        val btn = tpc.getConstructor(Context::class.java).newInstance(context).apply {
            val lm = tpc.methods.find {
                it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1
            }
            lm?.invoke(this, fragment)
            tpc.getMethod("setKey", String::class.java).invoke(this, PREF_KEY_CUTOFF_SETTING)
            tpc.getMethod("setTitle", CharSequence::class.java)
                .invoke(this, getModuleString(context, "smart_charge_value_title", "Cutoff Value"))
            tpc.getMethod("setSummary", CharSequence::class.java)
                .invoke(this, getModuleString(context, "smart_charge_value_summary", "Tap to set the charge limit"))
            tpc.getMethod("setText", String::class.java).invoke(this, getSmartChargeValueText(context))
            tpc.getMethod("setVisible", Boolean::class.java).invoke(this, hasCutoffSet)
        }
        category.javaClass.getMethod("addPreference", prefBaseClass).invoke(category, btn)
        settingButton = btn

        Log.i(TAG, "Cutoff radio + settings button injected, hasCutoffSet=$hasCutoffSet")
    }

    private fun showSettingButton(visible: Boolean, context: Context) {
        val btn = settingButton ?: return
        val tpc = textPrefClass ?: return
        try {
            tpc.getMethod("setVisible", Boolean::class.java).invoke(btn, visible)
            if (visible) {
                tpc.getMethod("setText", String::class.java)
                    .invoke(btn, getSmartChargeValueText(context))
            }
        } catch (_: Exception) {}
    }

    private fun launchSettingsActivity(context: Context) {
        Log.d(TAG, "Launching SettingsActivity")
        try {
            context.startActivity(
                android.content.Intent().apply {
                    setClassName(
                        "io.github.zyphoriate.hypercharge",
                        "io.github.zyphoriate.hypercharge.ui.SettingsActivity"
                    )
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SettingsActivity", e)
            Toast.makeText(context, "Launch failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findPreference(who: Any, key: String): Any? {
        return try {
            var method: Method? = null
            var clazz: Class<*>? = who.javaClass
            while (clazz != null && method == null) {
                try { method = clazz.getDeclaredMethod("findPreference", String::class.java) }
                catch (_: NoSuchMethodException) {
                    try { method = clazz.getDeclaredMethod("findPreference", CharSequence::class.java) }
                    catch (_: NoSuchMethodException) { clazz = clazz.superclass }
                }
            }
            method?.invoke(who, key)
        } catch (e: Exception) {
            Log.e(TAG, "findPreference error on ${who.javaClass.simpleName}", e)
            null
        }
    }

    private fun isSmartChargeAvailable(fragment: Any): Boolean {
        val category = findPreference(fragment, PREF_KEY_CATEGORY)
        if (category == null) return false
        val intellect = findPreference(category, PREF_KEY_INTELLECT)
        return intellect != null
    }

    private fun getSmartChargeValueText(context: Context): String {
        val percent = ChargeProtectionUtils.getSmartChargePercentValue(context)
        return if (percent != null && ChargeProtectionUtils.isSmartChargePercentValueValid(percent)) {
            "$percent%"
        } else {
            getModuleString(context, "smart_charge_close", "Close")
        }
    }

    @Suppress("DiscouragedApi")
    private fun getModuleString(context: Context, name: String, fallback: String): String {
        return try {
            val resId = context.resources.getIdentifier(name, "string", "io.github.zyphoriate.hypercharge")
            if (resId != 0) context.resources.getString(resId) else fallback
        } catch (_: Exception) { fallback }
    }
}
