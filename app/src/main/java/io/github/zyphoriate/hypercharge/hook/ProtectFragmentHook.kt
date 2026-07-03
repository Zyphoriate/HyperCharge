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

        // Hook onPreferenceClick: intercept clicks on our settings button
        xposedInterface.hook(onPreferenceClickMethod).intercept { chain ->
            val fragment = chain.thisObject
            val preference = chain.args[0]
            try {
                val prefKey = preference?.javaClass?.getMethod("getKey")?.invoke(preference) as? String
                if (prefKey == PREF_KEY_CUTOFF_SETTING) {
                    val ctx = appContext ?: requireContextMethod.invoke(fragment) as? Context
                    if (ctx != null) launchSettingsActivity(ctx)
                    return@intercept true
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

        // 1. Add "断冲" SingleChoicePreference (category only accepts this type)
        val radioClass = cl.loadClass("miuix.preference.SingleChoicePreference")
        val textPrefClass = cl.loadClass("miuix.preference.TextPreference")

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
                // Uncheck the currently checked item
                val getPreferenceCount = category.javaClass.getMethod("getPreferenceCount")
                val getPreference = category.javaClass.getMethod("getPreference", Int::class.java)
                val count = getPreferenceCount.invoke(category) as Int
                for (i in 0 until count) {
                    val pref = getPreference.invoke(category, i)
                    val isChecked = radioClass.getMethod("isChecked").invoke(pref) as? Boolean ?: false
                    if (isChecked) {
                        radioClass.getMethod("setChecked", Boolean::class.java).invoke(pref, false)
                        break
                    }
                }
                // Check our radio
                radioClass.getMethod("setChecked", Boolean::class.java).invoke(radioPref, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch selection to cutoff radio", e)
            }
        }

        // 2. Add clickable setting button (shown when cutoff is enabled)
        val settingButton = textPrefClass.getConstructor(Context::class.java).newInstance(context).apply {
            val lm = textPrefClass.methods.find {
                it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1
            }
            lm?.invoke(this, fragment)
            textPrefClass.getMethod("setKey", String::class.java).invoke(this, PREF_KEY_CUTOFF_SETTING)
            textPrefClass.getMethod("setTitle", CharSequence::class.java)
                .invoke(this, getModuleString(context, "smart_charge_value_title", "Cutoff Value"))
            textPrefClass.getMethod("setSummary", CharSequence::class.java)
                .invoke(this, getModuleString(context, "smart_charge_value_summary", "Tap to set the charge limit"))
            textPrefClass.getMethod("setText", String::class.java).invoke(this, getSmartChargeValueText(context))
            textPrefClass.getMethod("setVisible", Boolean::class.java).invoke(this, hasCutoffSet)
        }
        // Add button to PreferenceScreen (not the category — it only accepts SingleChoicePreference)
        val preferenceScreen = getPreferenceScreenMethod.invoke(fragment)
        preferenceScreen.javaClass.getMethod("addPreference", prefBaseClass).invoke(preferenceScreen, settingButton)

        // Hook setChecked to toggle setting button + charge mode
        xposedInterface.hook(
            radioClass.getMethod("setChecked", Boolean::class.java)
        ).intercept { chain ->
            val key = try { radioClass.getMethod("getKey").invoke(chain.thisObject) as? String }
                catch (_: Exception) { null }
            if (key != PREF_KEY_CUTOFF_CHECK) return@intercept chain.proceed()

            val newValue = chain.args[0] as Boolean
            if (newValue) {
                val cur = ChargeProtectionUtils.getSmartChargePercentValue(context)
                if (cur == null) {
                    ChargeProtectionUtils.openCommonProtectMode(70)
                    ChargeProtectionUtils.putSmartChargePercentValue(context, 70)
                }
            } else {
                ChargeProtectionUtils.closeSmartCharge()
                RemoteEventHelper.sendEvent(context, RemoteEventHelper.Event.UnregisterBatteryReceiver)
            }
            val result = chain.proceed()
            try {
                textPrefClass.getMethod("setVisible", Boolean::class.java).invoke(settingButton, newValue)
                textPrefClass.getMethod("setText", String::class.java)
                    .invoke(settingButton, getSmartChargeValueText(context))
            } catch (_: Exception) {}
            result
        }

        Log.i(TAG, "Cutoff checkbox + settings button injected")
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
